package com.muradgalayev.brainbuddy.ui.ai.offline

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.domain.ai.offline.OfflineAction
import com.muradgalayev.brainbuddy.domain.ai.offline.OfflineActionCatalog
import com.muradgalayev.brainbuddy.domain.ai.offline.OfflineActionIcon
import com.muradgalayev.brainbuddy.domain.ai.offline.OfflineActionResult
import com.muradgalayev.brainbuddy.domain.ai.offline.OfflineOption
import com.muradgalayev.brainbuddy.domain.ai.offline.OfflineSlot
import com.muradgalayev.brainbuddy.domain.ai.offline.OfflineSlotOptions
import com.muradgalayev.brainbuddy.ui.theme.MyndoraTheme
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// the assistant with no connection: a short list of things it can still do, each one a few
// taps from done. deliberately not a chat window with the input greyed out. offline the useful
// question isn't 'what do you want to say' but 'which of these do you want', and for an ADHD
// app a closed list of concrete options is easier to act on than a blank prompt even when the
// network is fine.
// the layout follows that: the two or three likeliest actions get big two-column cards you can
// hit without aiming, everything else collapses into quiet rows, and a picked action shows a
// running summary of what confirming will do, so the decision you already made never has to
// be held in your head
@Composable
fun OfflineAssistantPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OfflineAssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()

    OfflineAssistantContent(
        state = state,
        isOnline = isOnline,
        onDismiss = onDismiss,
        onBack = viewModel::back,
        onSelect = viewModel::select,
        onValue = viewModel::setValue,
        onConfirm = viewModel::confirm,
        onDone = viewModel::done,
        modifier = modifier,
    )
}

// stateless body, everything the panel draws is driven purely by state
@Composable
private fun OfflineAssistantContent(
    state: OfflineAssistantUiState,
    isOnline: Boolean,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onSelect: (OfflineAction) -> Unit,
    onValue: (String, String) -> Unit,
    onConfirm: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        shape = RoundedCornerShape(30.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 20.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            PanelHeader(
                selected = state.selected,
                isOnline = isOnline,
                onBack = onBack,
                onDismiss = onDismiss,
            )

            Spacer(Modifier.height(14.dp))

            // forward slides left, back slides right. cheap, but it's what makes the panel feel like one
            // place you're moving around in rather than three screens swapping under you
            val screen = when {
                state.result != null -> Screen.Result
                state.selected == null -> Screen.Browse
                else -> Screen.Action
            }
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    val shift = if (forward) 1 else -1
                    (slideInHorizontally(tween(220)) { (it / 6) * shift } + fadeIn(tween(180)))
                        .togetherWith(
                            slideOutHorizontally(tween(180)) { (it / 6) * -shift } +
                                fadeOut(tween(130))
                        )
                },
                label = "offline_panel_body",
            ) { target ->
                when (target) {
                    Screen.Result -> state.result?.let {
                        ResultBody(result = it, onDone = onDone, onClose = onDismiss)
                    }

                    Screen.Browse -> BrowseBody(
                        suggestions = state.suggestions,
                        browse = state.browse,
                        onSelect = onSelect,
                    )

                    Screen.Action -> state.selected?.let {
                        ActionBody(
                            action = it,
                            state = state,
                            onValue = onValue,
                            onConfirm = onConfirm,
                        )
                    }
                }
            }
        }
    }
}

private enum class Screen { Browse, Action, Result }

// header

