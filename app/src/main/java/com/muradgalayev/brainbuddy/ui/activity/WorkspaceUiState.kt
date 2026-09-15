package com.muradgalayev.brainbuddy.ui.activity

import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.ui.utils.UiText
import com.muradgalayev.brainbuddy.ui.utils.uiText

data class WorkspaceUiState(
    val todoTasksLeft: Int = 0,
    val todoProgress: Float = 0f,
    val todayCompletedTasks: Int = 0,
    val todayTotalTasks: Int = 0,
    val isLoading: Boolean = true,
    val nextMeetingText: UiText? = null,
    val todayCompletedText: UiText = uiText(R.string.ws_no_tasks_completed),
    val pomodoroStatusText: UiText = uiText(R.string.ws_pomo_focus_ready),
    val pomodoroTimeText: String = "25:00",
    val pomodoroProgress: Float = 0f,
    val pomodoroIsActive: Boolean = false,
    val todayFocusMinutes: Int = 0,
    val yesterdayFocusMinutes: Int = 0,
    val focusComparisonText: UiText = uiText(R.string.ws_no_focus_sessions),
    val focusHeroTitle: UiText = uiText(R.string.ws_hero_fresh),
    val focusHeroSubtitle: UiText = uiText(R.string.ws_hero_fresh_sub),
    val activityBannerTitle: UiText = uiText(R.string.ws_banner_fresh),
    val activityBannerMessage: UiText = uiText(R.string.ws_banner_fresh_sub),
    val isSimplified: Boolean = false,

    // the 'right now' hero: the single most relevant thing, resolved in the ViewModel so the UI
    // never has to decide what matters. a null title means nothing is scheduled
    val nextUpTitle: String? = null,
    // 'Now', 'in 20 min', 'Today 15:00', 'Tomorrow 09:00'
    val nextUpWhen: UiText? = null,
    // true while the item's start time has passed, so it's happening rather than upcoming
    val nextUpIsNow: Boolean = false,
    // true when the hero item came from the calendar rather than the to-do list
    val nextUpIsEvent: Boolean = false,

    val weekCompletedTasks: Int = 0,
    val weekFocusMinutes: Int = 0,
)
