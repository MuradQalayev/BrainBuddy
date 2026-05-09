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

class DeleteTodoTool @Inject constructor(
    private val todoRepository: TodoRepository
) : AiTool {

    override val name: String = "delete_todo"

    override val description: String =
        "Delete a todo by id. Use only when the user explicitly asks to remove or cancel."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") { put("type", "string") }
        }
        putJsonArray("required") { add("id") }
    }

    override suspend fun execute(args: JsonObject): String {
        val id = args["id"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (id.isBlank()) return "Failed: id is required."
        val existing = todoRepository.getTodoItemById(id)
            ?: return "Failed: no todo with id=$id."
        todoRepository.deleteTodoItem(existing)
        return "Deleted todo '${existing.title}'."
    }
}
