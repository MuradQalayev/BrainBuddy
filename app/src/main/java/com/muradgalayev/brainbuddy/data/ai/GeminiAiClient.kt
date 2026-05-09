package com.muradgalayev.brainbuddy.data.ai

import android.util.Log
import com.muradgalayev.brainbuddy.BuildConfig
import com.muradgalayev.brainbuddy.domain.ai.AiClient
import com.muradgalayev.brainbuddy.domain.ai.AiResponse
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import com.muradgalayev.brainbuddy.domain.ai.ChatMessage
import com.muradgalayev.brainbuddy.domain.ai.ChatRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiAiClient @Inject constructor() : AiClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun send(
        history: List<ChatMessage>,
        tools: List<AiTool>,
        systemPrompt: String
    ): AiResponse = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey == "PASTE_YOUR_KEY_HERE") {
            return@withContext AiResponse.Error(
                "Gemini API key missing — add GEMINI_API_KEY to local.properties."
            )
        }

        val body = buildRequestBody(history, tools, systemPrompt)
        var lastErrorCode = -1
        var lastErrorBody = ""

        // Retry transient errors with exponential backoff. 5xx and 429 are
        // worth retrying; 4xx other than 429 are our fault and won't change.
        var delayMs = 600L
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val url = URL(
                    "https://generativelanguage.googleapis.com/v1beta/models/$MODEL:generateContent?key=$apiKey"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connectTimeout = 20_000
                    readTimeout = 30_000
                    doOutput = true
                }
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream.bufferedReader().use { it.readText() }
                conn.disconnect()

                if (code in 200..299) return@withContext parseResponse(text)

                lastErrorCode = code
                lastErrorBody = text
                Log.w(TAG, "Gemini HTTP $code (attempt ${attempt + 1}): $text")

                val retryable = code == 429 || code in 500..599
                if (!retryable) return@withContext AiResponse.Error(friendlyError(code))
            } catch (e: Exception) {
                Log.w(TAG, "Gemini call failed (attempt ${attempt + 1})", e)
                lastErrorBody = e.message ?: "Network error"
            }

            if (attempt < MAX_ATTEMPTS - 1) {
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(4_000)
            }
        }
        AiResponse.Error(friendlyError(lastErrorCode, lastErrorBody))
    }

    private fun friendlyError(code: Int, body: String = ""): String = when (code) {
        503, 502, 504 -> "Gemini is busy right now — give it a moment and try again."
        429 -> "Too many requests in a minute — wait a few seconds and retry."
        401, 403 -> "API key is invalid or doesn't have access."
        in 500..599 -> "Gemini had a hiccup — try again."
        -1 -> body.ifBlank { "Couldn't reach Gemini. Check your connection." }
        else -> "Something went wrong (code $code)."
    }

    private fun buildRequestBody(
        history: List<ChatMessage>,
        tools: List<AiTool>,
        systemPrompt: String
    ): String {
        val obj = buildJsonObject {
            // System instruction
            putJsonObject("systemInstruction") {
                putJsonArray("parts") {
                    add(buildJsonObject { put("text", systemPrompt) })
                }
            }

            // Conversation
            putJsonArray("contents") {
                history.forEach { msg ->
                    add(buildJsonObject {
                        put(
                            "role", when (msg.role) {
                                ChatRole.USER -> "user"
                                ChatRole.ASSISTANT -> "model"
                                ChatRole.TOOL -> "user"
                            }
                        )
                        putJsonArray("parts") {
                            when (msg.role) {
                                ChatRole.USER -> add(buildJsonObject {
                                    put("text", msg.text)
                                })

                                ChatRole.ASSISTANT -> {
                                    if (msg.toolName != null && msg.toolArgs != null) {
                                        add(buildJsonObject {
                                            putJsonObject("functionCall") {
                                                put("name", msg.toolName)
                                                put("args", msg.toolArgs)
                                            }
                                        })
                                    } else {
                                        add(buildJsonObject {
                                            put("text", msg.text)
                                        })
                                    }
                                }

                                ChatRole.TOOL -> add(buildJsonObject {
                                    putJsonObject("functionResponse") {
                                        put("name", msg.toolName ?: "")
                                        putJsonObject("response") {
                                            put("result", msg.toolResult ?: "")
                                        }
                                    }
                                })
                            }
                        }
                    })
                }
            }

            // Tools (function declarations)
            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    add(buildJsonObject {
                        putJsonArray("functionDeclarations") {
                            tools.forEach { tool ->
                                add(buildJsonObject {
                                    put("name", tool.name)
                                    put("description", tool.description)
                                    put("parameters", tool.parametersSchema)
                                })
                            }
                        }
                    })
                }
            }
        }
        return Json.encodeToString(JsonObject.serializer(), obj)
    }

    private fun parseResponse(raw: String): AiResponse {
        val root = json.parseToJsonElement(raw).jsonObject
        val candidates = root["candidates"]?.jsonArray ?: return AiResponse.Error("No candidates")
        if (candidates.isEmpty()) return AiResponse.Error("Empty candidates")
        val parts = candidates[0].jsonObject["content"]
            ?.jsonObject?.get("parts")?.jsonArray
            ?: return AiResponse.Error("No content parts")

        // Prefer function call over text if both are present
        for (part in parts) {
            val fc = part.jsonObject["functionCall"]?.jsonObject
            if (fc != null) {
                val name = fc["name"]?.jsonPrimitive?.content ?: continue
                val args = fc["args"]?.jsonObject ?: buildJsonObject {}
                return AiResponse.ToolCall(name, args)
            }
        }
        val text = parts.firstNotNullOfOrNull {
            it.jsonObject["text"]?.jsonPrimitive?.content
        }.orEmpty()
        return AiResponse.Text(text)
    }

    companion object {
        private const val TAG = "GeminiAiClient"
        private const val MODEL = "gemini-2.5-flash"
        private const val MAX_ATTEMPTS = 3
    }
}
