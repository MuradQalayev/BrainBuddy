package com.muradgalayev.brainbuddy.ui.modes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.border
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoMode
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.domain.model.AppMode
import com.muradgalayev.brainbuddy.domain.model.ModeSelection
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.settings.components.SettingsDetailHero
import com.muradgalayev.brainbuddy.ui.settings.components.SettingsSectionLabel
import com.muradgalayev.brainbuddy.ui.settings.components.SettingsSubScaffold
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// pick a mode, turn modes off, or open the editor. reached from Settings, so it wears the same
// clothes as every other settings page: the shared sub-page scaffold, the gradient hero,
// section labels and bordered cards. what stays specific to modes is the live state, since the
// hero shows what's running right now and each row carries its own accent colour
@Composable
fun ModesScreen(
    onBack: () -> Unit,
    onEditMode: (String?) -> Unit,
    viewModel: ModesViewModel = hiltViewModel(),
) {
    val modes by viewModel.modes.collectAsState()
    val activeMode by viewModel.activeMode.collectAsState()
    val selection by viewModel.selection.collectAsState()
    val motionDuration = if (animationsOn()) 360 else 0

    SettingsSubScaffold(title = stringResource(R.string.settings_modes), onBack = onBack) {
        ActiveModeHero(
            activeMode = activeMode,
            selection = selection,
            motionDuration = motionDuration,
        )

        SettingsSectionLabel(
            title = stringResource(R.string.modes_choose_active),
            subtitle = stringResource(R.string.modes_choose_active_sub),
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AutomaticModeRow(
                selected = selection == ModeSelection.Automatic,
                hasScheduledModes = modes.any(AppMode::hasActiveSchedule),
                motionDuration = motionDuration,
                onClick = viewModel::useAutomaticModes,
            )

            NoModeRow(
                selected = selection == ModeSelection.NoMode,
                motionDuration = motionDuration,
                onClick = { viewModel.selectMode(null) },
            )
        }

        SettingsSectionLabel(
            title = stringResource(R.string.modes_your_modes),
            subtitle = if (modes.size == 1) {
                stringResource(R.string.modes_one_saved)
            } else {
                stringResource(R.string.modes_n_saved, modes.size)
            },
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            modes.forEach { mode ->
                key(mode.id) {
                    ModeRow(
                        mode = mode,
                        isSelected = (selection as? ModeSelection.Manual)?.modeId == mode.id,
                        isRunning = activeMode?.id == mode.id,
                        motionDuration = motionDuration,
                        onSelect = { viewModel.selectMode(mode.id) },
                        onEdit = { onEditMode(mode.id) },
                    )
                }
            }

            Button(
                onClick = { onEditMode(null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.home_create_mode), fontWeight = FontWeight.Bold)
            }
        }
    }
}

// the settings hero, told in the present tense. same gradient card that opens every other
// settings page, only its icon, accent and wording follow whatever is running, so the page
// answers 'what is on right now?' before offering anything to change
@Composable
private fun ActiveModeHero(
    activeMode: AppMode?,
    selection: ModeSelection,
    motionDuration: Int,
) {
    val colors = MaterialTheme.colorScheme
    val automaticIdle = selection == ModeSelection.Automatic && activeMode == null
    val accent by animateColorAsState(
        targetValue = activeMode?.let { modeAccentColor(it.accent) }
            ?: if (automaticIdle) colors.primary else colors.secondary,
        animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
        label = "modes_hero_accent",
    )
    val timing = activeMode?.let { modeTiming(it) }

    SettingsDetailHero(
        icon = when {
            activeMode != null -> modeIcon(activeMode.icon)
            automaticIdle -> Icons.Rounded.AutoMode
            else -> Icons.Rounded.Tune
        },
        title = when {
            activeMode != null -> activeMode.name
            automaticIdle -> stringResource(R.string.modes_between_schedules)
            else -> stringResource(R.string.home_no_mode)
        },
        description = when {
            timing != null -> stringResource(R.string.modes_active_now, timing.detail)
            automaticIdle -> stringResource(R.string.modes_auto_idle)
            else -> stringResource(R.string.modes_off)
        },
        accent = accent,
    )
}

