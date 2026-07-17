package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.muradgalayev.brainbuddy.data.google.GoogleCalendarAuthClient
import com.muradgalayev.brainbuddy.data.google.GoogleCalendarTokenStore
import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "GCalExport"

@Singleton
class GoogleCalendarRepository @Inject constructor(
    private val tokenStore: GoogleCalendarTokenStore,
    private val authClient: GoogleCalendarAuthClient,
    private val calendarRepository: CalendarRepository
) {

    suspend fun exportAllEvents(): ExportResult = withContext(Dispatchers.IO) {
        val token = ensureAccessToken()
            ?: return@withContext ExportResult.NeedsGoogleSignIn

        val events = calendarRepository.getAllEvents().first()
        if (events.isEmpty()) {
            return@withContext ExportResult.Success(pushed = 0, alreadyExisted = 0, failed = 0)
        }

        var pushed = 0
        var alreadyExisted = 0
        var failed = 0

        for (event in events) {
            when (pushEvent(event, token)) {
                PushResult.Created -> pushed++
                PushResult.AlreadyExists -> alreadyExisted++
                PushResult.Unauthorized -> {
                    tokenStore.clear()
                    return@withContext ExportResult.NeedsGoogleSignIn
                }
                PushResult.Failed -> failed++
            }
        }

        ExportResult.Success(pushed, alreadyExisted, failed)
    }

    /**
     * Returns a usable access token, refreshing it silently if the cached one
     * has expired. Returns null only when the user has never connected or
     * their consent has been revoked — in which case the caller should surface
     * `NeedsGoogleSignIn` and let the user reconnect interactively.
     */
    private suspend fun ensureAccessToken(): String? {
        tokenStore.getAccessToken()?.let { return it }
        if (!tokenStore.isLinked()) return null
        return authClient.tryGetFreshAccessTokenSilently()
    }

    private fun pushEvent(event: CalendarEvent, accessToken: String): PushResult {
        val start = parseLocalDateTime(event.startTime)
        val end = parseLocalDateTime(event.endTime)
        if (start == null || end == null || !end.isAfter(start)) {
            Log.w(
                TAG,
                "Skipping event id=${event.id} title='${event.title}': " +
                    "invalid time range start='${event.startTime}' end='${event.endTime}'"
            )
            return PushResult.Failed
        }

        val payload = buildEventJson(event)
        return try {
            val url = URL("https://www.googleapis.com/calendar/v3/calendars/primary/events")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connectTimeout = 15_000
                readTimeout = 15_000
                doOutput = true
            }
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val result = when {
                code in 200..299 -> PushResult.Created
                code == 409 -> PushResult.AlreadyExists
                code == 401 -> PushResult.Unauthorized
                else -> PushResult.Failed
            }
            if (result == PushResult.Failed) {
                val body = runCatching {
                    (conn.errorStream ?: conn.inputStream).bufferedReader().use { it.readText() }
                }.getOrDefault("<no body>")
                Log.w(
                    TAG,
                    "Push failed for event id=${event.id} title='${event.title}': " +
                        "HTTP $code body=$body payload=$payload"
                )
            }
            conn.disconnect()
            result
        } catch (e: Exception) {
            Log.w(TAG, "Push threw for event id=${event.id} title='${event.title}': ${e.message}", e)
            PushResult.Failed
        }
    }

    private fun buildEventJson(event: CalendarEvent): String {
        val tz = java.util.TimeZone.getDefault().id
        // Description gets the link appended so it's tappable in Google Calendar too.
        val description = buildString {
            append(event.description)
            if (event.link.isNotBlank()) {
                if (isNotEmpty()) append("\n\n")
                append(event.link)
            }
        }
        val json = buildJsonObject {
            put("id", googleCalendarEventId(event.id))
            put("summary", event.title.ifBlank { "Untitled event" })
            if (description.isNotBlank()) put("description", description)
            if (event.location.isNotBlank()) put("location", event.location)
            put("start", buildJsonObject {
                put("dateTime", normalizeIso(event.startTime))
                put("timeZone", tz)
            })
            put("end", buildJsonObject {
                put("dateTime", normalizeIso(event.endTime))
                put("timeZone", tz)
            })
        }
        return Json.encodeToString(JsonObject.serializer(), json)
    }

    // Google Calendar event IDs must match [a-v0-9]{5,1024}.
    // A UUID without hyphens is hex (0-9, a-f) — valid and stable per event.
    private fun googleCalendarEventId(eventId: String): String =
        eventId.lowercase().replace("-", "").take(1024).padEnd(5, '0')

    private fun parseLocalDateTime(raw: String): LocalDateTime? =
        runCatching { LocalDateTime.parse(normalizeIso(raw)) }.getOrNull()

    // Ensure trailing :ss so Google's RFC3339 parser is happy.
    private fun normalizeIso(raw: String): String {
        if (raw.length == 16 && raw.contains('T')) return "$raw:00"
        return raw
    }

    private enum class PushResult { Created, AlreadyExists, Unauthorized, Failed }
}

sealed class ExportResult {
    data class Success(val pushed: Int, val alreadyExisted: Int, val failed: Int) : ExportResult()
    data object NeedsGoogleSignIn : ExportResult()
}
