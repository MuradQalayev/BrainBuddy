package com.muradgalayev.brainbuddy.ui.calendar

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.domain.model.SubtaskKind
import kotlin.math.roundToInt
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// full-screen plan for one calendar event: build the breakdown, then work through it. this
// used to live inside the task card as an expanding rail plus a bottom-sheet editor, and both
// were cramped: the rail pushed every other task off screen, and the sheet could only ever be
// an editor, never a place to track from. a screen means the plan can be a timeline you read
// top-to-bottom, with the station you're actually on called out
@Composable
fun TaskBreakdownScreen(
    onBack: () -> Unit,
    onNavigateToPomodoro: () -> Unit,
    viewModel: TaskBreakdownViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val palette = rememberCalendarPalette()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BreakdownTopBar(
                palette = palette,
                accent = state.accent,
                saveState = state.saveState,
                showStatus = !state.isLoading && !state.missing && state.stations.isNotEmpty(),
                onBack = onBack,
            )

            when {
                state.isLoading -> Unit

                state.missing -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.bd_event_gone),
                        color = palette.muted,
                        fontSize = 14.sp,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 32.dp,
                    ),
                ) {
                    item(key = "hero") {
                        BreakdownHero(palette = palette, state = state)
                        Spacer(Modifier.height(18.dp))
                    }

                    if (state.stations.isEmpty()) {
                        item(key = "empty") {
                            EmptyPlanPrompt(
                                palette = palette,
                                accent = state.accent,
                                totalMinutes = state.totalMinutes,
                                onApplyPreset = viewModel::applyPreset,
                                onAddStation = viewModel::addStation,
                            )
                        }
                    } else {
                        itemsIndexed(state.stations, key = { _, s -> s.id }) { index, station ->
                            StationRow(
                                palette = palette,
                                accent = state.accent,
                                station = station,
                                index = index,
                                isNext = index == state.nextIndex,
                                isLast = index == state.stations.lastIndex,
                                canMoveUp = index > 0,
                                canMoveDown = index < state.stations.lastIndex,
                                onToggleComplete = { viewModel.toggleCompleted(station.id) },
                                onRename = { viewModel.renameStation(station.id, it) },
                                onAdjustMinutes = { viewModel.adjustMinutes(station.id, it) },
                                onToggleKind = { viewModel.toggleKind(station.id) },
                                onRemove = { viewModel.removeStation(station.id) },
                                onMoveUp = { viewModel.move(index, index - 1) },
                                onMoveDown = { viewModel.move(index, index + 1) },
                                onRunThisStation = {
                                    if (viewModel.launchStationInPomodoro(station.id)) {
                                        onNavigateToPomodoro()
                                    }
                                },
                            )
                        }

                        item(key = "actions") {
                            Spacer(Modifier.height(6.dp))
                            PlanActions(
                                palette = palette,
                                accent = state.accent,
                                canRunInPomodoro = state.canRunInPomodoro,
                                saveState = state.saveState,
                                onAddStation = viewModel::addStation,
                                onClearPlan = viewModel::clearPlan,
                                onSavePlan = viewModel::savePlan,
                                onStartFocus = {
                                    if (viewModel.launchInPomodoro()) onNavigateToPomodoro()
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// top bar

@Composable
private fun BreakdownTopBar(
    palette: CalendarPalette,
    accent: Color,
    saveState: PlanSaveState,
    showStatus: Boolean,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
            Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = palette.ink)
        }
        Spacer(Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.bd_title),
            color = palette.ink,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        if (showStatus) {
            SaveStatusPill(palette = palette, accent = accent, saveState = saveState)
            Spacer(Modifier.width(8.dp))
        }
    }
}

// a standing answer to 'is this stored?', so Save never has to be pressed to find out
@Composable
private fun SaveStatusPill(palette: CalendarPalette, accent: Color, saveState: PlanSaveState) {
    val label = when (saveState) {
        PlanSaveState.SAVED -> stringResource(R.string.bd_saved)
        PlanSaveState.PENDING -> stringResource(R.string.bd_unsaved)
        PlanSaveState.SAVING -> stringResource(R.string.common_saving)
        PlanSaveState.JUST_SAVED -> stringResource(R.string.bd_plan_saved)
    }
    val tint = when (saveState) {
        PlanSaveState.PENDING -> palette.muted
        PlanSaveState.JUST_SAVED -> accent
        else -> palette.muted
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = if (saveState == PlanSaveState.JUST_SAVED) 0.16f else 0.10f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (saveState == PlanSaveState.SAVED || saveState == PlanSaveState.JUST_SAVED) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(text = label, color = tint, fontSize = 10.5.sp, fontWeight = FontWeight.Medium)
    }
}

// hero: title, time, progress ring

@Composable
private fun BreakdownHero(palette: CalendarPalette, state: BreakdownUiState) {
    val animatedProgress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(520),
        label = "breakdown_progress",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = lerp(palette.cardBg, state.accent, 0.10f),
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.eventTitle,
                    color = palette.ink,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 24.sp,
                )
                state.timeRange?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(text = it, color = palette.muted, fontSize = 13.sp)
                }
                if (state.stations.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.bd_progress, state.doneCount, state.stations.size, state.doneMinutes, state.allocatedMinutes),
                        color = palette.muted,
                        fontSize = 12.sp,
                    )
                }
                // only surfaced when it matters: a plan that exactly fills the event needs no commentary, and
                // a nag on every screen is noise
                val leftover = state.unallocatedMinutes
                if (state.stations.isNotEmpty() && leftover != 0) {
                    Spacer(Modifier.height(8.dp))
                    AllocationChip(
                        palette = palette,
                        accent = state.accent,
                        leftoverMinutes = leftover,
                    )
                }
            }

            Spacer(Modifier.width(14.dp))
            ProgressRing(
                progress = animatedProgress,
                accent = state.accent,
                trackColor = palette.muted.copy(alpha = 0.18f),
                isComplete = state.isComplete,
                label = if (state.stations.isEmpty()) "—"
                else "${(state.progress * 100).roundToInt()}%",
                labelColor = palette.ink,
            )
        }
    }
}

