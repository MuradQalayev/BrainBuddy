package com.muradgalayev.brainbuddy.ui.calendar

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.domain.model.SubtaskKind
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtaskEditorSheet(
    palette: CalendarPalette,
    state: SubtaskEditorState,
    onDismiss: () -> Unit,
    onApplyPreset: (SplitPreset) -> Unit,
    onUpdateDrafts: (List<CalendarSubtaskUi>) -> Unit,
    onSave: () -> Unit,
) {
    val sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val configuration = LocalConfiguration.current
    val maxHeight = (configuration.screenHeightDp * 0.92f).dp

    val accent = state.accent
    val totalAllocated = state.drafts.sumOf { it.durationMinutes }
    val overflow = totalAllocated > state.totalMinutes && state.totalMinutes > 0
    val under = totalAllocated < state.totalMinutes && state.totalMinutes > 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = palette.sheetBg,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight)
        ) {
            // ── Drag handle ──
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
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

            // ── Header ──
            EditorHeader(
                palette = palette,
                accent = accent,
                title = state.eventTitle,
                totalMinutes = state.totalMinutes,
                allocated = totalAllocated,
                onClose = onDismiss,
            )

            // ── Smart split presets ──
            SplitPresets(palette = palette, accent = accent, onApplyPreset = onApplyPreset)

            Spacer(Modifier.height(14.dp))

            // ── Drafts ──
            Text(
                text = "Stations",
                color = palette.ink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )

            Box(modifier = Modifier.weight(1f, fill = false)) {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 16.dp,
                        vertical = 4.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.drafts, key = { it.id }) { draft ->
                        SubtaskDraftRow(
                            palette = palette,
                            accent = accent,
                            draft = draft,
                            onChange = { updated ->
                                onUpdateDrafts(state.drafts.map { if (it.id == updated.id) updated else it })
                            },
                            onDelete = {
                                onUpdateDrafts(state.drafts.filterNot { it.id == draft.id })
                            },
                        )
                    }

                    item {
                        AddStepButton(palette = palette, accent = accent) {
                            val nextDuration = if (state.drafts.isEmpty()) {
                                state.totalMinutes.coerceAtLeast(15).coerceAtMost(30)
                            } else 15
                            onUpdateDrafts(
                                state.drafts + CalendarSubtaskUi(
                                    id = UUID.randomUUID().toString(),
                                    title = "New step",
                                    durationMinutes = nextDuration,
                                    completed = false,
                                    kind = SubtaskKind.FOCUS,
                                )
                            )
                        }
                    }
                }
            }

            // ── Footer (totals + save) ──
            EditorFooter(
                palette = palette,
                accent = accent,
                allocated = totalAllocated,
                total = state.totalMinutes,
                overflow = overflow,
                under = under,
                onSave = onSave,
            )
        }
    }
}

@Composable
private fun EditorHeader(
    palette: CalendarPalette,
    accent: Color,
    title: String,
    totalMinutes: Int,
    allocated: Int,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Break it down",
                color = palette.muted,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
            )
            Text(
                text = title,
                color = palette.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (totalMinutes > 0) {
                Text(
                    text = "${allocated}m of ${totalMinutes}m mapped",
                    color = palette.muted,
                    fontSize = 11.sp,
                )
            }
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(palette.cardBg)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = "Close",
                tint = palette.ink,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun SplitPresets(
    palette: CalendarPalette,
    accent: Color,
    onApplyPreset: (SplitPreset) -> Unit,
) {
    Text(
        text = "Smart split",
        color = palette.ink,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.4.sp,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
    )
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { PresetChip(palette, accent, "½", "In two", SplitPreset.HALVES, onApplyPreset) }
        item { PresetChip(palette, accent, "⅓", "In three", SplitPreset.THIRDS, onApplyPreset) }
        item { PresetChip(palette, accent, "¼", "In four", SplitPreset.QUARTERS, onApplyPreset) }
        item { PresetChip(palette, accent, "25/5", "Pomodoro", SplitPreset.POMODORO, onApplyPreset) }
    }
}

