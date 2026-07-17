package com.muradgalayev.brainbuddy.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.domain.model.SubtaskKind

/**
 * Above this count, station chips on the horizontal rail get too narrow to read.
 * The rail switches to a vertical timeline that handles long plans gracefully.
 */
private const val MAX_HORIZONTAL_STATIONS = 5

/**
 * Thin segmented preview shown when the card is collapsed — gradient-filled, with the
 * completed portion saturated and the upcoming portion soft. Conveys "this block has a
 * plan" without taking visual space.
 */
@Composable
fun SubtaskRibbonPreview(
    palette: CalendarPalette,
    accent: Color,
    subtasks: List<CalendarSubtaskUi>,
    modifier: Modifier = Modifier,
) {
    if (subtasks.isEmpty()) return
    val total = subtasks.sumOf { it.durationMinutes }.coerceAtLeast(1)
    val completedColor = accent
    val pendingColor = lerp(palette.cardBg, accent, 0.22f)
    val breakColor = lerp(palette.cardBg, accent, 0.10f)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        subtasks.forEach { st ->
            val color = when {
                st.completed -> completedColor
                st.kind == SubtaskKind.BREAK -> breakColor
                else -> pendingColor
            }
            Box(
                modifier = Modifier
                    .weight(st.durationMinutes.toFloat() / total.toFloat())
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

@Composable
fun SubtaskTimelineRail(
    palette: CalendarPalette,
    accent: Color,
    totalMinutes: Int,
    subtasks: List<CalendarSubtaskUi>,
    isPomodoroPlan: Boolean,
    onToggleSubtask: (subtaskId: String, completed: Boolean) -> Unit,
    onManage: () -> Unit,
    onRunInPomodoro: () -> Unit,
    expanded: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = expanded,
        enter = expandVertically(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) + fadeIn(),
        exit = shrinkVertically(animationSpec = tween(180)) + fadeOut(animationSpec = tween(120)),
    ) {
        Column(modifier = modifier.fillMaxWidth()) {
            Spacer(Modifier.height(14.dp))

            // Soft "lane" background giving the rail visual weight without a hard divider
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.06f),
                                accent.copy(alpha = 0.02f),
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                if (subtasks.isEmpty()) {
                    EmptyTimelineCta(palette = palette, accent = accent, onManage = onManage)
                } else {
                    TimelineRailContent(
                        palette = palette,
                        accent = accent,
                        totalMinutes = totalMinutes,
                        subtasks = subtasks,
                        onToggleSubtask = onToggleSubtask,
                        onManage = onManage,
                    )
                }
            }

            if (isPomodoroPlan && subtasks.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                RunInPomodoroCta(
                    palette = palette,
                    accent = accent,
                    subtasks = subtasks,
                    onClick = onRunInPomodoro,
                )
            }

            Spacer(Modifier.height(2.dp))
        }
    }
}

@Composable
private fun EmptyTimelineCta(
    palette: CalendarPalette,
    accent: Color,
    onManage: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        lerp(palette.cardBg, accent, 0.14f),
                        lerp(palette.cardBg, accent, 0.06f),
                    )
                )
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onManage,
            )
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(accent, lerp(accent, Color.White, 0.25f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Break this into stations",
                color = palette.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Smaller steps make a long block easier to start",
                color = palette.muted,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(accent)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Split",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp,
            )
        }
    }
}

