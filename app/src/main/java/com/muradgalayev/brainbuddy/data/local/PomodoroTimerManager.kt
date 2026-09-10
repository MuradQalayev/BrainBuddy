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

// null when an in-flight or queued session owns the timer. kept pure so the completed-state
// regression is covered by a JVM test
internal fun prepareStandaloneTimerState(
    current: PomodoroTimerState,
    hasQueue: Boolean,
    defaultDurationMs: Long,
): PomodoroTimerState? = when {
    hasQueue -> null
    current.timerState == TimerState.RUNNING || current.timerState == TimerState.PAUSED -> null
    current.timerState == TimerState.COMPLETED -> {
        val duration = defaultDurationMs.coerceAtLeast(1L)
        current.copy(
            timerState = TimerState.IDLE,
            totalDurationMs = duration,
            remainingMs = duration,
            progress = 0f,
            resetCount = 0,
            extraTimeAddedMs = 0L,
            focusModeActive = false,
        )
    }
    else -> current
}

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
    // lazy because CalendarRepository references this manager back through the Hilt graph;
    // resolving lazily breaks the construction order without changing semantics
    private val calendarRepository: Lazy<CalendarRepository>,
    private val focusModeManager: FocusModeManager,
    private val modeManager: ModeManager,
) {
    companion object {
        const val DEFAULT_FOCUS_MINUTES = 25
        const val DEFAULT_BREAK_MINUTES = 5
        const val FOCUS_DURATION_MS = DEFAULT_FOCUS_MINUTES * 60 * 1000L
        const val BREAK_DURATION_MS = DEFAULT_BREAK_MINUTES * 60 * 1000L
        private const val TICK_INTERVAL_MS = 100L

        // scrubbing the ring closer to the end than this counts as finish now. without a dead zone
        // you can never actually land on completion with a fingertip
        private const val SCRUB_FINISH_THRESHOLD_MS = 1000L
    }

    private val scope = CoroutineScope(SupervisorJob())

    private val _state = MutableStateFlow(PomodoroTimerState())
    val state: StateFlow<PomodoroTimerState> = _state.asStateFlow()

    private val _queue = MutableStateFlow<PomodoroQueueState?>(null)
    val queue: StateFlow<PomodoroQueueState?> = _queue.asStateFlow()

    // why the idle duration has its current value. only a mode-derived default may follow a mode
    // change: a duration picked by the user, handed over by an agenda item, or loaded from a
    // calendar plan is deliberate input and must never jump at a schedule boundary
    private enum class DurationOrigin { MODE_DEFAULT, CUSTOM, QUEUE }

    @Volatile
    private var effectiveSettings = EffectivePomodoroSettings()
    private var durationOrigin = DurationOrigin.MODE_DEFAULT

    init {
        scope.launch {
            modeManager.effectivePomodoroSettings.collect(::applyEffectiveSettings)
        }
    }

    // refreshes from durable mode and preference state, returning what a launch should use.
    // closes the cold-start window where the eager flows may still hold defaults
    suspend fun refreshEffectiveSettingsNow(): EffectivePomodoroSettings =
        runCatching { modeManager.effectivePomodoroSettingsNow() }
            .getOrElse { effectiveSettings }
            .also(::applyEffectiveSettings)

    // starts with the active mode's effective auto-DND setting
    suspend fun startUsingEffectiveSettings() {
        val settings = refreshEffectiveSettingsNow()
        start(settings.autoDndOnFocusSession)
    }

    // makes the timer safe for a new one-off launch such as the AI start tool. a naturally
    // completed final queue item leaves the shared timer COMPLETED with zero remaining and no
    // queue, and session pickers ignore non-IDLE state, so starting from there used to create a
    // zero-length session of the previous type. re-primes without calling reset(), which would
    // rewrite the already-completed history row as RESET. a loaded calendar queue stays protected
    fun prepareForStandaloneStart(): Boolean {
        val current = _state.value
        val prepared = prepareStandaloneTimerState(
            current = current,
            hasQueue = _queue.value != null,
            defaultDurationMs = getDurationForType(current.sessionType),
        ) ?: return false

        if (prepared != current) {
            timerJob?.cancel()
            durationOrigin = DurationOrigin.MODE_DEFAULT
            currentSession = null
            _state.value = prepared
        }
        return true
    }

    fun applyEffectiveSettings(settings: EffectivePomodoroSettings) {
        effectiveSettings = settings
        val current = _state.value
        if (current.timerState != TimerState.IDLE || durationOrigin != DurationOrigin.MODE_DEFAULT) {
            return
        }
        val duration = durationForType(current.sessionType, settings)
        _state.update {
            it.copy(totalDurationMs = duration, remainingMs = duration, progress = 0f)
        }
    }

    // loads a calendar event's pomodoro plan and primes the timer with its first station. the
    // caller navigates to the Pomodoro screen straight after, and the timer stays IDLE so the
    // user can review the queue before starting
    fun loadCalendarQueue(state: PomodoroQueueState) {
        if (isRunningOrPaused) {
            // don't yank an in-flight session out from under the user
            return
        }
        _queue.value = state
        val first = state.current ?: return
        primeTimerFor(first)
    }

    fun clearQueue() {
        _queue.value = null
    }

    // points the loaded plan at a specific station and primes the timer with it, so any step can
    // be run on its own without working through the ones before it. refused mid-session:
    // re-priming under a running countdown would silently discard the session in flight, and
    // the caller should surface that as 'stop first' rather than retry
    fun selectQueueItem(index: Int): Boolean {
        if (isRunningOrPaused) return false
        val q = _queue.value ?: return false
        val item = q.items.getOrNull(index) ?: return false
        _queue.value = q.copy(currentIndex = index)
        primeTimerFor(item)
        return true
    }

    private fun primeTimerFor(item: PomodoroQueueItem) {
        val type = if (item.isFocus) PomodoroSessionType.FOCUS else PomodoroSessionType.BREAK
        durationOrigin = DurationOrigin.QUEUE
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

    // so the service knows when to start and stop
    var onTimerStarted: (() -> Unit)? = null
    var onTimerStopped: (() -> Unit)? = null
    var onTimerCompleted: (() -> Unit)? = null

    private var timerJob: Job? = null
    private var currentSession: PomodoroSession? = null
    private var sessionStartTimeMs: Long = 0L
    private var pauseStartTimeMs: Long = 0L
    private var totalPausedMs: Long = 0L

    // anchored on SystemClock.elapsedRealtime() so the countdown stays accurate even if delay()
    // gets throttled with the screen off. each tick derives remainingMs from this anchor rather
    // than decrementing in place
    private var targetEndElapsedMs: Long = 0L

    // starts a focus session locked to a shared session's clock. remainingMs comes from the
    // server's ends_at, not from the duration, and that's the whole point: two people notice a
    // session has begun on their own polls, which can be twenty seconds apart, and a fresh full
    // timer on each device would leave the two clocks that far out for the rest of the session.
    // no-op unless idle: arriving at a session while already running your own timer is accepting
    // the company, not a request to restart your work
    suspend fun startSharedSession(
        totalMinutes: Int,
        remainingMs: Long,
        isBreak: Boolean = false,
    ) {
        if (_state.value.timerState != TimerState.IDLE) return
        if (remainingMs <= 0L) return

        // the user's own DND preference applies here exactly as it does to a solo session. it used to
        // be hardcoded off, which had it backwards: a block you committed to another person is the
        // one you least want interrupted, so a shared session got less protection than a private one.
        // never during a break, though, that's the half where being reachable again is the point
        val focusModeEnabled = !isBreak && refreshEffectiveSettingsNow().autoDndOnFocusSession

        val totalMs = totalMinutes.coerceIn(1, 180) * 60 * 1000L
        val clamped = remainingMs.coerceAtMost(totalMs)
        durationOrigin = DurationOrigin.CUSTOM
        _state.update {
            it.copy(
                sessionType = if (isBreak) PomodoroSessionType.BREAK else PomodoroSessionType.FOCUS,
                totalDurationMs = totalMs,
                remainingMs = clamped,
                progress = 1f - clamped.toFloat() / totalMs,
            )
        }
        start(focusModeEnabled)
    }

    fun start(focusModeEnabled: Boolean) {
        val s = _state.value
        // starting is a transition out of a configured IDLE state only. a COMPLETED state has zero
        // remaining time and has to be explicitly primed first
        if (s.timerState != TimerState.IDLE || s.remainingMs <= 0L) return

        sessionStartTimeMs = System.currentTimeMillis()
        totalPausedMs = 0L
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
        // freeze remaining time so resume can re-anchor from now plus the frozen remainder
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

        val duration = durationForReset(s)
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
        // the user explicitly stopped, so don't auto-advance any queued plan
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
        durationOrigin = DurationOrigin.MODE_DEFAULT
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

    // suspends the countdown for the length of a ring drag. without it the ticker keeps running
    // under the finger, so holding the knob at the end for a second would complete the session
    // before the user ever decided to release there
    fun beginScrub() {
        if (!isRunningOrPaused) return
        timerJob?.cancel()
    }

    // ends a ring drag. finish comes from where the finger lifted: at the end of the ring the
    // session is banked as completed, anywhere else the countdown picks up from the scrubbed time
    fun endScrub(finish: Boolean) {
        if (!isRunningOrPaused) return
        if (finish) {
            finishEarly()
            return
        }
        if (_state.value.timerState == TimerState.RUNNING) {
            targetEndElapsedMs = SystemClock.elapsedRealtime() + _state.value.remainingMs
            startTicking()
        }
    }

    // drag-the-ring scrubbing. progress is the fraction of the session the knob has been dragged
    // to, so remaining time is its mirror. the total never grows: you can shorten a session or
    // hand back time you skipped, but not invent time that was never budgeted. returns true once
    // the drag has effectively reached the end
    fun scrubToProgress(progress: Float): Boolean {
        if (!isRunningOrPaused) return false
        val clamped = progress.coerceIn(0f, 1f)
        val total = _state.value.totalDurationMs
        val newRemaining = ((1f - clamped) * total).toLong().coerceIn(0L, total)

        // re-anchor so the next tick counts from where the finger left it, not the original end time
        if (_state.value.timerState == TimerState.RUNNING) {
            targetEndElapsedMs = SystemClock.elapsedRealtime() + newRemaining
        }
        _state.update { it.copy(remainingMs = newRemaining, progress = clamped) }
        return newRemaining <= SCRUB_FINISH_THRESHOLD_MS
    }

    // ends the session right now and books it as completed, the payoff for dragging the ring all
    // the way round. distinct from stop(), which files it as cancelled and clears any queued plan
    fun finishEarly() {
        if (!isRunningOrPaused) return
        timerJob?.cancel()
        // close out an in-flight pause first, or the paused span counts as time spent focusing
        if (_state.value.timerState == TimerState.PAUSED) {
            totalPausedMs += System.currentTimeMillis() - pauseStartTimeMs
        }
        onTimerComplete()
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
        durationOrigin = DurationOrigin.MODE_DEFAULT
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
        val durationMs = minutes.coerceIn(1, 180) * 60 * 1000L
        durationOrigin = DurationOrigin.CUSTOM
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
            // drive the countdown off SystemClock.elapsedRealtime() so it stays accurate when delay() is
            // throttled by Doze, a screen-off, or aggressive OEM background limits. missed ticks snap to
            // the true elapsed time on the next one
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
            // a mode may have changed during the completed station, so the continuously collected
            // effective setting is the right policy for this next session
            start(effectiveSettings.autoDndOnFocusSession)
        }
    }

    private fun moveToNextSession() {
        val s = _state.value
        val nextType = when (s.sessionType) {
            PomodoroSessionType.FOCUS -> PomodoroSessionType.BREAK
            PomodoroSessionType.BREAK -> PomodoroSessionType.FOCUS
        }
        val duration = getDurationForType(nextType)
        durationOrigin = DurationOrigin.MODE_DEFAULT
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

    private fun getDurationForType(type: PomodoroSessionType): Long =
        durationForType(type, effectiveSettings)

    private fun durationForReset(state: PomodoroTimerState): Long = when (durationOrigin) {
        DurationOrigin.CUSTOM,
        DurationOrigin.QUEUE,
        -> state.totalDurationMs
        DurationOrigin.MODE_DEFAULT -> getDurationForType(state.sessionType)
    }

    private fun durationForType(
        type: PomodoroSessionType,
        settings: EffectivePomodoroSettings,
    ): Long = settings.minutesFor(type) * 60 * 1000L
}
