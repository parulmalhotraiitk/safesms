package com.safesms.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

sealed class DownloadState {
    object Idle : DownloadState()
    data class Downloading(val progress: Float) : DownloadState()
    object Completed : DownloadState()
    data class Error(val message: String) : DownloadState()
}

class ModelDownloader(private val context: Context) {

    private val modelUrl = "https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm/resolve/main/gemma-4-E4B-it.litertlm?download=true"

    fun downloadModel(): Flow<DownloadState> = flow {
        val destFile = File(context.getExternalFilesDir(null), "gemma-4-E4B-it.litertlm")
        val tempFile = File(context.getExternalFilesDir(null), "gemma-4-E4B-it.litertlm.tmp")

        try {
            emit(DownloadState.Downloading(0f))
            
            var connection: HttpURLConnection
            var url = modelUrl
            var redirects = 0
            
            while (true) {
                connection = URL(url).openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = false // We handle cross-domain manually
                connection.connect()

                val status = connection.responseCode
                if (status == HttpURLConnection.HTTP_MOVED_TEMP || 
                    status == HttpURLConnection.HTTP_MOVED_PERM || 
                    status == HttpURLConnection.HTTP_SEE_OTHER ||
                    status == 307 || status == 308) {
                    
                    url = connection.getHeaderField("Location") ?: throw Exception("Redirect missing location")
                    connection.disconnect()
                    redirects++
                    if (redirects > 10) throw Exception("Too many redirects")
                } else if (status in 200..299) {
                    break // Connected successfully
                } else {
                    throw Exception("HTTP Error: $status")
                }
            }

            val totalBytes = connection.getHeaderField("Content-Length")?.toLongOrNull() ?: -1L
            val inputStream = connection.inputStream
            val outputStream = FileOutputStream(tempFile)

            val buffer = ByteArray(32 * 1024) // Increased buffer size for faster download
            var bytesRead: Int
            var downloadedBytes: Long = 0

            var lastProgressReportTime = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                downloadedBytes += bytesRead
                
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastProgressReportTime > 200 || downloadedBytes == totalBytes) { 
                    val progress = if (totalBytes > 0) downloadedBytes.toFloat() / totalBytes.toFloat() else 0f
                    emit(DownloadState.Downloading(progress))
                    lastProgressReportTime = currentTime
                }
            }

            outputStream.flush()
            outputStream.close()
            inputStream.close()
            connection.disconnect()

            if (tempFile.exists() && tempFile.length() > 0) {
                if (destFile.exists()) destFile.delete()
                tempFile.renameTo(destFile)
                emit(DownloadState.Completed)
            } else {
                throw Exception("File is empty after download.")
            }

        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            emit(DownloadState.Error(e.message ?: "Unknown error occurred"))
        }
    }.flowOn(Dispatchers.IO)
}
