package com.muradgalayev.brainbuddy.ui.settings.components

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.data.notifications.PomodoroNudgeFrequency
import com.muradgalayev.brainbuddy.ui.accessibility.speakingWith
import com.muradgalayev.brainbuddy.data.notifications.ReminderKind

private fun kindLabel(kind: ReminderKind): String = when (kind) {
    ReminderKind.DAY_BEFORE -> "Day before"
    ReminderKind.MIN_30_BEFORE -> "30 minutes before"
    ReminderKind.AT_START -> "At start time"
}

private fun kindSubtitle(kind: ReminderKind): String = when (kind) {
    ReminderKind.DAY_BEFORE -> "A heads-up the day ahead"
    ReminderKind.MIN_30_BEFORE -> "Time to wrap up and head over"
    ReminderKind.AT_START -> "A nudge right as it begins"
}

// order the toggles from earliest lead time to latest
private val KIND_ORDER = listOf(
    ReminderKind.DAY_BEFORE,
    ReminderKind.MIN_30_BEFORE,
    ReminderKind.AT_START,
)

private fun formatTimeLabel(hhmm: String): String {
    val parts = hhmm.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 10
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val hour12 = ((hour % 12).takeIf { it != 0 } ?: 12)
    val ampm = if (hour < 12) "AM" else "PM"
    return "%d:%02d %s".format(hour12, minute, ampm)
}

@Composable
fun NotificationsSection(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    todoKinds: Set<ReminderKind>,
    calendarKinds: Set<ReminderKind>,
    dailySummaryEnabled: Boolean,
    nudgeFrequency: PomodoroNudgeFrequency,
    nudgeTime: String,
    breakReminders: Boolean,
    onToggleTodoKind: (ReminderKind, Boolean) -> Unit,
    onToggleCalendarKind: (ReminderKind, Boolean) -> Unit,
    onDailySummaryChange: (Boolean) -> Unit,
    onNudgeFrequencyChange: (PomodoroNudgeFrequency) -> Unit,
    onNudgeTimeChange: (String) -> Unit,
    onBreakRemindersChange: (Boolean) -> Unit,
    embedded: Boolean = false,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "notifications_chevron"
    )

    // compact summary line, so the collapsed card communicates current state
    val activeCount = todoKinds.size + calendarKinds.size +
        (if (dailySummaryEnabled) 1 else 0) +
        (if (nudgeFrequency != PomodoroNudgeFrequency.OFF) 1 else 0) +
        (if (breakReminders) 1 else 0)

    if (embedded) {
        EmbeddedNotificationsContent(
            todoKinds = todoKinds,
            calendarKinds = calendarKinds,
            dailySummaryEnabled = dailySummaryEnabled,
            nudgeFrequency = nudgeFrequency,
            nudgeTime = nudgeTime,
            breakReminders = breakReminders,
            onToggleTodoKind = onToggleTodoKind,
            onToggleCalendarKind = onToggleCalendarKind,
            onDailySummaryChange = onDailySummaryChange,
            onNudgeFrequencyChange = onNudgeFrequencyChange,
            onNudgeTimeChange = onNudgeTimeChange,
            onBreakRemindersChange = onBreakRemindersChange,
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!embedded) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 0.dp,
            onClick = onToggleExpanded
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .heightIn(min = 60.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Outlined.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Notifications",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.size(2.dp))
                    Text(
                        text = "$activeCount active — reminders, summary & focus",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        }

        AnimatedVisibility(
            visible = expanded || embedded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(24.dp)
                    ),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    GroupHeader("To-do reminders")
                    KIND_ORDER.forEach { kind ->
                        ToggleRow(
                            title = kindLabel(kind),
                            subtitle = kindSubtitle(kind),
                            checked = kind in todoKinds,
                            onCheckedChange = { onToggleTodoKind(kind, it) }
                        )
                    }

                    GroupHeader("Calendar reminders")
                    KIND_ORDER.forEach { kind ->
                        ToggleRow(
                            title = kindLabel(kind),
                            subtitle = kindSubtitle(kind),
                            checked = kind in calendarKinds,
                            onCheckedChange = { onToggleCalendarKind(kind, it) }
                        )
                    }

                    GroupHeader("Daily summary")
                    ToggleRow(
                        title = "Morning rundown",
                        subtitle = "A gentle recap of your day, each morning",
                        checked = dailySummaryEnabled,
                        onCheckedChange = onDailySummaryChange
                    )

                    GroupHeader("Pomodoro")
                    FrequencySelector(
                        selected = nudgeFrequency,
                        onSelect = onNudgeFrequencyChange
                    )
                    AnimatedVisibility(visible = nudgeFrequency != PomodoroNudgeFrequency.OFF) {
                        NudgeTimeRow(
                            timeHhmm = nudgeTime,
                            onTimeChange = onNudgeTimeChange
                        )
                    }
                    ToggleRow(
                        title = "Break reminders",
                        subtitle = "Alert me when a focus session or break ends",
                        checked = breakReminders,
                        onCheckedChange = onBreakRemindersChange
                    )
                }
            }
        }
    }
}