@Composable
private fun AutomaticModeRow(
    selected: Boolean,
    hasScheduledModes: Boolean,
    motionDuration: Int,
    onClick: () -> Unit,
) {
    SystemModeRow(
        title = stringResource(R.string.modes_automatic),
        description = if (hasScheduledModes) {
            stringResource(R.string.modes_follow_schedules)
        } else {
            stringResource(R.string.modes_ready_schedule)
        },
        accessibilityDescription = if (hasScheduledModes) {
            stringResource(R.string.modes_auto_cd)
        } else {
            stringResource(R.string.modes_auto_none_cd)
        },
        icon = Icons.Rounded.AutoMode,
        accent = MaterialTheme.colorScheme.primary,
        selected = selected,
        selectedLabel = stringResource(R.string.common_on),
        motionDuration = motionDuration,
        onClick = onClick,
    )
}

@Composable
private fun NoModeRow(
    selected: Boolean,
    motionDuration: Int,
    onClick: () -> Unit,
) {
    SystemModeRow(
        title = stringResource(R.string.home_no_mode),
        description = stringResource(R.string.modes_no_mode_desc),
        accessibilityDescription = stringResource(R.string.modes_no_mode_cd),
        icon = Icons.Rounded.Tune,
        accent = MaterialTheme.colorScheme.secondary,
        selected = selected,
        selectedLabel = stringResource(R.string.ai_active),
        motionDuration = motionDuration,
        onClick = onClick,
    )
}

@Composable
private fun SystemModeRow(
    title: String,
    description: String,
    accessibilityDescription: String,
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    selectedLabel: String,
    motionDuration: Int,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val selectionState = if (selected) stringResource(R.string.common_selected) else stringResource(R.string.common_not_selected)
    val containerColor by animateColorAsState(
        targetValue = if (selected) {
            lerp(colors.surfaceContainer, accent, 0.12f)
        } else {
            colors.surfaceContainer
        },
        animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
        label = "system_mode_container",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            accent.copy(alpha = 0.28f)
        } else {
            colors.outlineVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(motionDuration),
        label = "system_mode_border",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = onClick,
                    role = Role.RadioButton,
                )
                .semantics(mergeDescendants = true) {
                    contentDescription = accessibilityDescription
                    stateDescription = selectionState
                }
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeIconTile(icon = icon, accent = accent, selected = selected, motionDuration = motionDuration)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 1,
                    )
                    SelectionBadge(
                        visible = selected,
                        accent = accent,
                        label = selectedLabel,
                        motionDuration = motionDuration,
                    )
                }
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

