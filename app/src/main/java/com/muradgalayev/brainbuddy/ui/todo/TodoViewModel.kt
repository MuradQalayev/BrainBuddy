package com.muradgalayev.brainbuddy.ui.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.graphics.Color
import com.muradgalayev.brainbuddy.domain.model.TodoItem
import com.muradgalayev.brainbuddy.data.local.entity.TodoPriority
import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import com.muradgalayev.brainbuddy.ui.together.writableFor
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

data class TodoScreenUiState(
    val todayTasks: List<TaskUi> = emptyList(),
    val completedTasks: List<TaskUi> = emptyList(),
    val categories: List<CategoryUi> = emptyList(),
    val taskFilters: List<CategoryUi> = emptyList(),
    val selectedCategory: String = "all",
    val selectedTaskFilter: String = "today",
    val dateTitle: String = "",
    // the day being browsed. defaults to today, the header's picker moves it
    val selectedDate: LocalDate = LocalDate.now(),
    // drives whether the Today shortcut is worth showing at all
    val isViewingToday: Boolean = true,
    val insightTitle: String = "Today's task insights",
    val progress: Float = 0f,
    val isLoading: Boolean = true,
    val showAddTaskDialog: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val editingTask: TaskEditData? = null,
    val successMessage: String? = null
)

data class TaskEditData(
    val id: String,
    val title: String,
    val description: String,
    val startTime: String,
    val endTime: String,
    val priority: String,
    val color: String,
    val category: String,
    val date: String,
)

