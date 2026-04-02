package com.muradgalayev.brainbuddy.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import com.muradgalayev.brainbuddy.data.local.entity.TodoColor
import com.muradgalayev.brainbuddy.data.local.entity.TodoItemEntity
import com.muradgalayev.brainbuddy.data.local.entity.TodoPriority
import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class TodoScreenUiState(
    val todayTasks: List<TaskUi> = emptyList(),
    val completedTasks: List<TaskUi> = emptyList(),
    val categories: List<CategoryUi> = emptyList(),
    val selectedCategory: String = "all",
    val dateTitle: String = "",
    val insightTitle: String = "Today's task insights",
    val progress: Float = 0f,
    val isLoading: Boolean = true,
    val showAddTaskDialog: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val editingTask: TaskEditData? = null
)

data class TaskEditData(
    val id: String,
    val title: String,
    val description: String,
    val startTime: String,
    val endTime: String,
    val priority: String,
    val color: String
)

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoRepository: TodoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TodoScreenUiState())
    val uiState: StateFlow<TodoScreenUiState> = _uiState.asStateFlow()

    private val defaultCategories = listOf(
        CategoryUi("all", "All", selected = true),
        CategoryUi("work", "Work"),
        CategoryUi("education", "Education"),
        CategoryUi("personal", "Personal"),
        CategoryUi("sport", "Sport"),
        CategoryUi("health", "Health"),
    )

    init {
        updateDateTitle()
        _uiState.update { it.copy(categories = defaultCategories) }
        observeTodoItems()
    }

    private fun observeTodoItems() {
        viewModelScope.launch {
            combine(
                todoRepository.getActiveTodoItems(),
                todoRepository.getCompletedTodoItems()
            ) { activeItems, completedItems ->
                Pair(activeItems, completedItems)
            }.collect { (activeItems, completedItems) ->
                val todayDate = LocalDate.now().toString()
                val query = _uiState.value.searchQuery.lowercase().trim()

                val todayActive = activeItems
                    .filter { it.date == todayDate }
                    .filter { matchesSearch(it, query) }
                    .map { it.toTaskUi() }

                val todayCompleted = completedItems
                    .filter { it.date == todayDate }
                    .filter { matchesSearch(it, query) }
                    .map { it.toTaskUi() }

                val allToday = activeItems.count { it.date == todayDate } +
                        completedItems.count { it.date == todayDate }
                val completedCount = completedItems.count { it.date == todayDate }
                val progress = if (allToday > 0) completedCount.toFloat() / allToday else 0f

                _uiState.update {
                    it.copy(
                        todayTasks = todayActive,
                        completedTasks = todayCompleted,
                        progress = progress,
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun matchesSearch(item: TodoItemEntity, query: String): Boolean {
        if (query.isEmpty()) return true
        return item.title.lowercase().contains(query) ||
                item.description.lowercase().contains(query)
    }

    private fun updateDateTitle() {
        val today = LocalDate.now()
        val formatter = DateTimeFormatter.ofPattern("MMMM dd")
        _uiState.update { it.copy(dateTitle = today.format(formatter)) }
    }

    fun onTaskClick(taskId: String) {
        viewModelScope.launch {
            todoRepository.toggleTodoItemCompletion(taskId)
        }
    }

    fun onCategoryClick(categoryId: String) {
        _uiState.update { state ->
            state.copy(
                selectedCategory = categoryId,
                categories = state.categories.map { it.copy(selected = it.id == categoryId) }
            )
        }
    }

    // ── Search ──

    fun toggleSearch() {
        _uiState.update {
            val newActive = !it.isSearchActive
            it.copy(
                isSearchActive = newActive,
                searchQuery = if (!newActive) "" else it.searchQuery
            )
        }
        if (!_uiState.value.isSearchActive) {
            observeTodoItems()
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        observeTodoItems()
    }

    // ── Flag / Priority ──

    fun toggleFlag(taskId: String) {
        viewModelScope.launch {
            val item = todoRepository.getTodoItemById(taskId) ?: return@launch
            val newPriority = if (item.priority == TodoPriority.HIGH.name) {
                TodoPriority.MEDIUM.name
            } else {
                TodoPriority.HIGH.name
            }
            todoRepository.updateTodoItem(item.copy(priority = newPriority))
        }
    }

    // ── Add Task ──

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
                date = LocalDate.now().toString(),
                color = color.name,
                priority = priority.name
            )
            todoRepository.insertTodoItem(newTask)
            _uiState.update { it.copy(showAddTaskDialog = false) }
        }
    }

    // ── Edit Task ──

    fun openEditTask(taskId: String) {
        viewModelScope.launch {
            val item = todoRepository.getTodoItemById(taskId) ?: return@launch
            _uiState.update {
                it.copy(
                    editingTask = TaskEditData(
                        id = item.id,
                        title = item.title,
                        description = item.description,
                        startTime = item.startTime,
                        endTime = item.endTime,
                        priority = item.priority,
                        color = item.color
                    )
                )
            }
        }
    }

    fun dismissEditTask() {
        _uiState.update { it.copy(editingTask = null) }
    }

    fun updateTask(
        id: String,
        title: String,
        description: String,
        startTime: String,
        endTime: String,
        priority: String,
        color: String
    ) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val existing = todoRepository.getTodoItemById(id) ?: return@launch
            todoRepository.updateTodoItem(
                existing.copy(
                    title = title.trim(),
                    description = description.trim(),
                    startTime = startTime,
                    endTime = endTime,
                    priority = priority,
                    color = color
                )
            )
            _uiState.update { it.copy(editingTask = null) }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val item = todoRepository.getTodoItemById(taskId) ?: return@launch
            todoRepository.deleteTodoItem(item)
            _uiState.update { it.copy(editingTask = null) }
        }
    }

    private fun TodoItemEntity.toTaskUi(): TaskUi {
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

        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM")
        val formattedDate = try {
            LocalDate.parse(this.date).format(dateFormatter)
        } catch (_: Exception) {
            this.date
        }

        return TaskUi(
            id = this.id,
            title = this.title,
            subtitle = this.description.ifEmpty { timeRange },
            trailingDate = formattedDate,
            accent = accentColor,
            completed = this.isCompleted,
            flagged = this.priority == TodoPriority.HIGH.name
        )
    }
}
