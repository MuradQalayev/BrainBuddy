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
    val pomodoroIsActive: Boolean = false,
    val todayFocusMinutes: Int = 0,
    val yesterdayFocusMinutes: Int = 0,
    val focusComparisonText: String = "No focus sessions yet",
    val focusHeroTitle: String = "No focus time yet",
    val focusHeroSubtitle: String = "Start your first focus session today.",
    val activityBannerTitle: String = "A fresh start",
    val activityBannerMessage: String = "Plan a task and try a quick focus session to get going.",
    val isSimplified: Boolean = false,

    // the 'right now' hero: the single most relevant thing, resolved in the ViewModel so the UI
    // never has to decide what matters. a null title means nothing is scheduled
    val nextUpTitle: String? = null,
    // 'Now', 'in 20 min', 'Today 15:00', 'Tomorrow 09:00'
    val nextUpWhen: String? = null,
    // true while the item's start time has passed, so it's happening rather than upcoming
    val nextUpIsNow: Boolean = false,
    // true when the hero item came from the calendar rather than the to-do list
    val nextUpIsEvent: Boolean = false,

    val weekCompletedTasks: Int = 0,
    val weekFocusMinutes: Int = 0,
)
