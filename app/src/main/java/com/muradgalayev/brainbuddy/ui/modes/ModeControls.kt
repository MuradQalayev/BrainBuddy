package com.muradgalayev.brainbuddy.ui.modes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// one setting a mode may override, as three choices rather than a switch. a plain on/off switch
// can't say the thing that matters most here: don't touch this. every override is optional,
// and the difference between 'this mode turns simplified workspace off' and 'this mode has no
// opinion about it' is the difference between a mode that quietly undoes your settings and one
// that doesn't. so the third state is first, and it's the default
@Composable
fun OverrideSwitchRow(
    title: String,
    description: String,
    value: Boolean?,
    accent: Color,
    onChange: (Boolean?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(9.dp))
        TriStateSelector(value = value, accent = accent, onChange = onChange)
    }
}

@Composable
private fun TriStateSelector(
    value: Boolean?,
    accent: Color,
    onChange: (Boolean?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .6f))
            .selectableGroup()
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        SegmentButton(stringResource(R.string.mode_dont_change), value == null, accent, Modifier.weight(1.2f)) { onChange(null) }
        SegmentButton(stringResource(R.string.common_on), value == true, accent, Modifier.weight(1f)) { onChange(true) }
        SegmentButton(stringResource(R.string.common_off), value == false, accent, Modifier.weight(1f)) { onChange(false) }
    }
}

@Composable
private fun SegmentButton(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val motionDuration = if (animationsOn()) 180 else 0
    val background by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = .18f) else Color.Transparent,
        animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
        label = "mode_segment_background",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
        label = "mode_segment_content",
    )
    Box(
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .selectable(
                selected = selected,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(
                visible = selected,
                enter = fadeIn(tween(motionDuration)) +
                    expandHorizontally(tween(motionDuration, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(motionDuration)) +
                    shrinkHorizontally(tween(motionDuration, easing = FastOutSlowInEasing)),
            ) {
                Row {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = contentColor,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
            )
        }
    }
}

// a set-valued override: which notifications, tiles or tabs a mode allows. same three-state
// idea, expressed as don't-change versus a list you tick. the list only appears once the mode
// has taken an opinion, so an unconfigured mode isn't a wall of meaningless checkboxes
@Composable
fun <T> OverrideSetRow(
    title: String,
    description: String,
    // null means the mode has no opinion
    selected: Set<T>?,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    accent: Color,
    // warns rather than blocks, for choices that are legal but usually a mistake
    warnWhenMissing: @Composable (T) -> String? = { null },
    onChange: (Set<T>?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motionDuration = if (animationsOn()) 220 else 0
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(9.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceContainerHighest.copy(alpha = .6f))
                .selectableGroup()
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            SegmentButton(stringResource(R.string.mode_dont_change), selected == null, accent, Modifier.weight(1f)) {
                onChange(null)
            }
            SegmentButton(stringResource(R.string.mode_choose), selected != null, accent, Modifier.weight(1f)) {
                if (selected == null) onChange(options.toSet())
            }
        }

        AnimatedVisibility(
            visible = selected != null,
            enter = fadeIn(tween(motionDuration)) +
                expandVertically(
                    animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
                    expandFrom = Alignment.Top,
                ),
            exit = fadeOut(tween(motionDuration)) +
                shrinkVertically(
                    animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
                    shrinkTowards = Alignment.Top,
                ),
        ) {
            val currentSelection = selected ?: emptySet()
            Column {
                Spacer(Modifier.height(8.dp))
                options.forEach { option ->
                    val isOn = option in currentSelection
                    val warning = if (selected != null && !isOn) warnWhenMissing(option) else null
                    val checkBackground by animateColorAsState(
                        targetValue = if (isOn) accent else colors.onSurface.copy(alpha = .08f),
                        animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
                        label = "mode_option_check_background",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .sizeIn(minHeight = 48.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .toggleable(
                                value = isOn,
                                role = Role.Checkbox,
                                onValueChange = { checked ->
                                    onChange(
                                        if (checked) currentSelection + option
                                        else currentSelection - option,
                                    )
                                },
                            )
                            .padding(vertical = 7.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(checkBackground),
                            contentAlignment = Alignment.Center,
                        ) {
                            AnimatedOptionCheck(
                                visible = isOn,
                                motionDuration = motionDuration,
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                optionLabel(option),
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.onSurface,
                            )
                            AnimatedVisibility(
                                visible = warning != null,
                                enter = fadeIn(tween(motionDuration)) +
                                    expandVertically(tween(motionDuration)),
                                exit = fadeOut(tween(motionDuration)) +
                                    shrinkVertically(tween(motionDuration)),
                            ) {
                                if (warning != null) {
                                    Text(
                                        warning,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colors.error,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimatedOptionCheck(
    visible: Boolean,
    motionDuration: Int,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(motionDuration)) + expandHorizontally(tween(motionDuration)),
        exit = fadeOut(tween(motionDuration)) + shrinkHorizontally(tween(motionDuration)),
    ) {
        Icon(
            Icons.Rounded.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(13.dp),
        )
    }
}
