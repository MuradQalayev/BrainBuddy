package com.muradgalayev.brainbuddy.ui.calendar

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.local.PomodoroQueueItem
import com.muradgalayev.brainbuddy.data.local.PomodoroQueueState
import com.muradgalayev.brainbuddy.data.local.PomodoroTimerManager
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.domain.model.CalendarSubtask
import com.muradgalayev.brainbuddy.domain.model.SubtaskKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

// where the plan stands relative to the database. PENDING only ever means a debounced rename
// hasn't landed yet, every other edit writes straight through. JUST_SAVED is the
// acknowledgement flash after an explicit save, and decays back to SAVED on its own
enum class PlanSaveState { SAVED, PENDING, SAVING, JUST_SAVED }

data class BreakdownUiState(
    val eventTitle: String = "",
    val timeRange: String? = null,
    val totalMinutes: Int = 0,
    val accent: Color = Color(0xFFEA580C),
    val stations: List<CalendarSubtaskUi> = emptyList(),
    val isLoading: Boolean = true,
    val missing: Boolean = false,
    val saveState: PlanSaveState = PlanSaveState.SAVED,
) {
    val allocatedMinutes: Int get() = stations.sumOf { it.durationMinutes }
    val doneMinutes: Int get() = stations.filter { it.completed }.sumOf { it.durationMinutes }
    val doneCount: Int get() = stations.count { it.completed }

    // progress by minutes, not station count, a 45m station outweighs a 5m break
    val progress: Float
        get() = if (allocatedMinutes > 0) doneMinutes / allocatedMinutes.toFloat() else 0f

    // index of the station to work on next, or -1 when the plan is finished
    val nextIndex: Int get() = stations.indexOfFirst { !it.completed }

    val isComplete: Boolean get() = stations.isNotEmpty() && nextIndex < 0
    val canRunInPomodoro: Boolean get() = stations.isPomodoroPlan()

    // positive = minutes still unplanned, negative = over the event's length
    val unallocatedMinutes: Int
        get() = if (totalMinutes > 0) totalMinutes - allocatedMinutes else 0
}

