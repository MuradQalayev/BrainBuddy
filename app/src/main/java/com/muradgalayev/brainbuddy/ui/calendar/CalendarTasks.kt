package com.muradgalayev.brainbuddy.ui.calendar

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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.content.Intent
import androidx.core.net.toUri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale


/* ── Bottom Sheet Content (Portrait mode) ── */
@Composable
fun EventsBottomSheet(
    palette: CalendarPalette,
    selectedDate: LocalDate,
    tasks: List<CalendarTaskUi>,
    onTaskClick: (String) -> Unit,
    onTaskDelete: (String) -> Unit,
    onAddTask: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Drag handle ──
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

        // ── Dark date header (like reference) ──
        DateHeader(palette = palette, selectedDate = selectedDate)

        Spacer(modifier = Modifier.height(4.dp))

        // ── Task list ──
        Box(modifier = Modifier.fillMaxWidth()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
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

                // Bottom spacing for FAB
                item { Spacer(modifier = Modifier.height(88.dp)) }
            }

            // FAB inside sheet
            FloatingActionButton(
                onClick = onAddTask,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 24.dp),
                shape = CircleShape,
                containerColor = palette.lavender,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(
                    defaultElevation = 6.dp
                )
            ) {
                Icon(Icons.Outlined.Add, "Add task", Modifier.size(28.dp))
            }
        }
    }
}

/* ── Dark date header ── */
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
            // Red dot indicator
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

/* ── Events section (landscape fallback) ── */
@Composable
fun EventsSection(
    palette: CalendarPalette,
    selectedDate: LocalDate,
    tasks: List<CalendarTaskUi>,
    onTaskClick: (String) -> Unit,
    onTaskDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        DateHeader(palette = palette, selectedDate = selectedDate)

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (tasks.isEmpty()) {
                item { EmptyTasksState(palette = palette) }
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

/* ── Category tag colors ── */
private fun categoryColor(category: String): Color = when (category.lowercase()) {
    "work" -> Color(0xFFD9B05C)      // muted amber
    "personal" -> Color(0xFF8AAE7E)  // sage green
    "education" -> Color(0xFFD97A3D) // accent orange
    "sport" -> Color(0xFFC75A4A)     // muted red
    "health" -> Color(0xFFA88AB8)    // muted lilac
    else -> Color(0xFF7FA3C9)        // accent blue
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

    // After triggering edit/delete, snap the row back to center.
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
                        contentDescription = "Edit event",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
                AnimatedVisibility(visible = showDeleteBackground) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Delete event",
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
            onClick = onClick
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = palette.cardBg,
            title = {
                Text(
                    text = "Delete event?",
                    color = palette.ink,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"${task.title}\"? This can't be undone.",
                    color = palette.muted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                }) {
                    Text(
                        "Delete",
                        color = palette.flagRed,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(
                        "Cancel",
                        color = palette.muted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        )
    }
}

/* ── Task Card (reference style) ── */
@Composable
fun CalendarTaskCard(
    palette: CalendarPalette,
    task: CalendarTaskUi,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val tintedBg = androidx.compose.ui.graphics.lerp(palette.cardBg, task.accent, 0.07f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = tintedBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            // Accent stripe — modern Linear-style indicator
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(task.accent)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
            // Completion circle
            TaskStatusCircle(palette = palette, completed = task.completed)

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Category tag pill
                if (!task.subtitle.isNullOrEmpty()) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                categoryColor(task.subtitle).copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = task.subtitle.uppercase(),
                            color = categoryColor(task.subtitle),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                }

                // Task title
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

                if (!task.location.isNullOrEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = task.location,
                        color = palette.muted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (!task.link.isNullOrEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    LinkChip(
                        palette = palette,
                        rawUrl = task.link,
                        onClick = { openUrl(context, task.link) }
                    )
                }
            }

            // Time range
            if (!task.timeRange.isNullOrEmpty()) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = task.timeRange,
                        color = palette.muted.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Flag indicator
            if (task.flagged) {
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(palette.flagRed)
                )
            }
            }
        }
    }
}

/* ── Link chip (tap to open) ── */
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

/* ── Task Status Circle ── */
@Composable
fun TaskStatusCircle(palette: CalendarPalette, completed: Boolean) {
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