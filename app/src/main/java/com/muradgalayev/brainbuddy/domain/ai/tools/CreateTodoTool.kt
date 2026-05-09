package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import com.muradgalayev.brainbuddy.domain.model.TodoItem
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

class CreateTodoTool @Inject constructor(
    private val todoRepository: TodoRepository
) : AiTool {

    override val name: String = "create_todo"

    override val description: String =
        "Create a new todo / calendar event. Title is required. " +
            "Date should be ISO yyyy-MM-dd; default to today if user didn't say. " +
            "Times are HH:mm 24h; leave empty if user didn't say."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put("description", "Short event title")
            }
            putJsonObject("date") {
                put("type", "string")
                put("description", "yyyy-MM-dd, defaults to today")
            }
            putJsonObject("start_time") {
                put("type", "string")
                put("description", "HH:mm 24h, optional")
            }
            putJsonObject("end_time") {
                put("type", "string")
                put("description", "HH:mm 24h, optional")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "Additional notes, optional")
            }
            putJsonObject("category") {
                put("type", "string")
                put(
                    "description",
                    "One of: work, education, personal, sport, health"
                )
            }
            putJsonObject("color") {
                put("type", "string")
                put("description", "One of: blue, red, yellow")
            }
            putJsonObject("priority") {
                put("type", "string")
                put("description", "One of: LOW, MEDIUM, HIGH")
            }
        }
        putJsonArray("required") { add("title") }
    }

    override suspend fun execute(args: JsonObject): String {
        val title = args["title"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (title.isBlank()) return "Failed: title is required."

        val item = TodoItem(
            id = UUID.randomUUID().toString(),
            title = title,
            description = args["description"]?.jsonPrimitive?.content.orEmpty(),
            isCompleted = false,
            date = args["date"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                ?: LocalDate.now().toString(),
            startTime = args["start_time"]?.jsonPrimitive?.content.orEmpty(),
            endTime = args["end_time"]?.jsonPrimitive?.content.orEmpty(),
            priority = args["priority"]?.jsonPrimitive?.content?.uppercase() ?: "MEDIUM",
            attendees = 0,
            color = args["color"]?.jsonPrimitive?.content ?: "blue",
            category = args["category"]?.jsonPrimitive?.content ?: "personal"
        )
        todoRepository.insertTodoItem(item)
        return "Created todo '${item.title}' on ${item.date}" +
            (if (item.startTime.isNotBlank()) " at ${item.startTime}" else "") +
            ". id=${item.id}"
    }
}