@Composable
private fun EmbeddedNotificationsContent(
    todoKinds: Set<ReminderKind>,
    calendarKinds: Set<ReminderKind>,
    dailySummaryEnabled: Boolean,
    nudgeFrequency: PomodoroNudgeFrequency,
    nudgeTime: String,
    breakReminders: Boolean,
    onToggleTodoKind: (ReminderKind, Boolean) -> Unit,
    onToggleCalendarKind: (ReminderKind, Boolean) -> Unit,
    onDailySummaryChange: (Boolean) -> Unit,
    onNudgeFrequencyChange: (PomodoroNudgeFrequency) -> Unit,
    onNudgeTimeChange: (String) -> Unit,
    onBreakRemindersChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        NotificationSettingsCard(
            title = "To-do reminders",
            subtitle = "Choose when Myndora should nudge you",
        ) {
            KIND_ORDER.forEach { kind ->
                ToggleRow(
                    title = kindLabel(kind),
                    subtitle = kindSubtitle(kind),
                    checked = kind in todoKinds,
                    onCheckedChange = { onToggleTodoKind(kind, it) },
                )
            }
        }

        NotificationSettingsCard(
            title = "Calendar reminders",
            subtitle = "Stay ahead of events without the noise",
        ) {
            KIND_ORDER.forEach { kind ->
                ToggleRow(
                    title = kindLabel(kind),
                    subtitle = kindSubtitle(kind),
                    checked = kind in calendarKinds,
                    onCheckedChange = { onToggleCalendarKind(kind, it) },
                )
            }
        }

        NotificationSettingsCard(
            title = "Daily rhythm",
            subtitle = "A calm overview when your day begins",
        ) {
            ToggleRow(
                title = "Morning rundown",
                subtitle = "A gentle recap of your day, each morning",
                checked = dailySummaryEnabled,
                onCheckedChange = onDailySummaryChange,
            )
        }

        NotificationSettingsCard(
            title = "Focus & Pomodoro",
            subtitle = "Helpful prompts while you work and recharge",
        ) {
            FrequencySelector(
                selected = nudgeFrequency,
                onSelect = onNudgeFrequencyChange,
            )
            AnimatedVisibility(visible = nudgeFrequency != PomodoroNudgeFrequency.OFF) {
                NudgeTimeRow(timeHhmm = nudgeTime, onTimeChange = onNudgeTimeChange)
            }
            ToggleRow(
                title = "Break reminders",
                subtitle = "Tell me when a focus session or break ends",
                checked = breakReminders,
                onCheckedChange = onBreakRemindersChange,
            )
        }
    }
}

@Composable
private fun NotificationSettingsCard(
    title: String,
    subtitle: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "notification_group_chevron",
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .width(4.dp)
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.secondary),
                )
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    modifier = Modifier.size(22.dp).rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun GroupHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        letterSpacing = 1.4.sp,
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.size(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            // reads the state it's moving to, not the one it left, so the sentence matches what the switch
            // now shows
            onCheckedChange = speakingWith({ now: Boolean -> "$title, ${if (now) "on" else "off"}" }, onCheckedChange),
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                checkedTrackColor = MaterialTheme.colorScheme.primary,
                checkedBorderColor = MaterialTheme.colorScheme.primary
            )
        )
    }
}

@Composable
private fun FrequencySelector(
    selected: PomodoroNudgeFrequency,
    onSelect: (PomodoroNudgeFrequency) -> Unit,
) {
    Column {
        Text(
            text = "Focus nudges",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.size(2.dp))
        Text(
            text = "Prompt me to start a Pomodoro session",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.size(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val options = listOf(
                PomodoroNudgeFrequency.OFF to "Off",
                PomodoroNudgeFrequency.ONCE to "Once a day",
                PomodoroNudgeFrequency.TWICE to "Twice",
            )
            options.forEach { (freq, label) ->
                SegmentChip(
                    label = label,
                    selected = selected == freq,
                    onClick = { onSelect(freq) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SegmentChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHighest
    val fg = if (selected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = fg
        )
    }
}

@Composable
private fun NudgeTimeRow(
    timeHhmm: String,
    onTimeChange: (String) -> Unit,
) {
    val context = LocalContext.current
    val parts = timeHhmm.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 10
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable {
                TimePickerDialog(
                    context,
                    { _, h, m -> onTimeChange("%02d:%02d".format(h, m)) },
                    hour, minute, false
                ).show()
            },
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "First nudge at",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = formatTimeLabel(timeHhmm),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
