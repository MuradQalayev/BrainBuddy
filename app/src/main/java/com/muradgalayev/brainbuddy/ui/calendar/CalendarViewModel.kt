package com.muradgalayev.brainbuddy.ui.calendar

import android.app.PendingIntent
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.google.GoogleCalendarAuthClient
import com.muradgalayev.brainbuddy.data.google.GoogleCalendarTokenStore
import com.muradgalayev.brainbuddy.data.local.PomodoroQueueItem
import com.muradgalayev.brainbuddy.data.local.PomodoroQueueState
import com.muradgalayev.brainbuddy.data.local.PomodoroTimerManager
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.ExportResult
import com.muradgalayev.brainbuddy.data.repository.GoogleCalendarRepository
import com.muradgalayev.brainbuddy.data.sync.CalendarSyncScheduler
import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import com.muradgalayev.brainbuddy.domain.model.CalendarSubtask
import com.muradgalayev.brainbuddy.domain.model.SubtaskKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

data class CalendarSubtaskUi(
    val id: String,
    val title: String,
    val durationMinutes: Int,
    val completed: Boolean,
    val kind: SubtaskKind,
)

data class CalendarTaskUi(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val location: String? = null,
    val link: String? = null,
    val timeRange: String? = null,
    val totalMinutes: Int = 0,
    val accent: Color,
    val completed: Boolean = false,
    val flagged: Boolean = false,
    val subtasks: List<CalendarSubtaskUi> = emptyList(),
)

data class SubtaskEditorState(
    val eventId: String,
    val eventTitle: String,
    val totalMinutes: Int,
    val accent: Color,
    val drafts: List<CalendarSubtaskUi>,
)

