package com.muradgalayev.brainbuddy.ui.pomodoro

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.local.AmbientSound
import com.muradgalayev.brainbuddy.data.local.FocusModeManager
import com.muradgalayev.brainbuddy.data.local.PomodoroTimerManager
import com.muradgalayev.brainbuddy.data.local.PomodoroTimerService
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.local.TimerState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.muradgalayev.brainbuddy.data.repository.PomodoroRepository

@HiltViewModel
class PomodoroViewModel @Inject constructor(
    private val timerManager: PomodoroTimerManager,
    private val focusModeManager: FocusModeManager,
    private val preferencesManager: PreferencesManager,
    private val pomodoroRepository: PomodoroRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val timerState = timerManager.state

    private val _uiExtra = MutableStateFlow(PomodoroUiExtra())
    val uiExtra: StateFlow<PomodoroUiExtra> = _uiExtra.asStateFlow()

    val focusModeSetting = preferencesManager.focusModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        viewModelScope.launch {
            focusModeSetting.collect { enabled ->
                _uiExtra.update {
                    it.copy(
                        focusModeEnabled = enabled,
                        focusModePermissionGranted = focusModeManager.hasPermission()
                    )
                }
            }
        }

        observeFocusMinutes()
        viewModelScope.launch { pomodoroRepository.sync() }

        timerManager.onTimerStarted = {
            PomodoroTimerService.start(appContext)
        }
    }

    fun start() {
        timerManager.start(_uiExtra.value.focusModeEnabled)
    }

    fun pause() = timerManager.pause()
    fun resume() = timerManager.resume()
    fun reset() = timerManager.reset()
    fun stop() = timerManager.stop()
    fun addTime() = timerManager.addTime()
    fun subtractTime() = timerManager.subtractTime()
    fun skipToNext() = timerManager.skipToNext()
    fun selectSessionType(type: com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType) =
        timerManager.selectSessionType(type)
    fun selectAmbientSound(sound: AmbientSound?) = timerManager.selectAmbientSound(sound)

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
        viewModelScope.launch { preferencesManager.setFocusModeEnabled(enabled) }
    }

    fun getFocusModePermissionIntent() = focusModeManager.getPermissionIntent()

    private fun observeFocusMinutes() {
        viewModelScope.launch {
            while (true) {
                val zone = java.time.ZoneId.systemDefault()
                val today = java.time.LocalDate.now()
                val yesterday = today.minusDays(1)

                val startOfToday = today.atStartOfDay(zone).toInstant().toEpochMilli()
                val endOfToday = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                val startOfYesterday = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()
                val endOfYesterday = today.atStartOfDay(zone).toInstant().toEpochMilli() - 1

                val todayMinutes = pomodoroRepository.getCompletedFocusMinutesForRange(
                    startMs = startOfToday,
                    endMs = endOfToday
                )
                val yesterdayMinutes = pomodoroRepository.getCompletedFocusMinutesForRange(
                    startMs = startOfYesterday,
                    endMs = endOfYesterday
                )

                _uiExtra.update {
                    it.copy(
                        todayFocusMinutes = todayMinutes,
                        yesterdayFocusMinutes = yesterdayMinutes
                    )
                }

                kotlinx.coroutines.delay(5000)
            }
        }
    }

    val recentSessions = pomodoroRepository.getRecentCompletedSessions(5)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

}

