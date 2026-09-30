package com.example.service.floating

import android.util.Log
import com.example.data.AppLogRepository
import com.example.data.LogLevel
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class PolishResult(
    val text: String?,
    val errorDetail: String?
)

object FloatingPolishClient {
    private const val TAG = "FloatingPolishClient"

    @Volatile
    private var lastSuccessfulPolishModel: String = "gemini-3.5-flash-lite"

    private const val SYSTEM_INSTRUCTION_TEXT = """You are Flow, a transcript cleaner. Input: a raw spoken transcript. Output: ONLY the cleaned final text — nothing else.

Never answer questions asked in the transcript — transcribe them as questions, don't respond to them.
Never explain your changes.
Never copy any word, number, or item from the examples below into your output — those are for pattern reference only. Every number and item in your output must come from the transcript you are given, not from these examples.
Preserve whatever language(s) the speaker used — do not translate.
Add natural punctuation and capitalization to whatever text isn't otherwise changed by the rules below.

RULES
1. Remove filler words and verbal hesitations (um, uh, like, so, okay, yeah, yes yeah, I think, you know, kind of, sort of).
2. When the speaker corrects a stated value (actually, no wait, I mean, sorry, scratch that), replace it — never keep the original, incorrect version.
3. When the speaker states a quantity needed, then separately mentions an amount already owned/available, calculate the true remaining amount and output ONLY that final number — don't show the math or mention what they already have. Only apply this when the "already have X" framing is unambiguous; if it's not clearly that pattern, leave the numbers exactly as spoken rather than guessing.
4. When the speaker names 2 or more discrete items, output them as a markdown bulleted list.

EXAMPLE 1 (simple correction)
Raw: "Let's meet at 5, actually 6."
Output: Let's meet at 6.

EXAMPLE 2 (list + correction, unrelated domain)
Raw: "I want to buy two no three books, a lamp, and a rug. Actually skip the rug."
Output: I want to buy:
- 3 books
- a lamp

EXAMPLE 3 (quantity adjustment, unrelated domain)
Raw: "I need 10 chairs for the event. Wait, I already have 4 chairs at home, so I'd only need 6."
Output: I need 6 chairs for the event."""

    fun polishTranscript(apiKey: String, rawTranscript: String): PolishResult {
        val baseModels = listOf(
            "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-2.5-flash-lite",
            "gemini-2.5-flash"
        )
        val modelsToTry = listOf(lastSuccessfulPolishModel) + baseModels.filter { it != lastSuccessfulPolishModel }

        fun buildJsonBody(modelName: String): String {
            return JSONObject().apply {
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_INSTRUCTION_TEXT)))
                })
                put("contents", JSONArray().put(
                    JSONObject().apply {
                        put("role", "user")
                        put("parts", JSONArray().put(JSONObject().put("text", rawTranscript)))
                    }
                ))
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.1)
                    if (modelName.contains("3.")) {
                        put("thinkingConfig", JSONObject().apply {
                            put("thinkingLevel", "MINIMAL")
                        })
                    } else {
                        put("thinkingConfig", JSONObject().apply {
                            put("thinkingBudget", 0)
                        })
                    }
                })
            }.toString()
        }

        val errorsLog = mutableListOf<String>()

        for (modelName in modelsToTry) {
            var connection: HttpURLConnection? = null
            try {
                val jsonBody = try {
                    buildJsonBody(modelName)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed creating Polish JSON request body for $modelName", e)
                    return PolishResult(null, "JSON build error: ${e.message}")
                }

                val urlString = "https://generativelanguage.googleapis.com/v1beta/models/$modelName:generateContent?key=$apiKey"
                val url = URL(urlString)

                AppLogRepository.addLog(
                    LogLevel.SENT,
                    "PolishAPI",
                    "Sending POST request to model '$modelName' (Key len=${apiKey.length})",
                    jsonBody
                )

                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("x-goog-api-key", apiKey)
                    setRequestProperty("Connection", "keep-alive")
                    connectTimeout = 5000
                    readTimeout = 8000
                    doOutput = true
                }

                connection.outputStream.use { os ->
                    os.write(jsonBody.toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                if (responseCode == 200) {
                    val responseString = connection.inputStream.bufferedReader().use { it.readText() }
                    Log.d(TAG, "Polish API success response from $modelName: $responseString")
                    AppLogRepository.addLog(
                        LogLevel.RECEIVED,
                        "PolishAPI",
                        "HTTP 200 OK from $modelName",
                        responseString
                    )

                    val rootJson = JSONObject(responseString)
                    val candidates = rootJson.optJSONArray("candidates")
                    if (candidates != null && candidates.length() > 0) {
                        val firstCandidate = candidates.getJSONObject(0)
                        val content = firstCandidate.optJSONObject("content")
                        val parts = content?.optJSONArray("parts")
                        if (parts != null && parts.length() > 0) {
                            val sb = StringBuilder()
                            for (i in 0 until parts.length()) {
                                val partObj = parts.getJSONObject(i)
                                val text = partObj.optString("text", "")
                                if (text.isNotEmpty()) {
                                    sb.append(text)
                                }
                            }
                            val textResult = sb.toString().trim()
                            if (textResult.isNotBlank()) {
                                Log.d(TAG, "Polish call succeeded using model $modelName")
                                lastSuccessfulPolishModel = modelName
                                return PolishResult(textResult, null)
                            }
                        }
                    }
                    errorsLog.add("$modelName returned empty candidates/parts")
                } else {
                    val errorStreamText = connection.errorStream?.bufferedReader()?.use { it.readText() }
                        ?: connection.inputStream?.bufferedReader()?.use { it.readText() }
                        ?: "No error body"
                    val errLogMsg = "HTTP $responseCode ($modelName): $errorStreamText"
                    Log.e(TAG, "Polish API Error: $errLogMsg")
                    AppLogRepository.addLog(
                        LogLevel.ERROR,
                        "PolishAPI",
                        "HTTP $responseCode Error ($modelName)",
                        errorStreamText
                    )
                    errorsLog.add(errLogMsg)
                }
            } catch (e: Exception) {
                val excMsg = "Exception ($modelName): ${e.message}"
                Log.e(TAG, "Error in Polish call for model $modelName", e)
                AppLogRepository.addLog(
                    LogLevel.ERROR,
                    "PolishAPI",
                    "Exception connecting to $modelName: ${e.message}",
                    e.stackTraceToString()
                )
                errorsLog.add(excMsg)
            } finally {
                connection?.disconnect()
            }
        }
        return PolishResult(null, errorsLog.joinToString(" | "))
    }
}