@Composable
private fun PresetChip(
    palette: CalendarPalette,
    accent: Color,
    glyph: String,
    label: String,
    preset: SplitPreset,
    onApply: (SplitPreset) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(palette.cardBg, accent, 0.16f),
                        lerp(palette.cardBg, accent, 0.04f),
                    )
                )
            )
            .border(1.dp, accent.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .clickable { onApply(preset) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(accent, lerp(accent, Color.White, 0.20f))
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = glyph,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            color = palette.ink,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SubtaskDraftRow(
    palette: CalendarPalette,
    accent: Color,
    draft: CalendarSubtaskUi,
    onChange: (CalendarSubtaskUi) -> Unit,
    onDelete: () -> Unit,
) {
    val isBreak = draft.kind == SubtaskKind.BREAK
    val stripeColor = if (isBreak) palette.muted else accent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.cardBg)
            .border(1.dp, palette.muted.copy(alpha = 0.10f), RoundedCornerShape(18.dp))
            .height(intrinsicSize = androidx.compose.foundation.layout.IntrinsicSize.Min)
    ) {
        // Linear-style accent stripe — shows kind at a glance
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            stripeColor,
                            lerp(stripeColor, Color.White, 0.25f),
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            // ── Top row: kind chip + title field + delete ──
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Kind toggle as a soft pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(stripeColor.copy(alpha = 0.14f))
                        .clickable {
                            onChange(
                                draft.copy(
                                    kind = if (isBreak) SubtaskKind.FOCUS else SubtaskKind.BREAK
                                )
                            )
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isBreak) {
                        Icon(
                            imageVector = Icons.Outlined.Coffee,
                            contentDescription = null,
                            tint = stripeColor,
                            modifier = Modifier.size(11.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(stripeColor)
                        )
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (isBreak) "BREAK" else "FOCUS",
                        color = stripeColor,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp,
                    )
                }

                Spacer(Modifier.width(10.dp))

                BasicTextField(
                    value = draft.title,
                    onValueChange = { onChange(draft.copy(title = it)) },
                    singleLine = true,
                    cursorBrush = SolidColor(accent),
                    textStyle = TextStyle(
                        color = palette.ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = Modifier.weight(1f),
                )

                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(palette.muted.copy(alpha = 0.08f))
                        .clickable(onClick = onDelete),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Remove",
                        tint = palette.muted,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Bottom row: duration controls — bigger touch targets ──
            DurationStepper(
                palette = palette,
                accent = accent,
                minutes = draft.durationMinutes,
                onChange = { onChange(draft.copy(durationMinutes = it)) },
            )
        }
    }
}

@Composable
private fun DurationStepper(
    palette: CalendarPalette,
    accent: Color,
    minutes: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        accent.copy(alpha = 0.14f),
                        accent.copy(alpha = 0.06f),
                    )
                )
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepperButton(
            icon = Icons.Outlined.Remove,
            tint = accent,
            onClick = { onChange((minutes - 5).coerceAtLeast(5)) },
        )
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom) {
            BasicTextField(
                value = minutes.toString(),
                onValueChange = { v ->
                    val n = v.filter { it.isDigit() }.toIntOrNull()?.coerceIn(1, 600)
                    if (n != null) onChange(n)
                    else if (v.isEmpty()) onChange(1)
                },
                singleLine = true,
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(
                    color = accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    letterSpacing = (-0.5).sp,
                ),
                modifier = Modifier.width(44.dp),
            )
            Text(
                text = "min",
                color = accent.copy(alpha = 0.7f),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp,
                modifier = Modifier.padding(bottom = 4.dp, start = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        StepperButton(
            icon = Icons.Outlined.Add,
            tint = accent,
            onClick = { onChange((minutes + 5).coerceAtMost(600)) },
        )
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(tint, lerp(tint, Color.White, 0.20f))
                )
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp)
        )
    }
}

@Composable
private fun AddStepButton(palette: CalendarPalette, accent: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.5.dp,
                color = accent.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "Add step",
            color = accent,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
        )
    }
}

@Composable
private fun EditorFooter(
    palette: CalendarPalette,
    accent: Color,
    allocated: Int,
    total: Int,
    overflow: Boolean,
    under: Boolean,
    onSave: () -> Unit,
) {
    val hint = when {
        total == 0 -> "Set a duration on the event to balance"
        overflow -> "${allocated - total}m over the block"
        under -> "${total - allocated}m left to plan"
        else -> "Perfectly mapped"
    }
    val hintColor = when {
        overflow -> palette.flagRed
        under -> palette.muted
        else -> accent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.sheetBg)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${allocated}",
                    color = palette.ink,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.6).sp,
                )
                Text(
                    text = "min",
                    color = palette.muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.padding(bottom = 4.dp, start = 3.dp),
                )
            }
            Text(
                text = hint,
                color = hintColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(
                    Brush.linearGradient(
                        listOf(accent, lerp(accent, Color.White, 0.18f))
                    )
                )
                .clickable(onClick = onSave)
                .padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Save plan",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.4.sp,
            )
        }
    }
}