data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val tasksForSelectedDate: List<CalendarTaskUi> = emptyList(),
    val datesWithTasks: Set<LocalDate> = emptySet(),
    val visibleMonth: YearMonth = YearMonth.now(),
    val showAddTaskDialog: Boolean = false,
    val editingEvent: CalendarEvent? = null,
    val expandedEventId: String? = null,
    val subtaskEditor: SubtaskEditorState? = null,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val googleCalendarRepository: GoogleCalendarRepository,
    private val googleCalendarAuthClient: GoogleCalendarAuthClient,
    private val googleCalendarTokenStore: GoogleCalendarTokenStore,
    private val pomodoroTimerManager: PomodoroTimerManager,
    private val calendarSyncScheduler: CalendarSyncScheduler,
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
        // Room emits reactively; SyncCoordinator already pulls Supabase on app
        // start and every network reconnect. Firing another sync here just made
        // every tab-tap on Calendar feel like a "refetch" flash for no gain.
    }

    private fun observeEventsForSelectedDate() {
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                combine(
                    calendarRepository.getEventsByDate(date.toString()),
                    calendarRepository.observeSubtasksForUser(),
                ) { events, allSubtasks ->
                    val grouped = allSubtasks.groupBy { it.eventId }
                    events.map { event -> event.toCalendarTaskUi(grouped[event.id].orEmpty()) }
                }
            }.collect { items ->
                _uiState.update { state ->
                    state.copy(
                        tasksForSelectedDate = items,
                        isLoading = false,
                        // Keep editor's drafts in sync if subtasks changed under it
                        subtaskEditor = state.subtaskEditor?.let { editor ->
                            val match = items.firstOrNull { it.id == editor.eventId }
                            if (match != null && editor.drafts.isEmpty()) {
                                editor.copy(
                                    drafts = match.subtasks,
                                    totalMinutes = match.totalMinutes,
                                )
                            } else editor
                        }
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
        _uiState.update { it.copy(selectedDate = date, expandedEventId = null) }
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

    fun toggleExpanded(taskId: String) {
        _uiState.update {
            it.copy(expandedEventId = if (it.expandedEventId == taskId) null else taskId)
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
                id = editing?.id ?: UUID.randomUUID().toString(),
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

    fun toggleSubtaskCompleted(subtaskId: String, completed: Boolean) {
        viewModelScope.launch {
            calendarRepository.setSubtaskCompleted(subtaskId, completed)
        }
    }

    /**
     * If the event's subtasks look like a Pomodoro plan (contains BREAK stations and at
     * least one FOCUS), prime the Pomodoro timer with the queue and signal the caller to
     * navigate. Returns true on success.
     */
    fun launchInPomodoro(eventId: String): Boolean {
        val task = _uiState.value.tasksForSelectedDate.firstOrNull { it.id == eventId }
            ?: return false
        if (!task.subtasks.isPomodoroPlan()) return false
        // Skip already-completed leading items so the user picks up where they left off.
        val firstUnfinished = task.subtasks.indexOfFirst { !it.completed }.let {
            if (it < 0) 0 else it
        }
        val items = task.subtasks.map {
            PomodoroQueueItem(
                subtaskId = it.id,
                title = it.title,
                durationMs = it.durationMinutes * 60_000L,
                isFocus = it.kind == SubtaskKind.FOCUS,
            )
        }
        pomodoroTimerManager.loadCalendarQueue(
            PomodoroQueueState(
                eventId = task.id,
                eventTitle = task.title,
                items = items,
                currentIndex = firstUnfinished,
            )
        )
        return true
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val event = calendarRepository.getEventById(taskId) ?: return@launch
            calendarRepository.deleteEvent(event)
        }
    }

    // ── Subtask editor ──

    fun openSubtaskEditor(taskId: String) {
        val task = _uiState.value.tasksForSelectedDate.firstOrNull { it.id == taskId } ?: return
        _uiState.update {
            it.copy(
                subtaskEditor = SubtaskEditorState(
                    eventId = task.id,
                    eventTitle = task.title,
                    totalMinutes = task.totalMinutes,
                    accent = task.accent,
                    drafts = task.subtasks,
                )
            )
        }
    }

    fun dismissSubtaskEditor() {
        _uiState.update { it.copy(subtaskEditor = null) }
    }

    fun updateEditorDrafts(drafts: List<CalendarSubtaskUi>) {
        _uiState.update { state ->
            state.copy(subtaskEditor = state.subtaskEditor?.copy(drafts = drafts))
        }
    }

    fun applySplitPreset(preset: SplitPreset) {
        val editor = _uiState.value.subtaskEditor ?: return
        val total = editor.totalMinutes.coerceAtLeast(1)
        val drafts = when (preset) {
            SplitPreset.HALVES -> evenSplit(total, 2, "Part")
            SplitPreset.THIRDS -> evenSplit(total, 3, "Part")
            SplitPreset.QUARTERS -> evenSplit(total, 4, "Part")
            SplitPreset.POMODORO -> pomodoroSplit(total)
        }
        updateEditorDrafts(drafts)
    }

    fun saveSubtaskEditor() {
        val editor = _uiState.value.subtaskEditor ?: return
        viewModelScope.launch {
            val cleaned = editor.drafts
                .filter { it.title.isNotBlank() && it.durationMinutes > 0 }
                .mapIndexed { index, ui ->
                    CalendarSubtask(
                        id = ui.id,
                        eventId = editor.eventId,
                        title = ui.title.trim(),
                        durationMinutes = ui.durationMinutes,
                        orderIndex = index,
                        completed = ui.completed,
                        kind = ui.kind,
                    )
                }
            calendarRepository.replaceSubtasks(editor.eventId, cleaned)
            _uiState.update { it.copy(subtaskEditor = null) }
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
                        calendarSyncScheduler.schedulePeriodic()
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
            calendarSyncScheduler.schedulePeriodic()
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

    private fun computeTotalMinutes(start: String, end: String): Int {
        return runCatching {
            val s = LocalDateTime.parse(start.padIsoSeconds())
            val e = LocalDateTime.parse(end.padIsoSeconds())
            Duration.between(s, e).toMinutes().toInt().coerceAtLeast(0)
        }.getOrDefault(0)
    }

    private fun String.padIsoSeconds(): String =
        if (length == 16 && contains('T')) "$this:00" else this

    private fun CalendarEvent.toCalendarTaskUi(subtasks: List<CalendarSubtask>): CalendarTaskUi {
        val s = extractHourMinute(startTime)
        val e = extractHourMinute(endTime)
        val timeRange = if (s != null && e != null) "$s – $e" else null
        val total = computeTotalMinutes(startTime, endTime)

        return CalendarTaskUi(
            id = this.id,
            title = this.title,
            subtitle = this.description.ifEmpty { null },
            location = this.location.ifEmpty { null },
            link = this.link.ifEmpty { null },
            timeRange = timeRange,
            totalMinutes = total,
            accent = resolveEventColor(this.color).accent,
            completed = false,
            flagged = false,
            subtasks = subtasks
                .sortedBy { it.orderIndex }
                .map {
                    CalendarSubtaskUi(
                        id = it.id,
                        title = it.title,
                        durationMinutes = it.durationMinutes,
                        completed = it.completed,
                        kind = it.kind,
                    )
                },
        )
    }

    private fun evenSplit(total: Int, parts: Int, baseLabel: String): List<CalendarSubtaskUi> {
        val each = (total / parts).coerceAtLeast(1)
        val remainder = total - each * parts
        return (0 until parts).map { i ->
            val extra = if (i < remainder) 1 else 0
            CalendarSubtaskUi(
                id = UUID.randomUUID().toString(),
                title = "$baseLabel ${i + 1}",
                durationMinutes = each + extra,
                completed = false,
                kind = SubtaskKind.FOCUS,
            )
        }
    }

    private fun pomodoroSplit(total: Int): List<CalendarSubtaskUi> {
        val out = mutableListOf<CalendarSubtaskUi>()
        var remaining = total
        var focusIndex = 1
        while (remaining > 0) {
            val focus = minOf(25, remaining)
            out += CalendarSubtaskUi(
                id = UUID.randomUUID().toString(),
                title = "Focus $focusIndex",
                durationMinutes = focus,
                completed = false,
                kind = SubtaskKind.FOCUS,
            )
            remaining -= focus
            if (remaining <= 0) break
            val br = minOf(5, remaining)
            out += CalendarSubtaskUi(
                id = UUID.randomUUID().toString(),
                title = "Break",
                durationMinutes = br,
                completed = false,
                kind = SubtaskKind.BREAK,
            )
            remaining -= br
            focusIndex++
        }
        return out
    }
}

enum class SplitPreset { HALVES, THIRDS, QUARTERS, POMODORO }

/**
 * Heuristic: a "Pomodoro plan" is any subtask list with at least one FOCUS station and
 * at least one BREAK station. Strict alternation isn't required — users may add custom
 * stations after applying the preset.
 */
fun List<CalendarSubtaskUi>.isPomodoroPlan(): Boolean {
    if (size < 2) return false
    var hasFocus = false
    var hasBreak = false
    for (s in this) {
        when (s.kind) {
            SubtaskKind.FOCUS -> hasFocus = true
            SubtaskKind.BREAK -> hasBreak = true
        }
        if (hasFocus && hasBreak) return true
    }
    return false
}