package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

class UpdateTodoTool @Inject constructor(
    private val todoRepository: TodoRepository
) : AiTool {

    override val name: String = "update_todo"

    override val description: String =
        "Update fields of an existing todo by id. Only provide the fields you want to change. " +
            "Use this when the user adds info to a todo you just created (e.g. clarifies the time)."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") { put("type", "string") }
            putJsonObject("title") { put("type", "string") }
            putJsonObject("date") { put("type", "string") }
            putJsonObject("start_time") { put("type", "string") }
            putJsonObject("end_time") { put("type", "string") }
            putJsonObject("description") { put("type", "string") }
            putJsonObject("category") { put("type", "string") }
            putJsonObject("color") { put("type", "string") }
            putJsonObject("priority") { put("type", "string") }
        }
        putJsonArray("required") { add("id") }
    }

    override suspend fun execute(args: JsonObject): String {
        val id = args["id"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (id.isBlank()) return "Failed: id is required."
        val existing = todoRepository.getTodoItemById(id)
            ?: return "Failed: no todo with id=$id."

        val updated = existing.copy(
            title = args["title"]?.jsonPrimitive?.content ?: existing.title,
            description = args["description"]?.jsonPrimitive?.content ?: existing.description,
            date = args["date"]?.jsonPrimitive?.content ?: existing.date,
            startTime = args["start_time"]?.jsonPrimitive?.content ?: existing.startTime,
            endTime = args["end_time"]?.jsonPrimitive?.content ?: existing.endTime,
            priority = args["priority"]?.jsonPrimitive?.content?.uppercase() ?: existing.priority,
            color = args["color"]?.jsonPrimitive?.content ?: existing.color,
            category = args["category"]?.jsonPrimitive?.content ?: existing.category
        )
        todoRepository.updateTodoItem(updated)
        return "Updated todo '${updated.title}'."
    }
}
