package com.muradgalayev.brainbuddy.domain.scheduling

import com.muradgalayev.brainbuddy.data.health.HealthConnectManager
import com.muradgalayev.brainbuddy.data.local.ModeManager
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.HabitTimingRepository
import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

// how many days past the requested one the engine may reach for a better slot
private const val LOOKAHEAD_DAYS = 3

// answers 'when should this go?' for callers that aren't the calendar screen. the engine itself
// is pure, taking a fully-built DayContext and returning suggestions, and assembling that
// context is the actual work: existing events, timed to-dos, the sleep window, the
// productive-time answer, today's energy signals, the active mode's working hours and the
// learned habit statistics. the calendar screen builds it from state it already holds.
// it exists so the assistant and the calendar give the same answer. before it, the model picked
// times by reading the profile text in its prompt while the chips used circular statistics over
// real history: two systems answering one question, free to disagree in front of the user, and
// only one of them able to explain itself.
// health data is read only for today. a future day gets null, unknown rather than well rested,
// because tomorrow's step count doesn't exist yet and inventing it would be inventing evidence
@Singleton
class SuggestTimeUseCase @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val todoRepository: TodoRepository,
    private val adhdProfileRepository: AdhdProfileRepository,
    private val habitTimingRepository: HabitTimingRepository,
    private val healthConnectManager: HealthConnectManager,
    private val modeManager: ModeManager,
) {

    suspend operator fun invoke(
        title: String,
        description: String = "",
        date: LocalDate = LocalDate.now(),
        now: LocalDateTime = LocalDateTime.now(),
    ): List<TimeSuggestion> {
        val lastDay = date.plusDays(LOOKAHEAD_DAYS.toLong())

        val eventsByDate = calendarRepository
            .getEventsInDateRange(date.toString(), lastDay.toString())
            .first()
            .groupBy { runCatching { LocalDate.parse(it.startTime.take(10)) }.getOrNull() }

        val todoBlocksByDate = todoRepository
            .getTodoItemsInRange(date.toString(), lastDay.toString())
            .first()
            .mapNotNull { todo ->
                val day = runCatching { LocalDate.parse(todo.date) }.getOrNull()
                val block = todo.toBusyIntervalOrNull()
                if (day != null && block != null) day to block else null
            }
            .groupBy({ it.first }, { it.second })

        val profile = runCatching { adhdProfileRepository.getProfile() }.getOrNull()
        val sleep = SleepWindow.parse(profile?.sleepBedtime, profile?.sleepWakeTime)
        val workingHours = runCatching { modeManager.effectiveWorkingHours.first() }.getOrNull()
        val today = now.toLocalDate()

        fun contextFor(day: LocalDate) = DayContext(
            date = day,
            now = now,
            // one day, however many lists the app keeps it in
            busy = eventsByDate[day].orEmpty().mapNotNull { it.toBusyIntervalOrNull() } +
                todoBlocksByDate[day].orEmpty(),
            sleep = sleep,
            productiveTime = profile?.productiveTime,
            energy = if (day == today) healthConnectManager.state.value.toEnergySignalsOrNull() else null,
            workingHours = workingHours,
        )

        return TimeSuggestionEngine.suggestAcrossDays(
            title = title,
            description = description,
            selectedDay = contextFor(date),
            laterDays = (1..LOOKAHEAD_DAYS).map { contextFor(date.plusDays(it.toLong())) },
            lookup = habitTimingRepository.lookupAt(System.currentTimeMillis()),
        )
    }
}
