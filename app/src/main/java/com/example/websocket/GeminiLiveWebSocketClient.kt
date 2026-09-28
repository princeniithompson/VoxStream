package com.example.websocket

import android.util.Base64
import android.util.Log
import com.example.data.ConnectionState
import com.example.data.LogLevel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class GeminiLiveWebSocketClient(
    private val onSetupComplete: () -> Unit,
    private val onInterimTranscription: (String) -> Unit,
    private val onFinalizedTranscription: (String) -> Unit,
    private val onStateChanged: (ConnectionState) -> Unit,
    private val onLog: (LogLevel, String, String, String?) -> Unit,
    private val onError: (String) -> Unit
) {
    companion object {
        private const val TAG = "GeminiLiveWS"
        const val DEFAULT_MODEL = "models/gemini-3.5-transcribe-live"
        private const val WS_BASE_URL =
            "wss://generativelanguage.googleapis.com/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read timeout for persistent WebSocket
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val isSetupComplete = AtomicBoolean(false)
    private val isConnecting = AtomicBoolean(false)
    private var activeModel: String = DEFAULT_MODEL

    val setupComplete: Boolean
        get() = isSetupComplete.get()

    private var isSmartMode: Boolean = false

    fun connect(apiKey: String, model: String = DEFAULT_MODEL, smartMode: Boolean = false) {
        if (apiKey.isBlank() || apiKey == "MY_GEMINI_API_KEY") {
            val errMsg = "Gemini API Key is missing or placeholder. Please provide a valid key in Secrets or the Diagnostics sheet."
            onLog(LogLevel.ERROR, TAG, errMsg, null)
            onError(errMsg)
            onStateChanged(ConnectionState.Error(errMsg))
            return
        }

        activeModel = model
        isSmartMode = smartMode
        isSetupComplete.set(false)
        isConnecting.set(true)
        onStateChanged(ConnectionState.Connecting)

        val url = "$WS_BASE_URL?key=$apiKey"
        val request = Request.Builder()
            .url(url)
            .build()

        val modeLabel = if (smartMode) "SMART (filler-cleanup + punctuation)" else "VERBATIM (raw fastest)"
        onLog(LogLevel.INFO, TAG, "Opening WebSocket connection to Gemini Live Transcribe ($model) in $modeLabel mode", null)

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnecting.set(false)
                Log.d(TAG, "WebSocket connection opened. Sending Step A initial setup JSON ($modeLabel)...")
                onLog(LogLevel.INFO, TAG, "WebSocket opened. Sending initial setup payload ($modeLabel)...", null)
                onStateChanged(ConnectionState.ConnectedWaitingSetup)

                // Step A: Send initial setup JSON
                // Off: inputAudioTranscription = {}
                // On: inputAudioTranscription = { "mode": "SMART" }
                val transcriptionConfig = JSONObject().apply {
                    if (isSmartMode) {
                        put("mode", "SMART")
                    }
                }

                val setupJson = JSONObject().apply {
                    val setupObj = JSONObject().apply {
                        put("model", activeModel)
                        put("generationConfig", JSONObject().apply {
                            put("responseModalities", org.json.JSONArray().apply {
                                put("TEXT")
                            })
                        })
                        put("inputAudioTranscription", transcriptionConfig)
                        put("realtimeInputConfig", JSONObject().apply {
                            put("automaticActivityDetection", JSONObject().apply {
                                put("startOfSpeechSensitivity", "START_SENSITIVITY_LOW")
                                put("endOfSpeechSensitivity", "END_SENSITIVITY_LOW")
                                put("prefixPaddingMs", 300)
                                put("silenceDurationMs", 2000)
                            })
                        })
                    }
                    put("setup", setupObj)
                }

                val payload = setupJson.toString()
                val sent = webSocket.send(payload)
                if (sent) {
                    onLog(LogLevel.SENT, TAG, "Setup message sent to server", payload)
                } else {
                    onLog(LogLevel.ERROR, TAG, "Failed to send setup message over socket", payload)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Google Live can send responses as binary frames (Opcode 0x02) as well as text
                val text = bytes.utf8()
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                val reasonDetail = if (reason.isNotBlank()) reason else "Normal shutdown"
                Log.d(TAG, "WebSocket closing (code: $code, reason: $reasonDetail)")
                onLog(LogLevel.INFO, TAG, "WebSocket closing (code: $code, reason: $reasonDetail)", null)
                onStateChanged(ConnectionState.Stopping)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                val reasonDetail = if (reason.isNotBlank()) reason else "No reason provided"
                Log.d(TAG, "WebSocket closed (code: $code, reason: $reasonDetail)")
                isSetupComplete.set(false)
                isConnecting.set(false)

                if (code == 1000) {
                    onLog(LogLevel.INFO, TAG, "WebSocket closed cleanly (code: 1000, reason: $reasonDetail)", null)
                    onStateChanged(ConnectionState.Idle)
                } else {
                    val abnormalMsg = "WebSocket closed by server (code: $code, reason: $reasonDetail)"
                    Log.e(TAG, abnormalMsg)
                    onLog(LogLevel.ERROR, TAG, abnormalMsg, null)
                    onStateChanged(ConnectionState.Error(abnormalMsg))
                    onError(abnormalMsg)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                val errorDetails = buildString {
                    append("WebSocket connection failed: ")
                    val primaryMsg = t.message?.takeIf { it.isNotBlank() } ?: t.javaClass.simpleName
                    append(primaryMsg)
                    if (response != null) {
                        append(" [HTTP ${response.code}: ${response.message}]")
                        try {
                            val body = response.body?.string()
                            if (!body.isNullOrBlank()) {
                                append(" - Response: ${body.take(200)}")
                            }
                        } catch (e: Exception) {
                            // ignore body stream read error
                        }
                    }
                    val cause = t.cause
                    if (cause != null && cause.message != t.message) {
                        append(" (Caused by: ${cause.message ?: cause.javaClass.simpleName})")
                    }
                }
                Log.e(TAG, errorDetails, t)
                onLog(LogLevel.ERROR, TAG, errorDetails, null)
                isSetupComplete.set(false)
                isConnecting.set(false)
                onStateChanged(ConnectionState.Error(errorDetails))
                onError(errorDetails)
            }
        })
    }

    private fun handleIncomingMessage(rawJson: String) {
        try {
            val json = JSONObject(rawJson)

            // Step B: Check for setupComplete
            if (json.has("setupComplete")) {
                isSetupComplete.set(true)
                Log.i(TAG, "setupComplete received from Gemini Live server!")
                onLog(LogLevel.RECEIVED, TAG, "setupComplete acknowledged by Gemini Live!", rawJson)
                onStateChanged(ConnectionState.Streaming)
                onSetupComplete()
                return
            }

            // Check for server errors
            if (json.has("error")) {
                val errorObj = json.optJSONObject("error")
                val code = errorObj?.optInt("code", 0) ?: 0
                val status = errorObj?.optString("status") ?: ""
                val errMsg = errorObj?.optString("message") ?: json.optString("error")
                val detailedError = buildString {
                    append("Gemini Live server error")
                    if (code != 0) append(" (code $code)")
                    if (status.isNotBlank()) append(" [$status]")
                    append(": $errMsg")
                }
                Log.e(TAG, detailedError)
                onLog(LogLevel.ERROR, TAG, detailedError, rawJson)
                onError(detailedError)
                onStateChanged(ConnectionState.Error(detailedError))
                return
            }

            // Step C & frame text extraction
            val serverContent = json.optJSONObject("serverContent")
            if (serverContent != null) {
                // Interim hypothesis (updates fast, partial text)
                val interimObj = serverContent.optJSONObject("interimInputTranscription")
                if (interimObj != null && interimObj.has("text")) {
                    val interimText = interimObj.optString("text")
                    if (interimText.isNotEmpty()) {
                        onLog(LogLevel.RECEIVED, TAG, "Interim: \"$interimText\"", null)
                        onInterimTranscription(interimText)
                    }
                }

                // Finalized transcription (once a segment completes)
                val finalizedObj = serverContent.optJSONObject("inputTranscription")
                if (finalizedObj != null && finalizedObj.has("text")) {
                    val finalizedText = finalizedObj.optString("text")
                    if (finalizedText.isNotEmpty()) {
                        onLog(LogLevel.RECEIVED, TAG, "Finalized: \"$finalizedText\"", null)
                        onFinalizedTranscription(finalizedText)
                    }
                }

                if (serverContent.optBoolean("turnComplete", false)) {
                    onLog(LogLevel.INFO, TAG, "Turn completed by server", null)
                }
            } else {
                // Other server message (e.g. initial handshake or ping)
                onLog(LogLevel.RECEIVED, TAG, "Server message: ${rawJson.take(120)}...", rawJson)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing incoming frame JSON: ${e.message}", e)
            onLog(LogLevel.ERROR, TAG, "JSON parse error: ${e.message}", rawJson)
        }
    }

    /**
     * Step C: Send 100ms PCM chunk Base64-encoded via realtimeInput.audio
     * CRITICAL: Must only be called once isSetupComplete is true.
     */
    fun sendAudioChunk(pcmChunk: ByteArray): Boolean {
        val ws = webSocket
        if (ws == null || !isSetupComplete.get()) {
            return false
        }

        return try {
            val base64Data = Base64.encodeToString(pcmChunk, Base64.NO_WRAP)
            val chunkJson = JSONObject().apply {
                put("realtimeInput", JSONObject().apply {
                    put("audio", JSONObject().apply {
                        put("data", base64Data)
                        put("mimeType", "audio/pcm;rate=16000")
                    })
                })
            }
            ws.send(chunkJson.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Error sending audio chunk", e)
            false
        }
    }

    /**
     * Step D: On user stop, signal audioStreamEnd
     */
    fun signalStreamEnd() {
        val ws = webSocket
        if (ws != null && isSetupComplete.get()) {
            try {
                val endJson = JSONObject().apply {
                    put("realtimeInput", JSONObject().apply {
                        put("audioStreamEnd", true)
                    })
                }
                ws.send(endJson.toString())
                onLog(LogLevel.SENT, TAG, "Sent audioStreamEnd signal", endJson.toString())
            } catch (e: Exception) {
                Log.e(TAG, "Error sending audioStreamEnd", e)
            }
        }
    }

    fun disconnect() {
        try {
            webSocket?.close(1000, "Normal closure")
        } catch (e: Exception) {
            Log.e(TAG, "Error closing WebSocket", e)
        } finally {
            webSocket = null
            isSetupComplete.set(false)
            isConnecting.set(false)
            onStateChanged(ConnectionState.Idle)
        }
    }
}
