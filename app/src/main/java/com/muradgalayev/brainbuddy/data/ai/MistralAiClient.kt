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
import com.muradgalayev.brainbuddy.R

// Mistral client. the API is OpenAI-compatible, so this is the standard messages array, tools
// array and tool_calls response format.
// Mistral is the sole provider on purpose, and the reason is jurisdictional rather than
// technical: it's a French company serving from the EU, so a prompt leaving this app is not a
// Chapter V transfer to a third country the way a call to a US or Chinese provider is. that
// removes the hardest GDPR question from the feature entirely, instead of papering over it
// with contract clauses.
// mistral-small-latest: fast, cheap, native tool calling, and more than enough for the short
// replies this assistant gives. swap MODEL to mistral-large-latest if tool selection drifts
@Singleton
class MistralAiClient @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) : AiClient {

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun send(
        history: List<ChatMessage>,
        tools: List<AiTool>,
        systemPrompt: String
    ): AiResponse = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.MISTRAL_API_KEY
        if (apiKey.isBlank() || apiKey == "PASTE_YOUR_KEY_HERE") {
            return@withContext AiResponse.Error(
                context.getString(R.string.ai_err_no_key)
            )
        }

        val body = buildRequestBody(history, tools, systemPrompt)
        var lastErrorCode = -1
        var lastErrorBody = ""

        var delayMs = 600L
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                val url = URL("https://api.mistral.ai/v1/chat/completions")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
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
                Log.w(TAG, "Mistral HTTP $code (attempt ${attempt + 1}): $text")

                val retryable = code == 429 || code in 500..599
                if (!retryable) return@withContext AiResponse.Error(friendlyError(code, text))
            } catch (e: Exception) {
                Log.w(TAG, "Mistral call failed (attempt ${attempt + 1})", e)
                lastErrorBody = e.message ?: context.getString(R.string.ai_err_network)
            }

            if (attempt < MAX_ATTEMPTS - 1) {
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(4_000)
            }
        }
        AiResponse.Error(friendlyError(lastErrorCode, lastErrorBody))
    }

    private fun friendlyError(code: Int, body: String = ""): String = when (code) {
        503, 502, 504 -> context.getString(R.string.ai_err_busy)
        429 -> context.getString(R.string.ai_err_rate)
        401, 403 -> context.getString(R.string.ai_err_key_invalid)
        402 -> context.getString(R.string.ai_err_no_credit)
        422, 400 -> context.getString(R.string.ai_err_bad_request, code, extractApiMessage(body) ?: context.getString(R.string.ai_err_rejected))
        in 500..599 -> context.getString(R.string.ai_err_hiccup)
        -1 -> body.ifBlank { context.getString(R.string.ai_err_unreachable) }
        else -> {
            val detail = extractApiMessage(body)
            if (detail != null) context.getString(R.string.ai_err_code_detail, code, detail)
            else context.getString(R.string.ai_err_code, code)
        }
    }

    // pulls the human-readable message out of an error body. Mistral uses a message field for auth
    // and rate errors but a detail array for schema validation failures, and the validation one is
    // exactly the case worth surfacing: it means a tool's schema is malformed
    private fun extractApiMessage(body: String): String? = runCatching {
        val root = json.parseToJsonElement(body).jsonObject
        root["message"]?.jsonPrimitive?.content
            ?: root["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
            ?: (root["detail"] as? JsonArray)?.firstOrNull()
                ?.jsonObject?.get("msg")?.jsonPrimitive?.content
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun buildRequestBody(
        history: List<ChatMessage>,
        tools: List<AiTool>,
        systemPrompt: String
    ): String {
        val obj = buildJsonObject {
            put("model", MODEL)
            put("temperature", 0.7)
            put("max_tokens", MAX_OUTPUT_TOKENS)
            // opt in to prompt caching, cached prefix tokens bill at 10%. the key is only a hint about
            // which requests are likely to share a prefix, and it's deliberately derived from the tool set
            // rather than from the user, so no identifier crosses the boundary to earn a cache hit, and
            // it self-invalidates when the tools change
            put("prompt_cache_key", cacheKeyFor(tools))

            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                // walk history sequentially and mint shared tool_call_ids for each assistant tool_call and
                // TOOL result pair. OpenAI-compatible APIs reject any mismatch with a 400
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
                                val id = toolCallId(toolCallCounter++)
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
                            // reuse the id minted for the immediately preceding assistant tool_call. if there is none
                            // (an orphan tool result, which shouldn't happen) skip it
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
                // let the model decide whether to call a tool or reply with text
                put("tool_choice", "auto")
            }
        }
        return Json.encodeToString(JsonObject.serializer(), obj)
    }

    // Mistral validates tool_call_id against exactly nine alphanumeric characters and rejects the
    // whole request otherwise, so the call_0 style id most OpenAI-compatible providers tolerate
    // fails here. zero-padding to nine keeps the ids stable and readable in logs
    private fun toolCallId(index: Int): String = "call" + index.toString().padStart(5, '0')

    // groups requests that share a cacheable prefix. contains no user data by construction, the
    // tool names are the same for everyone
    private fun cacheKeyFor(tools: List<AiTool>): String =
        "myndora-" + tools.joinToString(",") { it.name }.hashCode().toUInt().toString(16)

    // logs what the turn actually cost, including how much of the prompt came from cache. worth
    // keeping: the prefix only caches while the static part of the system prompt stays
    // byte-identical and stays first, and there is no other signal when an innocent-looking
    // prompt edit silently ends that
    private fun logUsage(root: JsonObject) {
        val usage = root["usage"]?.jsonObject ?: return
        val prompt = usage["prompt_tokens"]?.jsonPrimitive?.content ?: "?"
        val completion = usage["completion_tokens"]?.jsonPrimitive?.content ?: "?"
        val cached = usage["prompt_tokens_details"]
            ?.jsonObject?.get("cached_tokens")?.jsonPrimitive?.content ?: "0"
        Log.i(TAG, "usage: prompt=$prompt (cached=$cached) completion=$completion")
    }

    // assistant message that emitted a tool call. the OpenAI spec requires content to be null or
    // omitted when tool_calls is present, and empty strings trip validation on some providers.
    // arguments must be a JSON-encoded string, not a nested object
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
        logUsage(root)
        val choices = root["choices"]?.jsonArray ?: return AiResponse.Error("No choices in response")
        if (choices.isEmpty()) return AiResponse.Error("Empty choices")
        val message = choices[0].jsonObject["message"]?.jsonObject
            ?: return AiResponse.Error("No message in first choice")

        // prefer a tool call if the model asked for one
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

        // Mistral returns content: null alongside a finish_reason of tool_calls, so treat a null the
        // same as an empty reply rather than letting the literal string 'null' reach the UI
        val text = message["content"]
            ?.takeIf { it != JsonNull }
            ?.jsonPrimitive?.content
            .orEmpty()
        return AiResponse.Text(text)
    }

    companion object {
        private const val TAG = "MistralAiClient"
        // check two things before swapping this, not one. function calling, because a model that
        // can't emit tool calls makes every AiTool dead weight; and the account's allocation for
        // it, because a model the tier grants zero requests a minute answers 429 forever and looks
        // exactly like a bad key. mistral-small-latest is an alias of magistral-small-latest, which
        // the free tier allocates nothing to, which is why this used to fail every single call.
        // `curl https://api.mistral.ai/v1/chat/completions` and read x-ratelimit-limit-req-minute
        private const val MODEL = "ministral-8b-latest"
        private const val MAX_OUTPUT_TOKENS = 120
        private const val MAX_ATTEMPTS = 3
    }
}