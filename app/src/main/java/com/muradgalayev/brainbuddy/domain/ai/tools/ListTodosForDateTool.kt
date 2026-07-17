package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import javax.inject.Inject

/**
 * Lets the AI answer "what's on my plate today?" style questions. Reads from the
 * local Room DAO (fast, offline-safe) — returns a compact one-line-per-item
 * summary the model can quote back.
 */
class ListTodosForDateTool @Inject constructor(
    private val todoRepository: TodoRepository,
) : AiTool {

    override val name: String = "list_todos_for_date"

    override val description: String =
        "Read the user's todos for a specific date. Use this whenever the user " +
            "asks about their day — 'what do I have today?', 'what's tomorrow " +
            "looking like?', 'anything due Friday?'. Do NOT guess — call this " +
            "tool first, then summarize what you got in plain language."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("date") {
                put("type", "string")
                put(
                    "description",
                    "yyyy-MM-dd. If the user said 'today', 'tomorrow', 'Friday', " +
                        "resolve to the actual date yourself before calling."
                )
            }
        }
    }

    override suspend fun execute(args: JsonObject): String {
        val date = args["date"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
            ?: LocalDate.now().toString()

        val items = runCatching {
            todoRepository.getTodoItemsByDate(date).first()
        }.getOrElse { return "Couldn't read todos: ${it.message}" }

        if (items.isEmpty()) return "No todos on $date."
        return buildString {
            append("Todos on $date (${items.size}):")
            items.forEach { t ->
                val time = when {
                    t.startTime.isNotBlank() && t.endTime.isNotBlank() ->
                        " ${t.startTime}–${t.endTime}"
                    t.startTime.isNotBlank() -> " ${t.startTime}"
                    else -> ""
                }
                val status = if (t.isCompleted) " [done]" else ""
                append("\n  • ${t.title}$time$status (id=${t.id})")
            }
        }
    }
}
