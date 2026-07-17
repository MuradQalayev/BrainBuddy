package com.muradgalayev.brainbuddy.data.local

import android.os.SystemClock
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroCompletionStatus
import com.muradgalayev.brainbuddy.domain.model.PomodoroSession
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.PomodoroRepository
import dagger.Lazy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class PomodoroTimerState(
    val timerState: TimerState = TimerState.IDLE,
    val sessionType: PomodoroSessionType = PomodoroSessionType.FOCUS,
    val totalDurationMs: Long = 25 * 60 * 1000L,
    val remainingMs: Long = 25 * 60 * 1000L,
    val progress: Float = 0f,
    val resetCount: Int = 0,
    val extraTimeAddedMs: Long = 0L,
    val completedSessions: Int = 0,
    val focusModeActive: Boolean = false,
    val selectedAmbientSound: AmbientSound? = null
)

enum class TimerState { IDLE, RUNNING, PAUSED, COMPLETED }

enum class AmbientSound(val label: String) {
    RAIN("Rain"),
    FOREST("Forest"),
    OCEAN("Ocean Waves"),
    FIREPLACE("Fireplace"),
    WHITE_NOISE("White Noise"),
    CAFE("Cafe"),
    BIRDS("Birds"),
    WIND("Wind")
}

data class PomodoroQueueItem(
    val subtaskId: String,
    val title: String,
    val durationMs: Long,
    val isFocus: Boolean,
)

data class PomodoroQueueState(
    val eventId: String,
    val eventTitle: String,
    val items: List<PomodoroQueueItem>,
    val currentIndex: Int,
) {
    val current: PomodoroQueueItem? get() = items.getOrNull(currentIndex)
    val isLastItem: Boolean get() = currentIndex >= items.lastIndex
    val totalRemainingMs: Long get() =
        items.drop(currentIndex).sumOf { it.durationMs }
}

