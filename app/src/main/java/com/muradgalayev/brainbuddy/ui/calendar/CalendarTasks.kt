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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.EventNote
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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


/* ── Events section ── */
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
fun EmptyTasksState(palette: CalendarPalette) {
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

    val showDeleteBackground =
        dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart

    val backgroundColor by animateColorAsState(
        targetValue = if (showDeleteBackground) palette.flagRed else Color.Transparent,
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
                AnimatedVisibility(visible = showDeleteBackground) {
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
        CalendarTaskCard(
            palette = palette,
            task = task,
            onClick = onClick
        )
    }
}

@Composable
fun CalendarTaskCard(
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
fun TaskStatusCircle(palette: CalendarPalette, completed: Boolean) {
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
