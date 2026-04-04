package com.muradgalayev.brainbuddy.ui.activity

data class WorkspaceUiState(
    val todoTasksLeft: Int = 0,
    val todoProgress: Float = 0f,
    val todayCompletedTasks: Int = 0,
    val todayTotalTasks: Int = 0,
    val isLoading: Boolean = true,
    val nextMeetingText: String? = null,
    val todayCompletedText: String = "You completed 0 tasks",
    val pomodoroStatusText: String = "Ready",
    val pomodoroTimeText: String = "25:00",
    val pomodoroProgress: Float = 0f,
    val pomodoroIsActive: Boolean = false
)