@Composable
private fun ProgressRing(
    progress: Float,
    accent: Color,
    trackColor: Color,
    isComplete: Boolean,
    label: String,
    labelColor: Color,
) {
    Box(
        modifier = Modifier.size(74.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            if (progress > 0f) {
                drawArc(
                    color = accent,
                    // -90 so the ring fills from the top, the way a clock reads
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(
                        width = stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    ),
                )
            }
        }
        if (isComplete) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = stringResource(R.string.bd_plan_complete),
                tint = accent,
                modifier = Modifier.size(30.dp),
            )
        } else {
            Text(
                text = label,
                color = labelColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun AllocationChip(palette: CalendarPalette, accent: Color, leftoverMinutes: Int) {
    val over = leftoverMinutes < 0
    val text = if (over) stringResource(R.string.bd_over, -leftoverMinutes)
    else stringResource(R.string.bd_unplanned, leftoverMinutes)
    val tint = if (over) palette.flagRed else accent
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text = text, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

// empty state: the split presets, front and centre

@Composable
private fun EmptyPlanPrompt(
    palette: CalendarPalette,
    accent: Color,
    totalMinutes: Int,
    onApplyPreset: (SplitPreset) -> Unit,
    onAddStation: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.bd_break_it),
            color = palette.ink,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (totalMinutes > 0)
                stringResource(R.string.bd_split_minutes, totalMinutes)
            else stringResource(R.string.bd_add_stations),
            color = palette.muted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(14.dp))

        val presets = listOf(
            SplitPreset.HALVES to stringResource(R.string.bd_halves),
            SplitPreset.THIRDS to stringResource(R.string.bd_thirds),
            SplitPreset.QUARTERS to stringResource(R.string.bd_quarters),
            SplitPreset.POMODORO to stringResource(R.string.intake_coping_pomodoro),
        )
        presets.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { (preset, label) ->
                    PresetCard(
                        palette = palette,
                        accent = accent,
                        label = label,
                        subtitle = presetSubtitle(preset, totalMinutes),
                        onClick = { onApplyPreset(preset) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(4.dp))
        OutlinedAction(
            palette = palette,
            icon = Icons.Outlined.Add,
            label = stringResource(R.string.bd_add_manual),
            onClick = onAddStation,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun presetSubtitle(preset: SplitPreset, totalMinutes: Int): String {
    if (totalMinutes <= 0) return ""
    return when (preset) {
        SplitPreset.HALVES -> stringResource(R.string.bd_preset_halves, totalMinutes / 2)
        SplitPreset.THIRDS -> stringResource(R.string.bd_preset_thirds, totalMinutes / 3)
        SplitPreset.QUARTERS -> stringResource(R.string.bd_preset_quarters, totalMinutes / 4)
        SplitPreset.POMODORO -> stringResource(R.string.bd_preset_pomodoro)
    }
}

@Composable
private fun PresetCard(
    palette: CalendarPalette,
    accent: Color,
    label: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = lerp(palette.cardBg, accent, 0.06f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.20f)),
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp)) {
            Text(text = label, color = palette.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(text = subtitle, color = palette.muted, fontSize = 11.sp)
            }
        }
    }
}

// one station in the timeline

@Composable
private fun StationRow(
    palette: CalendarPalette,
    accent: Color,
    station: CalendarSubtaskUi,
    index: Int,
    isNext: Boolean,
    isLast: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggleComplete: () -> Unit,
    onRename: (String) -> Unit,
    onAdjustMinutes: (Int) -> Unit,
    onToggleKind: () -> Unit,
    onRemove: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRunThisStation: () -> Unit,
) {
    val isBreak = station.kind == SubtaskKind.BREAK
    val stationTint = if (isBreak) palette.muted else accent

    Row(modifier = Modifier.fillMaxWidth()) {
        // timeline rail: node plus connector to the next station
        Column(
            modifier = Modifier.width(34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (station.completed) stationTint else Color.Transparent)
                    .border(
                        width = 2.dp,
                        color = if (station.completed) stationTint
                        else stationTint.copy(alpha = if (isNext) 0.9f else 0.35f),
                        shape = CircleShape,
                    )
                    .clickable { onToggleComplete() },
                contentAlignment = Alignment.Center,
            ) {
                if (station.completed) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.bd_mark_not_done),
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(if (isNext) 96.dp else 78.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    stationTint.copy(alpha = if (station.completed) 0.55f else 0.22f),
                                    stationTint.copy(alpha = 0.12f),
                                ),
                            ),
                        ),
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // station card
        Surface(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 10.dp),
            shape = RoundedCornerShape(16.dp),
            color = when {
                station.completed -> palette.cardBg.copy(alpha = 0.55f)
                isNext -> lerp(palette.cardBg, stationTint, 0.10f)
                else -> palette.cardBg
            },
            // only the active station gets an outline. ringing every card would make the list read as
            // uniformly urgent, which is the opposite of what someone mid-task needs
            border = if (isNext) androidx.compose.foundation.BorderStroke(
                1.5.dp,
                stationTint.copy(alpha = 0.45f),
            ) else null,
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                AnimatedVisibility(
                    visible = isNext,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(120)),
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.home_next_up_caps),
                            color = stationTint,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        )
                        Spacer(Modifier.height(5.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = station.title,
                        onValueChange = onRename,
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(
                            color = if (station.completed) palette.muted else palette.ink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            textDecoration = if (station.completed)
                                androidx.compose.ui.text.style.TextDecoration.LineThrough
                            else null,
                        ),
                        cursorBrush = SolidColor(stationTint),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (station.title.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.bd_name_step),
                                    color = palette.muted.copy(alpha = 0.6f),
                                    fontSize = 15.sp,
                                )
                            }
                            inner()
                        },
                    )
                    IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.bd_remove_station),
                            tint = palette.muted.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    MinuteStepper(
                        palette = palette,
                        tint = stationTint,
                        minutes = station.durationMinutes,
                        onAdjust = onAdjustMinutes,
                    )
                    Spacer(Modifier.width(8.dp))
                    KindChip(
                        palette = palette,
                        tint = stationTint,
                        isBreak = isBreak,
                        onClick = onToggleKind,
                    )
                    // hidden once it's done, offering to start a timer for something ticked off is noise
                    if (!station.completed) {
                        Spacer(Modifier.width(8.dp))
                        RunStationChip(tint = stationTint, onClick = onRunThisStation)
                    }
                    Spacer(Modifier.weight(1f))
                    ReorderButton(
                        icon = Icons.Rounded.KeyboardArrowUp,
                        description = stringResource(R.string.bd_move_up),
                        enabled = canMoveUp,
                        tint = palette.muted,
                        onClick = onMoveUp,
                    )
                    ReorderButton(
                        icon = Icons.Rounded.KeyboardArrowDown,
                        description = stringResource(R.string.bd_move_down),
                        enabled = canMoveDown,
                        tint = palette.muted,
                        onClick = onMoveDown,
                    )
                }
            }
        }
    }
}

