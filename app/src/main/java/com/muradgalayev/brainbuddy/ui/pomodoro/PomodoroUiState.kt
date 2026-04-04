package com.muradgalayev.brainbuddy.ui.pomodoro

data class PomodoroUiExtra(
    val showDurationPicker: Boolean = false,
    val showPermissionDialog: Boolean = false,
    val focusModeEnabled: Boolean = false,
    val focusModePermissionGranted: Boolean = false,
    val todayFocusMinutes: Int = 0
)