package com.muradgalayev.brainbuddy.ui.todo
import com.muradgalayev.brainbuddy.ui.sharedcomponents.AppSearchBar
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Today
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.ui.sharedcomponents.SuccessPopup
import com.muradgalayev.brainbuddy.ui.accessibility.speaking
@Composable
fun TodoScreen(
    onConnectPeople: () -> Unit = {},
    viewModel: TodoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val listShareTargets by viewModel.listShareTargets.collectAsState()
    val p = rememberTodoPalette()
    var taskToDelete by remember { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val fabInteractionSource = remember { MutableInteractionSource() }
    val fabPressed by fabInteractionSource.collectIsPressedAsState()

    val fabScale by animateFloatAsState(
        targetValue = if (fabPressed) 0.95f else 1f,
        animationSpec = tween(100),
        label = "fabScale"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        TodoListContent(
            palette = p,
            dateTitle = uiState.dateTitle,
            insightTitle = uiState.insightTitle,
            progress = uiState.progress,
            categories = uiState.categories,
            taskFilters = uiState.taskFilters,
            selectedTaskFilter = uiState.selectedTaskFilter,
            isViewingToday = uiState.isViewingToday,
            todayTasks = uiState.todayTasks,
            completedTasks = uiState.completedTasks,
            isLoading = uiState.isLoading,
            isSearchActive = uiState.isSearchActive,
            searchQuery = uiState.searchQuery,
            onCategoryClick = viewModel::onCategoryClick,
            onTaskFilterClick = viewModel::onTaskFilterClick,
            onTaskClick = viewModel::onTaskClick,
            onTaskLongClick = viewModel::openEditTask,
            onFlagClick = viewModel::toggleFlag,
            onSearchClick = viewModel::toggleSearch,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onDeleteTask = { taskToDelete = it },
            onOpenFilters = { showFilters = true },
            // the platform dialog, the same one the add-task sheet uses, so the two ways of choosing a
            // date in this feature look and behave alike
            onPickDate = {
                val current = uiState.selectedDate
                android.app.DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        viewModel.selectDate(LocalDate.of(year, month + 1, day))
                    },
                    current.year,
                    current.monthValue - 1,
                    current.dayOfMonth,
                ).show()
            },
            onGoToToday = viewModel::goToToday,
        )

        FloatingActionButton(
            onClick = { viewModel.showAddTaskDialog() },
            interactionSource = fabInteractionSource,
            shape = CircleShape,
            containerColor = p.lavender,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .graphicsLayer {
                    scaleX = fabScale
                    scaleY = fabScale
                }
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = p.lavender.copy(alpha = 0.4f),
                    spotColor = p.lavender.copy(alpha = 0.4f)
                )
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = "Add task",
                modifier = Modifier.size(28.dp)
            )
        }
    }

    if (showFilters) {
        TodoFilterSheet(
            palette = p,
            taskFilters = uiState.taskFilters,
            categories = uiState.categories,
            onTaskFilterClick = viewModel::onTaskFilterClick,
            onCategoryClick = viewModel::onCategoryClick,
            onReset = {
                viewModel.onTaskFilterClick("today")
                viewModel.onCategoryClick("all")
            },
            onDismiss = { showFilters = false },
        )
    }

    if (uiState.showAddTaskDialog) {
        AddTaskDialog(
            palette = p,
            shareTargets = listShareTargets,
            onConnectPeople = onConnectPeople,
            onDismiss = viewModel::dismissAddTaskDialog,
            onConfirm = { title, description, startTime, endTime, category, color, date,
                          priority, alsoAddFor, addToMyList ->
                viewModel.addTask(
                    title, description, startTime, endTime, category, color, date, priority,
                    alsoAddFor = alsoAddFor,
                    addToMyList = addToMyList,
                )
            }
        )
    }

    uiState.editingTask?.let { task ->
        EditTaskDialog(
            palette = p,
            task = task,
            onDismiss = viewModel::dismissEditTask,
            onSave = { id, title, desc, start, end, priority, color, category, date ->
                viewModel.updateTask(id, title, desc, start, end, priority, color, category, date)
            },
            onDelete = { viewModel.deleteTask(it) }
        )
    }
    uiState.successMessage?.let { message ->
        SuccessPopup(
            message = message,
            onDismiss = { viewModel.clearSuccessMessage() }
        )
    }
    taskToDelete?.let { taskId ->
        Dialog(
            onDismissRequest = { taskToDelete = null }
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text(
                        text = "Delete Task?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Are you sure you want to delete this task?",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            taskToDelete = null
                        }) {
                            Text("Cancel")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        TextButton(
                            onClick = {
                                viewModel.deleteTask(taskId)
                                taskToDelete = null
                            }
                        ) {
                            Text(
                                "Delete",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

// list content
@Composable
fun TodoListContent(
    modifier: Modifier = Modifier,
    palette: TodoPalette = rememberTodoPalette(),
    dateTitle: String = "January 10",
    insightTitle: String = "Today's task insights",
    progress: Float = 0.2f,
    categories: List<CategoryUi> = emptyList(),
    taskFilters: List<CategoryUi> = emptyList(),
    selectedTaskFilter: String = "today",
    // false once the user has browsed away from today, reveals the shortcut back
    isViewingToday: Boolean = true,
    todayTasks: List<TaskUi> = emptyList(),
    completedTasks: List<TaskUi> = emptyList(),
    isLoading: Boolean = false,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onCategoryClick: (String) -> Unit = {},
    onTaskFilterClick: (String) -> Unit = {},
    onTaskClick: (String) -> Unit = {},
    onTaskLongClick: (String) -> Unit = {},
    onFlagClick: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onDeleteTask: (String) -> Unit = {},
    onOpenFilters: () -> Unit = {},
    onPickDate: () -> Unit = {},
    onGoToToday: () -> Unit = {},
) {
    val p = palette

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(p.bg),
        contentPadding = PaddingValues(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item { Spacer(Modifier.height(16.dp)) }

        // date header and search
        item {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    if (targetState) {
                        (fadeIn(tween(220)) + expandHorizontally(expandFrom = Alignment.End))
                            .togetherWith(fadeOut(tween(160)))
                    } else {
                        fadeIn(tween(160)).togetherWith(
                            fadeOut(tween(220)) + shrinkHorizontally(shrinkTowards = Alignment.End)
                        )
                    }
                },
                label = "headerSwitch"
            ) { searching ->
                if (searching) {
                    AppSearchBar(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClose = onSearchClick,
                        placeholderText = "Search tasks..."
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // the date is the control. a separate calendar icon would have been another thing to find, and
                        // the heading was already the most obvious place to tap to change the day
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onPickDate,
                                )
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = dateTitle,
                                color = p.ink,
                                fontSize = 26.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.3).sp
                            )
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Rounded.ExpandMore,
                                contentDescription = "Choose a date",
                                tint = p.muted,
                                modifier = Modifier.size(22.dp),
                            )
                        }

                        Spacer(Modifier.weight(1f))

                        // only while you're somewhere else. on today it's a button that does nothing, where you look most
                        AnimatedVisibility(
                            visible = !isViewingToday,
                            enter = fadeIn(tween(180)) + expandHorizontally(),
                            exit = fadeOut(tween(140)) + shrinkHorizontally(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(p.pillBg)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                        onClick = onGoToToday,
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Today,
                                    contentDescription = null,
                                    tint = p.lavender,
                                    modifier = Modifier.size(15.dp),
                                )
                                Spacer(Modifier.width(5.dp))
                                Text(
                                    text = "Today",
                                    color = p.lavender,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(p.pillBg)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onSearchClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = "Search",
                                tint = p.muted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        // insight card
        item {
            InsightCard(palette = p, title = insightTitle, progress = progress)
        }

        item {
            val activeCount = (if (selectedTaskFilter != "today") 1 else 0) +
                (if (categories.any { it.selected && it.id != "all" }) 1 else 0)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Surface(
                    onClick = onOpenFilters,
                    shape = RoundedCornerShape(50),
                    color = if (activeCount > 0) p.lavender else p.pillBg,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 15.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Tune, null, tint = if (activeCount > 0) Color.White else p.muted, modifier = Modifier.size(18.dp))
                        Text(
                            if (activeCount > 0) "Filters · $activeCount" else "Filters",
                            modifier = Modifier.padding(start = 7.dp),
                            color = if (activeCount > 0) Color.White else p.ink,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }

        // today section
        item {
            SectionTitle(
                text = when (selectedTaskFilter) {
                    "upcoming" -> "Upcoming tasks"
                    "overdue" -> "Overdue tasks"
                    "high" -> "High-priority tasks"
                    "completed" -> "Completed tasks"
                    else -> "Today"
                },
                palette = p,
            )
        }

        if (todayTasks.isEmpty() && completedTasks.isEmpty() && !isLoading) {
            item {
                EmptyState(
                    palette = p,
                    isSearchResult = isSearchActive && searchQuery.isNotEmpty(),
                    filter = selectedTaskFilter,
                )
            }
        }

        itemsIndexed(todayTasks, key = { _, task -> task.id }) { index, task ->
            AnimatedVisibility(
                visible = true,
                enter = slideInVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    initialOffsetY = { it / 2 }
                ) + fadeIn(tween(300, delayMillis = index * 50))
            ) {
                SwipeableTaskCard(
                    palette = p,
                    task = task,
                    onClick = { onTaskClick(task.id) },
                    onLongClick = { onTaskLongClick(task.id) },
                    onFlagClick = { onFlagClick(task.id) },
                    onEdit = { onTaskLongClick(task.id) },
                    onDelete = { onDeleteTask(task.id) }
                )
            }
        }

        // completed section
        if (completedTasks.isNotEmpty() && selectedTaskFilter != "completed") {
            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle(text = "Completed tasks", palette = p)
            }

            // namespaced against the active list above: both sections live in one LazyColumn, and a bare
            // id shared across them is a hard crash rather than a glitch. the two lists are disjoint by
            // construction now, this keeps it from being fatal if that ever stops holding
            itemsIndexed(completedTasks, key = { _, task -> "completed-${task.id}" }) { index, task ->
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(tween(300, delayMillis = index * 50)) + scaleIn(
                        initialScale = 0.95f,
                        animationSpec = tween(300, delayMillis = index * 50)
                    )
                ) {
                    SwipeableTaskCard(
                        palette = p,
                        task = task,
                        onClick = { onTaskClick(task.id) },
                        onLongClick = { onTaskLongClick(task.id) },
                        onFlagClick = { onFlagClick(task.id) },
                        onEdit = { onTaskLongClick(task.id) },
                        onDelete = { onDeleteTask(task.id) }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(100.dp)) }
    }
}

@Composable
private fun TodoFilterSheet(
    palette: TodoPalette,
    taskFilters: List<CategoryUi>,
    categories: List<CategoryUi>,
    onTaskFilterClick: (String) -> Unit,
    onCategoryClick: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
                color = palette.cardBg,
                shadowElevation = 12.dp,
            ) {
                Column(Modifier.padding(horizontal = 22.dp, vertical = 14.dp)) {
                    Box(
                        Modifier.align(Alignment.CenterHorizontally).width(42.dp).height(4.dp)
                            .clip(CircleShape).background(palette.muted.copy(alpha = .3f)),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Filter tasks", color = palette.ink, fontSize = 23.sp, fontWeight = FontWeight.Bold)
                            Text("Choose a view and category", color = palette.muted, fontSize = 13.sp)
                        }
                        TextButton(onClick = onReset) { Text("Reset", color = palette.lavender, fontWeight = FontWeight.Bold) }
                    }

                    Spacer(Modifier.height(20.dp))
                    Text("VIEW", color = palette.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        taskFilters.forEach { filter ->
                            CategoryChip(palette, filter.title, filter.selected) { onTaskFilterClick(filter.id) }
                        }
                    }

                    Spacer(Modifier.height(22.dp))
                    Text("CATEGORY", color = palette.muted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.foundation.layout.FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        categories.forEach { category ->
                            CategoryChip(palette, category.title, category.selected) { onCategoryClick(category.id) }
                        }
                    }

                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().padding(top = 26.dp).height(54.dp),
                        shape = RoundedCornerShape(17.dp),
                    ) { Text("Show tasks", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

// insight card
@Composable
private fun InsightCard(palette: TodoPalette, title: String, progress: Float) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = Color.Black.copy(alpha = 0.04f),
                spotColor = Color.Black.copy(alpha = 0.06f)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AvatarPlaceholder(palette = palette)
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        color = palette.ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "${(progress * 100).toInt()}%",
                        color = palette.ink,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(12.dp))
                ProgressTrack(palette = palette, progress = progress)
            }
        }
    }
}

