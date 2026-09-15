package com.muradgalayev.brainbuddy.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import com.muradgalayev.brainbuddy.ui.accessibility.speaking
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// overdue once the end time has passed and it isn't finished. finished covers both routes,
// ticked off directly or every station done. nagging about work someone already did is the
// fastest way to teach them to ignore the overdue section altogether
fun CalendarTaskUi.isOverdue(nowMillis: Long): Boolean {
    val end = endMillis ?: return false
    if (end >= nowMillis) return false
    // only unfinished work can be late
    return !isDone
}

// ticks once a minute so an event crossing its end time moves into the overdue group while
// the user is looking at it. otherwise the list only re-evaluates when the data changes
@Composable
private fun rememberNowMillis(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

// the day's events in the three groups the list renders: slipped past, still live, already
// done. finished work sinks to the bottom, a ticked-off event wedged between two live ones
// is noise and the point of ticking it off is to stop having to look at it
private data class DayGroups(
    val overdue: List<CalendarTaskUi>,
    val active: List<CalendarTaskUi>,
    val done: List<CalendarTaskUi>,
)

private fun List<CalendarTaskUi>.groupForDay(nowMillis: Long): DayGroups {
    val (done, open) = partition { it.isDone }
    val (overdue, active) = open.partition { it.isOverdue(nowMillis) }
    return DayGroups(overdue = overdue, active = active, done = done)
}

// uses the flag colour, not the event accent
@Composable
private fun OverdueSectionHeader(palette: CalendarPalette, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = palette.flagRed,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = if (count == 1) stringResource(R.string.cal_slipped_one) else stringResource(R.string.cal_slipped_many, count),
            color = palette.flagRed,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

// only shown when both groups exist
@Composable
private fun StillToComeHeader(palette: CalendarPalette) {
    Text(
        text = stringResource(R.string.cal_still_to_come),
        color = palette.muted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
    )
}

// header above the finished group at the bottom of the day
@Composable
private fun DoneSectionHeader(palette: CalendarPalette, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Check,
            contentDescription = null,
            tint = palette.muted.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = if (count == 1) stringResource(R.string.cal_one_done) else stringResource(R.string.cal_n_done, count),
            color = palette.muted.copy(alpha = 0.85f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// the day's task list: overdue, then live, then done. shared by the portrait sheet and the
// landscape column so the two layouts can't drift into different orderings
private fun LazyListScope.calendarTaskItems(
    palette: CalendarPalette,
    tasks: List<CalendarTaskUi>,
    nowMillis: Long,
    expandedEventId: String?,
    onTaskClick: (String) -> Unit,
    onTaskDelete: (String) -> Unit,
    onToggleCompleted: (String) -> Unit,
    onToggleExpanded: (String) -> Unit,
    onToggleSubtask: (String, Boolean) -> Unit,
    onManageSubtasks: (String) -> Unit,
    onRunInPomodoro: (String) -> Unit,
) {
    if (tasks.isEmpty()) {
        item { EmptyTasksState(palette = palette) }
        return
    }

    val groups = tasks.groupForDay(nowMillis)
    val handlers = CalendarTaskHandlers(
        expandedEventId = expandedEventId,
        onTaskClick = onTaskClick,
        onTaskDelete = onTaskDelete,
        onToggleCompleted = onToggleCompleted,
        onToggleExpanded = onToggleExpanded,
        onToggleSubtask = onToggleSubtask,
        onManageSubtasks = onManageSubtasks,
        onRunInPomodoro = onRunInPomodoro,
    )

    // overdue first. burying what slipped past under what's still coming is how it gets
    // forgotten, and forgetting is the thing this app exists to help with
    if (groups.overdue.isNotEmpty()) {
        item(key = "overdue_header") {
            OverdueSectionHeader(palette = palette, count = groups.overdue.size)
        }
        taskCards(palette, groups.overdue, isOverdue = true, dimmed = false, handlers = handlers)
        if (groups.active.isNotEmpty()) {
            item(key = "upcoming_header") { StillToComeHeader(palette) }
        }
    }

    taskCards(palette, groups.active, isOverdue = false, dimmed = false, handlers = handlers)

    // finished work last and faded: still reachable to un-tick, out of the way otherwise
    if (groups.done.isNotEmpty()) {
        item(key = "done_header") {
            DoneSectionHeader(palette = palette, count = groups.done.size)
        }
        taskCards(palette, groups.done, isOverdue = false, dimmed = true, handlers = handlers)
    }
}

// bundled so the group helper isn't a wall of parameters
private class CalendarTaskHandlers(
    val expandedEventId: String?,
    val onTaskClick: (String) -> Unit,
    val onTaskDelete: (String) -> Unit,
    val onToggleCompleted: (String) -> Unit,
    val onToggleExpanded: (String) -> Unit,
    val onToggleSubtask: (String, Boolean) -> Unit,
    val onManageSubtasks: (String) -> Unit,
    val onRunInPomodoro: (String) -> Unit,
)

// one group of cards with a shared overdue/dimmed treatment
private fun LazyListScope.taskCards(
    palette: CalendarPalette,
    group: List<CalendarTaskUi>,
    isOverdue: Boolean,
    dimmed: Boolean,
    handlers: CalendarTaskHandlers,
) {
    items(group, key = { it.id }) { task ->
        SwipeableCalendarTaskCard(
            modifier = Modifier
                .animateItem()
                .then(if (dimmed) Modifier.alpha(0.55f) else Modifier),
            palette = palette,
            task = task,
            expanded = handlers.expandedEventId == task.id,
            isOverdue = isOverdue,
            onClick = { handlers.onTaskClick(task.id) },
            onDelete = { handlers.onTaskDelete(task.id) },
            onToggleCompleted = { handlers.onToggleCompleted(task.id) },
            onToggleExpanded = { handlers.onToggleExpanded(task.id) },
            onToggleSubtask = handlers.onToggleSubtask,
            onManageSubtasks = { handlers.onManageSubtasks(task.id) },
            onRunInPomodoro = { handlers.onRunInPomodoro(task.id) },
        )
    }
}

// bottom sheet content (portrait)
@Composable
fun EventsBottomSheet(
    palette: CalendarPalette,
    selectedDate: LocalDate,
    tasks: List<CalendarTaskUi>,
    expandedEventId: String?,
    onTaskClick: (String) -> Unit,
    onTaskDelete: (String) -> Unit,
    onToggleCompleted: (String) -> Unit,
    onAddTask: () -> Unit,
    onToggleExpanded: (String) -> Unit,
    onToggleSubtask: (String, Boolean) -> Unit,
    onManageSubtasks: (String) -> Unit,
    onRunInPomodoro: (String) -> Unit,
) {
    val nowMillis = rememberNowMillis()
    Column(modifier = Modifier.fillMaxWidth()) {
        // drag handle
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(palette.muted.copy(alpha = 0.3f))
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // dark date header
        DateHeader(palette = palette, selectedDate = selectedDate)

        Spacer(modifier = Modifier.height(4.dp))

        // task list
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                calendarTaskItems(
                    palette = palette,
                    tasks = tasks,
                    nowMillis = nowMillis,
                    expandedEventId = expandedEventId,
                    onTaskClick = onTaskClick,
                    onTaskDelete = onTaskDelete,
                    onToggleCompleted = onToggleCompleted,
                    onToggleExpanded = onToggleExpanded,
                    onToggleSubtask = onToggleSubtask,
                    onManageSubtasks = onManageSubtasks,
                    onRunInPomodoro = onRunInPomodoro,
                )

                // bottom spacing for the FAB
                item { Spacer(modifier = Modifier.height(88.dp)) }
            }

            // the add button used to live here, aligned to the bottom of the sheet's content. once the
            // list grew past the peek height that bottom sat below the fold, so the button vanished at
            // exactly the point you had enough tasks to want another one. screen-level overlay now,
            // see CalendarScreen
        }
    }
}

// dark date header
@Composable
fun DateHeader(
    palette: CalendarPalette,
    selectedDate: LocalDate,
) {
    val dayOfWeek = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val dayNum = selectedDate.dayOfMonth
    val month = selectedDate.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
    val year = selectedDate.year

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(palette.dateHeaderBg)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // red dot indicator
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(palette.flagRed)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "$dayOfWeek, $dayNum $month $year",
                color = palette.dateHeaderText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp
            )
        }
    }
}

