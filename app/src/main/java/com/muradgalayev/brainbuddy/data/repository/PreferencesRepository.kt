package com.muradgalayev.brainbuddy.data.repository

import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.data.local.TextSpacing
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.data.notifications.PomodoroNudgeFrequency
import com.muradgalayev.brainbuddy.data.notifications.ReminderCategory
import com.muradgalayev.brainbuddy.data.notifications.ReminderKind
import com.muradgalayev.brainbuddy.data.remote.PreferencesDto
import com.muradgalayev.brainbuddy.data.remote.SupabasePreferencesDataSource
import com.muradgalayev.brainbuddy.data.sync.CalendarSyncFrequency
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepository @Inject constructor(
    private val prefsManager: PreferencesManager,
    private val remote: SupabasePreferencesDataSource,
    private val authRepository: AuthRepository
) {

    private fun getCurrentUserId(): String? = authRepository.getCurrentUserId()

    // pulls remote prefs for the current user and applies them locally. fields still queued for
    // push are skipped: without that guard, changing a setting offline and then reconnecting would
    // apply the stale remote row over the local change, and the setting would visibly revert a
    // second after the connection came back
    suspend fun pullRemoteAndApply() {
        val userId = getCurrentUserId() ?: return
        val dto = remote.get(userId) ?: return
        val pending = prefsManager.pendingPrefsPushSnapshot()
        fun <T> T?.unlessPending(field: String): T? = if (field in pending) null else this
        withContext(Dispatchers.IO) {
            dto.themeMode.unlessPending(Field.THEME)
                ?.let { prefsManager.setThemeMode(ThemeMode.valueOf(it)) }
            dto.fontMode.unlessPending(Field.FONT)
                // fromStored, not valueOf: another device or an older build can still have Classic, Modern or
                // Rounded in this column, and valueOf would throw and abort the rest of the pull with it
                ?.let { prefsManager.setFontMode(FontMode.fromStored(it)) }
            dto.fontSize.unlessPending(Field.FONT_SIZE)
                ?.let { prefsManager.setFontSize(FontSize.valueOf(it)) }
            dto.textSpacing.unlessPending(Field.TEXT_SPACING)
                ?.let { prefsManager.setTextSpacing(TextSpacing.fromStored(it)) }
            dto.focusModeEnabled.unlessPending(Field.FOCUS_MODE)
                ?.let { prefsManager.setFocusModeEnabled(it) }
            dto.simplifiedWorkspace.unlessPending(Field.SIMPLIFIED_WORKSPACE)
                ?.let { prefsManager.setSimplifiedWorkspace(it) }
            dto.enabledNavItems.unlessPending(Field.NAV_ITEMS)?.let { raw ->
                val set = if (raw.isBlank()) emptySet() else raw.split(",").toSet()
                prefsManager.setEnabledNavItems(set)
            }
            dto.todoReminderKinds.unlessPending(Field.TODO_REMINDERS)?.let {
                prefsManager.setReminderKinds(ReminderCategory.TODO, decodeKinds(it))
            }
            dto.calendarReminderKinds.unlessPending(Field.CALENDAR_REMINDERS)?.let {
                prefsManager.setReminderKinds(ReminderCategory.CALENDAR, decodeKinds(it))
            }
            dto.dailySummaryEnabled.unlessPending(Field.DAILY_SUMMARY)
                ?.let { prefsManager.setDailySummaryEnabled(it) }
            dto.pomodoroNudgeFrequency.unlessPending(Field.POMODORO_NUDGE_FREQ)?.let {
                runCatching { PomodoroNudgeFrequency.valueOf(it) }.getOrNull()
                    ?.let { freq -> prefsManager.setPomodoroNudgeFrequency(freq) }
            }
            dto.pomodoroNudgeTime.unlessPending(Field.POMODORO_NUDGE_TIME)
                ?.let { prefsManager.setPomodoroNudgeTime(it) }
            dto.pomodoroBreakReminders.unlessPending(Field.POMODORO_BREAKS)
                ?.let { prefsManager.setPomodoroBreakReminders(it) }
            dto.calendarSyncFrequency.unlessPending(Field.CALENDAR_SYNC_FREQ)
                ?.let { prefsManager.setCalendarSyncFrequency(CalendarSyncFrequency.fromName(it)) }
        }
    }

    // re-pushes every preference changed while the push couldn't go through. called by
    // SyncCoordinator on reconnect, before pullRemoteAndApply, so the server has the local truth
    // before we read it back. values are re-read from DataStore rather than remembered from the
    // failed call: if the user toggled a setting three times offline, only the final state matters
    suspend fun pushPending() {
        val userId = getCurrentUserId() ?: return
        val pending = prefsManager.pendingPrefsPushSnapshot()
        if (pending.isEmpty()) return

        val dto = PreferencesDto(
            userId = userId,
            themeMode = Field.THEME.ifPending(pending) { prefsManager.themeMode.first().name },
            fontMode = Field.FONT.ifPending(pending) { prefsManager.fontMode.first().name },
            fontSize = Field.FONT_SIZE.ifPending(pending) { prefsManager.fontSize.first().name },
            textSpacing = Field.TEXT_SPACING.ifPending(pending) {
                prefsManager.textSpacing.first().name
            },
            focusModeEnabled = Field.FOCUS_MODE.ifPending(pending) {
                prefsManager.focusModeEnabled.first()
            },
            simplifiedWorkspace = Field.SIMPLIFIED_WORKSPACE.ifPending(pending) {
                prefsManager.simplifiedWorkspace.first()
            },
            enabledNavItems = Field.NAV_ITEMS.ifPending(pending) {
                prefsManager.enabledNavItems.first().joinToString(",")
            },
            todoReminderKinds = Field.TODO_REMINDERS.ifPending(pending) {
                encodeKinds(prefsManager.reminderKindsSnapshot(ReminderCategory.TODO))
            },
            calendarReminderKinds = Field.CALENDAR_REMINDERS.ifPending(pending) {
                encodeKinds(prefsManager.reminderKindsSnapshot(ReminderCategory.CALENDAR))
            },
            dailySummaryEnabled = Field.DAILY_SUMMARY.ifPending(pending) {
                prefsManager.dailySummaryEnabledSnapshot()
            },
            pomodoroNudgeFrequency = Field.POMODORO_NUDGE_FREQ.ifPending(pending) {
                prefsManager.pomodoroNudgeFrequencySnapshot().name
            },
            pomodoroNudgeTime = Field.POMODORO_NUDGE_TIME.ifPending(pending) {
                prefsManager.pomodoroNudgeTimeSnapshot()
            },
            pomodoroBreakReminders = Field.POMODORO_BREAKS.ifPending(pending) {
                prefsManager.pomodoroBreakRemindersSnapshot()
            },
            calendarSyncFrequency = Field.CALENDAR_SYNC_FREQ.ifPending(pending) {
                prefsManager.getCalendarSyncFrequency().name
            },
        )

        // all-or-nothing: one upsert carries every queued field, so a still-failing network leaves
        // the queue exactly as it was for the next attempt
        if (runCatching { remote.upsert(dto) }.isSuccess) {
            prefsManager.clearPrefsPending(pending)
        }
    }

    // writes local first, then pushes. a failed push queues the field instead of throwing, so the
    // local change always stands and pushPending carries it up on the next reconnect
    private suspend fun pushOrQueue(field: String, dto: (String) -> PreferencesDto) {
        // no live session yet (cold start offline, session still restoring) is just another reason
        // the push can't happen, so queue it rather than drop it
        val userId = getCurrentUserId() ?: run {
            prefsManager.markPrefsPending(setOf(field))
            return
        }
        if (runCatching { remote.upsert(dto(userId)) }.isSuccess) {
            prefsManager.clearPrefsPending(setOf(field))
        } else {
            prefsManager.markPrefsPending(setOf(field))
        }
    }

    private suspend inline fun <T> String.ifPending(
        pending: Set<String>,
        read: () -> T,
    ): T? = if (this in pending) read() else null

    // Supabase column names, the identity of a preference across queue, pull and push
    private object Field {
        const val THEME = "theme_mode"
        const val FONT = "font_mode"
        const val FONT_SIZE = "font_size"
        const val TEXT_SPACING = "text_spacing"
        const val FOCUS_MODE = "focus_mode_enabled"
        const val SIMPLIFIED_WORKSPACE = "simplified_workspace"
        const val NAV_ITEMS = "enabled_nav_items"
        const val TODO_REMINDERS = "todo_reminder_kinds"
        const val CALENDAR_REMINDERS = "calendar_reminder_kinds"
        const val DAILY_SUMMARY = "daily_summary_enabled"
        const val POMODORO_NUDGE_FREQ = "pomodoro_nudge_frequency"
        const val POMODORO_NUDGE_TIME = "pomodoro_nudge_time"
        const val POMODORO_BREAKS = "pomodoro_break_reminders"
        const val CALENDAR_SYNC_FREQ = "calendar_sync_frequency"
    }

    private fun decodeKinds(raw: String): Set<ReminderKind> =
        raw.split(",").mapNotNull { ReminderKind.fromName(it.trim()) }.toSet()

    private fun encodeKinds(kinds: Set<ReminderKind>): String =
        kinds.joinToString(",") { it.name }

    // each setter updates local DataStore first, then pushes. the push is never allowed to fail
    // the call, see pushOrQueue
    suspend fun setThemeMode(mode: ThemeMode) {
        prefsManager.setThemeMode(mode)
        pushOrQueue(Field.THEME) { PreferencesDto(userId = it, themeMode = mode.name) }
    }

    suspend fun setFontMode(mode: FontMode) {
        prefsManager.setFontMode(mode)
        pushOrQueue(Field.FONT) { PreferencesDto(userId = it, fontMode = mode.name) }
    }

    suspend fun setFontSize(size: FontSize) {
        prefsManager.setFontSize(size)
        pushOrQueue(Field.FONT_SIZE) { PreferencesDto(userId = it, fontSize = size.name) }
    }

    suspend fun setTextSpacing(spacing: TextSpacing) {
        prefsManager.setTextSpacing(spacing)
        pushOrQueue(Field.TEXT_SPACING) { PreferencesDto(userId = it, textSpacing = spacing.name) }
    }

    suspend fun setFocusModeEnabled(enabled: Boolean) {
        prefsManager.setFocusModeEnabled(enabled)
        pushOrQueue(Field.FOCUS_MODE) {
            PreferencesDto(userId = it, focusModeEnabled = enabled)
        }
    }

    suspend fun setSimplifiedWorkspace(enabled: Boolean) {
        prefsManager.setSimplifiedWorkspace(enabled)
        pushOrQueue(Field.SIMPLIFIED_WORKSPACE) {
            PreferencesDto(userId = it, simplifiedWorkspace = enabled)
        }
    }

    suspend fun setEnabledNavItems(items: Set<String>) {
        prefsManager.setEnabledNavItems(items)
        val raw = items.joinToString(",")
        pushOrQueue(Field.NAV_ITEMS) { PreferencesDto(userId = it, enabledNavItems = raw) }
    }

    // notification frequency

    suspend fun setReminderKinds(category: ReminderCategory, kinds: Set<ReminderKind>) {
        prefsManager.setReminderKinds(category, kinds)
        val raw = encodeKinds(kinds)
        when (category) {
            ReminderCategory.TODO -> pushOrQueue(Field.TODO_REMINDERS) {
                PreferencesDto(userId = it, todoReminderKinds = raw)
            }
            ReminderCategory.CALENDAR -> pushOrQueue(Field.CALENDAR_REMINDERS) {
                PreferencesDto(userId = it, calendarReminderKinds = raw)
            }
        }
    }

    suspend fun setDailySummaryEnabled(enabled: Boolean) {
        prefsManager.setDailySummaryEnabled(enabled)
        pushOrQueue(Field.DAILY_SUMMARY) {
            PreferencesDto(userId = it, dailySummaryEnabled = enabled)
        }
    }

    suspend fun setPomodoroNudgeFrequency(freq: PomodoroNudgeFrequency) {
        prefsManager.setPomodoroNudgeFrequency(freq)
        pushOrQueue(Field.POMODORO_NUDGE_FREQ) {
            PreferencesDto(userId = it, pomodoroNudgeFrequency = freq.name)
        }
    }

    suspend fun setPomodoroNudgeTime(hhmm: String) {
        prefsManager.setPomodoroNudgeTime(hhmm)
        pushOrQueue(Field.POMODORO_NUDGE_TIME) {
            PreferencesDto(userId = it, pomodoroNudgeTime = hhmm)
        }
    }

    suspend fun setPomodoroBreakReminders(enabled: Boolean) {
        prefsManager.setPomodoroBreakReminders(enabled)
        pushOrQueue(Field.POMODORO_BREAKS) {
            PreferencesDto(userId = it, pomodoroBreakReminders = enabled)
        }
    }

    suspend fun setCalendarSyncFrequency(freq: CalendarSyncFrequency) {
        prefsManager.setCalendarSyncFrequency(freq)
        pushOrQueue(Field.CALENDAR_SYNC_FREQ) {
            PreferencesDto(userId = it, calendarSyncFrequency = freq.name)
        }
    }
}
