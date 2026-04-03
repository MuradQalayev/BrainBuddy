package com.muradgalayev.brainbuddy.data.local

import com.muradgalayev.brainbuddy.data.local.entity.PomodoroCompletionStatus
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionEntity
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType
import com.muradgalayev.brainbuddy.data.repository.PomodoroRepository
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

@Singleton
class PomodoroTimerManager @Inject constructor(
    private val pomodoroRepository: PomodoroRepository,
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

    // Callback for service to know when to start/stop
    var onTimerStarted: (() -> Unit)? = null
    var onTimerStopped: (() -> Unit)? = null
    var onTimerCompleted: (() -> Unit)? = null

    private var timerJob: Job? = null
    private var currentSession: PomodoroSessionEntity? = null
    private var sessionStartTimeMs: Long = 0L
    private var pauseStartTimeMs: Long = 0L
    private var totalPausedMs: Long = 0L
    private var focusModeEnabledForSession: Boolean = false

    fun start(focusModeEnabled: Boolean) {
        val s = _state.value
        if (s.timerState == TimerState.RUNNING) return

        sessionStartTimeMs = System.currentTimeMillis()
        totalPausedMs = 0L
        focusModeEnabledForSession = focusModeEnabled

        val session = PomodoroSessionEntity(
            sessionType = s.sessionType.name,
            plannedDurationMs = s.totalDurationMs,
            startTime = sessionStartTimeMs,
            completionStatus = PomodoroCompletionStatus.IN_PROGRESS.name,
            focusModeEnabled = focusModeEnabled
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
        _state.update { it.copy(timerState = TimerState.PAUSED) }
    }

    fun resume() {
        if (_state.value.timerState != TimerState.PAUSED) return
        totalPausedMs += System.currentTimeMillis() - pauseStartTimeMs
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
        _state.update {
            val subtract = minOf(ADD_TIME_INCREMENT_MS, it.remainingMs - 1000L)
            if (subtract <= 0) return@update it
            val newTotal = maxOf(it.totalDurationMs - ADD_TIME_INCREMENT_MS, it.remainingMs - subtract)
            val newRemaining = it.remainingMs - subtract
            it.copy(
                totalDurationMs = maxOf(newTotal, 1000L),
                remainingMs = maxOf(newRemaining, 1000L),
                progress = 1f - (maxOf(newRemaining, 1000L).toFloat() / maxOf(newTotal, 1000L))
            )
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

        moveToNextSession()
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
            while (_state.value.remainingMs > 0 && _state.value.timerState == TimerState.RUNNING) {
                delay(TICK_INTERVAL_MS)
                _state.update {
                    val newRemaining = maxOf(0L, it.remainingMs - TICK_INTERVAL_MS)
                    val newProgress = 1f - (newRemaining.toFloat() / it.totalDurationMs)
                    it.copy(remainingMs = newRemaining, progress = newProgress)
                }
            }
            if (_state.value.remainingMs <= 0) {
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