// events section (landscape fallback)
@Composable
fun EventsSection(
    palette: CalendarPalette,
    selectedDate: LocalDate,
    tasks: List<CalendarTaskUi>,
    expandedEventId: String?,
    onTaskClick: (String) -> Unit,
    onTaskDelete: (String) -> Unit,
    onToggleCompleted: (String) -> Unit,
    onToggleExpanded: (String) -> Unit,
    onToggleSubtask: (String, Boolean) -> Unit,
    onManageSubtasks: (String) -> Unit,
    onRunInPomodoro: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val nowMillis = rememberNowMillis()
    Column(modifier = modifier) {
        DateHeader(palette = palette, selectedDate = selectedDate)

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // same overdue-first / done-last grouping as the portrait sheet
            calendarTaskItems(
                palette = palette,
                tasks = tasks,
                nowMillis = nowMillis,
                expandedEventId = expandedEventId,
                onTaskClick = onTaskClick,
                onTaskDelete = onTaskDelete,
                onToggleCompleted = onToggleCompleted,
                onToggleExpanded = onToggleExpanded,
                onToggleSubtask = onToggleSubtask,
                onManageSubtasks = onManageSubtasks,
                onRunInPomodoro = onRunInPomodoro,
            )
        }
    }
}

// empty tasks state
@Composable
fun EmptyTasksState(palette: CalendarPalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
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
            text = stringResource(R.string.cal_no_tasks_day),
            color = palette.ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.cal_tap_plus),
            color = palette.muted,
            fontSize = 13.sp
        )
    }
}

