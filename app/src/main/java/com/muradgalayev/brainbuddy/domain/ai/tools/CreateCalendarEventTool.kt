package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.HabitTimingRepository
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject

// creates a calendar event: meeting, appointment, class. distinct from create_todo, since
// todos are checklist items and calendar events are timed things that show on the calendar
class CreateCalendarEventTool @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val habitTimingRepository: HabitTimingRepository,
) : AiTool {

    override val name: String = "create_calendar_event"

    override val description: String =
        "Add a scheduled event (meeting, class, appointment, deadline) to the user's " +
            "calendar. Requires title, date (yyyy-MM-dd), and start_time (HH:mm 24h). " +
            "end_time is optional — defaults to 1h after start. Use for time-blocked " +
            "activities that belong on the calendar view, not simple to-dos."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put("description", "Short event title, e.g. 'Design sync with Sam'")
            }
            putJsonObject("date") {
                put("type", "string")
                put("description", "yyyy-MM-dd — resolve relative dates like 'tomorrow' yourself")
            }
            putJsonObject("start_time") {
                put("type", "string")
                put("description", "HH:mm 24h. Required.")
            }
            putJsonObject("end_time") {
                put("type", "string")
                put("description", "HH:mm 24h. If omitted, defaults to start_time + 1h.")
            }
            putJsonObject("description") {
                put("type", "string")
                put("description", "Optional notes / agenda")
            }
            putJsonObject("location") {
                put("type", "string")
                put("description", "Optional physical location — room name or address")
            }
            putJsonObject("link") {
                put("type", "string")
                put("description", "Optional URL — a meeting/video link (Zoom, Meet), doc, or map link")
            }
            putJsonObject("color") {
                put("type", "string")
                put("description", "One of: blue, red, yellow, green. Defaults to blue.")
            }
        }
        putJsonArray("required") {
            add("title"); add("date"); add("start_time")
        }
    }

    override suspend fun execute(args: JsonObject): String {
        val title = args["title"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (title.isBlank()) return "Failed: title is required."

        val date = args["date"]?.jsonPrimitive?.content?.trim().orEmpty().ifBlank {
            LocalDate.now().toString()
        }
        val start = args["start_time"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (start.isBlank()) return "Failed: start_time (HH:mm) is required."

        val parsedStart = runCatching { LocalTime.parse(start) }.getOrNull()
            ?: return "Failed: start_time '$start' isn't a valid HH:mm."

        val end = args["end_time"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
        val parsedEnd = end?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            ?: parsedStart.plusHours(1)

        val startIso = "${date}T${parsedStart}:00"
        val endIso = "${date}T${parsedEnd}:00"

        val event = CalendarEvent(
            id = UUID.randomUUID().toString(),
            title = title,
            description = args["description"]?.jsonPrimitive?.content.orEmpty(),
            startTime = startIso,
            endTime = endIso,
            location = args["location"]?.jsonPrimitive?.content.orEmpty(),
            color = args["color"]?.jsonPrimitive?.content ?: "blue",
            link = args["link"]?.jsonPrimitive?.content.orEmpty(),
        )
        calendarRepository.insertEvent(event)
        // teach the timing engine, exactly as the calendar screen does when the user adds an event by
        // hand. without this the two features worked against each other: the more someone scheduled
        // through the assistant, the less their own suggestions learned, and the chips slowly went
        // stale on the habits they actually had
        habitTimingRepository.recordEvent(event, HabitTimingRepository.WEIGHT_PLANNED)
        return "Created event '${event.title}' on $date at $start. id=${event.id}"
    }
}
