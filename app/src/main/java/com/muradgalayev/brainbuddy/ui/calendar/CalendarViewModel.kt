package com.muradgalayev.brainbuddy.ui.calendar

import android.app.PendingIntent
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.google.GoogleCalendarAuthClient
import com.muradgalayev.brainbuddy.data.google.GoogleCalendarTokenStore
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.ExportResult
import com.muradgalayev.brainbuddy.data.repository.GoogleCalendarRepository
import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import javax.inject.Inject

data class CalendarTaskUi(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val location: String? = null,
    val link: String? = null,
    val timeRange: String? = null,
    val accent: Color,
    val completed: Boolean = false,
    val flagged: Boolean = false,
)

data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val tasksForSelectedDate: List<CalendarTaskUi> = emptyList(),
    val datesWithTasks: Set<LocalDate> = emptySet(),
    val visibleMonth: YearMonth = YearMonth.now(),
    val showAddTaskDialog: Boolean = false,
    val editingEvent: CalendarEvent? = null,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val googleCalendarRepository: GoogleCalendarRepository,
    private val googleCalendarAuthClient: GoogleCalendarAuthClient,
    private val googleCalendarTokenStore: GoogleCalendarTokenStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _visibleMonth = MutableStateFlow(YearMonth.now())

    private val _isSyncingToGoogle = MutableStateFlow(false)
    val isSyncingToGoogle: StateFlow<Boolean> = _isSyncingToGoogle.asStateFlow()

    private val _googleSyncMessage = MutableStateFlow<String?>(null)
    val googleSyncMessage: StateFlow<String?> = _googleSyncMessage.asStateFlow()

    private val _googleAuthRequest = MutableStateFlow<PendingIntent?>(null)
    val googleAuthRequest: StateFlow<PendingIntent?> = _googleAuthRequest.asStateFlow()

    init {
        observeEventsForSelectedDate()
        observeDatesWithEvents()
        syncRemoteData()
    }

    private fun syncRemoteData() {
        viewModelScope.launch {
            calendarRepository.sync()
        }
    }

    private fun observeEventsForSelectedDate() {
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                calendarRepository.getEventsByDate(date.toString())
            }.collect { items ->
                _uiState.update {
                    it.copy(
                        tasksForSelectedDate = items.map { event -> event.toCalendarTaskUi() },
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun observeDatesWithEvents() {
        viewModelScope.launch {
            _visibleMonth.flatMapLatest { month ->
                val start = month.atDay(1).toString()
                val end = month.atEndOfMonth().toString()
                calendarRepository.getEventsInDateRange(start, end)
            }.collect { items ->
                val dates = items
                    .mapNotNull { runCatching { LocalDate.parse(it.startTime.take(10)) }.getOrNull() }
                    .toSet()
                _uiState.update { it.copy(datesWithTasks = dates) }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        _uiState.update { it.copy(selectedDate = date) }
    }

    fun updateVisibleMonth(month: YearMonth) {
        _visibleMonth.value = month
        _uiState.update { it.copy(visibleMonth = month) }
    }

    fun showAddTaskDialog() {
        _uiState.update { it.copy(showAddTaskDialog = true, editingEvent = null) }
    }

    fun dismissAddTaskDialog() {
        _uiState.update { it.copy(showAddTaskDialog = false, editingEvent = null) }
    }

    fun editEvent(taskId: String) {
        viewModelScope.launch {
            val event = calendarRepository.getEventById(taskId) ?: return@launch
            _uiState.update {
                it.copy(showAddTaskDialog = true, editingEvent = event)
            }
        }
    }

    fun saveTask(
        title: String,
        description: String,
        startTime: String,  // "HH:mm"
        endTime: String,    // "HH:mm"
        location: String,
        color: String,
        link: String,
    ) {
        if (title.isBlank()) return
        val date = _uiState.value.selectedDate
        val editing = _uiState.value.editingEvent
        viewModelScope.launch {
            val event = CalendarEvent(
                id = editing?.id ?: java.util.UUID.randomUUID().toString(),
                title = title.trim(),
                description = description.trim(),
                startTime = combineDateAndTime(date, startTime),
                endTime = combineDateAndTime(date, endTime),
                location = location.trim(),
                color = color,
                link = link.trim(),
            )
            if (editing == null) {
                calendarRepository.insertEvent(event)
            } else {
                calendarRepository.updateEvent(event)
            }
            _uiState.update { it.copy(showAddTaskDialog = false, editingEvent = null) }
        }
    }

    fun toggleTaskCompletion(@Suppress("UNUSED_PARAMETER") taskId: String) {
        // Calendar events don't have completion state. Kept for UI compatibility — no-op.
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val event = calendarRepository.getEventById(taskId) ?: return@launch
            calendarRepository.deleteEvent(event)
        }
    }

    fun syncToGoogleCalendar() {
        if (_isSyncingToGoogle.value) return
        _isSyncingToGoogle.value = true
        viewModelScope.launch {
            val first = googleCalendarRepository.exportAllEvents()
            if (first !is ExportResult.NeedsGoogleSignIn) {
                publishSyncResult(first)
                _isSyncingToGoogle.value = false
                return@launch
            }
            try {
                when (val step = googleCalendarAuthClient.requestAuthorization()) {
                    is GoogleCalendarAuthClient.AuthorizationStep.AccessToken -> {
                        googleCalendarAuthClient.fetchAndStoreUserEmail(step.token)
                        publishSyncResult(googleCalendarRepository.exportAllEvents())
                        _isSyncingToGoogle.value = false
                    }
                    is GoogleCalendarAuthClient.AuthorizationStep.NeedsUserConsent -> {
                        _googleAuthRequest.value = step.pendingIntent
                    }
                }
            } catch (e: Exception) {
                _googleSyncMessage.value =
                    "Could not connect Google account: ${e.message ?: "unknown error"}"
                _isSyncingToGoogle.value = false
            }
        }
    }

    fun consumeGoogleAuthRequest() {
        _googleAuthRequest.value = null
    }

    fun onGoogleAuthResult(data: Intent?) {
        viewModelScope.launch {
            val token = googleCalendarAuthClient.extractFromActivityResult(data)
            if (token == null) {
                _googleSyncMessage.value = "Calendar connection cancelled"
                _isSyncingToGoogle.value = false
                return@launch
            }
            googleCalendarAuthClient.fetchAndStoreUserEmail(token)
            publishSyncResult(googleCalendarRepository.exportAllEvents())
            _isSyncingToGoogle.value = false
        }
    }

    fun clearGoogleSyncMessage() {
        _googleSyncMessage.value = null
    }

    private fun publishSyncResult(result: ExportResult) {
        _googleSyncMessage.value = when (result) {
            is ExportResult.Success -> {
                val parts = mutableListOf<String>()
                if (result.pushed > 0) parts += "${result.pushed} added"
                if (result.alreadyExisted > 0) parts += "${result.alreadyExisted} already there"
                if (result.failed > 0) parts += "${result.failed} failed"
                val core = if (parts.isEmpty()) "Nothing to sync" else parts.joinToString(", ")
                val email = googleCalendarTokenStore.getLinkedEmail()
                if (email != null && parts.isNotEmpty()) "$core → $email" else core
            }
            ExportResult.NeedsGoogleSignIn ->
                "Connect a Google account to enable Calendar sync"
        }
    }

    private fun combineDateAndTime(date: LocalDate, hhmm: String): String {
        if (hhmm.isBlank()) return "${date}T00:00:00"
        val parts = hhmm.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).toString()
            .let { if (it.length == 16) "$it:00" else it } // ensure trailing :00 seconds
    }

    private fun extractHourMinute(iso: String): String? {
        // ISO local datetime is like "2026-05-05T10:00" or "2026-05-05T10:00:00"
        val tIdx = iso.indexOf('T')
        if (tIdx < 0 || iso.length < tIdx + 6) return null
        return iso.substring(tIdx + 1, tIdx + 6)
    }

    private fun CalendarEvent.toCalendarTaskUi(): CalendarTaskUi {
        val s = extractHourMinute(startTime)
        val e = extractHourMinute(endTime)
        val timeRange = if (s != null && e != null) "$s – $e" else null

        return CalendarTaskUi(
            id = this.id,
            title = this.title,
            subtitle = this.description.ifEmpty { null },
            location = this.location.ifEmpty { null },
            link = this.link.ifEmpty { null },
            timeRange = timeRange,
            accent = resolveEventColor(this.color).accent,
            completed = false,
            flagged = false,
        )
    }
}
