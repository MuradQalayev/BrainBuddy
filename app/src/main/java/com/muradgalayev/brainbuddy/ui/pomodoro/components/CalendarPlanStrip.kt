package com.muradgalayev.brainbuddy.ui.pomodoro.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.data.local.PomodoroQueueItem
import com.muradgalayev.brainbuddy.data.local.PomodoroQueueState
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// the 'you are running this calendar plan' header that sits above the timer when a queue is
// loaded. collapsed it shows the event, the station ribbon and the current step; expanded it
// shows the whole breakdown, with any step startable on its own. the expanded list exists
// because the ribbon can only say where you are, not what's coming, and a plan you can't see
// the rest of is one you have to leave the timer to read
@Composable
fun CalendarPlanStrip(
    queue: PomodoroQueueState?,
    accentColor: Color,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    // true when the timer is idle, stations can only be re-primed between sessions
    canSelectStep: Boolean,
    onStartStep: (Int) -> Unit,
    onOpenBreakdown: () -> Unit,
    completedSubtaskIds: Set<String>,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = queue != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        if (queue == null) return@AnimatedVisibility
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val surface = if (isDark) Color(0xFF12100E) else Color(0xFFFAFAF9)
        val ink = MaterialTheme.colorScheme.onSurface
        val muted = ink.copy(alpha = 0.55f)

        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(surface)
                .border(1.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.plan_calendar),
                        color = muted,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        text = queue.eventTitle,
                        color = ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ink.copy(alpha = 0.06f))
                        .clickable(onClick = onOpenBreakdown),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = stringResource(R.string.plan_open_breakdown),
                        tint = muted,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ink.copy(alpha = 0.06f))
                        .clickable(onClick = onClear),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.plan_stop_following),
                        tint = muted,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // stations ribbon, proportional weights with the current item highlighted
            val total = queue.items.sumOf { it.durationMs }.coerceAtLeast(1L)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                queue.items.forEachIndexed { index, item ->
                    val color = when {
                        item.subtaskId in completedSubtaskIds -> accentColor
                        index == queue.currentIndex -> accentColor.copy(alpha = 0.9f)
                        item.isFocus -> accentColor.copy(alpha = 0.25f)
                        else -> ink.copy(alpha = 0.15f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(item.durationMs.toFloat() / total.toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            val current = queue.current
            val currentLabel = if (current != null) {
                val n = queue.currentIndex + 1
                stringResource(R.string.plan_step_of, n, queue.items.size, current.title)
            } else {
                stringResource(R.string.bd_plan_complete)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current != null && !current.isFocus) {
                    Icon(
                        imageVector = Icons.Outlined.Coffee,
                        contentDescription = null,
                        tint = muted,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = currentLabel,
                    color = muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                ExpandToggle(
                    expanded = expanded,
                    stepCount = queue.items.size,
                    tint = accentColor,
                    onClick = onToggleExpanded,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn(tween(180)) + expandVertically(tween(200)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(ink.copy(alpha = 0.08f))
                    )
                    Spacer(Modifier.height(6.dp))
                    queue.items.forEachIndexed { index, item ->
                        PlanStepRow(
                            item = item,
                            index = index,
                            isCurrent = index == queue.currentIndex,
                            isDone = item.subtaskId in completedSubtaskIds,
                            canStart = canSelectStep,
                            accentColor = accentColor,
                            ink = ink,
                            muted = muted,
                            onStart = { onStartStep(index) },
                        )
                    }
                    if (!canSelectStep) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            // says why the per-step buttons are gone, rather than leaving them tappable and silently
                            // ignoring the tap mid-session
                            text = stringResource(R.string.plan_finish_first),
                            color = muted,
                            fontSize = 10.5.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandToggle(
    expanded: Boolean,
    stepCount: Int,
    tint: Color,
    onClick: () -> Unit,
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "plan_expand_chevron",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (expanded) stringResource(R.string.plan_hide_steps) else stringResource(R.string.plan_all_steps, stepCount),
            color = tint,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = if (expanded) stringResource(R.string.plan_hide_all) else stringResource(R.string.plan_show_all),
            tint = tint,
            modifier = Modifier
                .size(15.dp)
                .rotate(rotation),
        )
    }
}

@Composable
private fun PlanStepRow(
    item: PomodoroQueueItem,
    index: Int,
    isCurrent: Boolean,
    isDone: Boolean,
    canStart: Boolean,
    accentColor: Color,
    ink: Color,
    muted: Color,
    onStart: () -> Unit,
) {
    val tint = if (item.isFocus) accentColor else muted
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrent) accentColor.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (isDone) tint else tint.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (isDone) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(12.dp),
                )
            } else {
                Text(
                    text = "${index + 1}",
                    color = tint,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = if (isDone) muted else ink,
                fontSize = 12.5.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                textDecoration = if (isDone) TextDecoration.LineThrough else null,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!item.isFocus) {
                    Icon(
                        imageVector = Icons.Outlined.Coffee,
                        contentDescription = null,
                        tint = muted,
                        modifier = Modifier.size(10.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = "${item.durationMs / 60_000L}m" +
                        if (isCurrent) stringResource(R.string.plan_current) else "",
                    color = if (isCurrent) accentColor else muted,
                    fontSize = 10.sp,
                )
            }
        }
        // done steps keep their start button: re-running one is a legitimate ask, and hiding it would
        // make the row the only un-startable thing in the list
        if (canStart) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.16f))
                    .clickable(onClick = onStart),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.plan_start_item, item.title),
                    tint = tint,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}
