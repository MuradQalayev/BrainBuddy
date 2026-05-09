package com.muradgalayev.brainbuddy.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.local.PomodoroTimerManager
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.local.TimerState
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType
import com.muradgalayev.brainbuddy.data.repository.PomodoroRepository
import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val pomodoroTimerManager: PomodoroTimerManager,
    private val pomodoroRepository: PomodoroRepository,
    private val preferencesManager: PreferencesManager
) : ViewModel(){

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        observeWorkspaceData()
        observePomodoroState()
        observePomodoroHistory()
        observeSimplifiedMode()
    }

    private fun observeSimplifiedMode() {
        viewModelScope.launch {
            preferencesManager.simplifiedWorkspace.collect { simplified ->
                _uiState.update { it.copy(isSimplified = simplified) }
            }
        }
    }

    fun toggleSimplifiedMode() {
        viewModelScope.launch {
            val current = _uiState.value.isSimplified
            preferencesManager.setSimplifiedWorkspace(!current)
        }
    }


    private fun observePomodoroState() {
        viewModelScope.launch {
            pomodoroTimerManager.state.collect { timer ->
                val totalSeconds = timer.remainingMs / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                val formattedTime = "%02d:%02d".format(minutes, seconds)

                val statusText = when (timer.sessionType) {
                    PomodoroSessionType.FOCUS -> when (timer.timerState) {
                        TimerState.RUNNING -> "Focus"
                        TimerState.PAUSED -> "Focus paused"
                        TimerState.COMPLETED -> "Focus complete"
                        TimerState.IDLE -> "Focus ready"
                    }
                    PomodoroSessionType.SHORT_BREAK -> when (timer.timerState) {
                        TimerState.RUNNING -> "Short break"
                        TimerState.PAUSED -> "Break paused"
                        TimerState.COMPLETED -> "Break complete"
                        TimerState.IDLE -> "Break ready"
                    }
                    PomodoroSessionType.LONG_BREAK -> when (timer.timerState) {
                        TimerState.RUNNING -> "Long break"
                        TimerState.PAUSED -> "Break paused"
                        TimerState.COMPLETED -> "Break complete"
                        TimerState.IDLE -> "Break ready"
                    }
                }

                _uiState.update {
                    it.copy(
                        pomodoroStatusText = statusText,
                        pomodoroTimeText = formattedTime,
                        pomodoroProgress = timer.progress,
                        pomodoroIsActive = timer.timerState == TimerState.RUNNING ||
                                timer.timerState == TimerState.PAUSED
                    )
                }
            }
        }
    }
    private fun observeWorkspaceData() {
        viewModelScope.launch {
            combine(
                todoRepository.getActiveTodoItems(),
                todoRepository.getCompletedTodoItems(),
                todoRepository.getAllTodoItems()
            ) { activeItems, completedItems, allItems ->
                Triple(activeItems, completedItems, allItems)
            }.collect { (activeItems, completedItems, allItems) ->
                val today = LocalDate.now().toString()
                val nowDate = LocalDate.now()
                val nowTime = LocalTime.now()
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

                val nextMeeting = allItems
                    .filter { !it.isCompleted }
                    .mapNotNull { item ->
                        try {
                            val itemDate = LocalDate.parse(item.date)
                            val itemStartTime = if (item.startTime.isNotBlank()) {
                                LocalTime.parse(item.startTime, timeFormatter)
                            } else {
                                LocalTime.MAX
                            }

                            val isUpcoming =
                                itemDate.isAfter(nowDate) ||
                                        (itemDate.isEqual(nowDate) && itemStartTime >= nowTime)

                            if (isUpcoming) {
                                Triple(item, itemDate, itemStartTime)
                            } else {
                                null
                            }
                        } catch (_: Exception) {
                            null
                        }
                    }
                    .sortedWith(compareBy({ it.second }, { it.third }))
                    .firstOrNull()

                val nextMeetingText = nextMeeting?.let { (item, itemDate, itemStartTime) ->
                    val dayLabel = when {
                        itemDate.isEqual(nowDate) -> "Today"
                        itemDate.isEqual(nowDate.plusDays(1)) -> "Tomorrow"
                        else -> itemDate.toString()
                    }

                    if (item.startTime.isNotBlank()) {
                        "$dayLabel · ${item.startTime}"
                    } else {
                        dayLabel
                    }
                }
                val todayActive = activeItems.count { it.date == today }
                val todayCompleted = completedItems.count { it.date == today }
                val todayTotal = todayActive + todayCompleted

                val progress =
                    if (todayTotal > 0) todayCompleted.toFloat() / todayTotal else 0f

                val completedText = when (todayCompleted) {
                    0 -> "No tasks completed yet"
                    1 -> "You completed 1 task"
                    else -> "You completed $todayCompleted tasks"
                }
                _uiState.update {
                    it.copy(
                        todoTasksLeft = todayActive,
                        todayCompletedTasks = todayCompleted,
                        todayTotalTasks = todayTotal,
                        todoProgress = progress,
                        isLoading = false,
                        nextMeetingText = nextMeetingText,
                        todayCompletedText = completedText
                    )
                }
            }
        }
    }
    private fun observePomodoroHistory() {
        viewModelScope.launch {
            while (true) {
                val today = LocalDate.now()
                val yesterday = today.minusDays(1)
                val zone = ZoneId.systemDefault()

                val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
                val todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

                val yesterdayStart = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()
                val yesterdayEnd = today.atStartOfDay(zone).toInstant().toEpochMilli() - 1

                val todayMinutes = pomodoroRepository.getCompletedFocusMinutesForDay(todayStart, todayEnd)
                val yesterdayMinutes = pomodoroRepository.getCompletedFocusMinutesForDay(yesterdayStart, yesterdayEnd)

                val comparisonText = when {
                    todayMinutes == 0 && yesterdayMinutes == 0 -> "No focus sessions yet"
                    todayMinutes > yesterdayMinutes -> "${todayMinutes - yesterdayMinutes} min more than yesterday"
                    todayMinutes < yesterdayMinutes -> "${yesterdayMinutes - todayMinutes} min less than yesterday"
                    else -> "Same as yesterday"
                }

                val (heroTitle, heroSubtitle) = buildFocusHeroCopy(
                    today = todayMinutes,
                    yesterday = yesterdayMinutes
                )
                val completedToday = _uiState.value.todayCompletedTasks
                val (bannerTitle, bannerMessage) = buildActivityBannerCopy(
                    completedToday = completedToday,
                    todayMinutes = todayMinutes
                )

                _uiState.update {
                    it.copy(
                        todayFocusMinutes = todayMinutes,
                        yesterdayFocusMinutes = yesterdayMinutes,
                        focusComparisonText = comparisonText,
                        focusHeroTitle = heroTitle,
                        focusHeroSubtitle = heroSubtitle,
                        activityBannerTitle = bannerTitle,
                        activityBannerMessage = bannerMessage
                    )
                }

                delay(5000)
            }
        }
    }
}

