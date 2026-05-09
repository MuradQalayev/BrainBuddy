package com.muradgalayev.brainbuddy.ui.pomodoro

data class PomodoroUiExtra(
    val showDurationPicker: Boolean = false,
    val showPermissionDialog: Boolean = false,
    val focusModeEnabled: Boolean = false,
    val focusModePermissionGranted: Boolean = false,
    val todayFocusMinutes: Int = 0,
    val yesterdayFocusMinutes: Int = 0,
    val todaySessionsCount: Int = 0
) {
    val hasFocusedToday: Boolean get() = todayFocusMinutes > 0

    val todayFocusLabel: String get() = formatMinutes(todayFocusMinutes)

    val emptyStateTitle: String get() = when {
        yesterdayFocusMinutes > 0 -> "Nothing focused today yet"
        else -> "No focus time yet"
    }

    val emptyStateMessage: String get() = when {
        yesterdayFocusMinutes > 0 ->
            "Unfortunately you haven't focused today. Yesterday you did ${formatMinutes(yesterdayFocusMinutes)} — keep the streak going!"
        else ->
            "Unfortunately you haven't focused today. Start your first session and let's get going!"
    }

    val comparisonMessage: String get() = buildComparisonMessage(
        today = todayFocusMinutes,
        yesterday = yesterdayFocusMinutes
    )
}

private fun formatMinutes(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours == 0 -> "${mins}m"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}

private fun buildComparisonMessage(today: Int, yesterday: Int): String = when {
    today == 0 -> ""
    yesterday == 0 -> "Great start — first focused day in a while!"
    today > yesterday -> "${formatMinutes(today - yesterday)} more than yesterday — keep it up!"
    today < yesterday -> "${formatMinutes(yesterday - today)} less than yesterday — push for it"
    else -> "Matching yesterday's pace"
}
