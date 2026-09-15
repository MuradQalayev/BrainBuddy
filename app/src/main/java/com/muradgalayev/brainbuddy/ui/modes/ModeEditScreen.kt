package com.muradgalayev.brainbuddy.ui.modes

import com.muradgalayev.brainbuddy.ui.utils.stateDescription
import com.muradgalayev.brainbuddy.ui.utils.contentDescription
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.domain.model.ModeNotificationKind
import com.muradgalayev.brainbuddy.domain.model.ModeOverrides
import com.muradgalayev.brainbuddy.domain.model.ModeSchedule
import com.muradgalayev.brainbuddy.domain.model.RingerSetting
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.home.HomeWidget
import kotlin.math.roundToInt
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

private const val SECTION_SOUND = "sound"
private const val SECTION_APP = "app"
private const val SECTION_FOCUS = "focus"
private const val SECTION_SCHEDULE = "schedule"
private const val MINUTES_PER_DAY = 24 * 60
private const val QUARTER_HOUR_MINUTES = 15
private const val LAST_START_MINUTE = MINUTES_PER_DAY - QUARTER_HOUR_MINUTES
private const val START_SLIDER_STEPS = LAST_START_MINUTE / QUARTER_HOUR_MINUTES - 1
private const val END_SLIDER_STEPS = MINUTES_PER_DAY / QUARTER_HOUR_MINUTES - 1

