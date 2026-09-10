package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import javax.inject.Inject

// reads calendar events for a date, or an inclusive range when end_date is given. complements
// ListTodosForDateTool: the model should call both when the user asks about their day, since
// todos and events are separate lists in this app
class ListCalendarEventsForDateTool @Inject constructor(
    private val calendarRepository: CalendarRepository,
) : AiTool {

    override val name: String = "list_calendar_events"

    override val description: String =
        "Read the user's scheduled calendar events (meetings, appointments, " +
            "classes, doctor visits). Use for questions like 'do I have anything " +
            "today?', 'when's my next doctor visit?', 'what's my week look like?'. " +
            "Pass a single date, or a range with end_date. Always call this AND " +
            "list_todos_for_date when the user asks broadly about their day."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("date") {
                put("type", "string")
                put(
                    "description",
                    "yyyy-MM-dd. Start of the range (or the only date). " +
                        "Resolve 'today', 'tomorrow', 'Friday' to a real date first."
                )
            }
            putJsonObject("end_date") {
                put("type", "string")
                put(
                    "description",
                    "yyyy-MM-dd. Optional. If provided, returns events between " +
                        "date and end_date inclusive. Great for 'this week'."
                )
            }
        }
    }

    override suspend fun execute(args: JsonObject): String {
        val start = args["date"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
            ?: LocalDate.now().toString()
        val end = args["end_date"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }

        val events = runCatching {
            if (end == null) {
                calendarRepository.getEventsByDate(start).first()
            } else {
                calendarRepository.getEventsInDateRange(start, end).first()
            }
        }.getOrElse { return "Couldn't read events: ${it.message}" }

        val label = if (end == null) "on $start" else "from $start to $end"
        if (events.isEmpty()) return "No calendar events $label."
        return buildString {
            append("Events $label (${events.size}):")
            events.forEach { e ->
                val time = "${e.startTime.substringAfter('T').take(5)}–" +
                    e.endTime.substringAfter('T').take(5)
                val date = e.startTime.substringBefore('T')
                val location = if (e.location.isNotBlank()) " @ ${e.location}" else ""
                val link = if (e.link.isNotBlank()) " link: ${e.link}" else ""
                append("\n  • [$date $time] ${e.title}$location$link (id=${e.id})")
            }
        }
    }
}
