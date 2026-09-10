package com.muradgalayev.brainbuddy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import com.muradgalayev.brainbuddy.domain.model.TodoItem
import com.muradgalayev.brainbuddy.ui.calendar.DefaultEventColorKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

enum class QuickCaptureMode { Task, Event }

// the one-tap when chips. nothing selected means whatever the text said, or today
enum class QuickWhen(val label: String) {
    Now("Now"),
    InAnHour("In 1 h"),
    Tonight("Tonight"),
    Tomorrow("Tomorrow");

    fun resolve(now: LocalDateTime): LocalDateTime = when (this) {
        Now -> now.withSecond(0).withNano(0)
        InAnHour -> now.plusHours(1).withSecond(0).withNano(0)
        Tonight -> now.toLocalDate().atTime(20, 0)
        Tomorrow -> now.toLocalDate().plusDays(1).atTime(9, 0)
    }
}

// a short confirmation shown on the card itself, so nothing has to cover the screen
data class QuickCaptureFeedback(
    val message: String,
    val detail: String,
    val isError: Boolean = false,
)

// backs the home screen's quick-capture card. the whole point is that a thought survives the
// two seconds it takes to write it down, so the write is fire-and-forget: the sheet has already
// closed and the confirmation is already on screen by the time Room, let alone Supabase, hears
// about it. nothing here is ever awaited by the UI
@HiltViewModel
class QuickCaptureViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val calendarRepository: CalendarRepository,
) : ViewModel() {

    private val _feedback = MutableStateFlow<QuickCaptureFeedback?>(null)
    val feedback: StateFlow<QuickCaptureFeedback?> = _feedback.asStateFlow()

    // bumped per capture, so a stale auto-dismiss can't clear a newer confirmation
    private var feedbackToken = 0

    fun capture(mode: QuickCaptureMode, rawText: String, whenChoice: QuickWhen?) {
        if (rawText.isBlank()) return
        val now = LocalDateTime.now()
        val parsed = parseQuickCapture(rawText, now)
        val chosen = whenChoice?.resolve(now)

        val date = chosen?.toLocalDate() ?: parsed.date ?: now.toLocalDate()
        val time = chosen?.toLocalTime() ?: parsed.time

        when (mode) {
            QuickCaptureMode.Task -> saveTask(parsed.title, date, time)
            QuickCaptureMode.Event -> saveEvent(parsed.title, date, time ?: nextSlot(now))
        }
    }

    private fun saveTask(title: String, date: LocalDate, time: LocalTime?) {
        val item = TodoItem(
            id = UUID.randomUUID().toString(),
            title = title,
            description = "",
            isCompleted = false,
            date = date.toString(),
            startTime = time?.format(HOUR_MINUTE).orEmpty(),
            endTime = "",
            priority = "MEDIUM",
            attendees = 0,
            color = "blue",
            category = "personal",
        )
        show("Task added", whenLabel(date, time))
        viewModelScope.launch {
            runCatching { todoRepository.insertTodoItem(item) }
                .onFailure { showError() }
        }
    }

    private fun saveEvent(title: String, date: LocalDate, time: LocalTime) {
        val start = date.atTime(time)
        val event = CalendarEvent(
            id = UUID.randomUUID().toString(),
            title = title,
            description = "",
            startTime = start.format(ISO_LOCAL),
            endTime = start.plusHours(1).format(ISO_LOCAL),
            location = "",
            color = DefaultEventColorKey,
        )
        show("Event added", whenLabel(date, time))
        viewModelScope.launch {
            runCatching { calendarRepository.insertEvent(event) }
                .onFailure { showError() }
        }
    }

    fun dismissFeedback() {
        _feedback.value = null
    }

    private fun show(message: String, detail: String) {
        val token = ++feedbackToken
        _feedback.value = QuickCaptureFeedback(message, detail)
        viewModelScope.launch {
            delay(FEEDBACK_MILLIS)
            if (token == feedbackToken) _feedback.value = null
        }
    }

    // bumps the token so the success timer already in flight can't clear the failure
    private fun showError() {
        feedbackToken++
        _feedback.value = QuickCaptureFeedback("Couldn't save", "Tap to dismiss", isError = true)
    }

    // events need a clock reading, and the next half hour is the least surprising guess
    private fun nextSlot(now: LocalDateTime): LocalTime {
        val minute = if (now.minute < 30) 30 else 0
        val base = if (now.minute < 30) now else now.plusHours(1)
        return LocalTime.of(base.hour, minute)
    }

    private fun whenLabel(date: LocalDate, time: LocalTime?): String {
        val today = LocalDate.now()
        val day = when (date) {
            today -> "Today"
            today.plusDays(1) -> "Tomorrow"
            else -> date.format(DAY_MONTH)
        }
        return time?.let { "$day · ${it.format(HOUR_MINUTE)}" } ?: day
    }

    private companion object {
        const val FEEDBACK_MILLIS = 2600L
        val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DAY_MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
        val ISO_LOCAL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
    }
}
