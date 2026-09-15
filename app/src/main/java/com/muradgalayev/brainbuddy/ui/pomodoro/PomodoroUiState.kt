package com.muradgalayev.brainbuddy.ui.pomodoro

import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.ui.utils.UiText
import com.muradgalayev.brainbuddy.ui.utils.uiText

data class PomodoroUiExtra(
    val showDurationPicker: Boolean = false,
    val showPermissionDialog: Boolean = false,
    val showDndStartPrompt: Boolean = false,
    val focusModeEnabled: Boolean = false,
    val focusModeControlledByMode: Boolean = false,
    val controllingModeName: String? = null,
    val focusModePermissionGranted: Boolean = false,
    val todayFocusMinutes: Int = 0,
    val yesterdayFocusMinutes: Int = 0,
    val todaySessionsCount: Int = 0
)

// one bar in the history chart
data class DayFocus(
    val label: String,
    val minutes: Int,
    val isToday: Boolean,
)

// everything the history sheet renders, computed once in the ViewModel so the composable stays
// a pure function of it
data class FocusHistory(
    val week: List<DayFocus> = emptyList(),
    val streakDays: Int = 0,
    val bestDayMinutes: Int = 0,
) {
    val weekTotalMinutes: Int get() = week.sumOf { it.minutes }

    val weekTotalLabel: UiText get() = formatFocusMinutes(weekTotalMinutes)

    val bestDayLabel: UiText get() = formatFocusMinutes(bestDayMinutes)

    // scales the bars. floored so a single short session doesn't render full-height
    val chartPeakMinutes: Int get() = maxOf(week.maxOfOrNull { it.minutes } ?: 0, 25)
}

// shared with the history sheet, which formats the same way the cards do
fun formatFocusMinutes(minutes: Int): UiText = formatMinutes(minutes)

private fun formatMinutes(minutes: Int): UiText {
    if (minutes <= 0) return uiText(R.string.focus_fmt_m, 0)
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours == 0 -> uiText(R.string.focus_fmt_m, mins)
        mins == 0 -> uiText(R.string.focus_fmt_h, hours)
        else -> uiText(R.string.focus_fmt_hm, hours, mins)
    }
}
