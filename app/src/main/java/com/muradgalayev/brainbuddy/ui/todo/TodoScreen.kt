package com.muradgalayev.brainbuddy.ui.todo

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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.OutlinedFlag
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.material.icons.rounded.Edit

/* ── Theme-aware palette ── */
@Immutable
data class TodoPalette(
    val bg: Color,
    val ink: Color,
    val muted: Color,
    val cardBg: Color,
    val lavender: Color,
    val lavenderSoft: Color,
    val pillBg: Color,
    val flagRed: Color,
    val sky: Color,
    val lilac: Color,
    val lime: Color,
    val periwinkle: Color,
    val progressTrack: Color,
    val avatarBg: Color,
    val avatarFace: Color,
    val avatarEyes: Color,
    val searchCursor: Color,
    val dialogBorder: Color,
)

private val LightPalette = TodoPalette(
    bg = Color(0xFFF6F4F8),
    ink = Color(0xFF2C295B),
    muted = Color(0xFF8F8CA1),
    cardBg = Color(0xFFFFFFFF),
    lavender = Color(0xFF9A7CF3),
    lavenderSoft = Color(0xFFE7DFFF),
    pillBg = Color(0xFFF0EEF5),
    flagRed = Color(0xFFE53E3E),
    sky = Color(0xFF82C8FF),
    lilac = Color(0xFFD8A4FF),
    lime = Color(0xFFD0DB56),
    periwinkle = Color(0xFFB9C5FF),
    progressTrack = Color(0xFFEAE7F0),
    avatarBg = Color(0xFFC2B1FF),
    avatarFace = Color(0xFFD6B9FF),
    avatarEyes = Color(0xFFF4DDFF),
    searchCursor = Color(0xFF9A7CF3),
    dialogBorder = Color(0xFFF0EEF5),
)

private val DarkPalette = TodoPalette(
    bg = Color(0xFF0F1115),
    ink = Color(0xFFE4E5EA),
    muted = Color(0xFF9A9DA6),
    cardBg = Color(0xFF1E2128),
    lavender = Color(0xFFB49BFF),
    lavenderSoft = Color(0xFF2D2547),
    pillBg = Color(0xFF282B34),
    flagRed = Color(0xFFFC5555),
    sky = Color(0xFF5DADEB),
    lilac = Color(0xFFC48FEE),
    lime = Color(0xFFB8C244),
    periwinkle = Color(0xFF8E9DE0),
    progressTrack = Color(0xFF2A2D36),
    avatarBg = Color(0xFF3D3460),
    avatarFace = Color(0xFF5A4B8A),
    avatarEyes = Color(0xFF7B6BAA),
    searchCursor = Color(0xFFB49BFF),
    dialogBorder = Color(0xFF32353F),
)

@Composable
private fun rememberTodoPalette(): TodoPalette {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) DarkPalette else LightPalette
}

/* ── UI Models ── */
@Immutable
data class CategoryUi(
    val id: String,
    val title: String,
    val selected: Boolean = false,
)

@Immutable
data class TaskUi(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val trailingDate: String,
    val accent: Color,
    val completed: Boolean = false,
    val flagged: Boolean = false,
)

/* ── Main Screen ── */
@Composable
fun TodoScreen(viewModel: TodoViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val p = rememberTodoPalette()
    var taskToDelete by remember { mutableStateOf<String?>(null) }

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
            shape = CircleShape,
            containerColor = p.lavender,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
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
            onConfirm = { title, description, startTime, endTime ->
                viewModel.addTask(title, description, startTime, endTime)
            }
        )
    }

    uiState.editingTask?.let { task ->
        EditTaskDialog(
            palette = p,
            task = task,
            onDismiss = viewModel::dismissEditTask,
            onSave = { id, title, desc, start, end, priority, color ->
                viewModel.updateTask(id, title, desc, start, end, priority, color)
            },
            onDelete = { viewModel.deleteTask(it) }
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
                    fadeIn(tween(200)) togetherWith fadeOut(tween(200))
                },
                label = "headerSwitch"
            ) { searching ->
                if (searching) {
                    SearchBar(
                        palette = p,
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClose = onSearchClick
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

/* ── Search Bar ── */
@Composable
private fun SearchBar(
    palette: TodoPalette,
    query: String,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.cardBg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = null,
            tint = palette.muted,
            modifier = Modifier
                .padding(start = 16.dp)
                .size(22.dp)
        )
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    "Search tasks...",
                    color = palette.muted.copy(alpha = 0.6f),
                    fontSize = 15.sp
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = palette.searchCursor,
                focusedTextColor = palette.ink,
                unfocusedTextColor = palette.ink
            ),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = palette.ink,
                fontSize = 15.sp
            ),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { })
        )

        AnimatedVisibility(
            visible = query.isNotEmpty(),
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally()
        ) {
            IconButton(onClick = { onQueryChange("") }) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Clear",
                    tint = palette.muted,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        IconButton(onClick = onClose) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Close search",
                tint = palette.ink,
                modifier = Modifier.size(20.dp)
            )
        }
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