// a compact editor: identity stays visible and one settings group opens at a time
@Composable
fun ModeEditScreen(
    modeId: String?,
    onBack: () -> Unit,
    viewModel: ModesViewModel = hiltViewModel(),
) {
    val draft by viewModel.draft.collectAsState()
    val draftLoadError by viewModel.draftLoadError.collectAsState()
    val message by viewModel.message.collectAsState()
    val saving by viewModel.saving.collectAsState()
    val deleting by viewModel.deleting.collectAsState()
    val colors = MaterialTheme.colorScheme
    var confirmDelete by remember { mutableStateOf(false) }
    var expandedSection by rememberSaveable(modeId) { mutableStateOf<String?>(null) }

    LaunchedEffect(modeId) { viewModel.beginEdit(modeId) }

    val mode = draft
    val accent by animateColorAsState(
        targetValue = modeAccentColor(mode?.accent),
        animationSpec = tween(
            durationMillis = if (animationsOn()) 260 else 0,
            easing = FastOutSlowInEasing,
        ),
        label = "mode_editor_accent",
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { viewModel.discardDraft(); onBack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.common_back))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (modeId == null) stringResource(R.string.mode_new) else stringResource(R.string.mode_edit),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            if (mode != null && !mode.isBuiltIn && modeId != null) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.mode_delete),
                        tint = colors.error,
                    )
                }
            }
        }

        if (mode == null) {
            if (draftLoadError == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.common_loading), color = colors.onSurfaceVariant)
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        modifier = Modifier.size(58.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = colors.errorContainer,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = colors.onErrorContainer,
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.mode_unavailable),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = draftLoadError.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { viewModel.discardDraft(); onBack() }) {
                        Text(stringResource(R.string.mode_back_to_modes))
                    }
                }
            }
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ModeIdentityCard(
                name = mode.name,
                iconKey = mode.icon,
                accentKey = mode.accent,
                accent = accent,
                onNameChange = { name -> viewModel.updateDraft { it.copy(name = name) } },
                onIconChange = { icon -> viewModel.updateDraft { it.copy(icon = icon) } },
                onAccentChange = { key -> viewModel.updateDraft { it.copy(accent = key) } },
            )

            Column {
                Text(
                    stringResource(R.string.common_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(R.string.mode_open_section),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }

            ModeSettingsCard(
                icon = Icons.Rounded.NotificationsNone,
                title = stringResource(R.string.mode_sound_notifications),
                summary = changeCountLabel(soundChangeCount(mode.overrides)),
                accent = accent,
                expanded = expandedSection == SECTION_SOUND,
                onToggle = {
                    expandedSection = SECTION_SOUND.takeUnless { expandedSection == SECTION_SOUND }
                },
            ) {
                RingerPicker(
                    value = mode.overrides.ringer,
                    accent = accent,
                    onChange = { ringer ->
                        viewModel.updateDraft {
                            it.copy(overrides = it.overrides.copy(ringer = ringer))
                        }
                    },
                )
                OverrideSwitchRow(
                    title = stringResource(R.string.mode_dnd),
                    description = stringResource(R.string.mode_dnd_desc),
                    value = mode.overrides.doNotDisturb,
                    accent = accent,
                    onChange = { value ->
                        viewModel.updateDraft {
                            it.copy(overrides = it.overrides.copy(doNotDisturb = value))
                        }
                    },
                )
                OverrideSetRow(
                    title = stringResource(R.string.mode_myndora_notifications),
                    description = stringResource(R.string.mode_myndora_notifications_desc),
                    selected = mode.overrides.allowedNotifications,
                    options = ModeNotificationKind.entries,
                    optionLabel = { notificationLabel(it) },
                    accent = accent,
                    warnWhenMissing = { kind ->
                        if (kind == ModeNotificationKind.MEDICATION_REMINDERS) {
                            stringResource(R.string.mode_med_hidden_warning)
                        } else null
                    },
                    onChange = { selected ->
                        viewModel.updateDraft {
                            it.copy(overrides = it.overrides.copy(allowedNotifications = selected))
                        }
                    },
                )
            }

            ModeSettingsCard(
                icon = Icons.Rounded.Palette,
                title = stringResource(R.string.mode_app_appearance),
                summary = changeCountLabel(appChangeCount(mode.overrides)),
                accent = accent,
                expanded = expandedSection == SECTION_APP,
                onToggle = {
                    expandedSection = SECTION_APP.takeUnless { expandedSection == SECTION_APP }
                },
            ) {
                OverrideSwitchRow(
                    title = stringResource(R.string.mode_simplified_workspace),
                    description = stringResource(R.string.mode_simplified_workspace_desc),
                    value = mode.overrides.simplifiedWorkspace,
                    accent = accent,
                    onChange = { value ->
                        viewModel.updateDraft {
                            it.copy(overrides = it.overrides.copy(simplifiedWorkspace = value))
                        }
                    },
                )
                OverrideSwitchRow(
                    title = stringResource(R.string.settings_reduce_motion),
                    description = stringResource(R.string.mode_reduce_motion_desc),
                    value = mode.overrides.reduceMotion,
                    accent = accent,
                    onChange = { value ->
                        viewModel.updateDraft {
                            it.copy(overrides = it.overrides.copy(reduceMotion = value))
                        }
                    },
                )
                OverrideSetRow(
                    title = stringResource(R.string.mode_home_tiles),
                    description = stringResource(R.string.mode_home_tiles_desc),
                    selected = mode.overrides.hiddenHomeWidgets?.let { hidden ->
                        HomeWidget.entries.filterNot { it.id in hidden }.toSet()
                    },
                    options = HomeWidget.entries.toList(),
                    optionLabel = { stringResource(it.labelRes) },
                    accent = accent,
                    onChange = { shown ->
                        viewModel.updateDraft {
                            it.copy(
                                overrides = it.overrides.copy(
                                    hiddenHomeWidgets = shown?.let { visible ->
                                        HomeWidget.entries.filterNot { widget -> widget in visible }
                                            .map { widget -> widget.id }
                                            .toSet()
                                    },
                                ),
                            )
                        }
                    },
                )
            }

            ModeSettingsCard(
                icon = Icons.Rounded.Timer,
                title = stringResource(R.string.mode_focus_planning),
                summary = changeCountLabel(focusChangeCount(mode.overrides)),
                accent = accent,
                expanded = expandedSection == SECTION_FOCUS,
                onToggle = {
                    expandedSection = SECTION_FOCUS.takeUnless { expandedSection == SECTION_FOCUS }
                },
            ) {
                OverrideSwitchRow(
                    title = stringResource(R.string.mode_silence_focus),
                    description = stringResource(R.string.mode_silence_focus_desc),
                    value = mode.overrides.autoDndOnFocusSession,
                    accent = accent,
                    onChange = { value ->
                        viewModel.updateDraft {
                            it.copy(overrides = it.overrides.copy(autoDndOnFocusSession = value))
                        }
                    },
                )
                MinutesOverrideRow(
                    title = stringResource(R.string.mode_focus_length),
                    value = mode.overrides.pomodoroFocusMinutes,
                    range = 5f..90f,
                    accent = accent,
                    onChange = { value ->
                        viewModel.updateDraft {
                            it.copy(overrides = it.overrides.copy(pomodoroFocusMinutes = value))
                        }
                    },
                )
                MinutesOverrideRow(
                    title = stringResource(R.string.mode_break_length),
                    value = mode.overrides.pomodoroBreakMinutes,
                    range = 3f..30f,
                    accent = accent,
                    onChange = { value ->
                        viewModel.updateDraft {
                            it.copy(overrides = it.overrides.copy(pomodoroBreakMinutes = value))
                        }
                    },
                )
                WorkingHoursRow(
                    start = mode.overrides.workingHoursStartMinute,
                    end = mode.overrides.workingHoursEndMinute,
                    accent = accent,
                    onChange = { start, end ->
                        viewModel.updateDraft {
                            it.copy(
                                overrides = it.overrides.copy(
                                    workingHoursStartMinute = start,
                                    workingHoursEndMinute = end,
                                ),
                            )
                        }
                    },
                )
            }

            ModeSettingsCard(
                icon = Icons.Rounded.Schedule,
                title = stringResource(R.string.mode_schedule),
                summary = mode.schedule?.let {
                    scheduleSummary(it.days, it.startMinute, it.endMinute, it.enabled)
                        ?: stringResource(R.string.mode_choose_days)
                } ?: stringResource(R.string.sync_manual),
                accent = accent,
                expanded = expandedSection == SECTION_SCHEDULE,
                onToggle = {
                    expandedSection = SECTION_SCHEDULE.takeUnless {
                        expandedSection == SECTION_SCHEDULE
                    }
                },
            ) {
                ScheduleEditor(
                    schedule = mode.schedule,
                    accent = accent,
                    onChange = { schedule ->
                        viewModel.updateDraft { it.copy(schedule = schedule) }
                    },
                )
            }

            if (mode.overrides.isEmpty && mode.schedule == null) {
                Text(
                    stringResource(R.string.mode_no_changes_yet),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        Surface(color = colors.background, shadowElevation = 4.dp) {
            Column {
                if (message != null) {
                    Text(
                        message!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
                    )
                }
                Button(
                    onClick = { viewModel.saveDraft { saved -> if (saved) onBack() } },
                    enabled = !saving,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(54.dp)
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color.White,
                    ),
                ) {
                    Text(if (saving) stringResource(R.string.common_saving) else stringResource(R.string.mode_save), fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    LaunchedEffect(message) {
        if (message != null) {
            kotlinx.coroutines.delay(2600)
            viewModel.consumeMessage()
        }
    }

    if (confirmDelete && mode != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = RoundedCornerShape(26.dp),
            title = { Text(stringResource(R.string.mode_delete_confirm, mode.name.ifBlank { stringResource(R.string.mode_this_mode) }), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.mode_delete_body)) },
            confirmButton = {
                TextButton(
                    enabled = !deleting,
                    onClick = {
                        viewModel.delete(mode.id) { removed ->
                            if (removed) {
                                confirmDelete = false
                                onBack()
                            }
                        }
                    },
                ) {
                    Text(
                        if (deleting) stringResource(R.string.common_deleting) else stringResource(R.string.common_delete),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.together_keep)) }
            },
        )
    }
}

@Composable
private fun ModeIdentityCard(
    name: String,
    iconKey: String,
    accentKey: String?,
    accent: Color,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onAccentChange: (String?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motionDuration = if (animationsOn()) 200 else 0
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.med_name)) },
                placeholder = { Text(stringResource(R.string.mode_name_placeholder)) },
                leadingIcon = {
                    Icon(modeIcon(iconKey), contentDescription = null, tint = accent)
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.mode_icon), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                modeIconOptions.forEach { option ->
                    val selected = iconKey == option.key
                    val iconBackground by animateColorAsState(
                        targetValue = if (selected) {
                            accent.copy(alpha = .2f)
                        } else {
                            colors.surfaceContainerHighest
                        },
                        animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
                        label = "mode_icon_background",
                    )
                    val iconTint by animateColorAsState(
                        targetValue = if (selected) accent else colors.onSurfaceVariant,
                        animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
                        label = "mode_icon_tint",
                    )
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(iconBackground)
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onIconChange(option.key) },
                            )
                            .contentDescription(stringResource(option.labelRes)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            option.icon,
                            contentDescription = null,
                            tint = iconTint,
                            modifier = Modifier.size(20.dp),
                        )
                        ModeIconSelectionBadge(
                            visible = selected,
                            accent = accent,
                            motionDuration = motionDuration,
                            modifier = Modifier.align(Alignment.BottomEnd),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.common_colour), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                modeAccentOptions.forEach { option ->
                    val key = option.key.takeIf { it != "theme" }
                    val selected = accentKey == key
                    val swatch = modeAccentColor(key)
                    val swatchContainer by animateColorAsState(
                        targetValue = if (selected) swatch.copy(alpha = .16f) else Color.Transparent,
                        animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
                        label = "mode_swatch_container",
                    )
                    val swatchBorder by animateColorAsState(
                        targetValue = swatch.copy(alpha = if (selected) .65f else .25f),
                        animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
                        label = "mode_swatch_border",
                    )
                    val swatchLabel by animateColorAsState(
                        targetValue = if (selected) swatch else colors.onSurface,
                        animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
                        label = "mode_swatch_label",
                    )
                    Surface(
                        modifier = Modifier
                            .sizeIn(minHeight = 48.dp)
                            .selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onAccentChange(key) },
                            ),
                        shape = RoundedCornerShape(50),
                        color = swatchContainer,
                        border = BorderStroke(1.dp, swatchBorder),
                    ) {
                        Row(
                            Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(11.dp).clip(CircleShape).background(swatch))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                stringResource(option.labelRes),
                                style = MaterialTheme.typography.labelMedium,
                                color = swatchLabel,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            )
                            AnimatedVisibility(
                                visible = selected,
                                enter = fadeIn(tween(motionDuration)) +
                                    expandHorizontally(tween(motionDuration)),
                                exit = fadeOut(tween(motionDuration)) +
                                    shrinkHorizontally(tween(motionDuration)),
                            ) {
                                Row {
                                    Spacer(Modifier.width(5.dp))
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = swatchLabel,
                                        modifier = Modifier.size(13.dp),
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
private fun ModeIconSelectionBadge(
    visible: Boolean,
    accent: Color,
    motionDuration: Int,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(motionDuration)) + expandHorizontally(tween(motionDuration)),
        exit = fadeOut(tween(motionDuration)) + shrinkHorizontally(tween(motionDuration)),
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

@Composable
private fun ModeSettingsCard(
    icon: ImageVector,
    title: String,
    summary: String,
    accent: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motionDuration = if (animationsOn()) 260 else 0
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
        label = "mode_section_chevron",
    )
    val iconBackground by animateColorAsState(
        targetValue = accent.copy(alpha = if (expanded) .2f else .12f),
        animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
        label = "mode_section_icon_background",
    )
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button, onClick = onToggle)
                    .stateDescription(
                        if (expanded) stringResource(R.string.common_expanded) else stringResource(R.string.common_collapsed)
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(iconBackground),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                    Text(
                        summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.rotate(chevronRotation),
                )
            }
            AnimatedVisibility(
                visible = expanded,
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
                Column {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .height(1.dp)
                            .background(colors.outlineVariant.copy(alpha = .55f)),
                    )
                    Column(
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        content = content,
                    )
                }
            }
        }
    }
}