@HiltViewModel
class TodoViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val togetherRepository: com.muradgalayev.brainbuddy.data.repository.TogetherRepository,
) : ViewModel() {

    // a connection whose to-do list I'm allowed to add to
    data class ShareTarget(val userId: String, val name: String)

    // filtered on grantedToMe: what matters is whether they opened their list to me. the write
    // is still authorised server-side by RLS, this only decides what to show
    val listShareTargets: StateFlow<List<ShareTarget>> = togetherRepository.connections
        .map { connections ->
            connections
                .writableFor(com.muradgalayev.brainbuddy.domain.model.ShareScope.TODOS)
                .map { ShareTarget(it.userId, it.name) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // user id to name, for the 'Added by' badge on tasks someone else created
    val connectionNames: StateFlow<Map<String, String>> = togetherRepository.connections
        .map { list -> list.associate { it.userId to it.name } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private val _uiState = MutableStateFlow(TodoScreenUiState())
    val uiState: StateFlow<TodoScreenUiState> = _uiState.asStateFlow()
    private val searchQuery = MutableStateFlow("")
    private val selectedCategory = MutableStateFlow("all")
    private val selectedTaskFilter = MutableStateFlow("today")
    private val selectedDate = MutableStateFlow(LocalDate.now())

    private val defaultCategories = listOf(
        CategoryUi("all", "All", selected = true),
        CategoryUi("work", "Work"),
        CategoryUi("education", "Education"),
        CategoryUi("personal", "Personal"),
        CategoryUi("sport", "Sport"),
        CategoryUi("health", "Health"),
    )
    private val defaultTaskFilters = listOf(
        CategoryUi("today", "Today", selected = true),
        CategoryUi("upcoming", "Upcoming"),
        CategoryUi("overdue", "Overdue"),
        CategoryUi("high", "High priority"),
        CategoryUi("completed", "Completed"),
    )

    init {
        // filters first: updateDateTitle relabels the day chip, so the list has to exist before it runs
        _uiState.update { it.copy(categories = defaultCategories, taskFilters = defaultTaskFilters) }
        updateDateTitle()
        observeTodoItems()
    }

    private fun observeTodoItems() {
        viewModelScope.launch {
            // connection names ride along in the flow rather than being read off the StateFlow at map
            // time: they arrive on the first Together sync, often after the tasks, and a snapshot read
            // would leave 'Added by a connection' stuck there until the next write to the list
            combine(
                // one query split here, not the active and completed queries combined. Room refreshes those
                // two independently, so a completed task could sit in both snapshots for an instant, and the
                // screen renders both lists in one LazyColumn keyed by task id: the duplicate key crashed
                // the app on roughly every other tick of a task off the list
                combine(
                    todoRepository.getAllTodoItems(),
                    searchQuery,
                    selectedCategory,
                    selectedTaskFilter,
                    selectedDate,
                ) { items, query, category, filter, date ->
                    FilteredTodoSource(items, query, category, filter, date)
                },
                connectionNames,
            ) { source, names -> source to names }
                .collect { (source, names) ->
                // two different todays. viewedDate is the day on screen and drives the list, realToday is
                // the actual date and is what overdue has to mean: browsing next week must not make this
                // week's unfinished tasks stop counting as late
                val viewedDate = source.date.toString()
                val realToday = LocalDate.now().toString()
                val query = source.query.lowercase().trim()

                // disjoint by construction, every task lands on exactly one side
                val (activeItems, completedItems) = source.items.partition { !it.isCompleted }

                val visibleActive = activeItems
                    .filter { matchesTaskFilter(it, source.taskFilter, viewedDate, realToday, completed = false) }
                    .filter { matchesSearch(it, query) }
                    .filter { matchesCategory(it, source.category) }
                    .map { it.toTaskUi(names) }

                val visibleCompleted = completedItems
                    .filter { matchesTaskFilter(it, source.taskFilter, viewedDate, realToday, completed = true) }
                    .filter { matchesSearch(it, query) }
                    .filter { matchesCategory(it, source.category) }
                    .map { it.toTaskUi(names) }

                // progress is for the day you're looking at, or the ring contradicts the list right under it
                val allOnDay = source.items.count { it.date == viewedDate }
                val completedCount = completedItems.count { it.date == viewedDate }
                val progress = if (allOnDay > 0) completedCount.toFloat() / allOnDay else 0f

                _uiState.update {
                    it.copy(
                        todayTasks = if (source.taskFilter == "completed") visibleCompleted else visibleActive,
                        completedTasks = if (source.taskFilter == "completed") emptyList() else visibleCompleted,
                        progress = progress,
                        isLoading = false
                    )
                }
            }
        }
    }
    private fun matchesCategory(item: TodoItem, selectedCategory: String): Boolean {
        return selectedCategory == "all" || item.category == selectedCategory
    }

    private fun matchesSearch(item: TodoItem, query: String): Boolean {
        if (query.isEmpty()) return true
        return item.title.lowercase().contains(query) ||
                item.description.lowercase().contains(query) ||
                item.category.lowercase().contains(query) ||
                item.priority.lowercase().contains(query)
    }

    private fun matchesTaskFilter(
        item: TodoItem,
        filter: String,
        viewedDate: String,
        realToday: String,
        completed: Boolean,
    ): Boolean = when (filter) {
        // upcoming and overdue are about lateness, so they stay anchored to the real date. the
        // default day view follows whatever date is being browsed
        "upcoming" -> !completed && item.date > realToday
        "overdue" -> !completed && item.date < realToday
        "high" -> !completed && item.priority == TodoPriority.HIGH.name
        "completed" -> completed
        else -> item.date == viewedDate
    }

    // same entry point for the picker and the shortcut
    fun selectDate(date: LocalDate) {
        if (selectedDate.value == date) return
        selectedDate.value = date
        updateDateTitle()
    }

    fun goToToday() = selectDate(LocalDate.now())

    private fun updateDateTitle() {
        val date = selectedDate.value
        val today = LocalDate.now()
        // 'Today' beats 'August 12' for the day you're actually on: it's what the user calls it, and
        // it makes leaving today visible without a second label
        val title = when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            today.plusDays(1) -> "Tomorrow"
            // only spell out the year when it isn't this one
            else -> date.format(
                if (date.year == today.year) DateTimeFormatter.ofPattern("MMMM dd")
                else DateTimeFormatter.ofPattern("MMM dd, yyyy")
            )
        }
        _uiState.update {
            it.copy(
                dateTitle = title,
                selectedDate = date,
                // the day filter is the one that follows the browsing date, so its chip has to say which day
                // it means. leaving it reading 'Today' while showing next Tuesday contradicts itself
                taskFilters = it.taskFilters.map { filter ->
                    if (filter.id == "today") filter.copy(title = title) else filter
                }.ifEmpty { defaultTaskFilters },
                isViewingToday = date == today,
                insightTitle = if (date == today) "Today's task insights"
                else "Task insights for ${date.format(DateTimeFormatter.ofPattern("MMM dd"))}",
            )
        }
    }

    fun onTaskClick(taskId: String) {
        viewModelScope.launch {
            todoRepository.toggleTodoItemCompletion(taskId)
        }
    }

    fun onCategoryClick(categoryId: String) {
        selectedCategory.value = categoryId
        _uiState.update { state ->
            state.copy(
                selectedCategory = categoryId,
                categories = state.categories.map { it.copy(selected = it.id == categoryId) }
            )
        }
    }

    fun onTaskFilterClick(filterId: String) {
        selectedTaskFilter.value = filterId
        _uiState.update { state ->
            state.copy(
                selectedTaskFilter = filterId,
                taskFilters = state.taskFilters.map { it.copy(selected = it.id == filterId) },
            )
        }
    }

    // search

    fun toggleSearch() {
        _uiState.update {
            val newActive = !it.isSearchActive
            it.copy(
                isSearchActive = newActive,
                searchQuery = if (!newActive) "" else it.searchQuery
            )
        }
        if (!_uiState.value.isSearchActive) searchQuery.value = ""
    }

    fun onSearchQueryChange(query: String) {
        searchQuery.value = query
        _uiState.update { it.copy(searchQuery = query) }
    }

    // flag / priority

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

    // add task

    fun showAddTaskDialog() {
        _uiState.update { it.copy(showAddTaskDialog = true) }
    }

    fun dismissAddTaskDialog() {
        _uiState.update { it.copy(showAddTaskDialog = false) }
    }
    // adds the task to my own list and/or to the lists of the connections in alsoAddFor, the same
    // audience model the calendar uses. each copy is its own row owned by that person with
    // created_by set to me, so they see it in their list while I can see and remove only my own.
    // my task is written first and independently, so a rejected copy can't cost the user what
    // they typed
    fun addTask(
        title: String,
        description: String,
        startTime: String,
        endTime: String,
        category: String,
        color: String,
        date: String,
        priority: TodoPriority = TodoPriority.MEDIUM,
        alsoAddFor: Set<String> = emptySet(),
        addToMyList: Boolean = true,
    ) {
        if (title.isBlank()) return
        // nothing to write anywhere, a no-op beats silently discarding the input
        if (!addToMyList && alsoAddFor.isEmpty()) return

        viewModelScope.launch {
            val newTask = TodoItem(
                id = java.util.UUID.randomUUID().toString(),
                title = title.trim(),
                description = description.trim(),
                isCompleted = false,
                startTime = startTime,
                endTime = endTime,
                date = date,
                color = color,
                priority = priority.name,
                attendees = 0,
                category = category
            )
            _uiState.update { it.copy(showAddTaskDialog = false) }
            if (addToMyList) {
                todoRepository.insertTodoItem(newTask)
            }

            val message = if (alsoAddFor.isEmpty()) {
                "Your task has been added"
            } else {
                shareTaskWith(alsoAddFor, newTask, addToMyList)
            }

            _uiState.update {
                it.copy(showAddTaskDialog = false, successMessage = message)
            }
        }
    }

    // returns the message to show. mirrors CalendarViewModel.shareEventWith
    private suspend fun shareTaskWith(
        targetIds: Set<String>,
        task: TodoItem,
        keptOnMyList: Boolean,
    ): String {
        val targets = listShareTargets.value.associateBy { it.userId }
        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (id in targetIds) {
            val name = targets[id]?.name ?: "your connection"
            togetherRepository.createTodoFor(
                ownerId = id,
                title = task.title,
                description = task.description,
                date = task.date,
                startTime = task.startTime,
                endTime = task.endTime,
                priority = task.priority,
            ).fold(
                onSuccess = { succeeded += name },
                onFailure = { failed += name },
            )
        }

        val outcome = com.muradgalayev.brainbuddy.ui.together.ShareOutcome(succeeded, failed)
        if (outcome.needsLocalRescue(keptOnMyList)) {
            runCatching { todoRepository.insertTodoItem(task) }
        }
        return outcome.message(
            keptOnMine = keptOnMyList,
            kind = com.muradgalayev.brainbuddy.ui.together.SharedItemKind.TASK,
        )
    }
    fun clearSuccessMessage() {
        _uiState.update { it.copy(successMessage = null) }
    }

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
                        color = item.color,
                        category = item.category,
                        date = item.date,
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
        color: String,
        category: String,
        date: String,
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
                    color = color,
                    category = category,
                    date = date,
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

    private fun TodoItem.toTaskUi(authorNames: Map<String, String> = emptyMap()): TaskUi {
        val accentColor = when (this.color) {
            "red" -> Color(0xFFEF4444)
            "blue" -> Color(0xFF0EA5E9)
            "yellow" -> Color(0xFFF59E0B)
            else -> Color(0xFF0EA5E9)
        }

        val timeRange = if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
            "$startTime \u2013 $endTime"
        } else {
            null
        }

        val durationMinutes = try {
            if (startTime.isNotEmpty() && endTime.isNotEmpty()) {
                val start = LocalTime.parse(startTime, DateTimeFormatter.ofPattern("HH:mm"))
                val end = LocalTime.parse(endTime, DateTimeFormatter.ofPattern("HH:mm"))
                ChronoUnit.MINUTES.between(start, end).toInt().coerceAtLeast(30)
            } else 60
        } catch (_: Exception) {
            60
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
            subtitle = this.description.ifEmpty { null },
            timeRange = timeRange,
            durationMinutes = durationMinutes,
            trailingDate = formattedDate,
            accent = accentColor,
            completed = this.isCompleted,
            flagged = this.priority == TodoPriority.HIGH.name,
            // a generic label rather than a raw uuid when the author is no longer a connection
            addedByName = this.createdByOther?.let { authorNames[it] ?: "a connection" },
        )
    }
}

private data class FilteredTodoSource(
    // every task for the user, active and completed, out of one snapshot
    val items: List<TodoItem>,
    val query: String,
    val category: String,
    val taskFilter: String,
    val date: LocalDate,
)
