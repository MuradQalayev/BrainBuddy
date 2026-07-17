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
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
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

/**
 * DeepSeek Chat client. Their API is OpenAI-compatible so we use the standard
 * `messages` array + `tools` array + `tool_calls` response format.
 *
 * Model choice: `deepseek-chat` (V3.1). Fast, cheap, native tool calling.
 * Don't use `deepseek-reasoner` here — that model doesn't emit tool calls.
 */
@Singleton
class DeepSeekAiClient @Inject constructor() : AiClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun send(
        history: List<ChatMessage>,
        tools: List<AiTool>,
        systemPrompt: String
    ): AiResponse = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.DEEPSEEK_API_KEY
        if (apiKey.isBlank() || apiKey == "PASTE_YOUR_KEY_HERE") {
            return@withContext AiResponse.Error(
                "DeepSeek API key missing — add DEEPSEEK_API_KEY to local.properties."
            )
        }

        val body = buildRequestBody(history, tools, systemPrompt)
        var lastErrorCode = -1
        var lastErrorBody = ""

        var delayMs = 600L
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val url = URL("https://api.deepseek.com/chat/completions")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Authorization", "Bearer $apiKey")
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
                Log.w(TAG, "DeepSeek HTTP $code (attempt ${attempt + 1}): $text")

                val retryable = code == 429 || code in 500..599
                if (!retryable) return@withContext AiResponse.Error(friendlyError(code))
            } catch (e: Exception) {
                Log.w(TAG, "DeepSeek call failed (attempt ${attempt + 1})", e)
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
        503, 502, 504 -> "DeepSeek is busy right now — give it a moment and try again."
        429 -> "Too many requests — wait a few seconds and retry."
        401, 403 -> "DeepSeek API key is invalid or has no credit."
        402 -> "DeepSeek account is out of credit — top up at platform.deepseek.com."
        in 500..599 -> "DeepSeek had a hiccup — try again."
        -1 -> body.ifBlank { "Couldn't reach DeepSeek. Check your connection." }
        else -> "Something went wrong (code $code)."
    }

    private fun buildRequestBody(
        history: List<ChatMessage>,
        tools: List<AiTool>,
        systemPrompt: String
    ): String {
        val obj = buildJsonObject {
            put("model", MODEL)
            // Deterministic-ish so the assistant doesn't get creative with tool args.
            put("temperature", 0.7)
            put("max_tokens", 800)

            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                // Walk history sequentially and mint shared tool_call_ids for each
                // ASSISTANT tool_call → TOOL result pair. OpenAI-compatible APIs
                // reject any mismatch with HTTP 400.
                var pendingToolCallId: String? = null
                var toolCallCounter = 0
                history.forEach { msg ->
                    when (msg.role) {
                        ChatRole.USER -> add(buildJsonObject {
                            put("role", "user")
                            put("content", msg.text)
                        })
                        ChatRole.ASSISTANT -> {
                            if (msg.toolName != null && msg.toolArgs != null) {
                                val id = "call_${toolCallCounter++}"
                                pendingToolCallId = id
                                add(buildAssistantToolCallMessage(msg, id))
                            } else {
                                add(buildJsonObject {
                                    put("role", "assistant")
                                    put("content", msg.text)
                                })
                            }
                        }
                        ChatRole.TOOL -> {
                            // Reuse the id we minted for the immediately preceding
                            // assistant tool_call. If there's none (orphan tool
                            // result, shouldn't happen but safe fallback), skip.
                            val id = pendingToolCallId ?: return@forEach
                            add(buildJsonObject {
                                put("role", "tool")
                                put("tool_call_id", id)
                                put("content", msg.toolResult ?: "")
                            })
                            pendingToolCallId = null
                        }
                    }
                }
            }

            if (tools.isNotEmpty()) {
                putJsonArray("tools") {
                    tools.forEach { tool ->
                        add(buildJsonObject {
                            put("type", "function")
                            putJsonObject("function") {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parametersSchema)
                            }
                        })
                    }
                }
                // Let the model decide whether to call a tool or reply with text.
                put("tool_choice", "auto")
            }
        }
        return Json.encodeToString(JsonObject.serializer(), obj)
    }

    /**
     * Assistant message that emitted a tool call. OpenAI spec requires `content`
     * to be null (or omitted) when `tool_calls` is present; empty strings trip
     * validation on some providers. `arguments` must be a JSON-encoded string,
     * NOT a nested object.
     */
    private fun buildAssistantToolCallMessage(msg: ChatMessage, callId: String): JsonObject =
        buildJsonObject {
            put("role", "assistant")
            put("content", JsonNull)
            putJsonArray("tool_calls") {
                add(buildJsonObject {
                    put("id", callId)
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", msg.toolName ?: "")
                        put(
                            "arguments",
                            Json.encodeToString(JsonObject.serializer(), msg.toolArgs ?: buildJsonObject { }),
                        )
                    }
                })
            }
        }

    private fun parseResponse(raw: String): AiResponse {
        val root = json.parseToJsonElement(raw).jsonObject
        val choices = root["choices"]?.jsonArray ?: return AiResponse.Error("No choices in response")
        if (choices.isEmpty()) return AiResponse.Error("Empty choices")
        val message = choices[0].jsonObject["message"]?.jsonObject
            ?: return AiResponse.Error("No message in first choice")

        // Prefer a tool call if the model asked for one.
        val toolCalls = message["tool_calls"] as? JsonArray
        if (!toolCalls.isNullOrEmpty()) {
            val call = toolCalls[0].jsonObject
            val fn = call["function"]?.jsonObject ?: return AiResponse.Error("Malformed tool call")
            val name = fn["name"]?.jsonPrimitive?.content ?: return AiResponse.Error("Tool call missing name")
            val argsRaw = fn["arguments"]?.jsonPrimitive?.content.orEmpty()
            val args = runCatching {
                if (argsRaw.isBlank()) buildJsonObject { }
                else Json.parseToJsonElement(argsRaw).jsonObject
            }.getOrElse { buildJsonObject { } }
            return AiResponse.ToolCall(name, args)
        }

        val text = message["content"]?.jsonPrimitive?.content.orEmpty()
        return AiResponse.Text(text)
    }

    companion object {
        private const val TAG = "DeepSeekAiClient"
        // deepseek-chat is DeepSeek V3.1. Supports tool calling. `deepseek-reasoner`
        // does NOT support tools, so don't swap unless you drop the tool set.
        private const val MODEL = "deepseek-chat"
        private const val MAX_ATTEMPTS = 3
    }
}
