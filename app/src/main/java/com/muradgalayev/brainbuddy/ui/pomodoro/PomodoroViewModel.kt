package com.muradgalayev.brainbuddy.ui.pomodoro

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.local.AmbientSound
import com.muradgalayev.brainbuddy.data.local.FocusModeManager
import com.muradgalayev.brainbuddy.data.local.ModeManager
import com.muradgalayev.brainbuddy.data.local.PomodoroTimerManager
import com.muradgalayev.brainbuddy.data.local.PomodoroTimerService
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.local.TimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.PomodoroRepository

@HiltViewModel
class PomodoroViewModel @Inject constructor(
    private val timerManager: PomodoroTimerManager,
    private val focusModeManager: FocusModeManager,
    private val preferencesManager: PreferencesManager,
    private val modeManager: ModeManager,
    private val pomodoroRepository: PomodoroRepository,
    private val calendarRepository: CalendarRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val timerState = timerManager.state
    val pomodoroQueue = timerManager.queue
    private val spotifyPrefs = appContext.getSharedPreferences("pomodoro_music", Context.MODE_PRIVATE)
    private val _spotifyPlaylistLink = MutableStateFlow(spotifyPrefs.getString("spotify_playlist", "").orEmpty())
    val spotifyPlaylistLink: StateFlow<String> = _spotifyPlaylistLink.asStateFlow()
    fun clearQueue() = timerManager.clearQueue()

    // which stations of the loaded plan are already ticked off, read from the same rows the
    // breakdown screen edits. deriving this from the queue's own index would be wrong the moment
    // someone jumps to step 3 directly: everything before it would render as done when it isn't
    @OptIn(ExperimentalCoroutinesApi::class)
    val planCompletedIds: StateFlow<Set<String>> = timerManager.queue
        .flatMapLatest { queue ->
            if (queue == null) flowOf(emptySet())
            else calendarRepository.observeSubtasksForEvent(queue.eventId)
                .map { subtasks -> subtasks.filter { it.completed }.map { it.id }.toSet() }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // primes the timer with one station of the plan. false when a session is in flight
    fun selectQueueItem(index: Int): Boolean = timerManager.selectQueueItem(index)

    // jumps to a station and starts it immediately. false when a session is in flight
    fun startQueueItem(index: Int): Boolean {
        if (!timerManager.selectQueueItem(index)) return false
        start()
        return true
    }

    private val _uiExtra = MutableStateFlow(PomodoroUiExtra())
    val uiExtra: StateFlow<PomodoroUiExtra> = _uiExtra.asStateFlow()

    val focusModeSetting = modeManager.effectivePomodoroSettings
        .map { it.autoDndOnFocusSession }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            combine(modeManager.effectivePomodoroSettings, modeManager.activeMode) { settings, mode ->
                Triple(settings.autoDndOnFocusSession, mode, mode?.overrides?.autoDndOnFocusSession != null)
            }.collect { (enabled, mode, controlledByMode) ->
                _uiExtra.update {
                    it.copy(
                        focusModeEnabled = enabled,
                        focusModeControlledByMode = controlledByMode,
                        controllingModeName = mode?.name.takeIf { controlledByMode },
                        focusModePermissionGranted = focusModeManager.hasPermission()
                    )
                }
            }
        }

        observeFocusMinutes()
        // sync is handled centrally by SyncCoordinator, on app start and reconnect. calling
        // repo.sync() from ViewModel init made every Pomodoro tab tap hit Supabase

        timerManager.onTimerStarted = {
            PomodoroTimerService.start(appContext)
        }
    }

    fun start() {
        viewModelScope.launch { timerManager.startUsingEffectiveSettings() }
    }

    fun pause() = timerManager.pause()
    fun resume() = timerManager.resume()
    fun reset() = timerManager.reset()
    fun stop() = timerManager.stop()
    // ring drag began, the countdown holds still until the finger lifts
    fun beginScrub() = timerManager.beginScrub()

    // ring drag: returns true once it has reached the end of the session
    fun scrubToProgress(progress: Float): Boolean = timerManager.scrubToProgress(progress)

    // ring released. at the end the session is banked as done, not cancelled
    fun endScrub(finish: Boolean) = timerManager.endScrub(finish)

    fun skipToNext() = timerManager.skipToNext()
    fun selectSessionType(type: com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType) =
        timerManager.selectSessionType(type)
    fun selectAmbientSound(sound: AmbientSound?) {
        timerManager.selectAmbientSound(sound)
    }

    fun setSpotifyPlaylistLink(link: String) {
        _spotifyPlaylistLink.value = link
        spotifyPrefs.edit().putString("spotify_playlist", link.trim()).apply()
    }

    fun openSpotifyPlaylist() {
        val uri = Uri.parse(_spotifyPlaylistLink.value.trim())
        if (uri.scheme !in listOf("http", "https") || uri.host != "open.spotify.com" ||
            !uri.path.orEmpty().startsWith("/playlist/")
        ) return
        timerManager.selectAmbientSound(null)
        appContext.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun setCustomDuration(minutes: Int) {
        timerManager.setCustomDuration(minutes)
        _uiExtra.update { it.copy(showDurationPicker = false) }
    }

    fun showDurationPicker() {
        if (timerState.value.timerState != TimerState.IDLE) return
        _uiExtra.update { it.copy(showDurationPicker = true) }
    }

    fun dismissDurationPicker() {
        _uiExtra.update { it.copy(showDurationPicker = false) }
    }

    fun requestFocusModePermission() {
        _uiExtra.update { it.copy(showPermissionDialog = true) }
    }

    fun dismissPermissionDialog() {
        _uiExtra.update { it.copy(showPermissionDialog = false) }
    }

    fun refreshFocusModePermission() {
        _uiExtra.update { it.copy(focusModePermissionGranted = focusModeManager.hasPermission()) }
    }

    fun toggleFocusMode(enabled: Boolean) {
        viewModelScope.launch {
            if (modeManager.activeModeNow()?.overrides?.autoDndOnFocusSession != null) return@launch
            preferencesManager.setFocusModeEnabled(enabled)
        }
    }

    fun getFocusModePermissionIntent() = focusModeManager.getPermissionIntent()

    private fun observeFocusMinutes() {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        val yesterday = today.minusDays(1)
        val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val startOfYesterday = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()
        val endOfYesterday = startOfToday - 1

        // Room emits only when the underlying data changes, so no timer and no polling
        viewModelScope.launch {
            pomodoroRepository
                .observeCompletedFocusMinutesForRange(startOfToday, endOfToday)
                .collect { minutes ->
                    _uiExtra.update { it.copy(todayFocusMinutes = minutes) }
                }
        }
        viewModelScope.launch {
            pomodoroRepository
                .observeCompletedFocusMinutesForRange(startOfYesterday, endOfYesterday)
                .collect { minutes ->
                    _uiExtra.update { it.copy(yesterdayFocusMinutes = minutes) }
                }
        }
    }

    val recentSessions = pomodoroRepository.getRecentCompletedSessions(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // the last seven days of focus, bucketed by the device's own calendar days. a list of the
    // last five sessions answers 'what did I just do', this answers 'am I actually keeping this
    // up', which is the question a focus history exists for
    val focusHistory: StateFlow<FocusHistory> = run {
        val zone = java.time.ZoneId.systemDefault()
        val today = java.time.LocalDate.now()
        val windowStart = today.minusDays(WEEK_DAYS - 1L)
        val startMs = windowStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        pomodoroRepository.observeCompletedFocusSessionsInRange(startMs, endMs)
            .map { sessions ->
                val minutesByDate = sessions
                    .groupBy {
                        java.time.Instant.ofEpochMilli(it.endTime).atZone(zone).toLocalDate()
                    }
                    .mapValues { (_, daySessions) ->
                        (daySessions.sumOf { it.actualDurationMs } / 60000L).toInt()
                    }

                val days = (0 until WEEK_DAYS).map { offset ->
                    val date = windowStart.plusDays(offset.toLong())
                    DayFocus(
                        label = date.dayOfWeek
                            .getDisplayName(
                                java.time.format.TextStyle.NARROW,
                                java.util.Locale.getDefault()
                            ),
                        minutes = minutesByDate[date] ?: 0,
                        isToday = date == today,
                    )
                }

                // counted backwards from today. today being empty doesn't break a streak, the day isn't over
                // yet, but any earlier gap does
                var streak = 0
                for (offset in 0 until WEEK_DAYS) {
                    val date = today.minusDays(offset.toLong())
                    val minutes = minutesByDate[date] ?: 0
                    if (minutes > 0) {
                        streak++
                    } else if (date != today) {
                        break
                    }
                }

                FocusHistory(
                    week = days,
                    streakDays = streak,
                    bestDayMinutes = days.maxOfOrNull { it.minutes } ?: 0,
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FocusHistory())
    }

    private companion object {
        const val WEEK_DAYS = 7
    }
}
