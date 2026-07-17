package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add
import javax.inject.Inject

class DeleteCalendarEventTool @Inject constructor(
    private val calendarRepository: CalendarRepository
) : AiTool {

    override val name: String = "delete_calendar_event"

    override val description: String =
        "Remove a calendar event by id. Only call when the user clearly wants it gone. " +
            "Use the id you received when you created the event."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "string")
                put("description", "Event id (UUID) to delete.")
            }
        }
        putJsonArray("required") { add("id") }
    }

    override suspend fun execute(args: JsonObject): String {
        val id = args["id"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (id.isBlank()) return "Failed: id is required."
        val existing = calendarRepository.getEventById(id)
            ?: return "Failed: no event with id=$id."
        calendarRepository.deleteEvent(existing)
        return "Deleted event '${existing.title}'."
    }
}