@Composable
private fun TimelineRailContent(
    palette: CalendarPalette,
    accent: Color,
    totalMinutes: Int,
    subtasks: List<CalendarSubtaskUi>,
    onToggleSubtask: (subtaskId: String, completed: Boolean) -> Unit,
    onManage: () -> Unit,
) {
    val totalDuration = subtasks.sumOf { it.durationMinutes }.coerceAtLeast(1)
    val completedMinutes = subtasks.filter { it.completed }.sumOf { it.durationMinutes }
    val percent = (completedMinutes.toFloat() / totalDuration.toFloat()).coerceIn(0f, 1f)
    val progress by animateFloatAsState(
        targetValue = percent,
        animationSpec = tween(650, easing = FastOutSlowInEasing),
        label = "rail-progress"
    )

    // ── Hero strip: clear, single-column counter + headline ──
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$completedMinutes",
                color = palette.ink,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.6).sp,
            )
            Text(
                text = " / ${totalDuration}m",
                color = palette.muted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 3.dp)
            )
        }
        val headline = when {
            percent >= 1f -> "Plan complete"
            percent <= 0f -> "${subtasks.size} stations · ready to start"
            else -> {
                val nextLabel = subtasks.firstOrNull { !it.completed }?.title ?: ""
                if (nextLabel.isNotBlank()) "Next · $nextLabel" else "${subtasks.size} stations"
            }
        }
        Text(
            text = headline,
            color = palette.muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }

    Spacer(Modifier.height(18.dp))

    // ── The rail — adaptive layout based on station count ──
    if (subtasks.size <= MAX_HORIZONTAL_STATIONS) {
        HorizontalRail(
            palette = palette,
            accent = accent,
            subtasks = subtasks,
            totalDuration = totalDuration,
            progress = progress,
            onToggleSubtask = onToggleSubtask,
        )
    } else {
        VerticalRail(
            palette = palette,
            accent = accent,
            subtasks = subtasks,
            onToggleSubtask = onToggleSubtask,
        )
    }

    Spacer(Modifier.height(12.dp))

    // ── Footer row: overflow note (left) + small Edit action (right) ──
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (totalMinutes > 0 && totalDuration != totalMinutes) {
            Text(
                text = if (totalDuration < totalMinutes)
                    "${totalMinutes - totalDuration}m unscheduled"
                else
                    "${totalDuration - totalMinutes}m over the block",
                color = if (totalDuration > totalMinutes) palette.flagRed else palette.muted,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        ManagePill(accent = accent, onClick = onManage)
    }
}

@Composable
private fun HorizontalRail(
    palette: CalendarPalette,
    accent: Color,
    subtasks: List<CalendarSubtaskUi>,
    totalDuration: Int,
    progress: Float,
    onToggleSubtask: (id: String, completed: Boolean) -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth()) {
        // Gradient track behind everything
        Canvas(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(56.dp)
        ) {
            val midY = size.height / 2f
            val stroke = 3.dp.toPx()
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(accent.copy(alpha = 0.20f), accent.copy(alpha = 0.10f))
                ),
                start = Offset(0f, midY),
                end = Offset(size.width, midY),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
            if (progress > 0f) {
                val fillEnd = size.width * progress
                drawLine(
                    brush = Brush.horizontalGradient(
                        colors = listOf(accent, lerp(accent, Color.White, 0.20f)),
                        startX = 0f,
                        endX = fillEnd,
                    ),
                    start = Offset(0f, midY),
                    end = Offset(fillEnd, midY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round,
                )
            }
        }

        // Stations row — proportional weights by duration
        Row(modifier = Modifier.fillMaxWidth()) {
            subtasks.forEachIndexed { index, st ->
                StationCell(
                    palette = palette,
                    accent = accent,
                    subtask = st,
                    index = index,
                    weight = st.durationMinutes.toFloat() / totalDuration.toFloat(),
                    onClick = { onToggleSubtask(st.id, !st.completed) },
                )
            }
        }
    }
}

/**
 * Compact vertical timeline used when there are too many stations to read horizontally.
 * Connectors meet between rows so the dot column reads as one continuous track.
 */
@Composable
private fun VerticalRail(
    palette: CalendarPalette,
    accent: Color,
    subtasks: List<CalendarSubtaskUi>,
    onToggleSubtask: (id: String, completed: Boolean) -> Unit,
) {
    val activeIndex = subtasks.indexOfFirst { !it.completed }

    Column(modifier = Modifier.fillMaxWidth()) {
        subtasks.forEachIndexed { index, st ->
            VerticalStationRow(
                palette = palette,
                accent = accent,
                subtask = st,
                index = index,
                isFirst = index == 0,
                isLast = index == subtasks.lastIndex,
                topConnectorActive = index > 0 && subtasks[index - 1].completed,
                bottomConnectorActive = st.completed,
                isActive = index == activeIndex,
                onClick = { onToggleSubtask(st.id, !st.completed) },
            )
        }
    }
}