// backs the full-screen task breakdown. replaces the old bottom-sheet editor, which had to
// double as an editor and a progress view inside a card and ran out of room doing either well.
// autosave, not draft-and-save: the screen is a tracking surface as much as an editor, and a
// Save button would be wrong for ticking off a station mid-session. structural edits persist
// immediately, typing debounces so a rename isn't one write per keystroke
@HiltViewModel
class TaskBreakdownViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val pomodoroTimerManager: PomodoroTimerManager,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val eventId: String = savedStateHandle["eventId"] ?: ""

    private val _uiState = MutableStateFlow(BreakdownUiState())
    val uiState: StateFlow<BreakdownUiState> = _uiState.asStateFlow()

    private var renameJob: Job? = null
    private var savedFlashJob: Job? = null

    // set while a local edit is in flight. the subtask flow below echoes our own writes back, and
    // applying that echo mid-edit would yank the cursor out of a field the user is still typing in
    private var localEditInFlight = false

    init {
        load()
        observeExternalChanges()
    }

    private fun load() {
        viewModelScope.launch {
            val event = calendarRepository.getEventById(eventId)
            if (event == null) {
                _uiState.update { it.copy(isLoading = false, missing = true) }
                return@launch
            }
            val start = extractHourMinute(event.startTime)
            val end = extractHourMinute(event.endTime)
            _uiState.update {
                it.copy(
                    eventTitle = event.title,
                    timeRange = if (start != null && end != null) "$start – $end" else null,
                    totalMinutes = computeTotalMinutes(event.startTime, event.endTime),
                    accent = resolveEventColor(event.color).accent,
                    isLoading = false,
                )
            }
        }
    }

    // keeps the plan honest when something else changes it, most importantly the Pomodoro timer,
    // which ticks stations complete while this screen may be open behind it
    private fun observeExternalChanges() {
        viewModelScope.launch {
            calendarRepository.observeSubtasksForEvent(eventId).collect { remote ->
                if (localEditInFlight) return@collect
                val mapped = remote.sortedBy { it.orderIndex }.map { it.toUi() }
                if (mapped != _uiState.value.stations) {
                    _uiState.update { it.copy(stations = mapped) }
                }
            }
        }
    }

    // editing

    fun applyPreset(preset: SplitPreset) {
        val total = _uiState.value.totalMinutes.coerceAtLeast(1)
        val stations = when (preset) {
            SplitPreset.HALVES -> evenSplit(total, 2)
            SplitPreset.THIRDS -> evenSplit(total, 3)
            SplitPreset.QUARTERS -> evenSplit(total, 4)
            SplitPreset.POMODORO -> pomodoroSplit(total)
        }
        mutate { stations }
    }

    fun addStation() {
        // default the new station to whatever is left over, so the common case ('one more block to
        // fill the hour') needs no stepper taps at all
        val leftover = _uiState.value.unallocatedMinutes
        val minutes = if (leftover > 0) leftover.coerceAtMost(60) else DEFAULT_STATION_MINUTES
        mutate { current ->
            current + CalendarSubtaskUi(
                id = UUID.randomUUID().toString(),
                // a real title rather than an empty string, so what we hold locally matches what we write.
                // see the note in persist() about echo clobbering
                title = "New step",
                durationMinutes = minutes,
                completed = false,
                kind = SubtaskKind.FOCUS,
            )
        }
    }

    fun removeStation(id: String) = mutate { current -> current.filterNot { it.id == id } }

    fun adjustMinutes(id: String, delta: Int) = mutate { current ->
        current.map {
            if (it.id == id) it.copy(
                durationMinutes = (it.durationMinutes + delta).coerceIn(MIN_MINUTES, MAX_MINUTES)
            ) else it
        }
    }

    fun toggleKind(id: String) = mutate { current ->
        current.map {
            if (it.id != id) it
            else it.copy(
                kind = if (it.kind == SubtaskKind.FOCUS) SubtaskKind.BREAK else SubtaskKind.FOCUS
            )
        }
    }

    fun toggleCompleted(id: String) = mutate { current ->
        current.map { if (it.id == id) it.copy(completed = !it.completed) else it }
    }

    fun move(from: Int, to: Int) {
        val current = _uiState.value.stations
        if (from !in current.indices || to !in current.indices) return
        mutate { it.toMutableList().apply { add(to, removeAt(from)) } }
    }

    // renames without persisting on every keystroke, the write lands once typing settles
    fun renameStation(id: String, title: String) {
        localEditInFlight = true
        _uiState.update { state ->
            state.copy(
                stations = state.stations.map { if (it.id == id) it.copy(title = title) else it },
                saveState = PlanSaveState.PENDING,
            )
        }
        renameJob?.cancel()
        renameJob = viewModelScope.launch {
            delay(RENAME_DEBOUNCE_MS)
            setSaveState(PlanSaveState.SAVING)
            persist()
            localEditInFlight = false
            markSaved(flash = false)
        }
    }

    fun clearPlan() = mutate { emptyList() }

    // writes the plan right now and acknowledges it on screen. the screen already autosaves, so
    // this changes nothing about what ends up stored: it exists because 'did that stick?' is a
    // real question when you're about to walk away from a plan, and a status line doesn't answer
    // it as convincingly as a button you pressed. it also flushes a rename still inside its debounce
    fun savePlan() {
        renameJob?.cancel()
        localEditInFlight = true
        setSaveState(PlanSaveState.SAVING)
        viewModelScope.launch {
            persist()
            localEditInFlight = false
            markSaved(flash = true)
        }
    }

    // applies a structural change locally, then writes it straight through
    private fun mutate(transform: (List<CalendarSubtaskUi>) -> List<CalendarSubtaskUi>) {
        localEditInFlight = true
        _uiState.update {
            it.copy(stations = transform(it.stations), saveState = PlanSaveState.SAVING)
        }
        renameJob?.cancel()
        viewModelScope.launch {
            persist()
            localEditInFlight = false
            markSaved(flash = false)
        }
    }

    private fun setSaveState(state: PlanSaveState) {
        _uiState.update { if (it.saveState == state) it else it.copy(saveState = state) }
    }

    private fun markSaved(flash: Boolean) {
        savedFlashJob?.cancel()
        if (!flash) {
            setSaveState(PlanSaveState.SAVED)
            return
        }
        setSaveState(PlanSaveState.JUST_SAVED)
        savedFlashJob = viewModelScope.launch {
            delay(SAVED_FLASH_MS)
            setSaveState(PlanSaveState.SAVED)
        }
    }

    private suspend fun persist() {
        // titles are written exactly as held, never substituted. swapping a blank for a placeholder
        // here would make the stored row differ from local state, and observeExternalChanges() would
        // echo that difference straight back into the field the user is still typing in
        val cleaned = _uiState.value.stations
            .filter { it.durationMinutes > 0 }
            .mapIndexed { index, ui ->
                CalendarSubtask(
                    id = ui.id,
                    eventId = eventId,
                    title = ui.title.trim(),
                    durationMinutes = ui.durationMinutes,
                    orderIndex = index,
                    completed = ui.completed,
                    kind = ui.kind,
                )
            }
        calendarRepository.replaceSubtasks(eventId, cleaned)
    }

    // sends a single station to the Pomodoro timer, as a queue of one. separate from
    // launchInPomodoro() on purpose: that runs the whole plan in order, which only makes sense
    // for a Pomodoro-shaped breakdown. this one works on any station, 'just do the 20-minute
    // review now', without committing to the rest of the plan
    fun launchStationInPomodoro(stationId: String): Boolean {
        val state = _uiState.value
        val station = state.stations.firstOrNull { it.id == stationId } ?: return false
        pomodoroTimerManager.loadCalendarQueue(
            PomodoroQueueState(
                eventId = eventId,
                eventTitle = state.eventTitle,
                items = listOf(
                    PomodoroQueueItem(
                        subtaskId = station.id,
                        title = station.title.ifBlank { "Focus" },
                        durationMs = station.durationMinutes * 60_000L,
                        isFocus = station.kind == SubtaskKind.FOCUS,
                    )
                ),
                currentIndex = 0,
            )
        )
        return true
    }

    // primes the Pomodoro queue from this plan. returns false when it isn't runnable
    fun launchInPomodoro(): Boolean {
        val state = _uiState.value
        if (!state.canRunInPomodoro) return false
        val firstUnfinished = state.nextIndex.coerceAtLeast(0)
        pomodoroTimerManager.loadCalendarQueue(
            PomodoroQueueState(
                eventId = eventId,
                eventTitle = state.eventTitle,
                items = state.stations.map {
                    PomodoroQueueItem(
                        subtaskId = it.id,
                        title = it.title,
                        durationMs = it.durationMinutes * 60_000L,
                        isFocus = it.kind == SubtaskKind.FOCUS,
                    )
                },
                currentIndex = firstUnfinished,
            )
        )
        return true
    }

    // helpers, mirrors of CalendarViewModel's which are private to it

    private fun CalendarSubtask.toUi() = CalendarSubtaskUi(
        id = id,
        title = title,
        durationMinutes = durationMinutes,
        completed = completed,
        kind = kind,
    )

    private fun evenSplit(total: Int, parts: Int): List<CalendarSubtaskUi> {
        val each = (total / parts).coerceAtLeast(1)
        val remainder = total - each * parts
        return (0 until parts).map { i ->
            CalendarSubtaskUi(
                id = UUID.randomUUID().toString(),
                title = "Part ${i + 1}",
                durationMinutes = each + if (i < remainder) 1 else 0,
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
            val focus = minOf(POMODORO_FOCUS_MINUTES, remaining)
            out += CalendarSubtaskUi(
                id = UUID.randomUUID().toString(),
                title = "Focus $focusIndex",
                durationMinutes = focus,
                completed = false,
                kind = SubtaskKind.FOCUS,
            )
            remaining -= focus
            focusIndex++
            if (remaining <= 0) break
            val brk = minOf(POMODORO_BREAK_MINUTES, remaining)
            out += CalendarSubtaskUi(
                id = UUID.randomUUID().toString(),
                title = "Break",
                durationMinutes = brk,
                completed = false,
                kind = SubtaskKind.BREAK,
            )
            remaining -= brk
        }
        return out
    }

    private fun extractHourMinute(iso: String): String? {
        val tIdx = iso.indexOf('T')
        if (tIdx < 0 || iso.length < tIdx + 6) return null
        return iso.substring(tIdx + 1, tIdx + 6)
    }

    private fun computeTotalMinutes(start: String, end: String): Int = runCatching {
        val s = LocalDateTime.parse(start.padIsoSeconds())
        val e = LocalDateTime.parse(end.padIsoSeconds())
        Duration.between(s, e).toMinutes().toInt().coerceAtLeast(0)
    }.getOrDefault(0)

    private fun String.padIsoSeconds(): String =
        if (length == 16 && contains('T')) "$this:00" else this

    private companion object {
        const val RENAME_DEBOUNCE_MS = 600L
        const val SAVED_FLASH_MS = 1800L
        const val DEFAULT_STATION_MINUTES = 25
        const val MIN_MINUTES = 5
        const val MAX_MINUTES = 240
        const val POMODORO_FOCUS_MINUTES = 25
        const val POMODORO_BREAK_MINUTES = 5
    }
}