@Composable
private fun changeCountLabel(count: Int): String = when (count) {
    0 -> stringResource(R.string.mode_no_changes)
    1 -> stringResource(R.string.mode_one_change)
    else -> stringResource(R.string.mode_changes, count)
}

private fun soundChangeCount(overrides: ModeOverrides): Int = listOf(
    overrides.ringer,
    overrides.doNotDisturb,
    overrides.allowedNotifications,
).count { it != null }

private fun appChangeCount(overrides: ModeOverrides): Int = listOf(
    overrides.simplifiedWorkspace,
    overrides.reduceMotion,
    overrides.hiddenHomeWidgets,
).count { it != null }

private fun focusChangeCount(overrides: ModeOverrides): Int = listOf(
    overrides.autoDndOnFocusSession,
    overrides.pomodoroFocusMinutes,
    overrides.pomodoroBreakMinutes,
    overrides.workingHoursStartMinute,
).count { it != null }

@Composable
private fun notificationLabel(kind: ModeNotificationKind): String = when (kind) {
    ModeNotificationKind.TODO_REMINDERS -> stringResource(R.string.notif_todo_reminders)
    ModeNotificationKind.CALENDAR_REMINDERS -> stringResource(R.string.notif_calendar_reminders)
    ModeNotificationKind.MEDICATION_REMINDERS -> stringResource(R.string.mode_med_reminders)
    ModeNotificationKind.DAILY_SUMMARY -> stringResource(R.string.notif_daily_summary)
    ModeNotificationKind.POMODORO_NUDGES -> stringResource(R.string.notif_focus_nudges)
    ModeNotificationKind.BREAK_REMINDERS -> stringResource(R.string.notif_break_reminders)
}

