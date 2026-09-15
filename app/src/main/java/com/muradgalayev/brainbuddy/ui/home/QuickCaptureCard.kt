package com.muradgalayev.brainbuddy.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.sharedcomponents.swipeToSwitch
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// quick capture, the fastest path from 'I must not forget this' to it being written down. it
// earns its place on the home screen rather than living behind the Todo or Calendar tab,
// because the gap between having the thought and losing it is measured in seconds. two taps
// and a line of text is the whole interaction, and the confirmation is on the card before the
// save has left the device
@Composable
fun QuickCaptureCard(
    modifier: Modifier = Modifier,
    viewModel: QuickCaptureViewModel = hiltViewModel(),
) {
    val feedback by viewModel.feedback.collectAsState()
    var sheetMode by remember { mutableStateOf<QuickCaptureMode?>(null) }
    val haptics = LocalHapticFeedback.current
    val accents = MaterialTheme.myndoraAccents

    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "quick_capture_press",
    )

    fun open(mode: QuickCaptureMode) {
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        sheetMode = mode
    }

    Surface(
        modifier = modifier.scale(scale),
        shape = HomeCardShape,
        color = Color.Transparent,
        tonalElevation = 0.dp,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                // diagonal ramp between the two brand accents, then a light source dropped into the top-left
                // corner. one flat gradient reads as a swatch, the second layer makes it read as a surface
                .background(
                    Brush.linearGradient(
                        listOf(accents.accent, accents.accentEnd),
                        start = Offset.Zero,
                        end = Offset.Infinite,
                    )
                )
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { open(QuickCaptureMode.Task) },
                ),
        ) {
            CaptureGlow()
            Column(
                Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                PulsingAdd()
                Column {
                    Text(
                        stringResource(R.string.widget_quick_add),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                    )
                    Text(
                        stringResource(R.string.qc_catch_it),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = .82f),
                        maxLines = 2,
                        lineHeight = 15.sp,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CaptureChip(Icons.Rounded.TaskAlt, stringResource(R.string.qc_task)) { open(QuickCaptureMode.Task) }
                    CaptureChip(Icons.Rounded.CalendarMonth, stringResource(R.string.qc_event)) { open(QuickCaptureMode.Event) }
                }
            }
            // the glass edge, drawn last so it sits above the wash and the content
            Box(
                Modifier
                    .fillMaxSize()
                    .border(1.dp, Color.White.copy(alpha = .22f), HomeCardShape)
            )

            // held past the clear so the words don't vanish a frame before the fade does
            val lastFeedback = remember { mutableStateOf<QuickCaptureFeedback?>(null) }
            if (feedback != null) lastFeedback.value = feedback

            AnimatedVisibility(
                visible = feedback != null,
                enter = fadeIn(tween(140)) + scaleIn(tween(220, easing = FastOutSlowInEasing), initialScale = .92f),
                exit = fadeOut(tween(180)),
            ) {
                CaptureConfirmation(
                    feedback = lastFeedback.value,
                    visible = feedback != null,
                    onClick = viewModel::dismissFeedback,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    sheetMode?.let { mode ->
        QuickCaptureSheet(
            initialMode = mode,
            onSubmit = { chosenMode, text, whenChoice ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.capture(chosenMode, text, whenChoice)
            },
            onDismiss = { sheetMode = null },
        )
    }
}

// light, not decoration. a broad radial from the top-left plus a soft bloom bottom-right, both
// falling off to nothing: depth without another image to decode
@Composable
private fun CaptureGlow() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = .26f), Color.Transparent),
                center = Offset(size.width * .12f, -size.height * .1f),
                radius = size.maxDimension * .95f,
            )
        )
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = .13f), Color.Transparent),
                center = Offset(size.width * .92f, size.height * .88f),
                radius = size.minDimension * .62f,
            ),
            radius = size.minDimension * .62f,
            center = Offset(size.width * .92f, size.height * .88f),
        )
    }
}

