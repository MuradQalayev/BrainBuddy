package com.muradgalayev.brainbuddy.ui.calendar

import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.local.entity.TodoColor
import com.muradgalayev.brainbuddy.data.local.entity.TodoItemEntity
import com.muradgalayev.brainbuddy.data.local.entity.TodoPriority
import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class CalendarTaskUi(
    val id: String,
    val title: String,
    val subtitle: String? = null,
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
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val todoRepository: TodoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _visibleMonth = MutableStateFlow(YearMonth.now())

    init {
        observeTasksForSelectedDate()
        observeDatesWithTasks()
    }

    private fun observeTasksForSelectedDate() {
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                todoRepository.getTodoItemsByDate(date.toString())
            }.collect { items ->
                _uiState.update {
                    it.copy(
                        tasksForSelectedDate = items.map { item -> item.toCalendarTaskUi() },
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun observeDatesWithTasks() {
        viewModelScope.launch {
            _visibleMonth.flatMapLatest { month ->
                val start = month.atDay(1).toString()
                val end = month.atEndOfMonth().toString()
                todoRepository.getTodoItemsInRange(start, end)
            }.collect { items ->
                val dates = items.map { LocalDate.parse(it.date) }.toSet()
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
        _uiState.update { it.copy(showAddTaskDialog = true) }
    }

    fun dismissAddTaskDialog() {
        _uiState.update { it.copy(showAddTaskDialog = false) }
    }

    fun addTask(
        title: String,
        description: String,
        startTime: String,
        endTime: String,
        category: String,
        priority: TodoPriority = TodoPriority.MEDIUM,
        color: TodoColor = TodoColor.LIGHT_PINK
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val newTask = TodoItemEntity(
                title = title.trim(),
                description = description.trim(),
                startTime = startTime,
                endTime = endTime,
                date = _uiState.value.selectedDate.toString(),
                color = color.name,
                priority = priority.name,
                category = category
            )
            todoRepository.insertTodoItem(newTask)
            _uiState.update { it.copy(showAddTaskDialog = false) }
        }
    }

    fun toggleTaskCompletion(taskId: String) {
        viewModelScope.launch {
            todoRepository.toggleTodoItemCompletion(taskId)
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val item = todoRepository.getTodoItemById(taskId) ?: return@launch
            todoRepository.deleteTodoItem(item)
        }
    }

    private fun TodoItemEntity.toCalendarTaskUi(): CalendarTaskUi {
        val accentColor = when (this.color) {
            "LIGHT_PINK" -> Color(0xFFFFB9D5)
            "LIGHT_YELLOW" -> Color(0xFFFFF9B9)
            "BURGUNDY" -> Color(0xFFC41E3A)
            "LIGHT_BLUE" -> Color(0xFFB9E4FF)
            else -> Color(0xFF82C8FF)
        }

        val timeRange = if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
            "$startTime \u2013 $endTime"
        } else {
            null
        }

        return CalendarTaskUi(
            id = this.id,
            title = this.title,
            subtitle = this.description.ifEmpty { null },
            timeRange = timeRange,
            accent = accentColor,
            completed = this.isCompleted,
            flagged = this.priority == TodoPriority.HIGH.name
        )
    }
}