@Composable
private fun RingerPicker(
    value: RingerSetting?,
    accent: Color,
    onChange: (RingerSetting?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(stringResource(R.string.mode_ringer), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            stringResource(R.string.mode_ringer_desc),
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
            RingerChip(stringResource(R.string.mode_dont_change), value == null, accent, Modifier.weight(1.3f)) { onChange(null) }
            RingerChip(stringResource(R.string.ringer_normal), value == RingerSetting.NORMAL, accent, Modifier.weight(1f)) {
                onChange(RingerSetting.NORMAL)
            }
            RingerChip(stringResource(R.string.ringer_vibrate), value == RingerSetting.VIBRATE, accent, Modifier.weight(1f)) {
                onChange(RingerSetting.VIBRATE)
            }
            RingerChip(stringResource(R.string.ringer_silent), value == RingerSetting.SILENT, accent, Modifier.weight(1f)) {
                onChange(RingerSetting.SILENT)
            }
        }
    }
}

@Composable
private fun RingerChip(
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
        label = "ringer_chip_background",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
        label = "ringer_chip_content",
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
            .padding(horizontal = 3.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = contentColor,
        )
    }
}

@Composable
private fun MinutesOverrideRow(
    title: String,
    value: Int?,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    onChange: (Int?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = value?.let { stringResource(R.string.common_minutes_short, it) } ?: stringResource(R.string.mode_dont_change),
                style = MaterialTheme.typography.bodyMedium,
                color = if (value == null) colors.onSurfaceVariant else accent,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(
                checked = value != null,
                onCheckedChange = { on -> onChange(if (on) range.start.roundToInt() else null) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                ),
            )
            Spacer(Modifier.width(10.dp))
            Slider(
                value = (value ?: range.start.roundToInt()).toFloat().coerceIn(range),
                onValueChange = { onChange(it.roundToInt()) },
                valueRange = range,
                enabled = value != null,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                ),
                modifier = Modifier
                    .weight(1f)
                    .contentDescription(stringResource(R.string.mode_duration_cd, title)),
            )
        }
    }
}