@Composable
private fun VerticalStationRow(
    palette: CalendarPalette,
    accent: Color,
    subtask: CalendarSubtaskUi,
    index: Int,
    isFirst: Boolean,
    isLast: Boolean,
    topConnectorActive: Boolean,
    bottomConnectorActive: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val isBreak = subtask.kind == SubtaskKind.BREAK
    val activeBg = if (isActive) accent.copy(alpha = 0.08f) else Color.Transparent
    val titleColor = when {
        subtask.completed -> palette.muted
        isBreak -> palette.muted
        else -> palette.ink
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .background(activeBg),
    ) {
        // ── Left: connector + dot ──
        Column(
            modifier = Modifier
                .width(36.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top half of connector (zero-height for first row)
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(
                        when {
                            isFirst -> Color.Transparent
                            topConnectorActive -> accent
                            else -> accent.copy(alpha = 0.18f)
                        }
                    )
            )
            StationDot(
                accent = accent,
                palette = palette,
                completed = subtask.completed,
                isBreak = isBreak,
                onClick = onClick,
            )
            // Bottom half of connector (zero-height for last row)
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .weight(1f)
                    .background(
                        when {
                            isLast -> Color.Transparent
                            bottomConnectorActive -> accent
                            else -> accent.copy(alpha = 0.18f)
                        }
                    )
            )
        }

        Spacer(Modifier.width(8.dp))

        // ── Right: number + title + meta + duration badge ──
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(end = 4.dp, top = 10.dp, bottom = 10.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(
                                if (subtask.completed) accent.copy(alpha = 0.15f)
                                else if (isBreak) palette.muted.copy(alpha = 0.12f)
                                else accent.copy(alpha = 0.15f)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = if (isBreak) palette.muted else accent,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Black,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = subtask.title,
                        color = titleColor,
                        fontSize = 13.5.sp,
                        fontWeight = if (subtask.completed) FontWeight.Medium else FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                }
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isBreak) {
                        Icon(
                            imageVector = Icons.Outlined.Coffee,
                            contentDescription = null,
                            tint = palette.muted,
                            modifier = Modifier.size(10.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = if (isBreak) "BREAK" else "FOCUS",
                        color = if (isBreak) palette.muted else accent.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                    )
                    if (isActive) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(3.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.5f))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "UP NEXT",
                            color = accent,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (subtask.completed) accent.copy(alpha = 0.14f)
                        else palette.muted.copy(alpha = 0.10f)
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "${subtask.durationMinutes}m",
                    color = if (subtask.completed) accent else palette.ink.copy(alpha = 0.7f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.2.sp,
                )
            }
        }
    }
}