@Composable
private fun CategoryChip(palette: TodoPalette, title: String, selected: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) palette.lavender else palette.pillBg,
        animationSpec = tween(200),
        label = "chipBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else palette.ink,
        animationSpec = tween(200),
        label = "chipText"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable(onClick = speaking(title, onClick))
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = title,
            color = textColor,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun SectionTitle(text: String, palette: TodoPalette) {
    Text(
        text = text,
        color = palette.ink,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.1).sp
    )
}
@Composable
fun TaskStatusCircle(palette: TodoPalette, completed: Boolean) {
    val bgColor by animateColorAsState(
        targetValue = if (completed) palette.lavender else Color.Transparent,
        animationSpec = tween(250),
        label = "statusBg"
    )

    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(bgColor)
            .then(
                if (!completed) {
                    Modifier.border(1.5.dp, palette.muted.copy(alpha = 0.4f), CircleShape)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = completed,
            enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy)) + fadeIn(),
            exit = fadeOut()
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Completed",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(
    palette: TodoPalette,
    isSearchResult: Boolean = false,
    filter: String = "today",
) {
    val emptyTitle = when (filter) {
        "upcoming" -> "Nothing upcoming"
        "overdue" -> "You’re all caught up"
        "high" -> "No high-priority tasks"
        "completed" -> "No completed tasks"
        else -> "No tasks for today"
    }
    val emptySubtitle = when (filter) {
        "upcoming" -> "Future tasks will appear here"
        "overdue" -> "No unfinished tasks are past due"
        "high" -> "Flag a task to prioritize it"
        "completed" -> "Completed tasks will appear here"
        else -> "Tap + to add your first task"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(palette.lavenderSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSearchResult) Icons.Outlined.Search else Icons.AutoMirrored.Rounded.EventNote,
                contentDescription = null,
                tint = palette.lavender,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (isSearchResult) "No tasks found" else emptyTitle,
            color = palette.ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isSearchResult) "Try a different search term" else emptySubtitle,
            color = palette.muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
