package com.safesms.app

import android.content.Context
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Session
import com.google.ai.edge.litertlm.SessionConfig
import com.google.ai.edge.litertlm.InputData
import com.google.ai.edge.litertlm.ResponseCallback
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import android.util.Log
import java.io.File

class SafeSMSModelController(private val context: Context) {

    private var llmEngine: Engine? = null

    fun isModelDownloaded(): Boolean {
        val modelFile = File(context.getExternalFilesDir(null), "gemma-4-E4B-it.litertlm")
        return modelFile.exists() && modelFile.length() > 0
    }

    suspend fun initializeModel() = withContext(Dispatchers.IO) {
        val modelFile = File(context.getExternalFilesDir(null), "gemma-4-E4B-it.litertlm")
        
        if (!modelFile.exists()) {
            return@withContext 
        }

        try {
            Log.d("SafeSMS-LiteRT", "⚡ Booting Edge Engine...")
            Log.d("SafeSMS-LiteRT", "📦 Loading 3.6GB weights from local storage: ${modelFile.absolutePath}")
            
            var engine: Engine? = null
            Log.d("SafeSMS-LiteRT", "Initializing CPU inference engine...")
            val config = EngineConfig(modelPath = modelFile.absolutePath)
            engine = Engine(config)
            engine.initialize()
            
            llmEngine = engine
            Log.d("SafeSMS-LiteRT", "✅ Engine Initialized perfectly. Ready for offline inference.")
        } catch (e: Exception) {
            Log.e("SafeSMS-LiteRT", "❌ Failed to load model: ${e.message}")
            e.printStackTrace()
        }
    }

    fun analyzeMessageStream(userMessage: String): Flow<String> = callbackFlow {
        val basePrompt = """<start_of_turn>user
You are a strict, highly accurate scam detection AI. Analyze the following incoming text message:

"$userMessage"

Classify the message above as exactly one of: SAFE, SUSPICIOUS, or SCAM.
(Note: Normal conversations, greetings like "Hi", or safe texts must be classified as SAFE).

Respond ONLY with a raw JSON object. Do not use markdown blocks. Use this exact format:
{
  "label": "SAFE" | "SUSPICIOUS" | "SCAM",
  "confidence": 99,
  "reason": "Short reason here.",
  "action": "Short advice here."
}<end_of_turn>
<start_of_turn>model
"""

        if (llmEngine == null) {
            initializeModel()
        }
        
        val engine = llmEngine
        if (engine == null) {
            trySend("{\"label\": \"ERROR\", \"confidence\": 0, \"reason\": \"Model not found. Push .litertlm file to Android/data/com.safesms.app/files/\", \"action\": \"Check ADB connection.\"}")
            close()
            return@callbackFlow
        }

        try {
            Log.d("SafeSMS-LiteRT", "🧠 Creating Inference Session...")
            val session = engine.createSession(SessionConfig())
            Log.d("SafeSMS-LiteRT", "🚀 Starting local text generation stream...")
            
            session.generateContentStream(listOf(InputData.Text(basePrompt)), object : ResponseCallback {
                override fun onNext(chunk: String) {
                    Log.d("SafeSMS-LiteRT", "Token [ ${chunk.replace("\n", "\\n")} ]")
                    trySend(chunk)
                }
                override fun onDone() {
                    Log.d("SafeSMS-LiteRT", "🏁 Inference Stream Complete. Closing session.")
                    close()
                }
                override fun onError(e: Throwable) {
                    Log.e("SafeSMS-LiteRT", "⚠️ Engine Error: ${e.message}")
                    trySend("{\"label\": \"ERROR\", \"confidence\": 0, \"reason\": \"${e.message}\", \"action\": \"Manual review needed.\"}")
                    close(e)
                }
            })
            awaitClose {
                session.close()
            }
        } catch (e: Exception) {
            trySend("{\"label\": \"ERROR\", \"confidence\": 0, \"reason\": \"${e.message}\", \"action\": \"Manual review needed.\"}")
            close(e)
        }
    }.flowOn(Dispatchers.IO)

    // Helper to safely parse the accumulated JSON once the stream finishes
    fun parseStreamedResult(fullResponse: String): VerificationResult {
        return try {
            val cleanStr = fullResponse.substringAfter("{").substringBeforeLast("}")
            val jsonStr = "{${cleanStr.trim()}}"
            val json = JSONObject(jsonStr)

            VerificationResult(
                label = json.optString("label", "SUSPICIOUS"),
                confidence = json.optInt("confidence", 50),
                reason = json.optString("reason", "Could not analyze clearly."),
                action = json.optString("action", "Stay alert.")
            )
        } catch (e: Exception) {
             VerificationResult(
                label = "ERROR", 
                confidence = 0, 
                reason = "Failed to parse JSON: ${e.message}", 
                action = "Check model prompt format."
            )
        }
    }
    
    fun close() {
        llmEngine?.close()
        llmEngine = null
    }
}

data class VerificationResult(
    val label: String,
    val confidence: Int,
    val reason: String,
    val action: String
)