@Composable
private fun WorkingHoursRow(
    start: Int?,
    end: Int?,
    accent: Color,
    onChange: (Int?, Int?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motionDuration = if (animationsOn()) 220 else 0
    val on = start != null || end != null
    val displayedStart = snapStartToQuarter((start ?: 9 * 60).toFloat())
    val minimumEnd = displayedStart + QUARTER_HOUR_MINUTES
    val displayedEnd = snapEndToQuarter((end ?: 17 * 60).toFloat())
        .coerceIn(minimumEnd, MINUTES_PER_DAY)
    val latestStart =
        ((displayedEnd - QUARTER_HOUR_MINUTES) / QUARTER_HOUR_MINUTES) *
            QUARTER_HOUR_MINUTES
    // keep a non-zero Slider range when 24:00 is the only valid end. the control is disabled in
    // that case, but Material still needs a finite span for its layout maths
    val endSliderStart = minimumEnd.coerceAtMost(MINUTES_PER_DAY - QUARTER_HOUR_MINUTES)
    Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.mode_usable_hours),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.mode_usable_hours_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Switch(
                checked = on,
                onCheckedChange = { enabled ->
                    if (enabled) onChange(9 * 60, 17 * 60) else onChange(null, null)
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                ),
            )
        }
        AnimatedVisibility(
            visible = on,
            enter = fadeIn(tween(motionDuration)) +
                expandVertically(tween(motionDuration), expandFrom = Alignment.Top),
            exit = fadeOut(tween(motionDuration)) +
                shrinkVertically(tween(motionDuration), shrinkTowards = Alignment.Top),
        ) {
            Column {
                Spacer(Modifier.height(6.dp))
                Text(
                    "${minutesLabel(displayedStart)} – ${minutesLabel(displayedEnd)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                )
                Slider(
                    value = displayedStart.toFloat(),
                    onValueChange = { raw ->
                        onChange(
                            snapStartToQuarter(raw).coerceIn(0, latestStart),
                            displayedEnd,
                        )
                    },
                    valueRange = 0f..latestStart
                        .coerceAtLeast(QUARTER_HOUR_MINUTES)
                        .toFloat(),
                    steps = sliderSteps(
                        startMinute = 0,
                        endMinute = latestStart.coerceAtLeast(QUARTER_HOUR_MINUTES),
                    ),
                    enabled = latestStart > 0,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                    ),
                    modifier = Modifier.contentDescription(stringResource(R.string.mode_usable_start)),
                )
                Slider(
                    value = displayedEnd.toFloat(),
                    // this is a same-day planning window. unlike a mode schedule it may not end before it begins,
                    // and both handles stay in the valid range
                    onValueChange = { raw ->
                        onChange(
                            displayedStart,
                            snapEndToQuarter(raw).coerceIn(minimumEnd, MINUTES_PER_DAY),
                        )
                    },
                    valueRange = endSliderStart.toFloat()..MINUTES_PER_DAY.toFloat(),
                    steps = sliderSteps(endSliderStart, MINUTES_PER_DAY),
                    enabled = minimumEnd < MINUTES_PER_DAY,
                    colors = SliderDefaults.colors(
                        thumbColor = accent,
                        activeTrackColor = accent,
                    ),
                    modifier = Modifier.contentDescription(stringResource(R.string.mode_usable_end)),
                )
            }
        }
    }
}

