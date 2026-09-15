package com.muradgalayev.brainbuddy.data.notifications

import android.content.Context
import com.muradgalayev.brainbuddy.R

enum class ReminderKind(val offsetCode: Int, val leadMillis: Long) {
    DAY_BEFORE(1, 24L * 60L * 60L * 1000L),
    MIN_30_BEFORE(2, 30L * 60L * 1000L),
    AT_START(3, 0L);

    companion object {
        fun fromName(name: String?): ReminderKind? =
            entries.firstOrNull { it.name == name }
    }
}

// read at the moment the notification is built, so it follows whatever language the app is in
// then rather than the one it had when the alarm was scheduled
object ReminderCopy {

    fun titleFor(context: Context, kind: ReminderKind): String = context.getString(
        when (kind) {
            ReminderKind.DAY_BEFORE -> R.string.reminder_title_day_before
            ReminderKind.MIN_30_BEFORE -> R.string.reminder_title_30
            ReminderKind.AT_START -> R.string.reminder_title_start
        }
    )

    fun bodyFor(context: Context, kind: ReminderKind, itemTitle: String, timeLabel: String): String {
        val variants = when (kind) {
            ReminderKind.DAY_BEFORE -> listOf(
                R.string.reminder_day_before_1,
                R.string.reminder_day_before_2,
                R.string.reminder_day_before_3,
                R.string.reminder_day_before_4,
            )
            ReminderKind.MIN_30_BEFORE -> listOf(
                R.string.reminder_30_1,
                R.string.reminder_30_2,
                R.string.reminder_30_3,
                R.string.reminder_30_4,
            )
            ReminderKind.AT_START -> listOf(
                R.string.reminder_start_1,
                R.string.reminder_start_2,
                R.string.reminder_start_3,
            )
        }
        // stable per-item variety, so the same event keeps the same line each time
        val idx = ((itemTitle.hashCode() ushr 1) % variants.size + variants.size) % variants.size
        return context.getString(variants[idx], itemTitle, timeLabel)
    }

    private val focusNudges = listOf(
        R.string.nudge_title_1 to R.string.nudge_body_1,
        R.string.nudge_title_2 to R.string.nudge_body_2,
        R.string.nudge_title_3 to R.string.nudge_body_3,
        R.string.nudge_title_4 to R.string.nudge_body_4,
    )

    fun focusNudge(context: Context, seed: Int = 0): Pair<String, String> {
        val idx = ((seed % focusNudges.size) + focusNudges.size) % focusNudges.size
        val (title, body) = focusNudges[idx]
        return context.getString(title) to context.getString(body)
    }

    fun pomodoroBreakStart(context: Context): Pair<String, String> =
        context.getString(R.string.break_start_title) to context.getString(R.string.break_start_body)

    fun pomodoroBreakOver(context: Context): Pair<String, String> =
        context.getString(R.string.break_over_title) to context.getString(R.string.break_over_body)

    fun morningSummary(context: Context, eventTitles: List<String>): Pair<String, String> {
        val title = context.getString(R.string.morning_title)
        if (eventTitles.isEmpty()) {
            return title to context.getString(R.string.morning_empty)
        }
        val list = when (eventTitles.size) {
            1 -> eventTitles[0]
            2 -> context.getString(R.string.list_and, eventTitles[0], eventTitles[1])
            else -> context.getString(
                R.string.morning_list_more,
                eventTitles[0],
                eventTitles[1],
                eventTitles.size - 2,
            )
        }
        return title to context.getString(R.string.morning_today, list)
    }
}