/* ── Avatar Placeholder (for later real photo) ── */
@Composable
private fun AvatarPlaceholder(palette: TodoPalette, size: Dp = 56.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(palette.avatarBg, palette.avatarBg.copy(alpha = 0.7f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.72f)) {
            drawCircle(color = palette.avatarFace, radius = this.size.minDimension * 0.42f)
            drawCircle(
                color = palette.avatarEyes,
                radius = this.size.minDimension * 0.20f,
                center = Offset(this.size.width * 0.38f, this.size.height * 0.52f)
            )
            drawCircle(
                color = palette.avatarEyes,
                radius = this.size.minDimension * 0.20f,
                center = Offset(this.size.width * 0.62f, this.size.height * 0.52f)
            )
            val pupilColor = if (palette.bg.luminance() < 0.5f) Color(0xFFE4E5EA) else Color(0xFF2C295B)
            drawCircle(
                color = pupilColor,
                radius = this.size.minDimension * 0.06f,
                center = Offset(this.size.width * 0.38f, this.size.height * 0.54f)
            )
            drawCircle(
                color = pupilColor,
                radius = this.size.minDimension * 0.06f,
                center = Offset(this.size.width * 0.62f, this.size.height * 0.54f)
            )
            drawLine(
                color = Color(0xFFFFC94D),
                start = Offset(this.size.width * 0.32f, this.size.height * 0.18f),
                end = Offset(this.size.width * 0.18f, this.size.height * 0.04f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFFFC94D),
                start = Offset(this.size.width * 0.68f, this.size.height * 0.18f),
                end = Offset(this.size.width * 0.82f, this.size.height * 0.04f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color(0xFFFFC94D),
                radius = this.size.minDimension * 0.045f,
                center = Offset(this.size.width * 0.17f, this.size.height * 0.03f)
            )
            drawCircle(
                color = Color(0xFFFFC94D),
                radius = this.size.minDimension * 0.045f,
                center = Offset(this.size.width * 0.83f, this.size.height * 0.03f)
            )
        }
    }
}

/* ── Progress Track ── */
@Composable
private fun ProgressTrack(palette: TodoPalette, progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "progress"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(palette.progressTrack)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .height(10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF8C70EC), palette.lavender)
                    )
                )
        )
    }
}

/* ── Category Chip ── */
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

