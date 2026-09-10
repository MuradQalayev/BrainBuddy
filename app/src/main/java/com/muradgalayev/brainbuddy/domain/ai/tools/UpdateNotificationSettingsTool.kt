package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.data.notifications.PomodoroNudgeFrequency
import com.muradgalayev.brainbuddy.data.notifications.ReminderBootstrapper
import com.muradgalayev.brainbuddy.data.notifications.ReminderCategory
import com.muradgalayev.brainbuddy.data.notifications.ReminderKind
import com.muradgalayev.brainbuddy.data.repository.PreferencesRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import javax.inject.Inject

// lets the assistant tune notification preferences: which lead-time reminders fire for to-dos
// and calendar events, the daily summary, and the focus nudge and break alerts. every field is
// optional and only provided fields change. re-arms the relevant alarms after a change
class UpdateNotificationSettingsTool @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val reminderBootstrapper: ReminderBootstrapper,
) : AiTool {

    override val name: String = "update_notification_settings"

    override val description: String =
        "Change how the app reminds the user. Fields are optional; omit any to leave " +
            "it unchanged. todo_reminders / calendar_reminders each take a list of lead " +
            "times from: day_before, min_30_before, at_start (an empty list turns that " +
            "category's reminders off). daily_summary is the morning rundown on/off. " +
            "focus_nudge_frequency is off|once|twice; focus_nudge_time is 'HH:mm' (24h). " +
            "break_reminders alerts when a focus session or break ends."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("todo_reminders") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "string")
                    putJsonArray("enum") { add("day_before"); add("min_30_before"); add("at_start") }
                }
                put("description", "Lead times for to-do reminders. Empty list = off.")
            }
            putJsonObject("calendar_reminders") {
                put("type", "array")
                putJsonObject("items") {
                    put("type", "string")
                    putJsonArray("enum") { add("day_before"); add("min_30_before"); add("at_start") }
                }
                put("description", "Lead times for calendar-event reminders. Empty list = off.")
            }
            putJsonObject("daily_summary") {
                put("type", "boolean")
                put("description", "Morning summary notification on/off.")
            }
            putJsonObject("focus_nudge_frequency") {
                put("type", "string")
                putJsonArray("enum") { add("off"); add("once"); add("twice") }
                put("description", "How often the 'come focus' Pomodoro nudge fires per day.")
            }
            putJsonObject("focus_nudge_time") {
                put("type", "string")
                put("description", "Time of the first focus nudge, 'HH:mm' 24-hour, e.g. 10:00.")
            }
            putJsonObject("break_reminders") {
                put("type", "boolean")
                put("description", "Alert when a Pomodoro focus session or break ends.")
            }
        }
    }

    override suspend fun execute(args: JsonObject): String {
        val changes = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        var needItemReschedule = false

        (args["todo_reminders"] as? JsonArray)?.let { arr ->
            val kinds = parseKinds(arr, warnings, "todo_reminders")
            preferencesRepository.setReminderKinds(ReminderCategory.TODO, kinds)
            changes += "to-do reminders: ${describeKinds(kinds)}"
            needItemReschedule = true
        }

        (args["calendar_reminders"] as? JsonArray)?.let { arr ->
            val kinds = parseKinds(arr, warnings, "calendar_reminders")
            preferencesRepository.setReminderKinds(ReminderCategory.CALENDAR, kinds)
            changes += "calendar reminders: ${describeKinds(kinds)}"
            needItemReschedule = true
        }

        args["daily_summary"]?.jsonPrimitive?.booleanOrNull?.let { on ->
            preferencesRepository.setDailySummaryEnabled(on)
            reminderBootstrapper.rescheduleMorningSummary()
            changes += "daily summary ${if (on) "on" else "off"}"
        }

        args["focus_nudge_frequency"]?.jsonPrimitive?.content?.let { raw ->
            val freq = when (raw.trim().lowercase()) {
                "off", "none" -> PomodoroNudgeFrequency.OFF
                "once", "daily", "1" -> PomodoroNudgeFrequency.ONCE
                "twice", "2" -> PomodoroNudgeFrequency.TWICE
                else -> null
            }
            if (freq == null) {
                warnings += "focus nudge frequency '$raw' isn't valid (off, once, twice)"
            } else {
                preferencesRepository.setPomodoroNudgeFrequency(freq)
                reminderBootstrapper.rescheduleFocusNudge()
                changes += "focus nudges ${freq.name.lowercase()}"
            }
        }

        args["focus_nudge_time"]?.jsonPrimitive?.content?.let { raw ->
            val normalized = normalizeTime(raw)
            if (normalized == null) {
                warnings += "focus nudge time '$raw' isn't a valid HH:mm time"
            } else {
                preferencesRepository.setPomodoroNudgeTime(normalized)
                reminderBootstrapper.rescheduleFocusNudge()
                changes += "focus nudge time $normalized"
            }
        }

        args["break_reminders"]?.jsonPrimitive?.booleanOrNull?.let { on ->
            preferencesRepository.setPomodoroBreakReminders(on)
            changes += "break reminders ${if (on) "on" else "off"}"
        }

        if (needItemReschedule) reminderBootstrapper.rescheduleItemReminders()

        return when {
            changes.isEmpty() && warnings.isEmpty() -> "No notification fields were provided."
            changes.isEmpty() -> "Couldn't change anything: ${warnings.joinToString()}."
            warnings.isEmpty() -> "Updated ${changes.joinToString()}."
            else -> "Updated ${changes.joinToString()}. Skipped: ${warnings.joinToString()}."
        }
    }

    private fun parseKinds(
        arr: JsonArray,
        warnings: MutableList<String>,
        field: String,
    ): Set<ReminderKind> = arr.mapNotNull { el ->
        when (el.jsonPrimitive.content.trim().lowercase().replace('-', '_')) {
            "day_before", "day", "1_day", "24h" -> ReminderKind.DAY_BEFORE
            "min_30_before", "30_min_before", "30min", "30_minutes", "half_hour" ->
                ReminderKind.MIN_30_BEFORE
            "at_start", "start", "on_time", "at_time" -> ReminderKind.AT_START
            else -> {
                warnings += "$field value '${el.jsonPrimitive.content}' isn't recognised"
                null
            }
        }
    }.toSet()

    private fun describeKinds(kinds: Set<ReminderKind>): String {
        if (kinds.isEmpty()) return "off"
        return kinds.sortedByDescending { it.leadMillis }.joinToString(", ") {
            when (it) {
                ReminderKind.DAY_BEFORE -> "day before"
                ReminderKind.MIN_30_BEFORE -> "30 min before"
                ReminderKind.AT_START -> "at start"
            }
        }
    }

    private fun normalizeTime(raw: String): String? {
        val parts = raw.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return "%02d:%02d".format(h, m)
    }
}
