package com.safesms.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        var sender = "Unknown"
        var body = ""

        if (intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            for (message in messages) {
                sender = message.originatingAddress ?: "Unknown"
                body += message.messageBody ?: ""
            }
            Log.d("SafeSMS", "SMS Received from $sender: $body")
            if (SmsRepository.isUiActive) {
                SmsRepository.emitSms(body)
            }
        } else if (intent.action == "com.safesms.app.DEMO_SMS") {
            sender = "Demo Contact"
            body = intent.getStringExtra("message_body") ?: ""
            Log.d("SafeSMS", "🚨 DEMO SMS Triggered via ADB: $body")
            if (SmsRepository.isUiActive) {
                SmsRepository.emitSms(body)
            }
        } else {
            return
        }

        if (body.isBlank()) return

        // If the UI is open, MainActivity will run the inference via SmsRepository flow
        // Do not run background inference at the same time or the GPU/NPU will crash
        if (SmsRepository.isUiActive) {
            Log.d("SafeSMS-LiteRT", "UI is active. Skipping background inference.")
            return
        }

        Log.d("SafeSMS-LiteRT", "Starting SmsAnalysisService...")
        val serviceIntent = Intent(context, SmsAnalysisService::class.java).apply {
            putExtra("sender", sender)
            putExtra("body", body)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