// swipeable task card
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCalendarTaskCard(
    modifier: Modifier = Modifier,
    palette: CalendarPalette,
    task: CalendarTaskUi,
    expanded: Boolean,
    isOverdue: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleCompleted: () -> Unit,
    onToggleExpanded: () -> Unit,
    onToggleSubtask: (String, Boolean) -> Unit,
    onManageSubtasks: () -> Unit,
    onRunInPomodoro: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> {
                    showDeleteConfirm = true
                    false
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    onClick()
                    false
                }
                else -> false
            }
        }
    )

    // snap the row back to centre after edit or delete
    LaunchedEffect(showDeleteConfirm) {
        if (!showDeleteConfirm && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    val direction = dismissState.dismissDirection
    val showDeleteBackground = direction == SwipeToDismissBoxValue.EndToStart
    val showEditBackground = direction == SwipeToDismissBoxValue.StartToEnd

    val backgroundColor by animateColorAsState(
        targetValue = when {
            showDeleteBackground -> palette.flagRed
            showEditBackground -> palette.lavender
            else -> Color.Transparent
        },
        label = "swipeBackground"
    )

    SwipeToDismissBox(
        modifier = modifier,
        state = dismissState,
        backgroundContent = {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(backgroundColor)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = when {
                    showEditBackground -> Arrangement.Start
                    else -> Arrangement.End
                }
            ) {
                AnimatedVisibility(visible = showEditBackground) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.cal_edit_event),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                AnimatedVisibility(visible = showDeleteBackground) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.cal_delete_event),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    ) {
        CalendarTaskCard(
            palette = palette,
            task = task,
            expanded = expanded,
            isOverdue = isOverdue,
            onClick = onClick,
            onToggleCompleted = onToggleCompleted,
            onToggleExpanded = onToggleExpanded,
            onToggleSubtask = onToggleSubtask,
            onManageSubtasks = onManageSubtasks,
            onRunInPomodoro = onRunInPomodoro,
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.cardBg,
            title = {
                Text(
                    text = stringResource(R.string.cal_delete_event_q),
                    color = palette.ink,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.cal_delete_confirm, task.title),
                    color = palette.muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text(
                        stringResource(R.string.common_delete),
                        color = palette.flagRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(
                        stringResource(R.string.common_cancel),
                        color = palette.muted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }
}

// task card
@Composable
fun CalendarTaskCard(
    palette: CalendarPalette,
    task: CalendarTaskUi,
    expanded: Boolean,
    isOverdue: Boolean = false,
    onClick: () -> Unit,
    onToggleCompleted: () -> Unit,
    onToggleExpanded: () -> Unit,
    onToggleSubtask: (String, Boolean) -> Unit,
    onManageSubtasks: () -> Unit,
    onRunInPomodoro: () -> Unit,
) {
    val context = LocalContext.current
    val tintedBg = androidx.compose.ui.graphics.lerp(palette.cardBg, task.accent, 0.07f)
    val canExpand = task.subtasks.isNotEmpty() || task.totalMinutes > 0
    val showCompactBreakdown = !task.isDone &&
        !task.isMedication &&
        !task.isReservation &&
        task.subtasks.isEmpty() &&
        canExpand
    val hasDetails = !task.subtitle.isNullOrEmpty() ||
        !task.location.isNullOrEmpty() ||
        !task.link.isNullOrEmpty()

    val totalSubtaskMinutes = task.subtasks.sumOf { it.durationMinutes }
    val completedSubtaskMinutes = task.subtasks
        .filter { it.completed }
        .sumOf { it.durationMinutes }
    val subtaskProgress = if (totalSubtaskMinutes > 0)
        completedSubtaskMinutes.toFloat() / totalSubtaskMinutes.toFloat()
    else 0f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = speaking(task.title, onClick)
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tintedBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // accent stripe
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(task.accent)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // plain status dot, and the expand button. the breakdown used to hide behind a 34dp ring
                    // here and people simply didn't see it: a small circle among other small circles doesn't
                    // read as something you can press. it's a labelled full-width row at the card's foot now
                    TaskStatusCircle(
                        palette = palette,
                        completed = task.isDone,
                        onClick = onToggleCompleted,
                    )

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        // the description used to render here as an uppercase category pill, which read like a label
                        // the user had chosen rather than the note they wrote. plain text in the dropdown now

                        // task title
                        Text(
                            text = task.title,
                            color = if (task.isDone) palette.muted else palette.ink,
                            fontSize = 15.sp,
                            lineHeight = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (task.isDone) TextDecoration.LineThrough else TextDecoration.None,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // attribution when someone else put this here. under the title rather than in the dropdown:
                        // 'who put this in my day' is the first question they'll have, it shouldn't need a tap
                        task.addedByName?.let { author ->
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(task.accent.copy(alpha = 0.16f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Diversity3,
                                            contentDescription = null,
                                            tint = task.accent,
                                            modifier = Modifier.size(10.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.cal_added_by, author),
                                            color = task.accent,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.3.sp,
                                        )
                                    }
                                }
                            }
                        }

                        // location and link used to stack here under the title, which made every card a different
                        // height and buried the time. they live in the details dropdown now
                    }

                    // time range, with an overdue marker under it so the card says why it's in the overdue
                    // group without the user scrolling back to the section header
                    if (!task.timeRange.isNullOrEmpty()) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = task.timeRange,
                                color = if (isOverdue) palette.flagRed
                                else palette.muted.copy(alpha = 0.8f),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                            if (isOverdue) {
                                Spacer(Modifier.height(3.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(palette.flagRed.copy(alpha = 0.15f))
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.cal_overdue),
                                        color = palette.flagRed,
                                        fontSize = 8.5.sp,
                                        fontWeight = FontWeight.Black,
                                        letterSpacing = 0.5.sp,
                                    )
                                }
                            }
                        }
                    }

                    // flag indicator
                    if (task.flagged) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(palette.flagRed)
                        )
                    }

                    if (showCompactBreakdown) {
                        Spacer(Modifier.width(4.dp))
                        CompactBreakdownButton(
                            accent = task.accent,
                            onClick = onManageSubtasks,
                        )
                    }

                    // only rendered when there's something behind it, a chevron that opens an empty drawer is
                    // worse than no chevron
                    if (hasDetails) {
                        Spacer(Modifier.width(4.dp))
                        DetailsChevron(
                            palette = palette,
                            expanded = expanded,
                            onClick = onToggleExpanded,
                        )
                    }
                }

                AnimatedVisibility(
                    visible = expanded && hasDetails,
                    // expandFrom/shrinkTowards = Top. the default is Bottom, which reveals the panel from its
                    // lower edge upward and made the details look like they popped up over the card instead of
                    // opening beneath the title
                    enter = expandVertically(
                        expandFrom = Alignment.Top,
                        animationSpec = tween(220),
                    ) + fadeIn(tween(180, delayMillis = 60)),
                    exit = shrinkVertically(
                        shrinkTowards = Alignment.Top,
                        animationSpec = tween(180),
                    ) + fadeOut(tween(100)),
                ) {
                    TaskDetailsBlock(
                        palette = palette,
                        description = task.subtitle,
                        location = task.location,
                        link = task.link,
                        onOpenLink = { task.link?.let { openUrl(context, it) } },
                    )
                }

                // breakdown lives on its own screen now. the card carries a status line, enough to know a
                // plan exists and how far along it is, without the full station rail pushing every other
                // task off the list.
                // a finished task shows none of it: offering to break down work that's already done is the
                // app failing to notice you did it. un-ticking brings it straight back, nothing is deleted.
                // medication doses and medical bookings are never broken into steps either, 'take Ritalin'
                // and 'appointment at the clinic' have no smaller parts
                if (!task.isDone && !task.isMedication && !task.isReservation) {
                    if (task.subtasks.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        BreakdownSummaryRow(
                            palette = palette,
                            accent = task.accent,
                            doneCount = task.subtasks.count { it.completed },
                            totalCount = task.subtasks.size,
                            progress = subtaskProgress,
                            onClick = onManageSubtasks,
                        )
                    }
                }

            }
        }
    }
}