/* ── Section Title ── */
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskCard(
    palette: TodoPalette,
    task: TaskUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFlagClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    onEdit()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onDelete()
                    false
                }
                SwipeToDismissBoxValue.Settled -> false
            }
        }
    )

    val backgroundColor by animateColorAsState(
        targetValue = when (dismissState.targetValue) {
            SwipeToDismissBoxValue.StartToEnd -> palette.lavender
            SwipeToDismissBoxValue.EndToStart -> palette.flagRed
            SwipeToDismissBoxValue.Settled -> palette.cardBg
        },
        label = "swipeBackground"
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.StartToEnd -> {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "Edit task",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Box(modifier = Modifier.size(24.dp))
                    }

                    SwipeToDismissBoxValue.EndToStart -> {
                        Box(modifier = Modifier.size(24.dp))
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete task",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    SwipeToDismissBoxValue.Settled -> {
                        Box(modifier = Modifier.size(24.dp))
                        Box(modifier = Modifier.size(24.dp))
                    }
                }
            }
        }
    ) {
        TaskCard(
            palette = palette,
            task = task,
            onClick = onClick,
            onLongClick = onLongClick,
            onFlagClick = onFlagClick
        )
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TaskCard(
    palette: TodoPalette,
    task: TaskUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFlagClick: () -> Unit
) {
    val flagColor by animateColorAsState(
        targetValue = if (task.flagged) palette.flagRed else palette.muted.copy(alpha = 0.45f),
        animationSpec = tween(250),
        label = "flagColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(80.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .background(task.accent)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TaskStatusCircle(palette = palette, completed = task.completed)
                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        color = if (task.completed) palette.muted else palette.ink,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!task.subtitle.isNullOrEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = task.subtitle,
                            color = palette.muted,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(4.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onFlagClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (task.flagged) Icons.Outlined.Flag else Icons.Outlined.OutlinedFlag,
                            contentDescription = if (task.flagged) "Remove flag" else "Flag as important",
                            tint = flagColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = task.trailingDate,
                        color = palette.muted.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/* ── Task Status Circle ── */
@Composable
private fun TaskStatusCircle(palette: TodoPalette, completed: Boolean) {
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

/* ── Empty State ── */
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

/* ── Add Task Dialog ── */
@Composable
private fun AddTaskDialog(
    palette: TodoPalette,
    onDismiss: () -> Unit,
    onConfirm: (title: String, description: String, startTime: String, endTime: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = palette.cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "New Task",
                    color = palette.ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(20.dp))

                DialogTextField(
                    palette = palette,
                    value = title,
                    onValueChange = { title = it },
                    label = "Task name",
                    placeholder = "e.g. Yoga practice",
                    imeAction = ImeAction.Next
                )
                Spacer(Modifier.height(14.dp))

                DialogTextField(
                    palette = palette,
                    value = description,
                    onValueChange = { description = it },
                    label = "Description (optional)",
                    placeholder = "e.g. Morning stretch routine",
                    imeAction = ImeAction.Next
                )
                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        DialogTextField(
                            palette = palette,
                            value = startTime,
                            onValueChange = { startTime = it },
                            label = "Start",
                            placeholder = "09:00",
                            imeAction = ImeAction.Next
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        DialogTextField(
                            palette = palette,
                            value = endTime,
                            onValueChange = { endTime = it },
                            label = "End",
                            placeholder = "10:00",
                            imeAction = ImeAction.Done,
                            onDone = {
                                focusManager.clearFocus()
                                if (title.isNotBlank()) {
                                    onConfirm(title, description, startTime, endTime)
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = palette.muted, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (title.isNotBlank()) {
                                onConfirm(title, description, startTime, endTime)
                            }
                        }
                    ) {
                        Text("Add Task", color = palette.lavender, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/* ── Edit Task Dialog ── */
@Composable
private fun EditTaskDialog(
    palette: TodoPalette,
    task: TaskEditData,
    onDismiss: () -> Unit,
    onSave: (id: String, title: String, description: String, startTime: String, endTime: String, priority: String, color: String) -> Unit,
    onDelete: (id: String) -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description) }
    var startTime by remember { mutableStateOf(task.startTime) }
    var endTime by remember { mutableStateOf(task.endTime) }
    val focusManager = LocalFocusManager.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = palette.cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Task",
                        color = palette.ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { onDelete(task.id) }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete task",
                            tint = palette.flagRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                DialogTextField(
                    palette = palette,
                    value = title,
                    onValueChange = { title = it },
                    label = "Task name",
                    placeholder = "e.g. Yoga practice",
                    imeAction = ImeAction.Next
                )
                Spacer(Modifier.height(14.dp))

                DialogTextField(
                    palette = palette,
                    value = description,
                    onValueChange = { description = it },
                    label = "Description (optional)",
                    placeholder = "e.g. Morning stretch routine",
                    imeAction = ImeAction.Next
                )
                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        DialogTextField(
                            palette = palette,
                            value = startTime,
                            onValueChange = { startTime = it },
                            label = "Start",
                            placeholder = "09:00",
                            imeAction = ImeAction.Next
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        DialogTextField(
                            palette = palette,
                            value = endTime,
                            onValueChange = { endTime = it },
                            label = "End",
                            placeholder = "10:00",
                            imeAction = ImeAction.Done,
                            onDone = {
                                focusManager.clearFocus()
                                if (title.isNotBlank()) {
                                    onSave(task.id, title, description, startTime, endTime, task.priority, task.color)
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = palette.muted, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.width(8.dp))
                    TextButton(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSave(task.id, title, description, startTime, endTime, task.priority, task.color)
                            }
                        }
                    ) {
                        Text("Save", color = palette.lavender, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/* ── Shared Dialog TextField ── */
@Composable
private fun DialogTextField(
    palette: TodoPalette,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    imeAction: ImeAction = ImeAction.Default,
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        placeholder = { Text(placeholder, color = palette.muted.copy(alpha = 0.5f), fontSize = 14.sp) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.lavender,
            unfocusedBorderColor = palette.dialogBorder,
            focusedLabelColor = palette.lavender,
            unfocusedLabelColor = palette.muted,
            cursorColor = palette.lavender,
            focusedTextColor = palette.ink,
            unfocusedTextColor = palette.ink,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() })
    )
}

/* ── Previews ── */
@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Light")
@Composable
private fun TodoListLightPreview() {
    var selectedCategory by remember { mutableStateOf("all") }

    val categories = listOf("All", "Work", "Education", "Personal", "Sport", "Health").map {
        CategoryUi(id = it.lowercase(), title = it, selected = selectedCategory == it.lowercase())
    }
    val todayTasks = listOf(
        TaskUi("1", "Yoga practice", "9:00 \u2013 10:30", "10.01", LightPalette.sky),
        TaskUi("2", "English lesson", "12:00 \u2013 13:00", "10.01", LightPalette.lilac, flagged = true),
        TaskUi("3", "Client call", "Discuss design", "10.01", LightPalette.lime),
        TaskUi("4", "Sport walk", "20:00 \u2013 21:00", "10.01", LightPalette.sky),
    )
    val doneTasks = listOf(
        TaskUi("5", "Order cake", "Caramel with cherries", "10.01", LightPalette.periwinkle, completed = true),
        TaskUi("6", "Walk the dog", "Go to the park", "10.01", LightPalette.sky, completed = true),
    )

    MaterialTheme {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                TodoListContent(
                    palette = LightPalette,
                    categories = categories,
                    todayTasks = todayTasks,
                    completedTasks = doneTasks,
                    onCategoryClick = { selectedCategory = it }
                )
                FloatingActionButton(
                    onClick = {},
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                    shape = CircleShape,
                    containerColor = LightPalette.lavender,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Outlined.Add, "Add task", Modifier.size(28.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Dark",
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun TodoListDarkPreview() {
    var selectedCategory by remember { mutableStateOf("all") }

    val categories = listOf("All", "Work", "Education", "Personal", "Sport", "Health").map {
        CategoryUi(id = it.lowercase(), title = it, selected = selectedCategory == it.lowercase())
    }
    val todayTasks = listOf(
        TaskUi("1", "Yoga practice", "9:00 \u2013 10:30", "10.01", DarkPalette.sky),
        TaskUi("2", "English lesson", "12:00 \u2013 13:00", "10.01", DarkPalette.lilac, flagged = true),
        TaskUi("3", "Client call", "Discuss design", "10.01", DarkPalette.lime),
        TaskUi("4", "Sport walk", "20:00 \u2013 21:00", "10.01", DarkPalette.sky),
    )
    val doneTasks = listOf(
        TaskUi("5", "Order cake", "Caramel with cherries", "10.01", DarkPalette.periwinkle, completed = true),
        TaskUi("6", "Walk the dog", "Go to the park", "10.01", DarkPalette.sky, completed = true),
    )

    MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme()) {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                TodoListContent(
                    palette = DarkPalette,
                    categories = categories,
                    todayTasks = todayTasks,
                    completedTasks = doneTasks,
                    onCategoryClick = { selectedCategory = it }
                )
                FloatingActionButton(
                    onClick = {},
                    modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
                    shape = CircleShape,
                    containerColor = DarkPalette.lavender,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Outlined.Add, "Add task", Modifier.size(28.dp))
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844, name = "Empty")
@Composable
private fun TodoEmptyPreview() {
    MaterialTheme {
        Surface {
            Box(modifier = Modifier.fillMaxSize()) {
                TodoListContent(
                    palette = LightPalette,
                    dateTitle = "April 02",
                    categories = listOf(
                        CategoryUi("all", "All", selected = true),
                        CategoryUi("work", "Work"),
                        CategoryUi("personal", "Personal"),
                    ),
                    todayTasks = emptyList(),
                    completedTasks = emptyList()
                )
            }
        }
    }
}
