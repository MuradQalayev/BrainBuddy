package com.muradgalayev.brainbuddy.ui.activity



import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.muradgalayev.brainbuddy.data.local.PomodoroTimerManager
import com.muradgalayev.brainbuddy.data.local.TimerState
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType
@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val pomodoroTimerManager: PomodoroTimerManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    init {
        observeWorkspaceData()
        observePomodoroState()
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
    }    private fun observeWorkspaceData() {
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
}