package com.muradgalayev.brainbuddy.ui.todo
import com.muradgalayev.brainbuddy.ui.searchbar.AppSearchBar
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.ui.sharedcomponents.SuccessPopup
@Composable
private fun rememberTodoPalette(): TodoPalette {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) DarkPalette else LightPalette
}

@Composable
fun TodoScreen(viewModel: TodoViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val p = rememberTodoPalette()
    var taskToDelete by remember { mutableStateOf<String?>(null) }
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
            todayTasks = uiState.todayTasks,
            completedTasks = uiState.completedTasks,
            isLoading = uiState.isLoading,
            isSearchActive = uiState.isSearchActive,
            searchQuery = uiState.searchQuery,
            onCategoryClick = viewModel::onCategoryClick,
            onTaskClick = viewModel::onTaskClick,
            onTaskLongClick = viewModel::openEditTask,
            onFlagClick = viewModel::toggleFlag,
            onSearchClick = viewModel::toggleSearch,
            onSearchQueryChange = viewModel::onSearchQueryChange,
            onDeleteTask = { taskToDelete = it },
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

    if (uiState.showAddTaskDialog) {
        AddTaskDialog(
            palette = p,
            onDismiss = viewModel::dismissAddTaskDialog,
            onConfirm = { title, description, startTime, endTime, category,color ->
                viewModel.addTask(title, description, startTime, endTime, category, color)
            }
        )
    }

    uiState.editingTask?.let { task ->
        EditTaskDialog(
            palette = p,
            task = task,
            onDismiss = viewModel::dismissEditTask,
            onSave = { id, title, desc, start, end, priority, color, category ->
                viewModel.updateTask(id, title, desc, start, end, priority, color, category)
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

/* ── List Content ── */
@Composable
fun TodoListContent(
    modifier: Modifier = Modifier,
    palette: TodoPalette = LightPalette,
    dateTitle: String = "January 10",
    insightTitle: String = "Today's task insights",
    progress: Float = 0.2f,
    categories: List<CategoryUi> = emptyList(),
    todayTasks: List<TaskUi> = emptyList(),
    completedTasks: List<TaskUi> = emptyList(),
    isLoading: Boolean = false,
    isSearchActive: Boolean = false,
    searchQuery: String = "",
    onCategoryClick: (String) -> Unit = {},
    onTaskClick: (String) -> Unit = {},
    onTaskLongClick: (String) -> Unit = {},
    onFlagClick: (String) -> Unit = {},
    onSearchClick: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onDeleteTask: (String) -> Unit = {},
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

        // Date header + search
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
                        Text(
                            text = dateTitle,
                            color = p.ink,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp
                        )
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

        // Insight card
        item {
            InsightCard(palette = p, title = insightTitle, progress = progress)
        }

        // Category chips
        if (categories.isNotEmpty()) {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(categories, key = { it.id }) { category ->
                        CategoryChip(
                            palette = p,
                            title = category.title,
                            selected = category.selected,
                            onClick = { onCategoryClick(category.id) }
                        )
                    }
                }
            }
        }

        // Today section
        item {
            SectionTitle(text = "Today", palette = p)
        }

        if (todayTasks.isEmpty() && completedTasks.isEmpty() && !isLoading) {
            item {
                EmptyState(
                    palette = p,
                    isSearchResult = isSearchActive && searchQuery.isNotEmpty()
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

        // Completed section
        if (completedTasks.isNotEmpty()) {
            item {
                Spacer(Modifier.height(4.dp))
                SectionTitle(text = "Completed tasks", palette = p)
            }

            itemsIndexed(completedTasks, key = { _, task -> task.id }) { index, task ->
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



/* ── Insight Card ── */
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
            .clickable(onClick = onClick)
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
private fun EmptyState(palette: TodoPalette, isSearchResult: Boolean = false) {
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
            text = if (isSearchResult) "No tasks found" else "No tasks for today",
            color = palette.ink,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (isSearchResult) "Try a different search term" else "Tap + to add your first task",
            color = palette.muted,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal
        )
    }
}
