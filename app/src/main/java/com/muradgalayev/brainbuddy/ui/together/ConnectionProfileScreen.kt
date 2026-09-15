package com.muradgalayev.brainbuddy.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.domain.model.Connection
import com.muradgalayev.brainbuddy.domain.model.ShareScope
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.RemoveDone
import com.muradgalayev.brainbuddy.domain.scheduling.TimeSuggestion
import com.muradgalayev.brainbuddy.ui.calendar.TimeSuggestionStrip
import com.muradgalayev.brainbuddy.ui.calendar.rememberCalendarPalette
import com.muradgalayev.brainbuddy.ui.sharedcomponents.TimePickerDialog
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// one connection: what you've opened up to them, what they've opened up to you, and where
// they've allowed it, the things you've added to their calendar or list. the two permission
// blocks are deliberately separate, because sharing here is never mutual by accident: your
// switches control only your data, and theirs are read-only so it's obvious you can't grant
// yourself access
@Composable
fun ConnectionProfileScreen(
    onBack: () -> Unit,
    viewModel: ConnectionProfileViewModel = hiltViewModel(),
) {
    val connection by viewModel.connection.collectAsState()
    val busyScopes by viewModel.busyScopes.collectAsState()
    val optimisticGrants by viewModel.optimisticGrants.collectAsState()
    val message by viewModel.message.collectAsState()
    val myEvents by viewModel.myEventsForThem.collectAsState()
    val myTodos by viewModel.myTodosForThem.collectAsState()
    val wellness by viewModel.wellness.collectAsState()
    val eventDraft by viewModel.eventDraft.collectAsState()
    val todoDraft by viewModel.todoDraft.collectAsState()
    val removed by viewModel.removed.collectAsState()
    val suggestions by viewModel.eventSuggestions.collectAsState()
    val usingAvailability by viewModel.usingTheirAvailability.collectAsState()

    var confirmRemove by remember { mutableStateOf(false) }

    // removing the connection leaves this screen with nothing to show
    LaunchedEffect(removed) { if (removed) onBack() }

    val person = connection
    TogetherScaffold(title = person?.name ?: stringResource(R.string.together_connection), onBack = onBack) {
        if (person == null) {
            TogetherCard {
                Text(
                    stringResource(R.string.together_conn_unavailable),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@TogetherScaffold
        }

        ProfileHeader(person)

        // what I share with them
        TogetherSectionHeader(title = stringResource(R.string.together_what_you_share, person.name))
        TogetherCard {
            Text(
                stringResource(R.string.together_what_you_share_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // one tap for the common case of trusting someone with everything, and back again once it's all on
            val allShared = ShareScope.entries.all { optimisticGrants[it] ?: person.canGrant(it) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
            ) {
                androidx.compose.material3.TextButton(
                    onClick = { viewModel.setAllPermissions(!allShared) },
                    enabled = busyScopes.isEmpty() && optimisticGrants.isEmpty(),
                ) {
                    Icon(
                        if (allShared) Icons.Rounded.RemoveDone else Icons.Rounded.DoneAll,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(if (allShared) R.string.together_turn_all_off else R.string.together_select_all),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            for (scope in ShareScope.entries) {
                ScopeToggleRow(
                    scope = scope,
                    // a select all shows its answer immediately; the server catches up behind it
                    granted = optimisticGrants[scope] ?: person.canGrant(scope),
                    busy = scope in busyScopes,
                    onChange = { viewModel.togglePermission(scope, it) },
                )
            }
        }

        // what they share with me, and the actions it unlocks
        TogetherSectionHeader(
            title = stringResource(R.string.together_what_they_share, person.name),
            accent = MaterialTheme.colorScheme.tertiary,
        )

        if (person.grantedToMe.isEmpty()) {
            TogetherCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        stringResource(R.string.together_nothing_yet_their_side),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (person.canUse(ShareScope.CALENDAR)) {
            SharedCalendarCard(
                personName = person.name,
                events = myEvents.map { EventRow(it.id, it.title, it.startTime) },
                onAdd = viewModel::startEventDraft,
                onDelete = viewModel::deleteEvent,
            )
        }

        if (person.canUse(ShareScope.TODOS)) {
            SharedTodosCard(
                personName = person.name,
                todos = myTodos.map { EventRow(it.id, it.title, "${it.date} ${it.startTime}") },
                onAdd = viewModel::startTodoDraft,
                onDelete = viewModel::deleteTodo,
            )
        }

        if (person.canUse(ShareScope.WELLNESS)) {
            WellnessCard(
                personName = person.name,
                steps = wellness?.steps7d,
                sleepHours = wellness?.lastSleepHours,
                exerciseMinutes = wellness?.exerciseMinutes7d,
                restingHeartRate = wellness?.restingHeartRateBpm,
                calories = wellness?.caloriesToday,
            )
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { confirmRemove = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Text(stringResource(R.string.together_remove_connection))
        }

        message?.let { text ->
            LaunchedEffect(text) {
                kotlinx.coroutines.delay(2600)
                viewModel.consumeMessage()
            }
            TogetherCard {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }

    if (confirmRemove && person != null) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            shape = RoundedCornerShape(24.dp),
            title = { Text(stringResource(R.string.together_remove_person, person.name)) },
            text = {
                Text(
                    stringResource(R.string.together_remove_body),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        confirmRemove = false
                        viewModel.removeConnection()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) { Text(stringResource(R.string.common_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text(stringResource(R.string.together_keep)) }
            },
        )
    }

    eventDraft?.let { draft ->
        ComposeItemDialog(
            title = person?.name?.let { stringResource(R.string.together_add_to_calendar_of, it) }
                ?: stringResource(R.string.together_add_to_their_calendar),
            draft = draft,
            showTimes = true,
            suggestions = suggestions,
            // named, because 'we're working around someone's calendar' is only reassuring if you know
            // whose, and the sentence has to say what is not happening as plainly as what is
            suggestionNote = if (usingAvailability && person != null) {
                stringResource(R.string.together_busy_hours, person.name)
            } else {
                null
            },
            onPickSuggestion = viewModel::applySuggestion,
            onUpdate = viewModel::updateEventDraft,
            onDismiss = viewModel::cancelEventDraft,
            onSave = viewModel::saveEvent,
        )
    }

    todoDraft?.let { draft ->
        ComposeItemDialog(
            title = person?.name?.let { stringResource(R.string.together_add_to_list_of, it) }
                ?: stringResource(R.string.together_add_to_their_list),
            draft = draft,
            showTimes = true,
            onUpdate = viewModel::updateTodoDraft,
            onDismiss = viewModel::cancelTodoDraft,
            onSave = viewModel::saveTodo,
        )
    }
}

// gradient hero, matching the Together hub. the two-way summary underneath states the sharing
// situation in words before the switches restate it as controls: 'you share 2, they share 1'
// is the thing people actually want to confirm
@Composable
private fun ProfileHeader(person: Connection) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.tertiary.copy(alpha = .24f),
                        colors.surfaceContainer,
                        colors.primaryContainer.copy(alpha = .40f),
                    ),
                ),
            )
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(colors.surface.copy(alpha = .7f))
                .padding(3.dp),
        ) {
            Box(Modifier.alpha(if (person.deactivated) .45f else 1f)) {
                TogetherAvatar(name = person.name, avatarUrl = person.avatarUrl, size = 74.dp)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            person.name,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
        person.username?.let {
            Spacer(Modifier.height(2.dp))
            Text(
                "@$it",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(colors.primary.copy(alpha = .14f))
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text(
                stringResource(person.relation.labelRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.primary,
            )
        }
        if (person.deactivated) {
            Spacer(Modifier.height(10.dp))
            DeactivatedChip()
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.together_deactivated_desc),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = stringResource(
                R.string.together_share_counts,
                person.grantedByMe.size,
                person.grantedToMe.size,
            ),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

// title and subtitle pair, shared by the calendar and todo lists
private data class EventRow(val id: String, val title: String, val subtitle: String)

@Composable
private fun SharedCalendarCard(
    personName: String,
    events: List<EventRow>,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
) {
    SharedItemsCard(
        icon = Icons.Rounded.CalendarMonth,
        heading = stringResource(R.string.together_their_calendar),
        // stating the limit here, not just in the permission copy, so the person acting on it
        // understands the boundary at the moment they use it
        explanation = stringResource(R.string.together_their_calendar_body, personName),
        addLabel = stringResource(R.string.together_add_event),
        emptyLabel = stringResource(R.string.together_nothing_added),
        items = events,
        onAdd = onAdd,
        onDelete = onDelete,
    )
}

@Composable
private fun SharedTodosCard(
    personName: String,
    todos: List<EventRow>,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
) {
    SharedItemsCard(
        icon = Icons.Rounded.TaskAlt,
        heading = stringResource(R.string.together_their_todos),
        explanation = stringResource(R.string.together_their_todos_body, personName),
        addLabel = stringResource(R.string.together_add_task),
        emptyLabel = stringResource(R.string.together_no_tasks_added),
        items = todos,
        onAdd = onAdd,
        onDelete = onDelete,
    )
}

@Composable
private fun SharedItemsCard(
    icon: ImageVector,
    heading: String,
    explanation: String,
    addLabel: String,
    emptyLabel: String,
    items: List<EventRow>,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    TogetherCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                heading,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            explanation,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))

        if (items.isEmpty()) {
            Text(
                emptyLabel,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        } else {
            for (item in items) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = colors.onSurface,
                        )
                        Text(
                            item.subtitle.prettyDateTime(),
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { onDelete(item.id) }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = stringResource(R.string.common_remove),
                            tint = colors.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Button(onClick = onAdd, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(addLabel)
        }
    }
}

@Composable
private fun WellnessCard(
    personName: String,
    steps: Long?,
    sleepHours: Double?,
    exerciseMinutes: Long?,
    restingHeartRate: Long?,
    calories: Long?,
) {
    val colors = MaterialTheme.colorScheme
    TogetherCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.MonitorHeart,
                contentDescription = null,
                tint = colors.tertiary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.together_wellness_of, personName),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
        }
        Spacer(Modifier.height(10.dp))

        val hasAny = listOfNotNull(steps, sleepHours, exerciseMinutes, restingHeartRate, calories).isNotEmpty()
        if (!hasAny) {
            Text(
                stringResource(R.string.together_wellness_no_data),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            return@TogetherCard
        }

        steps?.let { WellnessMetric(Icons.Rounded.DirectionsWalk, stringResource(R.string.wellness_steps_7d), "%,d".format(it)) }
        sleepHours?.let { WellnessMetric(Icons.Rounded.Bedtime, stringResource(R.string.wellness_last_sleep), "%.1f h".format(it)) }
        exerciseMinutes?.let { WellnessMetric(Icons.Rounded.FitnessCenter, stringResource(R.string.wellness_exercise_7d), "$it min") }
        restingHeartRate?.let { WellnessMetric(Icons.Rounded.MonitorHeart, stringResource(R.string.wellness_resting_hr), "$it bpm") }
        calories?.let { WellnessMetric(Icons.Rounded.LocalFireDepartment, stringResource(R.string.wellness_calories_today), "$it kcal") }

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.together_wellness_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun WellnessMetric(icon: ImageVector, label: String, value: String) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.tertiary, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
    }
}

// compose an event or task for someone else. deliberately minimal: title, when, and an
// optional note. anything richer belongs on their own calendar screen
@Composable
private fun ComposeItemDialog(
    title: String,
    draft: ComposeItemDraft,
    showTimes: Boolean,
    onUpdate: ((ComposeItemDraft) -> ComposeItemDraft) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    // times that clear both calendars. empty renders as no strip at all
    suggestions: List<TimeSuggestion> = emptyList(),
    // one line under the strip saying whose availability is in play, if any
    suggestionNote: String? = null,
    onPickSuggestion: (TimeSuggestion) -> Unit = {},
) {
    val colors = MaterialTheme.colorScheme
    val palette = rememberCalendarPalette()
    var picking by remember { mutableStateOf<TimeField?>(null) }

    AlertDialog(
        onDismissRequest = { if (!draft.saving) onDismiss() },
        shape = RoundedCornerShape(26.dp),
        title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
        text = {
            // scrollable: with a suggestion strip and its note the sheet can outgrow a short screen, and
            // an AlertDialog clips rather than scrolls
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { value -> onUpdate { it.copy(title = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.together_what_is_it)) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = draft.description,
                    onValueChange = { value -> onUpdate { it.copy(description = value) } },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.together_note_optional)) },
                    shape = RoundedCornerShape(14.dp),
                )

                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.common_when),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DayPill(
                        label = stringResource(R.string.common_today),
                        selected = draft.date == LocalDate.now(),
                        onClick = { onUpdate { it.copy(date = LocalDate.now()) } },
                    )
                    DayPill(
                        label = stringResource(R.string.common_tomorrow),
                        selected = draft.date == LocalDate.now().plusDays(1),
                        onClick = { onUpdate { it.copy(date = LocalDate.now().plusDays(1)) } },
                    )
                    DayPill(
                        label = stringResource(R.string.common_in_a_week),
                        selected = draft.date == LocalDate.now().plusWeeks(1),
                        onClick = { onUpdate { it.copy(date = LocalDate.now().plusWeeks(1)) } },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    draft.date.format(DateTimeFormatter.ofPattern("EEEE d MMMM")),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )

                if (showTimes) {
                    // above the pickers: a shortcut offered after the clock dial is one they've already walked past
                    if (suggestions.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        TimeSuggestionStrip(
                            palette = palette,
                            suggestions = suggestions,
                            selectedDate = draft.date,
                            selectedStart = draft.startTime.format(HourMinute),
                            onPick = onPickSuggestion,
                        )
                        suggestionNote?.let { note ->
                            Spacer(Modifier.height(8.dp))
                            Text(
                                note,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimeBox(
                            label = stringResource(R.string.common_starts),
                            value = draft.startTime,
                            modifier = Modifier.weight(1f),
                            onClick = { picking = TimeField.START },
                        )
                        TimeBox(
                            label = stringResource(R.string.common_ends),
                            value = draft.endTime,
                            modifier = Modifier.weight(1f),
                            onClick = { picking = TimeField.END },
                        )
                    }
                }

                draft.error?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error)
                }
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = draft.canSave, shape = RoundedCornerShape(16.dp)) {
                if (draft.saving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.common_add))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !draft.saving) { Text(stringResource(R.string.common_cancel)) }
        },
    )

    picking?.let { field ->
        val current = if (field == TimeField.START) draft.startTime else draft.endTime
        TimePickerDialog(
            title = if (field == TimeField.START) stringResource(R.string.common_start_time) else stringResource(R.string.common_end_time),
            initialHour = current.hour,
            initialMinute = current.minute,
            onConfirm = { hour, minute ->
                val time = LocalTime.of(hour, minute)
                onUpdate {
                    if (field == TimeField.START) it.copy(startTime = time) else it.copy(endTime = time)
                }
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

private enum class TimeField { START, END }

private val HourMinute: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun DayPill(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) colors.primary.copy(alpha = .16f) else colors.surfaceVariant.copy(alpha = .45f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) colors.primary else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun TimeBox(
    label: String,
    value: LocalTime,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onClick, shape = RoundedCornerShape(14.dp)) {
            Text(value.format(HourMinute))
        }
    }
}

// the lists come straight off the wire as ISO strings. render them readably, but fall back to
// the raw value rather than throwing on anything odd
private fun String.prettyDateTime(): String {
    val trimmed = trim()
    return runCatching {
        val normalised = trimmed.replace(" ", "T")
        val datePart = normalised.substringBefore('T')
        val timePart = normalised.substringAfter('T', "").take(5)
        val date = LocalDate.parse(datePart)
        val pretty = date.format(DateTimeFormatter.ofPattern("EEE d MMM"))
        if (timePart.isBlank()) pretty else "$pretty • $timePart"
    }.getOrDefault(trimmed)
}
