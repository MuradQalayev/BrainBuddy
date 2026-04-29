package com.muradgalayev.brainbuddy.data.repository

import com.muradgalayev.brainbuddy.data.google.GoogleCalendarTokenStore
import com.muradgalayev.brainbuddy.domain.model.TodoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GoogleCalendarRepository @Inject constructor(
    private val tokenStore: GoogleCalendarTokenStore,
    private val todoRepository: TodoRepository
) {

    suspend fun exportAllTodos(): ExportResult = withContext(Dispatchers.IO) {
        val token = tokenStore.getAccessToken()
            ?: return@withContext ExportResult.NeedsGoogleSignIn

        val todos = todoRepository.getAllTodoItems().first()
        if (todos.isEmpty()) return@withContext ExportResult.Success(pushed = 0, alreadyExisted = 0, failed = 0)

        var pushed = 0
        var alreadyExisted = 0
        var failed = 0

        for (todo in todos) {
            when (pushTodo(todo, token)) {
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

    private fun pushTodo(todo: TodoItem, accessToken: String): PushResult {
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
            conn.outputStream.use { it.write(buildEventJson(todo).toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            conn.disconnect()
            when {
                code in 200..299 -> PushResult.Created
                code == 409 -> PushResult.AlreadyExists
                code == 401 -> PushResult.Unauthorized
                else -> PushResult.Failed
            }
        } catch (e: Exception) {
            PushResult.Failed
        }
    }

    private fun buildEventJson(todo: TodoItem): String {
        val tz = java.util.TimeZone.getDefault().id
        val startTime = normalizeTime(todo.startTime)
        val endTime = normalizeTime(todo.endTime)
        val event = buildJsonObject {
            put("id", googleCalendarEventId(todo.id))
            put("summary", todo.title.ifBlank { "Untitled task" })
            if (todo.description.isNotBlank()) put("description", todo.description)
            put("start", buildJsonObject {
                put("dateTime", "${todo.date}T$startTime:00")
                put("timeZone", tz)
            })
            put("end", buildJsonObject {
                put("dateTime", "${todo.date}T$endTime:00")
                put("timeZone", tz)
            })
        }
        return Json.encodeToString(JsonObject.serializer(), event)
    }

    // Google Calendar event IDs must match [a-v0-9]{5,1024}.
    // A UUID without hyphens is hex (0-9, a-f) — valid and stable per todo.
    private fun googleCalendarEventId(todoId: String): String =
        todoId.lowercase().replace("-", "").take(1024).padEnd(5, '0')

    private fun normalizeTime(time: String): String =
        when (time.length) {
            5 -> time             // "HH:mm"
            8 -> time.substring(0, 5) // "HH:mm:ss" -> "HH:mm"
            else -> "00:00"
        }

    private enum class PushResult { Created, AlreadyExists, Unauthorized, Failed }
}

sealed class ExportResult {
    data class Success(val pushed: Int, val alreadyExisted: Int, val failed: Int) : ExportResult()
    data object NeedsGoogleSignIn : ExportResult()
}
