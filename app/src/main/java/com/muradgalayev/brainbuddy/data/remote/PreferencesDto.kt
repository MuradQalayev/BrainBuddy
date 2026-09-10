package com.muradgalayev.brainbuddy.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PreferencesDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("theme_mode")
    val themeMode: String? = null,
    @SerialName("font_mode")
    val fontMode: String? = null,
    @SerialName("font_size")
    val fontSize: String? = null,
    @SerialName("text_spacing")
    val textSpacing: String? = null,
    @SerialName("focus_mode_enabled")
    val focusModeEnabled: Boolean? = null,
    @SerialName("simplified_workspace")
    val simplifiedWorkspace: Boolean? = null,
    @SerialName("enabled_nav_items")
    val enabledNavItems: String? = null,
    @SerialName("todo_reminder_kinds")
    val todoReminderKinds: String? = null,
    @SerialName("calendar_reminder_kinds")
    val calendarReminderKinds: String? = null,
    @SerialName("daily_summary_enabled")
    val dailySummaryEnabled: Boolean? = null,
    @SerialName("pomodoro_nudge_frequency")
    val pomodoroNudgeFrequency: String? = null,
    @SerialName("pomodoro_nudge_time")
    val pomodoroNudgeTime: String? = null,
    @SerialName("pomodoro_break_reminders")
    val pomodoroBreakReminders: Boolean? = null,
    @SerialName("calendar_sync_frequency")
    val calendarSyncFrequency: String? = null
)
