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
import java.time.LocalTime
import javax.inject.Inject

/**
 * Refines an existing calendar event. Only provided fields overwrite; unset fields
 * keep their prior value. Use the event `id` returned by `create_calendar_event`
 * or by asking the user which event they mean.
 */
class UpdateCalendarEventTool @Inject constructor(
    private val calendarRepository: CalendarRepository
) : AiTool {

    override val name: String = "update_calendar_event"

    override val description: String =
        "Change an existing calendar event by id. Pass only the fields the user wants " +
            "to change — the rest stay as they were. Common refinements: move to a new " +
            "time, rename, add location or notes."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("id") {
                put("type", "string")
                put("description", "Event id (UUID) returned by create_calendar_event.")
            }
            putJsonObject("title") { put("type", "string") }
            putJsonObject("date") {
                put("type", "string")
                put("description", "yyyy-MM-dd. If provided, replaces the event's date.")
            }
            putJsonObject("start_time") {
                put("type", "string")
                put("description", "HH:mm 24h. If provided, replaces the start time.")
            }
            putJsonObject("end_time") {
                put("type", "string")
                put("description", "HH:mm 24h.")
            }
            putJsonObject("description") { put("type", "string") }
            putJsonObject("location") { put("type", "string") }
            putJsonObject("color") {
                put("type", "string")
                put("description", "blue | red | yellow | green")
            }
        }
        putJsonArray("required") { add("id") }
    }

    override suspend fun execute(args: JsonObject): String {
        val id = args["id"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (id.isBlank()) return "Failed: id is required."

        val existing = calendarRepository.getEventById(id)
            ?: return "Failed: no event with id=$id."

        // Compose the new start/end from whichever pieces the caller supplied.
        val newDate = args["date"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
            ?: existing.startTime.substringBefore('T')
        val existingStartClock = existing.startTime.substringAfter('T').take(5)
        val existingEndClock = existing.endTime.substringAfter('T').take(5)
        val newStartClock = args["start_time"]?.jsonPrimitive?.content?.trim()
            ?.takeIf { it.isNotEmpty() } ?: existingStartClock
        val newEndClock = args["end_time"]?.jsonPrimitive?.content?.trim()
            ?.takeIf { it.isNotEmpty() } ?: existingEndClock

        val validStart = runCatching { LocalTime.parse(newStartClock) }.isSuccess
        val validEnd = runCatching { LocalTime.parse(newEndClock) }.isSuccess
        if (!validStart) return "Failed: start_time '$newStartClock' isn't a valid HH:mm."
        if (!validEnd) return "Failed: end_time '$newEndClock' isn't a valid HH:mm."

        val updated = existing.copy(
            title = args["title"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
                ?: existing.title,
            description = args["description"]?.jsonPrimitive?.content ?: existing.description,
            location = args["location"]?.jsonPrimitive?.content ?: existing.location,
            color = args["color"]?.jsonPrimitive?.content ?: existing.color,
            startTime = "${newDate}T${newStartClock}:00",
            endTime = "${newDate}T${newEndClock}:00",
        )
        calendarRepository.updateEvent(updated)
        return "Updated event '${updated.title}' → $newDate ${newStartClock}–${newEndClock}."
    }
}