// a slow halo, once every couple of seconds. fast enough to say 'press me', slow and
// low-contrast enough that it doesn't become one more thing pulling at your attention. under
// reduce motion the halo is dropped and the button just sits there, and it can't be slowed to
// nothing instead: the pulse restarts rather than reverses, so a zero-duration version has no
// still frame to rest on
@Composable
private fun PulsingAdd() {
    val pulse: State<Float>? = if (animationsOn()) {
        val transition = rememberInfiniteTransition(label = "quick_capture_pulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Restart),
            label = "quick_capture_halo",
        )
    } else {
        null
    }
    Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
        if (pulse != null) {
            Canvas(Modifier.fillMaxSize()) {
                val p = pulse.value
                val radius = size.minDimension / 2f * (0.72f + p * 0.42f)
                drawCircle(Color.White.copy(alpha = .30f * (1f - p)), radius = radius)
            }
        }
        Box(
            Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = .22f))
                .border(1.dp, Color.White.copy(alpha = .32f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun CaptureChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(HomePillShape)
            .background(Color.White.copy(alpha = .18f))
            .border(1.dp, Color.White.copy(alpha = .26f), HomePillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(13.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun CaptureConfirmation(
    feedback: QuickCaptureFeedback?,
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = MaterialTheme.myndoraAccents
    val error = feedback?.isError == true
    val tick by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.5f),
        label = "quick_capture_tick",
    )
    Column(
        modifier
            .background(
                Brush.linearGradient(
                    if (error) listOf(MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.error)
                    else listOf(accents.accentEnd, accents.accent)
                )
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            if (error) Icons.Rounded.ErrorOutline else Icons.Rounded.CheckCircle,
            null,
            tint = Color.White,
            modifier = Modifier.size(40.dp).scale(tick),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            feedback?.message.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
        )
        Text(
            feedback?.detail.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = .85f),
            maxLines = 1,
        )
    }
}

// the capture sheet. opens with the keyboard already up and the field already focused, so the
// first thing you can do is type, with no aiming at a text box first
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QuickCaptureSheet(
    initialMode: QuickCaptureMode,
    onSubmit: (QuickCaptureMode, String, QuickWhen?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val accents = MaterialTheme.myndoraAccents

    var mode by remember { mutableStateOf(initialMode) }
    var text by remember { mutableStateOf("") }
    var whenChoice by remember { mutableStateOf<QuickWhen?>(null) }

    val parsed = remember(text) { parseQuickCapture(text) }
    val hint = if (whenChoice == null) rememberWhenHint(parsed) else null

    fun close() {
        keyboard?.hide()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() }
    }

    fun submit() {
        if (text.isBlank()) return
        onSubmit(mode, text, whenChoice)
        close()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = null,
    ) {
        // inside the sheet, not above it: the sheet is its own window with its own composition, and
        // requestFocus throws if the field's node isn't attached yet. from here it always is.
        // the wait lets the sheet finish sliding before the IME arrives, otherwise the Task/Event
        // switch spends half a second behind the keyboard. the timeout is the safety net
        LaunchedEffect(Unit) {
            withTimeoutOrNull(700) {
                snapshotFlow { sheetState.currentValue }.first { it == SheetValue.Expanded }
            }
            delay(60)
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        }
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(top = 14.dp, bottom = 24.dp),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .25f), HomePillShape)
            )
            Spacer(Modifier.height(18.dp))

            ModeSwitch(mode = mode, onModeChange = { mode = it })
            Spacer(Modifier.height(14.dp))

            Surface(
                modifier = Modifier.border(1.dp, homeCardBorder(), HomeInnerShape),
                shape = HomeInnerShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .55f),
                tonalElevation = 0.dp,
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (mode == QuickCaptureMode.Task) Icons.Rounded.TaskAlt else Icons.Rounded.CalendarMonth,
                        null,
                        tint = accents.accent,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    BasicTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(accents.accent),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        decorationBox = { field ->
                            if (text.isEmpty()) {
                                Text(
                                    if (mode == QuickCaptureMode.Task) stringResource(R.string.qc_placeholder_task)
                                    else stringResource(R.string.qc_placeholder_event),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            field()
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = hint != null,
                enter = fadeIn(tween(160)) + expandVertically(tween(200)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(160)),
            ) {
                Row(
                    Modifier.padding(top = 10.dp, start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Rounded.Schedule, null, tint = accents.accent, modifier = Modifier.size(14.dp))
                    Text(
                        hint.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = accents.accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.qc_picked_up),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickWhen.entries.forEach { option ->
                    WhenChip(
                        label = stringResource(option.labelRes),
                        selected = whenChoice == option,
                        onClick = { whenChoice = if (whenChoice == option) null else option },
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            SubmitButton(
                label = if (mode == QuickCaptureMode.Task) stringResource(R.string.qc_add_task) else stringResource(R.string.qc_add_event),
                enabled = text.isNotBlank(),
                onClick = ::submit,
            )
        }
    }
}

// two segments with a sliding pill behind them, so switching reads as one control. swipeable
// as well as tappable: drag right for Event, left for Task. the pill is already animated
// between the two, so a swipe just moves it and the gesture ends up looking identical to the
// tap, which is what stops the swipe feeling like a separate hidden feature
@Composable
private fun ModeSwitch(mode: QuickCaptureMode, onModeChange: (QuickCaptureMode) -> Unit) {
    val accents = MaterialTheme.myndoraAccents
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .clip(HomePillShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .swipeToSwitch(
                onSwipeLeft = { onModeChange(QuickCaptureMode.Task) },
                onSwipeRight = { onModeChange(QuickCaptureMode.Event) },
            )
            .padding(4.dp),
    ) {
        val segmentWidth = maxWidth / 2
        val offset by animateDpAsState(
            targetValue = if (mode == QuickCaptureMode.Task) 0.dp else segmentWidth,
            animationSpec = spring(dampingRatio = 0.78f),
            label = "quick_capture_mode",
        )
        Box(
            Modifier
                .offset(x = offset)
                .width(segmentWidth)
                .height(40.dp)
                .clip(HomePillShape)
                .background(Brush.linearGradient(listOf(accents.accent, accents.accentEnd)))
        )
        Row(Modifier.fillMaxWidth()) {
            QuickCaptureMode.entries.forEach { option ->
                val selected = option == mode
                val color by animateColorAsState(
                    if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "quick_capture_mode_label",
                )
                Box(
                    Modifier
                        .width(segmentWidth)
                        .height(40.dp)
                        .clip(HomePillShape)
                        .clickable { onModeChange(option) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (option == QuickCaptureMode.Task) stringResource(R.string.qc_task) else stringResource(R.string.qc_event),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = color,
                    )
                }
            }
        }
    }
}

@Composable
private fun WhenChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val accents = MaterialTheme.myndoraAccents
    val background by animateColorAsState(
        if (selected) accents.accent else MaterialTheme.colorScheme.surfaceContainerHighest,
        label = "quick_when_bg",
    )
    val content by animateColorAsState(
        if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "quick_when_fg",
    )
    val outline by animateColorAsState(
        if (selected) Color.Transparent else homeCardBorder(),
        label = "quick_when_border",
    )
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium.tabular(),
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = content,
        maxLines = 1,
        modifier = Modifier
            .clip(HomePillShape)
            .background(background)
            .border(1.dp, outline, HomePillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    )
}

@Composable
private fun SubmitButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val accents = MaterialTheme.myndoraAccents
    val alpha by animateFloatAsState(if (enabled) 1f else .4f, label = "quick_capture_submit")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) .97f else 1f,
        animationSpec = spring(dampingRatio = .55f),
        label = "quick_capture_submit_press",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .height(54.dp)
            .clip(HomeInnerShape)
            .background(Brush.linearGradient(listOf(accents.accent.copy(alpha = alpha), accents.accentEnd.copy(alpha = alpha))))
            .clickable(interaction, indication = null, enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Check, null, tint = Color.White.copy(alpha = alpha), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = alpha),
        )
    }
}

// 'Tomorrow, 15:00', or null when the text didn't say anything about when
@Composable
private fun rememberWhenHint(draft: QuickCaptureDraft): String? {
    val todayLabel = stringResource(R.string.common_today)
    val tomorrowLabel = stringResource(R.string.common_tomorrow)
    return remember(draft, todayLabel) { whenHint(draft, todayLabel, tomorrowLabel) }
}

private fun whenHint(draft: QuickCaptureDraft, todayLabel: String, tomorrowLabel: String): String? {
    if (!draft.hasWhen) return null
    val today = LocalDate.now()
    val day = when (draft.date) {
        null -> null
        today -> todayLabel
        today.plusDays(1) -> tomorrowLabel
        else -> draft.date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
    }
    val clock = draft.time?.format(DateTimeFormatter.ofPattern("HH:mm"))
    return listOfNotNull(day, clock).joinToString(" · ")
}
