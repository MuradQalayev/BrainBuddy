package com.muradgalayev.brainbuddy.data.ai

import com.muradgalayev.brainbuddy.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Title-only bridge. It does not implement AiClient or execute any tool. */
@Singleton
class BackendConversationTitle @Inject constructor(
    private val supabase: SupabaseClient,
    private val redactor: PiiRedactor,
) {
    val enabled: Boolean get() = BuildConfig.BRAINBUDDY_TITLE_BACKEND_URL.isNotBlank()

    suspend fun generate(prompt: String): String? = withContext(Dispatchers.IO) {
        val token = supabase.auth.currentSessionOrNull()?.accessToken ?: return@withContext null
        val base = URL(BuildConfig.BRAINBUDDY_TITLE_BACKEND_URL.trimEnd('/') + "/")
        // Debug loopback works with adb reverse; never send JWTs over arbitrary cleartext LAN URLs.
        require(base.protocol == "https" || (BuildConfig.DEBUG && base.protocol == "http" &&
            base.host in setOf("127.0.0.1", "localhost", "10.0.2.2")))
        require(base.userInfo == null && base.query == null && base.ref == null)
        val payload = buildJsonObject {
            put("contractVersion", "android-inference-v1")
            put("purpose", "conversation-title")
            put("timeZone", ZoneId.systemDefault().id)
            putJsonArray("capabilities") { }
            putJsonArray("history") {
                add(buildJsonObject {
                    put("id", UUID.randomUUID().toString())
                    put("role", "USER")
                    // Same outbound redaction used by the existing title's AiClient call.
                    put("text", redactor.redactUserText(prompt))
                })
            }
        }.toString()
        val connection = URL(base, "v1/assistant/inferences").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 20_000
            connection.readTimeout = 30_000
            connection.doOutput = true
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            if (connection.responseCode !in 200..299) return@withContext null
            val bytes = connection.inputStream.use { it.readBytesBounded() }
            val response = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
            if (response["type"]?.jsonPrimitive?.content != "text") return@withContext null
            response["text"]?.jsonPrimitive?.content
        } finally {
            connection.disconnect()
        }
    }

    private fun java.io.InputStream.readBytesBounded(): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            require(output.size() + count <= 65_536) { "Title response too large" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }
}