private fun snapStartToQuarter(raw: Float): Int =
    ((raw / QUARTER_HOUR_MINUTES).roundToInt() * QUARTER_HOUR_MINUTES)
        .coerceIn(0, LAST_START_MINUTE)

private fun snapEndToQuarter(raw: Float): Int =
    ((raw / QUARTER_HOUR_MINUTES).roundToInt() * QUARTER_HOUR_MINUTES)
        .coerceIn(0, MINUTES_PER_DAY)

// number of interior quarter-hour values expected by Material's Slider API
private fun sliderSteps(startMinute: Int, endMinute: Int): Int =
    ((endMinute - startMinute) / QUARTER_HOUR_MINUTES - 1).coerceAtLeast(0)

// ISO day number to its full name in the app's language
private fun dayContentDescription(day: Int): String =
    java.time.DayOfWeek.of(day.coerceIn(1, 7))
        .getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.getDefault())
        .replaceFirstChar { it.titlecase(java.util.Locale.getDefault()) }

@Composable
private fun ScheduleEditor(
    schedule: ModeSchedule?,
    accent: Color,
    onChange: (ModeSchedule?) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val current = schedule
    val motionDuration = if (animationsOn()) 220 else 0
    Column(
        Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
            )
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.mode_turn_on_auto),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    current?.let {
                        scheduleSummary(it.days, it.startMinute, it.endMinute, it.enabled)
                            ?: stringResource(R.string.mode_pick_day)
                    } ?: stringResource(R.string.mode_off_by_hand),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            Switch(
                checked = current?.enabled == true,
                onCheckedChange = { on ->
                    onChange(
                        current?.copy(enabled = on)
                            ?: ModeSchedule(days = ModeSchedule.WEEKDAYS, enabled = on)
                    )
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = accent,
                ),
            )
        }

        if (current != null) {
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val initials = dayInitials()
                (1..7).forEach { day ->
                    val on = day in current.days
                    val dayBackground by animateColorAsState(
                        targetValue = if (on) {
                            accent.copy(alpha = .2f)
                        } else {
                            colors.surfaceContainerHighest.copy(alpha = .6f)
                        },
                        animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
                        label = "mode_schedule_day_background",
                    )
                    val dayContent by animateColorAsState(
                        targetValue = if (on) accent else colors.onSurfaceVariant,
                        animationSpec = tween(motionDuration, easing = FastOutSlowInEasing),
                        label = "mode_schedule_day_content",
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .clip(CircleShape)
                            .background(dayBackground)
                            .toggleable(
                                value = on,
                                role = Role.Checkbox,
                                onValueChange = { selected ->
                                    val days = if (selected) {
                                        current.days + day
                                    } else {
                                        current.days - day
                                    }
                                    onChange(current.copy(days = days))
                                },
                            )
                            .semantics {
                                contentDescription = dayContentDescription(day)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            initials[day - 1],
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                            color = dayContent,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.mode_from_to, minutesLabel(current.startMinute), minutesLabel(current.endMinute)),
                style = MaterialTheme.typography.bodyMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
            Slider(
                value = current.startMinute.coerceIn(0, LAST_START_MINUTE).toFloat(),
                onValueChange = { onChange(current.copy(startMinute = snapStartToQuarter(it))) },
                valueRange = 0f..LAST_START_MINUTE.toFloat(),
                steps = START_SLIDER_STEPS,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                ),
                modifier = Modifier.contentDescription(stringResource(R.string.mode_auto_start)),
            )
            Slider(
                value = current.endMinute.coerceIn(0, MINUTES_PER_DAY).toFloat(),
                onValueChange = { onChange(current.copy(endMinute = snapEndToQuarter(it))) },
                valueRange = 0f..MINUTES_PER_DAY.toFloat(),
                steps = END_SLIDER_STEPS,
                colors = SliderDefaults.colors(
                    thumbColor = accent,
                    activeTrackColor = accent,
                ),
                modifier = Modifier.contentDescription(stringResource(R.string.mode_auto_end)),
            )
            if (current.crossesMidnight) {
                Text(
                    stringResource(R.string.mode_overnight),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
