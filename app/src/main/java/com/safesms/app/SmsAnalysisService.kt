package com.safesms.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class SmsAnalysisService : Service() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private val channelId = "safesms_alerts"

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra("sender") ?: "Unknown"
        val body = intent?.getStringExtra("body") ?: ""

        if (body.isBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 1. Immediately post a Foreground Notification to satisfy Android 8+ restrictions
        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("sms_body", body)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            body.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setContentTitle("SafeSMS AI")
            .setContentText("Analyzing incoming message from $sender...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(199, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(199, notification)
        }

        // 2. Run the heavy 3.6GB LiteRT Inference
        serviceScope.launch {
            try {
                Log.d("SafeSMS-LiteRT", "Service: Starting background inference...")
                val modelController = SafeSMSModelController(applicationContext)
                var fullResponse = ""
                
                modelController.analyzeMessageStream(body)
                    .catch { e -> Log.e("SafeSMS", "Service analysis error", e) }
                    .collect { chunk -> fullResponse += chunk }
                
                val result = modelController.parseStreamedResult(fullResponse)
                Log.d("SafeSMS-LiteRT", "Service Inference Complete: ${result.label}")
                
                // 3. Update the notification with the final result
                showFinalNotification(sender, body, result)
            } catch (e: Exception) {
                Log.e("SafeSMS", "Failed to run service inference", e)
            } finally {
                // 4. Shut down the service to save battery
                stopForeground(true)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    private fun showFinalNotification(sender: String, body: String, result: VerificationResult) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val openIntent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("sms_body", body)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            body.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val isScam = result.label.uppercase() == "SCAM" || result.label.uppercase() == "SUSPICIOUS"
        val title = if (isScam) "🚨 Scam Alert: ${result.label}" else "✅ Message Safe"
        
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText("From $sender: ${result.reason}")
            .setStyle(NotificationCompat.BigTextStyle().bigText("From $sender: $body\n\nAI Reason: ${result.reason}\nAdvice: ${result.action}"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        if (isScam) {
            notificationBuilder.setColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        } else {
            notificationBuilder.setColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
        }

        // Use a different ID from the foreground service so it stays when service stops
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "SafeSMS Scam Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts for incoming SMS analysis"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }
}
