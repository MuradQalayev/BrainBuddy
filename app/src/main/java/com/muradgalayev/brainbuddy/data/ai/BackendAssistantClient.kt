package com.muradgalayev.brainbuddy.data.ai

import com.muradgalayev.brainbuddy.BuildConfig
import com.muradgalayev.brainbuddy.domain.ai.ChatMessage
import com.muradgalayev.brainbuddy.domain.ai.ChatRole
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One /v1/assistant/turns exchange. Unlike [com.muradgalayev.brainbuddy.domain.ai.AiClient],
 * this is a whole-message call, not a per-hop one: the server already ran its own tool loop for
 * the tools it owns before answering, so there is no local continuation here — see
 * docs/ai-porting-plan.md (Fase 2) for why this can't just be a new AiClient implementation.
 */
sealed class BackendTurnResult {
    // touched names which local repositories (Room) must resync: the server writes straight to
    // Supabase, bypassing the local cache entirely.
    data class Text(val text: String, val touched: Set<String>) : BackendTurnResult()
    data class Completed(val text: String, val touched: Set<String>) : BackendTurnResult()
    data class Action(val toolName: String, val args: JsonObject) : BackendTurnResult()
    data class Error(val message: String, val httpStatus: Int?) : BackendTurnResult()
}

@Singleton
class BackendAssistantClient @Inject constructor(
    private val supabase: SupabaseClient,
    private val redactor: PiiRedactor,
) {
    val enabled: Boolean get() = BuildConfig.BRAINBUDDY_ASSISTANT_BACKEND_URL.isNotBlank()

    suspend fun sendTurn(
        turnId: String,
        message: String,
        timeZone: String,
        priorMessages: List<ChatMessage>,
    ): BackendTurnResult = withContext(Dispatchers.IO) {
        val token = supabase.auth.currentSessionOrNull()?.accessToken
            ?: return@withContext BackendTurnResult.Error("Sign in again to keep chatting.", 401)
        val base = URL(BuildConfig.BRAINBUDDY_ASSISTANT_BACKEND_URL.trimEnd('/') + "/")
        // Debug loopback works with adb reverse; never send JWTs over arbitrary cleartext LAN URLs.
        require(base.protocol == "https" || (BuildConfig.DEBUG && base.protocol == "http" &&
            base.host in setOf("127.0.0.1", "localhost", "10.0.2.2")))
        require(base.userInfo == null && base.query == null && base.ref == null)

        // The turn contract forbids TOOL role and tool fields (the server owns tool execution
        // and rebuilds context itself); sending its own artifacts back would just be rejected.
        val history = priorMessages
            .filter { it.role != ChatRole.TOOL && it.toolName == null && it.text.isNotBlank() }
            .takeLast(20)

        val payload = buildJsonObject {
            put("turnId", turnId)
            put("message", redactor.redactUserText(message))
            put("timeZone", timeZone)
            if (history.isNotEmpty()) {
                putJsonArray("history") {
                    history.forEach { m ->
                        add(buildJsonObject {
                            put("role", m.role.name)
                            put("text", redactor.redactUserText(m.text))
                        })
                    }
                }
            }
        }.toString()

        val connection = URL(base, "v1/assistant/turns").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) return@withContext BackendTurnResult.Error(errorMessageFor(status), status)
            val bytes = connection.inputStream.use { it.readBytesBounded() }
            runCatching { Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject }
                .fold({ parseResponse(it) }, { BackendTurnResult.Error("Couldn't read the assistant's reply.", null) })
        } catch (e: Exception) {
            BackendTurnResult.Error("Couldn't reach the assistant — check your connection.", null)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(response: JsonObject): BackendTurnResult {
        val touched = response["touched"]?.jsonArray?.map { it.jsonPrimitive.content }?.toSet().orEmpty()
        return when (response["type"]?.jsonPrimitive?.content) {
            "text" -> BackendTurnResult.Text(response["text"]?.jsonPrimitive?.content.orEmpty(), touched)
            "completed" -> BackendTurnResult.Completed(response["text"]?.jsonPrimitive?.content.orEmpty(), touched)
            "action" -> BackendTurnResult.Action(
                toolName = response["toolName"]?.jsonPrimitive?.content.orEmpty(),
                args = response["args"]?.jsonObject ?: JsonObject(emptyMap()),
            )
            else -> BackendTurnResult.Error("Unrecognised assistant response.", null)
        }
    }

    // Mirrors migration-plan.md's Kotlin error mapping (400/401/404/409/422/429/503).
    private fun errorMessageFor(status: Int): String = when (status) {
        400 -> "That request wasn't valid — try rephrasing."
        401 -> "Sign in again to keep chatting."
        404 -> "I couldn't find that."
        409 -> "Something else changed that at the same time — try again."
        422 -> "That's not something I can do yet."
        429 -> "You've used today's AI messages. They come back tomorrow."
        503 -> "The assistant is temporarily unavailable — try again shortly."
        else -> "Something went wrong on the assistant's side."
    }

    private fun InputStream.readBytesBounded(): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= 262_144) { "Turn response too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