@Composable
private fun MinuteStepper(
    palette: CalendarPalette,
    tint: Color,
    minutes: Int,
    onAdjust: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.10f)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { onAdjust(-5) }, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Rounded.Remove,
                contentDescription = stringResource(R.string.bd_5_less),
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = "${minutes}m",
            color = palette.ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(30.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        IconButton(onClick = { onAdjust(5) }, modifier = Modifier.size(28.dp)) {
            Icon(
                Icons.Outlined.Add,
                contentDescription = stringResource(R.string.bd_5_more),
                tint = tint,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

// sends one station straight to the Pomodoro timer, independent of the full plan
@Composable
private fun RunStationChip(tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.16f))
            .clickable { onClick() }
            .padding(start = 7.dp, end = 9.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(3.dp))
        // 'Start', not 'Focus': KindChip sits right beside this and already says Focus or Break for
        // the station's type, and two adjacent chips reading Focus would be unreadable
        Text(
            text = stringResource(R.string.common_start),
            color = tint,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun KindChip(
    palette: CalendarPalette,
    tint: Color,
    isBreak: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = 0.10f))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isBreak) {
            Icon(
                Icons.Outlined.Coffee,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = if (isBreak) stringResource(R.string.bd_break) else stringResource(R.string.home_focus_widget),
            color = if (isBreak) tint else palette.ink,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun ReorderButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(28.dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = tint.copy(alpha = if (enabled) 0.7f else 0.2f),
            modifier = Modifier.size(17.dp),
        )
    }
}

// footer actions

@Composable
private fun PlanActions(
    palette: CalendarPalette,
    accent: Color,
    canRunInPomodoro: Boolean,
    saveState: PlanSaveState,
    onAddStation: () -> Unit,
    onClearPlan: () -> Unit,
    onSavePlan: () -> Unit,
    onStartFocus: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (canRunInPomodoro) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStartFocus,
                shape = RoundedCornerShape(16.dp),
                color = accent,
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = stringResource(R.string.bd_start_focus),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        SavePlanButton(accent = accent, saveState = saveState, onClick = onSavePlan)
        Spacer(Modifier.height(6.dp))
        Text(
            // stated outright rather than implied. someone who sees a Save button will otherwise assume
            // the opposite, that leaving without pressing it loses work
            text = stringResource(R.string.bd_autosave),
            color = palette.muted,
            fontSize = 11.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedAction(
                palette = palette,
                icon = Icons.Outlined.Add,
                label = stringResource(R.string.bd_add_station),
                onClick = onAddStation,
                modifier = Modifier.weight(1f),
            )
            OutlinedAction(
                palette = palette,
                icon = Icons.Rounded.Close,
                label = stringResource(R.string.bd_clear_plan),
                onClick = onClearPlan,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// explicit save. always tappable: pressing it when everything is already stored is harmless,
// and a greyed-out Save reads as 'something is wrong' rather than 'nothing to do'
@Composable
private fun SavePlanButton(
    accent: Color,
    saveState: PlanSaveState,
    onClick: () -> Unit,
) {
    val confirmed = saveState == PlanSaveState.JUST_SAVED
    val containerColor by animateColorAsState(
        targetValue = if (confirmed) accent.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(220),
        label = "save_button_bg",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            accent.copy(alpha = if (confirmed) 0.45f else 0.30f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 13.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = when (saveState) {
                    PlanSaveState.SAVING -> stringResource(R.string.common_saving)
                    PlanSaveState.JUST_SAVED -> stringResource(R.string.bd_plan_saved)
                    else -> stringResource(R.string.bd_save_plan)
                },
                color = accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun OutlinedAction(
    palette: CalendarPalette,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        border = androidx.compose.foundation.BorderStroke(1.dp, palette.muted.copy(alpha = 0.25f)),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = palette.muted, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(text = label, color = palette.ink, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}