@Singleton
class PomodoroTimerManager @Inject constructor(
    private val pomodoroRepository: PomodoroRepository,
    // Lazy because CalendarRepository indirectly references this manager via Hilt graph;
    // resolving lazily breaks the construction order without changing semantics.
    private val calendarRepository: Lazy<CalendarRepository>,
    private val focusModeManager: FocusModeManager
) {
    companion object {
        const val FOCUS_DURATION_MS = 25 * 60 * 1000L
        const val SHORT_BREAK_DURATION_MS = 5 * 60 * 1000L
        const val LONG_BREAK_DURATION_MS = 15 * 60 * 1000L
        const val ADD_TIME_INCREMENT_MS = 5 * 60 * 1000L
        const val SESSIONS_BEFORE_LONG_BREAK = 4
        private const val TICK_INTERVAL_MS = 100L
    }

    private val scope = CoroutineScope(SupervisorJob())

    private val _state = MutableStateFlow(PomodoroTimerState())
    val state: StateFlow<PomodoroTimerState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<PomodoroQueueState?>(null)
    val queue: StateFlow<PomodoroQueueState?> = _queue.asStateFlow()

    /**
     * Load a calendar event's Pomodoro plan and prime the timer with its first station.
     * Caller is expected to navigate to the Pomodoro screen right after; the timer stays
     * IDLE so the user can review the queue before starting.
     */
    fun loadCalendarQueue(state: PomodoroQueueState) {
        if (isRunningOrPaused) {
            // Don't yank an in-flight session out from under the user.
            return
        }
        _queue.value = state
        val first = state.current ?: return
        primeTimerFor(first)
    }

    fun clearQueue() {
        _queue.value = null
    }

    private fun primeTimerFor(item: PomodoroQueueItem) {
        val type = if (item.isFocus) PomodoroSessionType.FOCUS else PomodoroSessionType.SHORT_BREAK
        _state.update {
            it.copy(
                timerState = TimerState.IDLE,
                sessionType = type,
                totalDurationMs = item.durationMs,
                remainingMs = item.durationMs,
                progress = 0f,
                extraTimeAddedMs = 0L,
                resetCount = 0,
            )
        }
    }

    // Callback for service to know when to start/stop
    var onTimerStarted: (() -> Unit)? = null
    var onTimerStopped: (() -> Unit)? = null
    var onTimerCompleted: (() -> Unit)? = null

    private var timerJob: Job? = null
    private var currentSession: PomodoroSession? = null
    private var sessionStartTimeMs: Long = 0L
    private var pauseStartTimeMs: Long = 0L
    private var totalPausedMs: Long = 0L
    private var focusModeEnabledForSession: Boolean = false

    /**
     * Anchor end time on SystemClock.elapsedRealtime() so the countdown stays accurate
     * even if the coroutine's delay() gets throttled while the screen is off. Each tick
     * derives `remainingMs` from this anchor instead of decrementing in place.
     */
    private var targetEndElapsedMs: Long = 0L

    fun start(focusModeEnabled: Boolean) {
        val s = _state.value
        if (s.timerState == TimerState.RUNNING) return

        sessionStartTimeMs = System.currentTimeMillis()
        totalPausedMs = 0L
        focusModeEnabledForSession = focusModeEnabled
        targetEndElapsedMs = SystemClock.elapsedRealtime() + s.remainingMs

        val session = PomodoroSession(
            id = java.util.UUID.randomUUID().toString(),
            sessionType = s.sessionType.name,
            plannedDurationMs = s.totalDurationMs,
            actualDurationMs = 0L,
            pausedDurationMs = 0L,
            extraTimeAddedMs = 0L,
            startTime = sessionStartTimeMs,
            endTime = 0L,
            completionStatus = PomodoroCompletionStatus.IN_PROGRESS.name,
            resetCount = 0,
            wasInterrupted = false,
            focusModeEnabled = focusModeEnabled,
            focusModePermissionGranted = false,
            focusModeActivated = false,
            focusModeOnTimestamp = 0L,
            focusModeOffTimestamp = 0L,
            focusModeRestoredSuccessfully = false
        )
        currentSession = session
        scope.launch { pomodoroRepository.insertSession(session) }

        if (focusModeEnabled && s.sessionType == PomodoroSessionType.FOCUS) {
            activateFocusMode()
        }

        _state.update { it.copy(timerState = TimerState.RUNNING) }
        startTicking()
        onTimerStarted?.invoke()
    }

    fun pause() {
        if (_state.value.timerState != TimerState.RUNNING) return
        timerJob?.cancel()
        pauseStartTimeMs = System.currentTimeMillis()
        // Freeze remaining time so resume can re-anchor from "now + frozen remaining".
        val frozen = (targetEndElapsedMs - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
        _state.update { it.copy(timerState = TimerState.PAUSED, remainingMs = frozen) }
    }

    fun resume() {
        if (_state.value.timerState != TimerState.PAUSED) return
        totalPausedMs += System.currentTimeMillis() - pauseStartTimeMs
        targetEndElapsedMs = SystemClock.elapsedRealtime() + _state.value.remainingMs
        _state.update { it.copy(timerState = TimerState.RUNNING) }
        startTicking()
    }

    fun reset() {
        timerJob?.cancel()
        deactivateFocusMode()

        val s = _state.value
        val newResetCount = s.resetCount + 1

        currentSession?.let { session ->
            scope.launch {
                pomodoroRepository.updateSession(
                    session.copy(
                        endTime = System.currentTimeMillis(),
                        actualDurationMs = System.currentTimeMillis() - sessionStartTimeMs - totalPausedMs,
                        pausedDurationMs = totalPausedMs,
                        completionStatus = PomodoroCompletionStatus.RESET.name,
                        resetCount = newResetCount
                    )
                )
            }
        }

        val duration = getDurationForType(s.sessionType)
        _state.update {
            it.copy(
                timerState = TimerState.IDLE,
                remainingMs = duration,
                totalDurationMs = duration,
                progress = 0f,
                resetCount = newResetCount,
                extraTimeAddedMs = 0L
            )
        }
        currentSession = null
        onTimerStopped?.invoke()
    }

    fun stop() {
        timerJob?.cancel()
        deactivateFocusMode()
        // User explicitly stopped — don't auto-advance any queued plan.
        _queue.value = null

        currentSession?.let { session ->
            scope.launch {
                pomodoroRepository.updateSession(
                    session.copy(
                        endTime = System.currentTimeMillis(),
                        actualDurationMs = System.currentTimeMillis() - sessionStartTimeMs - totalPausedMs,
                        pausedDurationMs = totalPausedMs,
                        completionStatus = PomodoroCompletionStatus.CANCELLED.name,
                        wasInterrupted = true,
                        extraTimeAddedMs = _state.value.extraTimeAddedMs,
                        resetCount = _state.value.resetCount
                    )
                )
            }
        }

        val type = PomodoroSessionType.FOCUS
        val duration = getDurationForType(type)
        _state.update {
            PomodoroTimerState(
                sessionType = type,
                totalDurationMs = duration,
                remainingMs = duration,
                completedSessions = it.completedSessions,
                selectedAmbientSound = it.selectedAmbientSound
            )
        }
        currentSession = null
        onTimerStopped?.invoke()
    }

    fun addTime() {
        if (_state.value.timerState == TimerState.RUNNING) {
            targetEndElapsedMs += ADD_TIME_INCREMENT_MS
        }
        _state.update {
            val newExtra = it.extraTimeAddedMs + ADD_TIME_INCREMENT_MS
            val newTotal = it.totalDurationMs + ADD_TIME_INCREMENT_MS
            val newRemaining = it.remainingMs + ADD_TIME_INCREMENT_MS
            it.copy(
                extraTimeAddedMs = newExtra,
                totalDurationMs = newTotal,
                remainingMs = newRemaining,
                progress = 1f - (newRemaining.toFloat() / newTotal)
            )
        }
    }

    fun subtractTime() {
        var actuallySubtracted = 0L
        _state.update {
            val subtract = minOf(ADD_TIME_INCREMENT_MS, it.remainingMs - 1000L)
            if (subtract <= 0) return@update it
            actuallySubtracted = subtract
            val newTotal = maxOf(it.totalDurationMs - ADD_TIME_INCREMENT_MS, it.remainingMs - subtract)
            val newRemaining = it.remainingMs - subtract
            it.copy(
                totalDurationMs = maxOf(newTotal, 1000L),
                remainingMs = maxOf(newRemaining, 1000L),
                progress = 1f - (maxOf(newRemaining, 1000L).toFloat() / maxOf(newTotal, 1000L))
            )
        }
        if (_state.value.timerState == TimerState.RUNNING && actuallySubtracted > 0) {
            targetEndElapsedMs -= actuallySubtracted
        }
    }

    fun skipToNext() {
        timerJob?.cancel()
        deactivateFocusMode()

        currentSession?.let { session ->
            scope.launch {
                pomodoroRepository.updateSession(
                    session.copy(
                        endTime = System.currentTimeMillis(),
                        actualDurationMs = System.currentTimeMillis() - sessionStartTimeMs - totalPausedMs,
                        pausedDurationMs = totalPausedMs,
                        completionStatus = PomodoroCompletionStatus.COMPLETED.name,
                        extraTimeAddedMs = _state.value.extraTimeAddedMs,
                        resetCount = _state.value.resetCount
                    )
                )
            }
        }

        if (_queue.value != null) {
            advanceQueueAfterCompletion(autoStart = false)
        } else {
            moveToNextSession()
        }
        onTimerStopped?.invoke()
    }

    fun selectSessionType(type: PomodoroSessionType) {
        if (_state.value.timerState != TimerState.IDLE) return
        val duration = getDurationForType(type)
        _state.update {
            it.copy(
                sessionType = type,
                totalDurationMs = duration,
                remainingMs = duration,
                progress = 0f,
                extraTimeAddedMs = 0L
            )
        }
    }

    fun setCustomDuration(minutes: Int) {
        if (_state.value.timerState != TimerState.IDLE) return
        val durationMs = minutes * 60 * 1000L
        _state.update {
            it.copy(totalDurationMs = durationMs, remainingMs = durationMs, progress = 0f)
        }
    }

    fun selectAmbientSound(sound: AmbientSound?) {
        _state.update { it.copy(selectedAmbientSound = sound) }
    }

    val isRunningOrPaused: Boolean
        get() = _state.value.timerState == TimerState.RUNNING || _state.value.timerState == TimerState.PAUSED

    private fun startTicking() {
        timerJob?.cancel()
        timerJob = scope.launch {
            // Drive the countdown off SystemClock.elapsedRealtime() so the timer stays
            // accurate even when delay() gets throttled (Doze, screen-off, OEM aggressive
            // background limits). If we miss ticks, the next one snaps to true elapsed.
            while (_state.value.timerState == TimerState.RUNNING) {
                val nowElapsed = SystemClock.elapsedRealtime()
                val newRemaining = (targetEndElapsedMs - nowElapsed).coerceAtLeast(0L)
                _state.update {
                    val newProgress = if (it.totalDurationMs > 0)
                        1f - (newRemaining.toFloat() / it.totalDurationMs)
                    else 0f
                    it.copy(remainingMs = newRemaining, progress = newProgress)
                }
                if (newRemaining <= 0L) break
                delay(TICK_INTERVAL_MS)
            }
            if (_state.value.timerState == TimerState.RUNNING && _state.value.remainingMs <= 0) {
                onTimerComplete()
            }
        }
    }

    private fun onTimerComplete() {
        deactivateFocusMode()

        currentSession?.let { session ->
            scope.launch {
                pomodoroRepository.updateSession(
                    session.copy(
                        endTime = System.currentTimeMillis(),
                        actualDurationMs = System.currentTimeMillis() - sessionStartTimeMs - totalPausedMs,
                        pausedDurationMs = totalPausedMs,
                        completionStatus = PomodoroCompletionStatus.COMPLETED.name,
                        extraTimeAddedMs = _state.value.extraTimeAddedMs,
                        resetCount = _state.value.resetCount,
                        focusModeRestoredSuccessfully = true
                    )
                )
            }
        }

        _state.update {
            val newCompleted = if (it.sessionType == PomodoroSessionType.FOCUS)
                it.completedSessions + 1 else it.completedSessions
            it.copy(
                timerState = TimerState.COMPLETED,
                progress = 1f,
                remainingMs = 0L,
                completedSessions = newCompleted
            )
        }
        onTimerCompleted?.invoke()

        if (_queue.value != null) {
            advanceQueueAfterCompletion(autoStart = true)
        }
    }

    private fun advanceQueueAfterCompletion(autoStart: Boolean) {
        val q = _queue.value ?: return
        val finished = q.current
        if (finished != null) {
            scope.launch {
                runCatching {
                    calendarRepository.get().setSubtaskCompleted(finished.subtaskId, true)
                }
            }
        }
        if (q.isLastItem) {
            _queue.value = null
            return
        }
        val next = q.copy(currentIndex = q.currentIndex + 1)
        _queue.value = next
        val nextItem = next.current ?: return
        primeTimerFor(nextItem)
        if (autoStart) {
            // Re-arm with whatever focus-mode preference was used last; the manager keeps
            // that flag from the previous start.
            start(focusModeEnabledForSession)
        }
    }

    private fun moveToNextSession() {
        val s = _state.value
        val nextType = when (s.sessionType) {
            PomodoroSessionType.FOCUS -> {
                if (s.completedSessions > 0 && s.completedSessions % SESSIONS_BEFORE_LONG_BREAK == 0)
                    PomodoroSessionType.LONG_BREAK
                else PomodoroSessionType.SHORT_BREAK
            }
            else -> PomodoroSessionType.FOCUS
        }
        val duration = getDurationForType(nextType)
        _state.update {
            it.copy(
                timerState = TimerState.IDLE,
                sessionType = nextType,
                totalDurationMs = duration,
                remainingMs = duration,
                progress = 0f,
                extraTimeAddedMs = 0L,
                resetCount = 0
            )
        }
        currentSession = null
    }

    private fun activateFocusMode() {
        val permGranted = focusModeManager.hasPermission()
        val activated = if (permGranted) focusModeManager.enableDnd() else false

        currentSession = currentSession?.copy(
            focusModePermissionGranted = permGranted,
            focusModeActivated = activated,
            focusModeOnTimestamp = if (activated) System.currentTimeMillis() else 0L
        )
        currentSession?.let { session ->
            scope.launch { pomodoroRepository.updateSession(session) }
        }
        _state.update { it.copy(focusModeActive = activated) }
    }

    private fun deactivateFocusMode() {
        if (focusModeManager.isActiveByUs()) {
            val restored = focusModeManager.disableDnd()
            currentSession = currentSession?.copy(
                focusModeOffTimestamp = System.currentTimeMillis(),
                focusModeRestoredSuccessfully = restored
            )
            currentSession?.let { session ->
                scope.launch { pomodoroRepository.updateSession(session) }
            }
            _state.update { it.copy(focusModeActive = false) }
        }
    }

    private fun getDurationForType(type: PomodoroSessionType): Long = when (type) {
        PomodoroSessionType.FOCUS -> FOCUS_DURATION_MS
        PomodoroSessionType.SHORT_BREAK -> SHORT_BREAK_DURATION_MS
        PomodoroSessionType.LONG_BREAK -> LONG_BREAK_DURATION_MS
    }
}