@Composable
private fun ManagePill(accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        accent.copy(alpha = 0.20f),
                        accent.copy(alpha = 0.10f),
                    )
                )
            )
            .border(1.dp, accent.copy(alpha = 0.30f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "Edit",
            color = accent,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.StationCell(
    palette: CalendarPalette,
    accent: Color,
    subtask: CalendarSubtaskUi,
    index: Int,
    weight: Float,
    onClick: () -> Unit,
) {
    val isBreak = subtask.kind == SubtaskKind.BREAK
    Column(
        modifier = Modifier
            .weight(weight.coerceAtLeast(0.05f))
            .padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Floating glass title chip — constrained to cell width so titles
        //    ellipsize instead of overflowing into the next station. ──
        StationChip(
            palette = palette,
            accent = accent,
            number = index + 1,
            title = subtask.title,
            completed = subtask.completed,
            isBreak = isBreak,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        StationDot(
            accent = accent,
            palette = palette,
            completed = subtask.completed,
            isBreak = isBreak,
            onClick = onClick,
        )

        Spacer(Modifier.height(10.dp))

        // ── Duration label ──
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(palette.muted.copy(alpha = 0.08f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                text = "${subtask.durationMinutes}m",
                color = palette.muted,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.3.sp,
            )
        }
    }
}

@Composable
private fun StationChip(
    palette: CalendarPalette,
    accent: Color,
    number: Int,
    title: String,
    completed: Boolean,
    isBreak: Boolean,
    modifier: Modifier = Modifier,
) {
    val bg = when {
        completed -> Brush.linearGradient(
            listOf(
                lerp(accent, Color.White, 0.15f),
                accent,
            )
        )
        isBreak -> Brush.linearGradient(
            listOf(
                lerp(palette.cardBg, palette.muted, 0.08f),
                lerp(palette.cardBg, palette.muted, 0.04f),
            )
        )
        else -> Brush.linearGradient(
            listOf(palette.cardBg, palette.cardBg)
        )
    }
    val borderColor = when {
        completed -> accent.copy(alpha = 0f)
        isBreak -> palette.muted.copy(alpha = 0.20f)
        else -> accent.copy(alpha = 0.32f)
    }
    val titleColor = if (completed) Color.White else palette.ink
    val numberColor = if (completed) Color.White.copy(alpha = 0.8f)
    else if (isBreak) palette.muted else accent

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$number",
            color = numberColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
        )
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(8.dp)
                .background(numberColor.copy(alpha = 0.3f))
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = title,
            color = titleColor,
            fontSize = 9.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun StationDot(
    accent: Color,
    palette: CalendarPalette,
    completed: Boolean,
    isBreak: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (completed) 1f else 0.94f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "station-scale"
    )
    val pulse = rememberInfiniteTransition(label = "station-pulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse-scale"
    )
    val pulseAlpha by pulse.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "pulse-alpha"
    )

    Box(
        modifier = Modifier.size(34.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Expanding pulse ring on incomplete stations
        if (!completed) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .scale(pulseScale)
                    .alpha(pulseAlpha)
                    .clip(CircleShape)
                    .background(accent)
            )
        }
        // Soft outer halo (always)
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accent.copy(alpha = if (completed) 0.30f else 0.16f),
                            Color.Transparent,
                        )
                    )
                )
        )
        // The pearl
        Box(
            modifier = Modifier
                .size(20.dp)
                .scale(scale)
                .clip(CircleShape)
                .background(
                    if (completed)
                        Brush.linearGradient(
                            listOf(
                                lerp(accent, Color.White, 0.25f),
                                accent,
                            )
                        )
                    else Brush.linearGradient(listOf(palette.cardBg, palette.cardBg))
                )
                .border(
                    width = if (completed) 0.dp else 2.dp,
                    color = if (completed) Color.Transparent else accent.copy(alpha = 0.7f),
                    shape = CircleShape,
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (completed) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Done",
                    tint = Color.White,
                    modifier = Modifier.size(11.dp),
                )
            } else if (isBreak) {
                Icon(
                    imageVector = Icons.Outlined.Coffee,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(9.dp),
                )
            }
        }
    }
}

@Composable
private fun RunInPomodoroCta(
    palette: CalendarPalette,
    accent: Color,
    subtasks: List<CalendarSubtaskUi>,
    onClick: () -> Unit,
) {
    val nextIndex = subtasks.indexOfFirst { !it.completed }
    val nextLabel = subtasks.getOrNull(if (nextIndex < 0) 0 else nextIndex)?.title
    val allDone = nextIndex < 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        lerp(accent, Color.Black, 0.05f),
                        accent,
                        lerp(accent, Color.White, 0.18f),
                    )
                )
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.24f))
                .border(1.dp, Color.White.copy(alpha = 0.30f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (allDone) "Replay in Pomodoro" else "Run in Pomodoro",
                color = Color.White,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.3.sp,
            )
            Text(
                text = if (allDone) "Start the plan from the top"
                else "Next · ${nextLabel.orEmpty()}",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.20f))
                .padding(horizontal = 11.dp, vertical = 5.dp)
        ) {
            Text(
                text = "OPEN",
                color = Color.White,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.6.sp,
            )
        }
    }
}
