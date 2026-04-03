package com.muradgalayev.brainbuddy.ui.calendar

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
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
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.WeekDay
import com.kizitonwose.calendar.core.WeekDayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

enum class CalendarMode { Monthly, Weekly }

/* ── Theme-aware palette (consistent with Todo/Pomodoro) ── */
@Immutable
private data class CalendarPalette(
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
    val dialogBorder: Color,
)

private val LightCalendarPalette = CalendarPalette(
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
    dialogBorder = Color(0xFFF0EEF5),
)

private val DarkCalendarPalette = CalendarPalette(
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
    dialogBorder = Color(0xFF32353F),
)

@Composable
private fun rememberCalendarPalette(): CalendarPalette {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) DarkCalendarPalette else LightCalendarPalette
}

/* ── Main Screen ── */
@Composable
fun CalendarScreen(
    onBackClick: () -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val p = rememberCalendarPalette()

    var mode by rememberSaveable { mutableStateOf(CalendarMode.Monthly) }
    val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val startMonth = remember { YearMonth.now().minusMonths(12) }
    val endMonth = remember { YearMonth.now().plusMonths(12) }
    val startWeek = remember { LocalDate.now().minusWeeks(52) }
    val endWeek = remember { LocalDate.now().plusWeeks(52) }

    val monthState = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = uiState.visibleMonth,
        firstDayOfWeek = firstDayOfWeek
    )

    val weekState = rememberWeekCalendarState(
        startDate = startWeek,
        endDate = endWeek,
        firstVisibleWeekDate = uiState.selectedDate,
        firstDayOfWeek = firstDayOfWeek
    )

    // Sync visible month to ViewModel for dot indicators
    LaunchedEffect(monthState.firstVisibleMonth.yearMonth) {
        viewModel.updateVisibleMonth(monthState.firstVisibleMonth.yearMonth)
    }

    // Add task dialog
    if (uiState.showAddTaskDialog) {
        AddTaskDialog(
            palette = p,
            selectedDate = uiState.selectedDate,
            onDismiss = { viewModel.dismissAddTaskDialog() },
            onConfirm = { title, description, startTime, endTime, category ->
                viewModel.addTask(title, description, startTime, endTime, category)
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            // Landscape: side-by-side
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Left: calendar
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    CalendarHeader(mode = mode, onModeChange = { mode = it }, onBackClick = onBackClick)
                    Spacer(modifier = Modifier.height(12.dp))
                    MonthNavigator(
                        currentMonth = if (mode == CalendarMode.Monthly)
                            monthState.firstVisibleMonth.yearMonth
                        else YearMonth.from(uiState.selectedDate),
                        onPrevious = {
                            scope.launch {
                                if (mode == CalendarMode.Monthly)
                                    monthState.animateScrollToMonth(
                                        monthState.firstVisibleMonth.yearMonth.minusMonths(1)
                                    )
                                else {
                                    val newDate = uiState.selectedDate.minusWeeks(1)
                                    viewModel.selectDate(newDate)
                                    weekState.animateScrollToWeek(newDate)
                                }
                            }
                        },
                        onNext = {
                            scope.launch {
                                if (mode == CalendarMode.Monthly)
                                    monthState.animateScrollToMonth(
                                        monthState.firstVisibleMonth.yearMonth.plusMonths(1)
                                    )
                                else {
                                    val newDate = uiState.selectedDate.plusWeeks(1)
                                    viewModel.selectDate(newDate)
                                    weekState.animateScrollToWeek(newDate)
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DayOfWeekHeader(firstDayOfWeek)
                    Spacer(modifier = Modifier.height(4.dp))
                    CalendarBody(
                        mode = mode,
                        monthState = monthState,
                        weekState = weekState,
                        selectedDate = uiState.selectedDate,
                        datesWithTasks = uiState.datesWithTasks,
                        onDateSelect = { viewModel.selectDate(it) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Right: events
                EventsSection(
                    palette = p,
                    selectedDate = uiState.selectedDate,
                    tasks = uiState.tasksForSelectedDate,
                    onTaskClick = { viewModel.toggleTaskCompletion(it) },
                    onTaskDelete = { viewModel.deleteTask(it) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            // Portrait: stacked
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                CalendarHeader(mode = mode, onModeChange = { mode = it }, onBackClick = onBackClick)
                Spacer(modifier = Modifier.height(20.dp))
                MonthNavigator(
                    currentMonth = if (mode == CalendarMode.Monthly)
                        monthState.firstVisibleMonth.yearMonth
                    else YearMonth.from(uiState.selectedDate),
                    onPrevious = {
                        scope.launch {
                            if (mode == CalendarMode.Monthly)
                                monthState.animateScrollToMonth(
                                    monthState.firstVisibleMonth.yearMonth.minusMonths(1)
                                )
                            else {
                                val newDate = uiState.selectedDate.minusWeeks(1)
                                viewModel.selectDate(newDate)
                                weekState.animateScrollToWeek(newDate)
                            }
                        }
                    },
                    onNext = {
                        scope.launch {
                            if (mode == CalendarMode.Monthly)
                                monthState.animateScrollToMonth(
                                    monthState.firstVisibleMonth.yearMonth.plusMonths(1)
                                )
                            else {
                                val newDate = uiState.selectedDate.plusWeeks(1)
                                viewModel.selectDate(newDate)
                                weekState.animateScrollToWeek(newDate)
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                DayOfWeekHeader(firstDayOfWeek)
                Spacer(modifier = Modifier.height(8.dp))

                CalendarBody(
                    mode = mode,
                    monthState = monthState,
                    weekState = weekState,
                    selectedDate = uiState.selectedDate,
                    datesWithTasks = uiState.datesWithTasks,
                    onDateSelect = { viewModel.selectDate(it) },
                    modifier = Modifier.height(if (mode == CalendarMode.Monthly) 340.dp else 130.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                EventsSection(
                    palette = p,
                    selectedDate = uiState.selectedDate,
                    tasks = uiState.tasksForSelectedDate,
                    onTaskClick = { viewModel.toggleTaskCompletion(it) },
                    onTaskDelete = { viewModel.deleteTask(it) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { viewModel.showAddTaskDialog() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            shape = CircleShape,
            containerColor = p.lavender,
            contentColor = Color.White
        ) {
            Icon(Icons.Outlined.Add, "Add task", Modifier.size(28.dp))
        }
    }
}

/* ── Header: back button + title + mode toggle ── */
@Composable
private fun CalendarHeader(
    mode: CalendarMode,
    onModeChange: (CalendarMode) -> Unit,
    onBackClick: () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Calendar",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        ModePill(currentMode = mode, onModeChange = onModeChange)
    }
}

/* ── Calendar body (month or week) ── */
@Composable
private fun CalendarBody(
    mode: CalendarMode,
    monthState: com.kizitonwose.calendar.compose.CalendarState,
    weekState: com.kizitonwose.calendar.compose.weekcalendar.WeekCalendarState,
    selectedDate: LocalDate,
    datesWithTasks: Set<LocalDate>,
    onDateSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        when (mode) {
            CalendarMode.Monthly -> {
                HorizontalCalendar(
                    state = monthState,
                    dayContent = { day ->
                        MonthDay(
                            day = day,
                            isSelected = day.date == selectedDate,
                            isToday = day.date == LocalDate.now(),
                            hasEvents = day.date in datesWithTasks,
                            onClick = { onDateSelect(day.date) }
                        )
                    },
                    modifier = Modifier.padding(8.dp)
                )
            }
            CalendarMode.Weekly -> {
                WeekCalendar(
                    state = weekState,
                    dayContent = { day ->
                        WeekDayItem(
                            day = day,
                            isSelected = day.date == selectedDate,
                            isToday = day.date == LocalDate.now(),
                            hasEvents = day.date in datesWithTasks,
                            onClick = { onDateSelect(day.date) }
                        )
                    },
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

/* ── Events section ── */
@Composable
private fun EventsSection(
    palette: CalendarPalette,
    selectedDate: LocalDate,
    tasks: List<CalendarTaskUi>,
    onTaskClick: (String) -> Unit,
    onTaskDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = "Tasks",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = palette.ink,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Selected date header
            item {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = selectedDate.dayOfWeek.getDisplayName(
                                TextStyle.FULL, Locale.getDefault()
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.lavender
                        )
                        Text(
                            text = "${selectedDate.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${selectedDate.dayOfMonth}, ${selectedDate.year}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.muted
                        )
                    }
                }
            }

            if (tasks.isEmpty()) {
                item {
                    EmptyTasksState(palette = palette)
                }
            } else {
                items(tasks, key = { it.id }) { task ->
                    SwipeableCalendarTaskCard(
                        palette = palette,
                        task = task,
                        onClick = { onTaskClick(task.id) },
                        onDelete = { onTaskDelete(task.id) }
                    )
                }
            }
        }
    }
}

/* ── Empty tasks state ── */
@Composable
private fun EmptyTasksState(palette: CalendarPalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(palette.lavenderSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.EventNote,
                contentDescription = null,
                tint = palette.lavender,
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "No tasks for this day",
            color = palette.ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Tap + to add a task",
            color = palette.muted,
            fontSize = 13.sp
        )
    }
}

/* ── Swipeable task card ── */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCalendarTaskCard(
    palette: CalendarPalette,
    task: CalendarTaskUi,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onDelete()
            }
            false
        }
    )

    val backgroundColor by animateColorAsState(
        targetValue = when (dismissState.targetValue) {
            SwipeToDismissBoxValue.EndToStart -> palette.flagRed
            else -> palette.cardBg
        },
        label = "swipeBackground"
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(20.dp))
                    .background(backgroundColor)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                if (dismissState.targetValue == SwipeToDismissBoxValue.EndToStart) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete task",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) {
        CalendarTaskCard(palette = palette, task = task, onClick = onClick)
    }
}

/* ── Task card ── */
@Composable
private fun CalendarTaskCard(
    palette: CalendarPalette,
    task: CalendarTaskUi,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accent bar
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .background(task.accent)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Completion circle
                TaskStatusCircle(palette = palette, completed = task.completed)
                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        color = if (task.completed) palette.muted else palette.ink,
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!task.subtitle.isNullOrEmpty()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = task.subtitle,
                            color = palette.muted,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!task.timeRange.isNullOrEmpty()) {
                    Text(
                        text = task.timeRange,
                        color = palette.muted.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

/* ── Task Status Circle ── */
@Composable
private fun TaskStatusCircle(palette: CalendarPalette, completed: Boolean) {
    val bgColor by animateColorAsState(
        targetValue = if (completed) palette.lavender else Color.Transparent,
        animationSpec = tween(250),
        label = "statusBg"
    )

    Box(
        modifier = Modifier
            .size(28.dp)
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
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/* ── Mode toggle pill ── */
@Composable
private fun ModePill(
    currentMode: CalendarMode,
    onModeChange: (CalendarMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            CalendarMode.entries.forEach { mode ->
                val selected = mode == currentMode
                val bg by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent,
                    animationSpec = tween(200),
                    label = "modeBg"
                )
                val fg by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "modeFg"
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .clickable { onModeChange(mode) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = mode.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = fg
                    )
                }
            }
        }
    }
}

/* ── Month navigator ── */
@Composable
private fun MonthNavigator(
    currentMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = "Previous",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(12.dp))
        IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ── Day of week header ── */
@Composable
private fun DayOfWeekHeader(firstDayOfWeek: DayOfWeek) {
    val daysOfWeek = remember(firstDayOfWeek) {
        val days = DayOfWeek.entries.toMutableList()
        val index = days.indexOf(firstDayOfWeek)
        days.subList(index, days.size) + days.subList(0, index)
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        daysOfWeek.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/* ── Month day cell ── */
@Composable
private fun MonthDay(
    day: CalendarDay,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit
) {
    val inMonth = day.position == DayPosition.MonthDate

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isToday -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "dayBg"
    )

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        inMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    }

    Column(
        modifier = Modifier
            .height(48.dp)
            .fillMaxWidth()
            .padding(1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(bgColor)
                .clickable(enabled = inMonth) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                fontSize = 13.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        }
        if (hasEvents && inMonth) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.primary
                    )
            )
        }
    }
}

/* ── Week day cell ── */
@Composable
private fun WeekDayItem(
    day: WeekDay,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit
) {
    val inWeek = day.position == WeekDayPosition.RangeDate

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isToday -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "weekDayBg"
    )

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        inWeek -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .width(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(bgColor)
                .clickable(enabled = inWeek) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        }
        if (hasEvents && inWeek) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.primary
                    )
            )
        }
    }
}

/* ── Add Task Dialog ── */
@Composable
private fun AddTaskDialog(
    palette: CalendarPalette,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        description: String,
        startTime: String,
        endTime: String,
        category: String
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    var selectedCategory by remember { mutableStateOf("personal") }

    val formattedDate = "${selectedDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${selectedDate.dayOfMonth}, ${selectedDate.year}"

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
                Spacer(Modifier.height(4.dp))
                Text(
                    text = formattedDate,
                    color = palette.lavender,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(20.dp))

                CalendarDialogTextField(
                    palette = palette,
                    value = title,
                    onValueChange = { title = it },
                    label = "Task name",
                    placeholder = "e.g. Yoga practice",
                    imeAction = ImeAction.Next
                )
                Spacer(Modifier.height(14.dp))

                CalendarDialogTextField(
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
                        CalendarDialogTextField(
                            palette = palette,
                            value = startTime,
                            onValueChange = { startTime = it },
                            label = "Start",
                            placeholder = "09:00",
                            imeAction = ImeAction.Next
                        )
                    }
                    Box(Modifier.weight(1f)) {
                        CalendarDialogTextField(
                            palette = palette,
                            value = endTime,
                            onValueChange = { endTime = it },
                            label = "End",
                            placeholder = "10:00",
                            imeAction = ImeAction.Done,
                            onDone = {
                                focusManager.clearFocus()
                                if (title.isNotBlank()) {
                                    onConfirm(title, description, startTime, endTime, selectedCategory)
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Category",
                    color = palette.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        listOf("work", "education", "personal", "sport", "health")
                    ) { category ->
                        val selected = selectedCategory == category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) palette.lavender else palette.pillBg)
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = category.replaceFirstChar { it.uppercase() },
                                color = if (selected) Color.White else palette.ink,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
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
                                onConfirm(title, description, startTime, endTime, selectedCategory)
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

/* ── Dialog TextField ── */
@Composable
private fun CalendarDialogTextField(
    palette: CalendarPalette,
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