@Composable
private fun ModeRow(
    mode: AppMode,
    isSelected: Boolean,
    isRunning: Boolean,
    motionDuration: Int,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val accent = modeAccentColor(mode.accent)
    val timing = modeTiming(mode)
    val rowDescription = stringResource(R.string.modes_mode_cd, mode.name, timing.badge, timing.detail)
    val rowState = when {
        isSelected -> stringResource(R.string.modes_selected_active)
        isRunning -> stringResource(R.string.modes_running_auto)
        else -> stringResource(R.string.modes_not_active)
    }
    val containerColor by animateColorAsState(
        targetValue = if (isRunning) {
            lerp(colors.surfaceContainer, accent, 0.12f)
        } else {
            colors.surfaceContainer
        },
        animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
        label = "${mode.id}_container",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isRunning) {
            accent.copy(alpha = 0.28f)
        } else {
            colors.outlineVariant.copy(alpha = 0.5f)
        },
        animationSpec = tween(motionDuration),
        label = "${mode.id}_border",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        color = containerColor,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .selectable(
                        selected = isSelected,
                        onClick = onSelect,
                        role = Role.RadioButton,
                    )
                    .semantics(mergeDescendants = true) {
                        contentDescription = rowDescription
                        stateDescription = rowState
                    }
                    .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp)
                    .heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeIconTile(
                    icon = modeIcon(mode.icon),
                    accent = accent,
                    selected = isRunning,
                    motionDuration = motionDuration,
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = mode.name,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        SelectionBadge(
                            visible = isRunning,
                            accent = accent,
                            label = if (isSelected) stringResource(R.string.ai_active) else stringResource(R.string.modes_running),
                            motionDuration = motionDuration,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TimingBadge(timing = timing, accent = accent)
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = timing.detail,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = stringResource(R.string.modes_customize_cd, mode.name),
                    tint = colors.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModeIconTile(
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    motionDuration: Int,
) {
    val backgroundColor by animateColorAsState(
        targetValue = accent.copy(alpha = if (selected) 0.18f else 0.11f),
        animationSpec = tween(motionDuration),
        label = "mode_icon_background",
    )

    Surface(
        modifier = Modifier.size(42.dp),
        shape = RoundedCornerShape(14.dp),
        color = backgroundColor,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SelectionBadge(
    visible: Boolean,
    accent: Color,
    label: String = stringResource(R.string.ai_active),
    motionDuration: Int,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(motionDuration)) +
            expandHorizontally(tween(motionDuration, easing = FastOutSlowInEasing)),
        exit = fadeOut(tween(if (motionDuration == 0) 0 else 140)) +
            shrinkHorizontally(tween(if (motionDuration == 0) 0 else 220)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = accent.copy(alpha = 0.17f),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                    )
                }
            }
        }
    }
}

@Composable
private fun TimingBadge(timing: ModeTiming, accent: Color) {
    val colors = MaterialTheme.colorScheme
    val badgeColor = when (timing.kind) {
        TimingKind.Scheduled -> accent.copy(alpha = 0.14f)
        TimingKind.Manual -> colors.surfaceContainerHighest
        TimingKind.ScheduleOff -> colors.surfaceVariant
    }
    val contentColor = when (timing.kind) {
        TimingKind.Scheduled -> accent
        TimingKind.Manual,
        TimingKind.ScheduleOff,
        -> colors.onSurfaceVariant
    }

    Surface(
        shape = CircleShape,
        color = badgeColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (timing.kind == TimingKind.Scheduled) {
                    Icons.Rounded.Schedule
                } else {
                    Icons.Rounded.TouchApp
                },
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(11.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = timing.badge,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor,
            )
        }
    }
}

private enum class TimingKind { Scheduled, Manual, ScheduleOff }

private data class ModeTiming(
    val badge: String,
    val detail: String,
    val kind: TimingKind,
)

@Composable
private fun modeTiming(mode: AppMode): ModeTiming {
    val schedule = mode.schedule
    return when {
        schedule == null -> ModeTiming(
            badge = stringResource(R.string.modes_manual),
            detail = stringResource(R.string.modes_starts_when),
            kind = TimingKind.Manual,
        )

        mode.hasActiveSchedule() -> ModeTiming(
            badge = stringResource(R.string.modes_scheduled),
            detail = scheduleSummary(
                days = schedule.days,
                startMinute = schedule.startMinute,
                endMinute = schedule.endMinute,
                enabled = true,
            ) ?: stringResource(R.string.modes_auto_schedule),
            kind = TimingKind.Scheduled,
        )

        else -> ModeTiming(
            badge = stringResource(R.string.modes_schedule_off),
            detail = stringResource(R.string.modes_starts_when),
            kind = TimingKind.ScheduleOff,
        )
    }
}

private fun AppMode.hasActiveSchedule(): Boolean =
    schedule?.let { it.enabled && it.days.isNotEmpty() } == true