@Composable
private fun PanelHeader(
    selected: OfflineAction?,
    isOnline: Boolean,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(
                    if (isOnline) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                )
                .clickable(enabled = selected != null, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (selected != null) Icons.AutoMirrored.Rounded.ArrowBack
                else Icons.Rounded.CloudOff,
                contentDescription = if (selected != null) stringResource(R.string.offline_back_to_actions) else null,
                tint = if (isOnline) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = selected?.title ?: stringResource(R.string.offline_assistant),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(isOnline)
                Spacer(Modifier.width(6.dp))
                Text(
                    // the moment the connection returns, say so: the full assistant is one dismissal away and the
                    // user shouldn't have to guess
                    text = if (isOnline) stringResource(R.string.offline_back_online)
                    else stringResource(R.string.offline_on_device),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// slow pulse while offline, steady green once the connection is back
@Composable
private fun StatusDot(isOnline: Boolean) {
    val transition = rememberInfiniteTransition(label = "offline_status_dot")
    val alpha by transition.animateFloat(
        initialValue = .35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "offline_status_dot_alpha",
    )
    Box(
        Modifier
            .size(6.dp)
            .graphicsLayer { this.alpha = if (isOnline) 1f else alpha }
            .background(
                if (isOnline) OnlineGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                CircleShape,
            )
    )
}

private val OnlineGreen = Color(0xFF5FAF6B)

// browse: suggested cards, then the quiet list

@Composable
private fun BrowseBody(
    suggestions: List<OfflineAction>,
    browse: List<OfflineAction>,
    onSelect: (OfflineAction) -> Unit,
) {
    val rest = remember(suggestions, browse) {
        val suggestedIds = suggestions.map { it.id }.toSet()
        browse.filterNot { it.id in suggestedIds }.groupBy { it.group }
    }
    Column(
        Modifier
            .heightIn(max = 440.dp)
            .verticalScroll(rememberScrollState())
    ) {
        SectionLabel(stringResource(R.string.offline_suggested))
        Spacer(Modifier.height(10.dp))
        // two per row, hand-laid rather than a LazyVerticalGrid: a lazy grid inside a scrolling
        // column is an infinite-height crash waiting to happen
        suggestions.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { action ->
                    SuggestedCard(
                        action = action,
                        onClick = { onSelect(action) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // keeps a lone card at half width rather than letting it stretch and outshout the pair above
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }

        rest.forEach { (group, actions) ->
            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(group.labelRes))
            Spacer(Modifier.height(6.dp))
            actions.forEach { action ->
                CompactActionRow(action = action, onClick = { onSelect(action) })
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
    )
}

@Composable
private fun SuggestedCard(
    action: OfflineAction,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(112.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = .38f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .16f)),
    ) {
        Column(
            Modifier.padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = .7f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = action.icon.vector(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(17.dp),
                )
            }
            Text(
                text = action.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CompactActionRow(action: OfflineAction, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = action.icon.vector(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = action.title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Icon(
                Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .5f),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// one action: fill the slots, confirm

@Composable
private fun ActionBody(
    action: OfflineAction,
    state: OfflineAssistantUiState,
    onValue: (String, String) -> Unit,
    onConfirm: () -> Unit,
) {
    Column {
        Column(
            Modifier
                .heightIn(max = 380.dp)
                .verticalScroll(rememberScrollState())
        ) {
            action.slots.forEachIndexed { index, slot ->
                SlotHeader(
                    index = index + 1,
                    slot = slot,
                    answered = !state.values[slot.key].isNullOrBlank(),
                )
                Spacer(Modifier.height(8.dp))
                when (slot) {
                    is OfflineSlot.Choice -> ChipRow(
                        options = slot.options,
                        selected = state.values[slot.key],
                        onPick = { onValue(slot.key, it) },
                    )

                    is OfflineSlot.DayPick -> ChipRow(
                        options = state.dayOptions,
                        selected = state.values[slot.key],
                        onPick = { onValue(slot.key, it) },
                    )

                    is OfflineSlot.TimePick -> ChipRow(
                        options = state.timeOptions,
                        selected = state.values[slot.key],
                        onPick = { onValue(slot.key, it) },
                    )

                    // the one place a keyboard is unavoidable, nothing can pre-write what you're trying not to forget
                    is OfflineSlot.FreeText -> OutlinedTextField(
                        value = state.values[slot.key].orEmpty(),
                        onValueChange = { onValue(slot.key, it) },
                        placeholder = {
                            Text(slot.hint, style = MaterialTheme.typography.bodyMedium)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(18.dp))
            }
        }

        // running summary of the decision already made, so confirming doesn't require scrolling back
        // up to check what's selected
        AnimatedVisibility(
            visible = state.canConfirm,
            enter = fadeIn(tween(160)) + expandVertically(tween(180)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(140)),
        ) {
            SummaryStrip(action = action, state = state)
        }

        Spacer(Modifier.height(12.dp))

        Button(
            onClick = onConfirm,
            enabled = state.canConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
            ),
        ) {
            if (state.running) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text(stringResource(R.string.offline_do_it), fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SlotHeader(index: Int, slot: OfflineSlot, answered: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(
                    if (answered) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (answered) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(11.dp),
                )
            } else {
                Text(
                    text = "$index",
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = slot.label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        if (slot.optional) {
            Spacer(Modifier.width(6.dp))
            Text(
                text = "optional",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .7f),
            )
        }
    }
}

@Composable
private fun SummaryStrip(action: OfflineAction, state: OfflineAssistantUiState) {
    val details = remember(action, state.values, state.dayOptions, state.timeOptions) {
        action.slots.mapNotNull { slot ->
            val raw = state.values[slot.key]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            when (slot) {
                is OfflineSlot.FreeText -> "“$raw”"
                is OfflineSlot.Choice -> slot.options.firstOrNull { it.value == raw }?.label
                is OfflineSlot.DayPick -> state.dayOptions.firstOrNull { it.value == raw }?.label
                is OfflineSlot.TimePick -> state.timeOptions.firstOrNull { it.value == raw }?.label
            }
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(3.dp)
                .height(26.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(10.dp))
        Column {
            Text(
                text = action.title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = details.joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// horizontally scrolling chips. wrapping to multiple lines would push the confirm button
// off-screen on the time row, and a row you can flick is easier to skim than a block of eight
@Composable
private fun ChipRow(
    options: List<OfflineOption>,
    selected: String?,
    onPick: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isSelected = option.value == selected
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceContainerHigh,
                border = if (isSelected) null
                else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .7f)),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { onPick(option.value) },
                ),
            ) {
                Row(
                    modifier = Modifier.padding(
                        start = if (isSelected) 10.dp else 15.dp,
                        end = 15.dp,
                        top = 9.dp,
                        bottom = 9.dp,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AnimatedVisibility(visible = isSelected, enter = scaleIn(tween(140))) {
                        Row {
                            Icon(
                                Icons.Rounded.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(13.dp),
                            )
                            Spacer(Modifier.width(5.dp))
                        }
                    }
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// result

@Composable
private fun ResultBody(
    result: OfflineActionResult,
    onDone: () -> Unit,
    onClose: () -> Unit,
) {
    val failed = result is OfflineActionResult.Failed
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    if (failed) MaterialTheme.colorScheme.errorContainer
                    else MaterialTheme.colorScheme.primaryContainer
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (failed) Icons.Rounded.ErrorOutline else Icons.Rounded.Check,
                contentDescription = null,
                tint = if (failed) MaterialTheme.colorScheme.onErrorContainer
                else MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = when (result) {
                is OfflineActionResult.Done -> result.message
                is OfflineActionResult.Failed -> result.message
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        if (result is OfflineActionResult.Done && result.queuedForSync) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    text = stringResource(R.string.offline_saved_here),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.offline_do_else), fontWeight = FontWeight.SemiBold)
        }
        TextButton(onClick = onClose) {
            Text(stringResource(R.string.common_close), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// domain icon to the actual glyph. keeps Compose types out of the catalog
private fun OfflineActionIcon.vector(): ImageVector = when (this) {
    OfflineActionIcon.Todo -> Icons.Outlined.TaskAlt
    OfflineActionIcon.Event -> Icons.Outlined.Event
    OfflineActionIcon.Font -> Icons.Outlined.TextFields
    OfflineActionIcon.TextSize -> Icons.Outlined.FormatSize
    OfflineActionIcon.TextSpacing -> Icons.Outlined.FormatLineSpacing
    OfflineActionIcon.Theme -> Icons.Outlined.DarkMode
    OfflineActionIcon.FocusTimer -> Icons.Outlined.Timer
    OfflineActionIcon.Clock -> Icons.Outlined.Schedule
    OfflineActionIcon.Tone -> Icons.Outlined.RecordVoiceOver
    OfflineActionIcon.Note -> Icons.Outlined.EditNote
    OfflineActionIcon.Care -> Icons.Outlined.LocalHospital
}

// previews. stateless body plus hand-built state, so the three screens can be designed in
// Studio without a device, a Hilt graph, or actually turning the wifi off

@Composable
private fun previewState(
    selectedId: String? = null,
    values: Map<String, String> = emptyMap(),
    result: OfflineActionResult? = null,
): OfflineAssistantUiState {
    val catalog = OfflineActionCatalog(androidx.compose.ui.platform.LocalContext.current)
    return OfflineAssistantUiState(
        suggestions = catalog.suggest(careAvailable = true),
        browse = catalog.actions,
        selected = selectedId?.let(catalog::byId),
        values = values,
        dayOptions = OfflineSlotOptions.days("Today", "Tomorrow"),
        timeOptions = OfflineSlotOptions.times(),
        result = result,
    )
}

@Preview(name = "Browse", showBackground = true)
@Preview(name = "Browse · dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OfflineAssistantBrowsePreview() {
    MyndoraTheme(fontSize = FontSize.Medium) {
        OfflineAssistantContent(
            state = previewState(),
            isOnline = false,
            onDismiss = {}, onBack = {}, onSelect = {}, onValue = { _, _ -> },
            onConfirm = {}, onDone = {},
        )
    }
}

@Preview(name = "Action", showBackground = true)
@Preview(name = "Action · dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OfflineAssistantActionPreview() {
    MyndoraTheme(fontSize = FontSize.Medium) {
        OfflineAssistantContent(
            state = previewState(
                selectedId = "capture_todo",
                values = mapOf(
                    "title" to "Call the pharmacy",
                    "date" to java.time.LocalDate.now().toString(),
                    "priority" to "MEDIUM",
                ),
            ),
            isOnline = false,
            onDismiss = {}, onBack = {}, onSelect = {}, onValue = { _, _ -> },
            onConfirm = {}, onDone = {},
        )
    }
}

@Preview(name = "Result", showBackground = true)
@Preview(name = "Result · dark", showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun OfflineAssistantResultPreview() {
    MyndoraTheme(fontSize = FontSize.Medium) {
        OfflineAssistantContent(
            state = previewState(
                selectedId = "capture_todo",
                result = OfflineActionResult.Done(
                    message = "Created todo 'Call the pharmacy' on 2026-08-07.",
                    queuedForSync = true,
                ),
            ),
            isOnline = false,
            onDismiss = {}, onBack = {}, onSelect = {}, onValue = { _, _ -> },
            onConfirm = {}, onDone = {},
        )
    }
}