// details disclosure

@Composable
private fun DetailsChevron(
    palette: CalendarPalette,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label = "details_chevron",
    )
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (expanded) stringResource(R.string.cal_hide_details) else stringResource(R.string.cal_show_details),
            tint = palette.muted.copy(alpha = 0.75f),
            modifier = Modifier
                .size(18.dp)
                .rotate(rotation),
        )
    }
}

// description, location and link, revealed on demand. labelled rows so they read as facts
// about the event rather than loose text stacked under a title
@Composable
private fun TaskDetailsBlock(
    palette: CalendarPalette,
    description: String?,
    location: String?,
    link: String?,
    onOpenLink: () -> Unit,
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        if (!description.isNullOrEmpty()) {
            DetailRow(
                palette = palette,
                icon = Icons.AutoMirrored.Rounded.EventNote,
                text = description,
            )
        }
        if (!location.isNullOrEmpty()) {
            if (!description.isNullOrEmpty()) Spacer(Modifier.height(8.dp))
            DetailRow(
                palette = palette,
                icon = Icons.Outlined.Place,
                text = location,
            )
        }
        if (!link.isNullOrEmpty()) {
            if (!description.isNullOrEmpty() || !location.isNullOrEmpty()) {
                Spacer(Modifier.height(10.dp))
            }
            LinkChip(palette = palette, rawUrl = link, onClick = onOpenLink)
        }
    }
}

