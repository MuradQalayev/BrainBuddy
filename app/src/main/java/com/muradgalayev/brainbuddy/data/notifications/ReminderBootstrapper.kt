package com.muradgalayev.brainbuddy.data.notifications

import android.util.Log
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderBootstrapper @Inject constructor(
    private val scheduler: ReminderScheduler,
    private val calendarRepository: CalendarRepository,
    private val todoRepository: TodoRepository,
    private val adhdProfileRepository: AdhdProfileRepository,
    private val preferencesManager: PreferencesManager,
    private val questionnaireReminderScheduler: QuestionnaireReminderScheduler,
) {
    suspend fun rescheduleAll() {
        runCatching {
            calendarRepository.getAllEvents().first().forEach { event ->
                scheduler.scheduleForItem(
                    itemId = event.id,
                    title = event.title,
                    dateIso = event.startTime,
                    category = ReminderCategory.CALENDAR,
                )
            }
        }.onFailure { Log.w(TAG, "calendar reschedule failed: ${it.message}") }

        runCatching {
            todoRepository.getAllTodoItems().first()
                .filter { !it.isCompleted && it.startTime.isNotBlank() && it.date.isNotBlank() }
                .forEach { todo ->
                    scheduler.scheduleForItem(
                        itemId = todo.id,
                        title = todo.title,
                        dateIso = todo.date,
                        timeIso = todo.startTime,
                        category = ReminderCategory.TODO,
                    )
                }
        }.onFailure { Log.w(TAG, "todo reschedule failed: ${it.message}") }

        rescheduleMorningSummary()
        runCatching { scheduler.scheduleFocusNudge() }
            .onFailure { Log.w(TAG, "focus nudge reschedule failed: ${it.message}") }
        runCatching { questionnaireReminderScheduler.rearmSaved() }
            .onFailure { Log.w(TAG, "questionnaire reminder reschedule failed: ${it.message}") }
    }

    suspend fun rescheduleMorningSummary() {
        val enabled = runCatching { preferencesManager.dailySummaryEnabledSnapshot() }
            .getOrDefault(true)
        if (!enabled) {
            scheduler.cancelMorningSummary()
            return
        }
        val raw = adhdProfileRepository.peekProfile()?.sleepWakeTime
            ?: runCatching { adhdProfileRepository.getProfile()?.sleepWakeTime }.getOrNull()
        val wakeTime = parseWakeTime(raw.orEmpty()) ?: DEFAULT_WAKE_TIME
        scheduler.scheduleMorningSummary(wakeTime)
    }

    // re-arm just the lead-time reminders, used after a frequency toggle changes
    suspend fun rescheduleItemReminders() {
        runCatching {
            calendarRepository.getAllEvents().first().forEach { event ->
                scheduler.scheduleForItem(
                    itemId = event.id,
                    title = event.title,
                    dateIso = event.startTime,
                    category = ReminderCategory.CALENDAR,
                )
            }
        }.onFailure { Log.w(TAG, "calendar reschedule failed: ${it.message}") }

        runCatching {
            todoRepository.getAllTodoItems().first()
                .filter { !it.isCompleted && it.startTime.isNotBlank() && it.date.isNotBlank() }
                .forEach { todo ->
                    scheduler.scheduleForItem(
                        itemId = todo.id,
                        title = todo.title,
                        dateIso = todo.date,
                        timeIso = todo.startTime,
                        category = ReminderCategory.TODO,
                    )
                }
        }.onFailure { Log.w(TAG, "todo reschedule failed: ${it.message}") }
    }

    suspend fun rescheduleFocusNudge() {
        runCatching { scheduler.scheduleFocusNudge() }
            .onFailure { Log.w(TAG, "focus nudge reschedule failed: ${it.message}") }
    }

    private fun parseWakeTime(raw: String): LocalTime? {
        if (raw.isBlank()) return null
        val cleaned = raw.trim()
        return runCatching { LocalTime.parse(cleaned) }.getOrNull()
            ?: runCatching {
                LocalTime.parse(cleaned, DateTimeFormatter.ofPattern("H:mm"))
            }.getOrNull()
            ?: runCatching {
                LocalTime.parse(cleaned.uppercase(), DateTimeFormatter.ofPattern("h:mm a"))
            }.getOrNull()
    }

    companion object {
        private const val TAG = "ReminderBootstrap"
        private val DEFAULT_WAKE_TIME: LocalTime = LocalTime.of(8, 0)
    }
}