private fun formatFocusMinutes(minutes: Int): String {
    if (minutes <= 0) return "0m"
    val hours = minutes / 60
    val mins = minutes % 60
    return when {
        hours == 0 -> "${mins}m"
        mins == 0 -> "${hours}h"
        else -> "${hours}h ${mins}m"
    }
}

private fun buildFocusHeroCopy(today: Int, yesterday: Int): Pair<String, String> = when {
    today == 0 && yesterday == 0 ->
        "No focus time yet" to "Unfortunately you haven't focused today. Start a session to begin."
    today == 0 && yesterday > 0 ->
        "Nothing focused today yet" to "Yesterday you did ${formatFocusMinutes(yesterday)}. Don't break the streak!"
    yesterday == 0 && today > 0 ->
        "${formatFocusMinutes(today)} focused today" to "Great start — first focused day in a while!"
    today > yesterday ->
        "${formatFocusMinutes(today)} focused today" to "${formatFocusMinutes(today - yesterday)} more than yesterday — keep it up!"
    today < yesterday ->
        "${formatFocusMinutes(today)} focused today" to "${formatFocusMinutes(yesterday - today)} less than yesterday — push for it."
    else ->
        "${formatFocusMinutes(today)} focused today" to "Matching yesterday's pace — steady wins."
}

private fun buildActivityBannerCopy(completedToday: Int, todayMinutes: Int): Pair<String, String> {
    val tasksPart = when (completedToday) {
        0 -> "no tasks completed"
        1 -> "1 task completed"
        else -> "$completedToday tasks completed"
    }
    val focusPart = if (todayMinutes > 0) "stayed focused for ${formatFocusMinutes(todayMinutes)}"
    else "no focus time logged"

    return when {
        completedToday == 0 && todayMinutes == 0 ->
            "A fresh start" to "Plan a task and try a quick focus session to get going."
        else ->
            "Great work today" to "You've $tasksPart and $focusPart."
    }
}