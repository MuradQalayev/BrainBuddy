package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import com.muradgalayev.brainbuddy.domain.model.CalendarSubtask
import com.muradgalayev.brainbuddy.domain.model.SubtaskKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add
import java.util.UUID
import javax.inject.Inject

// breaks a calendar event into ordered stations, e.g. a 90-minute Math prep block into 'review
// formulas 20m', 'break 5m', 'practice 45m'. replaces the event's existing plan, so the caller
// passes the full desired list
class SplitCalendarEventTool @Inject constructor(
    private val calendarRepository: CalendarRepository,
) : AiTool {

    override val name: String = "split_calendar_event"

    override val description: String =
        "Break a calendar event into smaller ordered stations (steps). Use for asks " +
            "like 'split my study block into steps', 'break this into a pomodoro plan', " +
            "'add stations to the meeting'. Needs the event id (from create_calendar_event " +
            "or list_calendar_events) and a stations list. This REPLACES the whole plan — " +
            "to add or edit one station, pass the complete updated list."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("event_id") {
                put("type", "string")
                put("description", "Event id (UUID) to attach the stations to.")
            }
            putJsonObject("stations") {
                put("type", "array")
                put(
                    "description",
                    "Ordered stations that make up the plan. Replaces any existing plan.",
                )
                putJsonObject("items") {
                    put("type", "object")
                    putJsonObject("properties") {
                        putJsonObject("title") {
                            put("type", "string")
                            put("description", "Short station name, e.g. 'Review formulas'")
                        }
                        putJsonObject("minutes") {
                            put("type", "integer")
                            put("description", "Duration in minutes (defaults to 25).")
                        }
                        putJsonObject("kind") {
                            put("type", "string")
                            put("description", "'focus' (default) or 'break'.")
                        }
                    }
                }
            }
        }
        putJsonArray("required") {
            add("event_id"); add("stations")
        }
    }

    override suspend fun execute(args: JsonObject): String {
        val eventId = args["event_id"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (eventId.isBlank()) return "Failed: event_id is required."

        val event = calendarRepository.getEventById(eventId)
            ?: return "Failed: no event with id=$eventId."

        val stationsJson = runCatching { args["stations"]?.jsonArray }.getOrNull()
            ?: return "Failed: 'stations' must be a list of { title, minutes, kind }."

        val subtasks = stationsJson.mapIndexedNotNull { index, element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@mapIndexedNotNull null
            val title = obj["title"]?.jsonPrimitive?.content?.trim().orEmpty()
            if (title.isBlank()) return@mapIndexedNotNull null
            val minutes = obj["minutes"]?.jsonPrimitive?.content?.toIntOrNull()
                ?.coerceIn(1, 600) ?: 25
            val kind = if (obj["kind"]?.jsonPrimitive?.content?.trim()?.lowercase() == "break") {
                SubtaskKind.BREAK
            } else {
                SubtaskKind.FOCUS
            }
            CalendarSubtask(
                id = UUID.randomUUID().toString(),
                eventId = eventId,
                title = title,
                durationMinutes = minutes,
                orderIndex = index,
                completed = false,
                kind = kind,
            )
        }

        if (subtasks.isEmpty()) {
            return "Failed: no valid stations — each needs a title (minutes optional)."
        }

        calendarRepository.replaceSubtasks(eventId, subtasks)

        val total = subtasks.sumOf { it.durationMinutes }
        val plan = subtasks.joinToString(", ") { st ->
            "${st.title} ${st.durationMinutes}m" +
                if (st.kind == SubtaskKind.BREAK) " (break)" else ""
        }
        return "Split '${event.title}' into ${subtasks.size} stations (${total}m total): $plan"
    }
}