@Composable
private fun DetailRow(
    palette: CalendarPalette,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = palette.muted.copy(alpha = 0.7f),
            modifier = Modifier
                .padding(top = 1.dp)
                .size(14.dp),
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = text,
            color = palette.muted,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
        )
    }
}

// Timed events without a plan used to repeat a full-width CTA below every card. Ten events meant
// ten extra panels and very little calendar. Keep the visible affordance small in the existing
// title row, but retain a generous invisible touch target and an explicit accessibility role.
@Composable
private fun CompactBreakdownButton(
    accent: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 66.dp, height = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(accent.copy(alpha = 0.13f))
                .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(50))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.cal_divide),
                color = accent,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

// one-line status of the breakdown, and the way into the full screen. not the plan itself,
// the stations live on TaskBreakdownScreen so the card is the same height whether an event
// has three steps or twelve
@Composable
private fun BreakdownSummaryRow(
    palette: CalendarPalette,
    accent: Color,
    doneCount: Int,
    totalCount: Int,
    progress: Float,
    onClick: () -> Unit,
) {
    val complete = doneCount == totalCount && totalCount > 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.09f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (complete) Icons.Outlined.Check else Icons.Outlined.AutoAwesome,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (complete) stringResource(R.string.cal_breakdown_complete) else stringResource(R.string.cal_steps_done, doneCount, totalCount),
                color = palette.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(accent.copy(alpha = 0.18f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(accent),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = stringResource(R.string.cal_open_breakdown),
            tint = accent,
            // pointing right: this opens another screen, it doesn't expand in place
            modifier = Modifier
                .size(16.dp)
                .rotate(-90f),
        )
    }
}

// link chip
@Composable
private fun LinkChip(
    palette: CalendarPalette,
    rawUrl: String,
    onClick: () -> Unit
) {
    val display = rawUrl
        .removePrefix("https://")
        .removePrefix("http://")
        .removePrefix("www.")
        .let { if (it.length > 32) it.take(32) + "…" else it }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(palette.lavender.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Link,
            contentDescription = null,
            tint = palette.lavender,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = display,
            color = palette.lavender,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun openUrl(context: android.content.Context, raw: String) {
    val normalized = when {
        raw.startsWith("http://", ignoreCase = true) -> raw
        raw.startsWith("https://", ignoreCase = true) -> raw
        else -> "https://$raw"
    }
    val intent = Intent(Intent.ACTION_VIEW, normalized.toUri()).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

// task status circle
@Composable
fun TaskStatusCircle(
    palette: CalendarPalette,
    completed: Boolean,
    onClick: (() -> Unit)? = null,
) {
    val bgColor by animateColorAsState(
        targetValue = if (completed) palette.lavender else Color.Transparent,
        animationSpec = tween(250),
        label = "statusBg"
    )

    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(bgColor)
            .then(
                if (!completed) {
                    Modifier.border(1.5.dp, palette.muted.copy(alpha = 0.35f), CircleShape)
                } else {
                    Modifier
                }
            )
            // was decoration until calendar events got a completion flag, now it's the tick-off control
            .then(
                if (onClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ) else Modifier
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
                contentDescription = stringResource(R.string.onboarding_completed),
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}
