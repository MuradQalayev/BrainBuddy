package com.muradgalayev.brainbuddy.ui.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import com.muradgalayev.brainbuddy.domain.scheduling.TimeSuggestion
import com.muradgalayev.brainbuddy.ui.together.NoConnectionsNotice
import com.muradgalayev.brainbuddy.ui.together.canSaveForAudience
import com.muradgalayev.brainbuddy.ui.sharedcomponents.ColorOption
import com.muradgalayev.brainbuddy.ui.sharedcomponents.TimePickerDialog
import com.muradgalayev.brainbuddy.ui.sharedcomponents.TimePickerField
import com.muradgalayev.brainbuddy.ui.sharedcomponents.swipeToSwitch
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    palette: CalendarPalette,
    selectedDate: LocalDate,
    existingEvent: CalendarEvent? = null,
    // connections who have opened their calendar to me. empty still shows the switcher
    shareTargets: List<CalendarViewModel.ShareTarget> = emptyList(),
    // opens the add-connection flow from the empty-state notice
    onConnectPeople: () -> Unit = {},
    // on-device time proposals for what's typed. recomputed as onSuggestionInputChanged fires,
    // empty means nothing worth saying and renders as no strip at all
    suggestions: List<TimeSuggestion> = emptyList(),
    // null when nobody's is in play, which is most of the time and has to stay silent
    suggestionNote: String? = null,
    onSuggestionInputChanged: (title: String, description: String) -> Unit = { _, _ -> },
    // reports who the event is for so the suggester can route around their hours. sent as the
    // selection changes, not at save time: a suggestion after you've picked a time is too late
    onShareTargetsChanged: (Set<String>) -> Unit = {},
    // taking a suggestion for another day moves the whole event. routed out to the ViewModel
    // because selectedDate is what the save is keyed on, a date of the dialog's own would drift
    onSuggestionDateChange: (LocalDate) -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        description: String,
        startTime: String,
        endTime: String,
        location: String,
        color: String,
        link: String,
        alsoAddFor: Set<String>,
        addToMyCalendar: Boolean,
    ) -> Unit
) {
    val isEditing = existingEvent != null
    // reset per event, or a previous selection would silently re-share the next one created
    var alsoAddFor by remember(existingEvent?.id) { mutableStateOf(emptySet<String>()) }
    // always true in Mine mode, opt-in in Together where the point is it usually isn't yours
    var addToMyCalendar by remember(existingEvent?.id) { mutableStateOf(true) }
    var audience by remember(existingEvent?.id) { mutableStateOf(EventAudience.Mine) }
    val initialStart = existingEvent?.let { extractHourMinute(it.startTime) }.orEmpty()
    val initialEnd = existingEvent?.let { extractHourMinute(it.endTime) }.orEmpty()

    var title by remember(existingEvent?.id) { mutableStateOf(existingEvent?.title.orEmpty()) }
    var description by remember(existingEvent?.id) { mutableStateOf(existingEvent?.description.orEmpty()) }
    var location by remember(existingEvent?.id) { mutableStateOf(existingEvent?.location.orEmpty()) }
    var link by remember(existingEvent?.id) { mutableStateOf(existingEvent?.link.orEmpty()) }
    var startTime by remember(existingEvent?.id) { mutableStateOf(initialStart) }
    var endTime by remember(existingEvent?.id) { mutableStateOf(initialEnd) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var selectedColor by remember(existingEvent?.id) {
        mutableStateOf(existingEvent?.color?.takeIf { it.isNotBlank() } ?: DefaultEventColorKey)
    }

    val focusManager = LocalFocusManager.current
    val configuration = LocalConfiguration.current
    val maxDialogHeight = (configuration.screenHeightDp * 0.9f).dp

    // debouncing lives in the ViewModel, so this can fire on every character for free
    LaunchedEffect(title, description) { onSuggestionInputChanged(title, description) }
    LaunchedEffect(alsoAddFor) { onShareTargetsChanged(alsoAddFor) }

    val formattedDate =
        "${selectedDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${selectedDate.dayOfMonth}, ${selectedDate.year}"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxDialogHeight)
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = palette.cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.lavender.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Event,
                            contentDescription = null,
                            tint = palette.lavender,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isEditing -> stringResource(R.string.cal_edit_event)
                                audience == EventAudience.Together -> stringResource(R.string.cal_event_for_someone)
                                else -> stringResource(R.string.cal_new_event)
                            },
                            color = palette.ink,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = formattedDate,
                            color = palette.lavender,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.common_close),
                            tint = palette.muted,
                        )
                    }
                }

                // who is this for, answered before the form rather than after. the audience changes what the
                // event is, so asking last (a checkbox under the colours) buried the one decision that isn't
                // about the event's contents. shown even with nobody connected, picking 'For someone' then
                // explains how to connect. hidden only while editing, where the audience is already settled
                if (!isEditing) {
                    Spacer(Modifier.height(18.dp))
                    AudienceSwitcher(
                        palette = palette,
                        audience = audience,
                        onSelect = { next ->
                            audience = next
                            // each mode has its own default: for me implies your calendar, for someone else implies theirs
                            addToMyCalendar = next == EventAudience.Mine
                            if (next == EventAudience.Mine) alsoAddFor = emptySet()
                        },
                    )
                }

                if (audience == EventAudience.Together && shareTargets.isEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    NoConnectionsNotice(
                        kind = com.muradgalayev.brainbuddy.ui.together.SharedItemKind.EVENT,
                        accent = palette.lavender,
                        ink = palette.ink,
                        muted = palette.muted,
                        onConnect = {
                            // close first, the dialog sits above the nav host and would strand itself over the new screen
                            onDismiss()
                            onConnectPeople()
                        },
                    )
                }

                if (audience == EventAudience.Together && shareTargets.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    AudiencePickerSection(
                        palette = palette,
                        targets = shareTargets,
                        selected = alsoAddFor,
                        heading = stringResource(R.string.cal_who_for),
                        required = true,
                        addToMyCalendar = addToMyCalendar,
                        showMineToggle = true,
                        onToggle = { id ->
                            alsoAddFor = if (id in alsoAddFor) alsoAddFor - id else alsoAddFor + id
                        },
                        onToggleAddToMine = { addToMyCalendar = it },
                    )
                }

                Spacer(Modifier.height(20.dp))

                CalendarDialogTextField(
                    palette = palette,
                    value = title,
                    onValueChange = { title = it },
                    label = stringResource(R.string.cal_event_name),
                    placeholder = stringResource(R.string.cal_event_name_hint),
                    imeAction = ImeAction.Next
                )

                Spacer(Modifier.height(14.dp))

                CalendarDialogTextField(
                    palette = palette,
                    value = description,
                    onValueChange = { description = it },
                    label = stringResource(R.string.cal_description_optional),
                    placeholder = stringResource(R.string.cal_description_hint),
                    imeAction = ImeAction.Next
                )

                Spacer(Modifier.height(14.dp))

                CalendarDialogTextField(
                    palette = palette,
                    value = location,
                    onValueChange = { location = it },
                    label = stringResource(R.string.cal_location_optional),
                    placeholder = stringResource(R.string.cal_location_hint),
                    imeAction = ImeAction.Next
                )

                Spacer(Modifier.height(14.dp))

                CalendarDialogTextField(
                    palette = palette,
                    value = link,
                    onValueChange = { link = it },
                    label = stringResource(R.string.cal_link_optional),
                    placeholder = "https://meet.google.com/…",
                    imeAction = ImeAction.Next
                )

                Spacer(Modifier.height(18.dp))

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.cal_time),
                        color = palette.muted,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "*",
                        color = palette.flagRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(10.dp))

                // above the pickers, not below: the point is to spare the user the decision, and a shortcut
                // offered after they've already opened the clock dial is one they've walked past
                TimeSuggestionStrip(
                    palette = palette,
                    suggestions = suggestions,
                    selectedDate = selectedDate,
                    selectedStart = startTime,
                    onPick = { suggestion ->
                        startTime = suggestion.startLabel
                        endTime = suggestion.endLabel
                        // date last keeps the times in place: changing it re-runs the suggester, and an empty strip
                        // mid-update would flicker the chip away under the user's finger
                        if (suggestion.movesDay(selectedDate)) {
                            onSuggestionDateChange(suggestion.date)
                        }
                    },
                    modifier = Modifier.padding(bottom = 14.dp),
                )

                if (suggestionNote != null && suggestions.isNotEmpty()) {
                    Text(
                        text = suggestionNote,
                        color = palette.muted,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        TimePickerField(
                            value = startTime,
                            label = stringResource(R.string.common_start),
                            placeholder = "09:00",
                            mutedColor = palette.muted,
                            accentColor = palette.lavender,
                            borderColor = palette.dialogBorder,
                            textColor = palette.ink,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                focusManager.clearFocus()
                                showStartPicker = true
                            }
                        )
                    }

                    Box(Modifier.weight(1f)) {
                        TimePickerField(
                            value = endTime,
                            label = stringResource(R.string.cal_end),
                            placeholder = "10:00",
                            mutedColor = palette.muted,
                            accentColor = palette.lavender,
                            borderColor = palette.dialogBorder,
                            textColor = palette.ink,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                focusManager.clearFocus()
                                showEndPicker = true
                            }
                        )
                    }
                }

                val timesProvided = startTime.isNotBlank() && endTime.isNotBlank()
                val endAfterStart = remember(startTime, endTime) {
                    val s = parseHourMinute(startTime)
                    val e = parseHourMinute(endTime)
                    if (s == null || e == null) true
                    else (e.first * 60 + e.second) > (s.first * 60 + s.second)
                }
                val timeError = when {
                    !timesProvided -> stringResource(R.string.cal_pick_times)
                    !endAfterStart -> stringResource(R.string.cal_end_after_start)
                    else -> null
                }
                if (timeError != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = timeError,
                        color = palette.flagRed,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = stringResource(R.string.cal_color),
                    color = palette.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(10.dp))

                val resolvedSelected = remember(selectedColor) {
                    resolveEventColor(selectedColor).key
                }
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    EventColors.chunked(4).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            row.forEach { option ->
                                ColorOption(
                                    color = option.accent,
                                    selected = resolvedSelected == option.key,
                                    onClick = { selectedColor = option.key }
                                )
                            }
                        }
                    }
                }

                // also add to a connection's calendar: the 'for me, and copy it to them' case. creation only,
                // re-sharing on edit would mean diffing previously sent copies, and silently duplicating
                // them is worse than not offering it at all
                if (!isEditing && shareTargets.isNotEmpty() && audience == EventAudience.Mine) {
                    Spacer(Modifier.height(22.dp))
                    AudiencePickerSection(
                        palette = palette,
                        targets = shareTargets,
                        selected = alsoAddFor,
                        heading = stringResource(R.string.cal_also_add_to),
                        required = false,
                        addToMyCalendar = true,
                        showMineToggle = false,
                        onToggle = { id ->
                            alsoAddFor = if (id in alsoAddFor) alsoAddFor - id else alsoAddFor + id
                        },
                        onToggleAddToMine = {},
                    )
                }

                Spacer(Modifier.height(28.dp))

                val canSave = canSaveForAudience(
                    hasTitle = title.isNotBlank(),
                    hasTimeError = timeError != null,
                    addToMine = addToMyCalendar,
                    selectedCount = alsoAddFor.size,
                    isTogetherMode = audience == EventAudience.Together,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // secondary, cancel
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = palette.pillBg),
                        elevation = null,
                    ) {
                        Text(stringResource(R.string.common_cancel), color = palette.ink, fontWeight = FontWeight.SemiBold)
                    }
                    // primary, add / save
                    Button(
                        enabled = canSave,
                        onClick = {
                            onConfirm(
                                title,
                                description,
                                startTime,
                                endTime,
                                location,
                                selectedColor,
                                link,
                                alsoAddFor,
                                addToMyCalendar,
                            )
                        },
                        modifier = Modifier.weight(1.4f).height(52.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = palette.lavender,
                            disabledContainerColor = palette.lavender.copy(alpha = 0.4f),
                        ),
                    ) {
                        // the label says where it's going. 'Add event' on a friend-only save would read as if it
                        // were landing on your own calendar
                        val targetName = shareTargets
                            .firstOrNull { it.userId in alsoAddFor }?.name
                        Text(
                            when {
                                isEditing -> stringResource(R.string.common_save_changes)
                                alsoAddFor.isEmpty() -> stringResource(R.string.qc_add_event)
                                !addToMyCalendar && alsoAddFor.size == 1 && targetName != null ->
                                    stringResource(R.string.cal_add_for, targetName)
                                !addToMyCalendar -> stringResource(R.string.cal_add_for_people, alsoAddFor.size)
                                alsoAddFor.size == 1 && targetName != null ->
                                    stringResource(R.string.cal_add_for_both)
                                else -> stringResource(R.string.cal_add_for_calendars, alsoAddFor.size + 1)
                            },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        val (h0, m0) = parseHourMinute(startTime) ?: (9 to 0)
        TimePickerDialog(
            title = stringResource(R.string.cal_select_start),
            initialHour = h0,
            initialMinute = m0,
            onConfirm = { h, m ->
                startTime = "%02d:%02d".format(h, m)
                // keep end after start: if it's empty or no longer later, push it an hour past the new start
                val startMins = h * 60 + m
                val endMins = parseHourMinute(endTime)?.let { it.first * 60 + it.second }
                if (endMins == null || endMins <= startMins) {
                    endTime = oneHourAfter(h, m)
                }
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }

    if (showEndPicker) {
        // open on the current end, else an hour after the start, else 10:00
        val (h0, m0) = parseHourMinute(endTime)
            ?: parseHourMinute(startTime)?.let { ((it.first + 1).coerceAtMost(23)) to it.second }
            ?: (10 to 0)
        TimePickerDialog(
            title = stringResource(R.string.cal_select_end),
            initialHour = h0,
            initialMinute = m0,
            onConfirm = { h, m ->
                val startMins = parseHourMinute(startTime)?.let { it.first * 60 + it.second }
                val endMins = h * 60 + m
                // never let the end land on or before the start, snap it forward
                endTime = if (startMins != null && endMins <= startMins) {
                    oneHourAfter(startMins / 60, startMins % 60)
                } else {
                    "%02d:%02d".format(h, m)
                }
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }
}

// who a new event is being created for
enum class EventAudience { Mine, Together }

// two-up switch at the top: is this event yours, or one you're making for someone else?
// everything below reads differently depending on the answer, which is why it sits above the
// form rather than under it. swipeable as well as tappable, matching the todo dialog and
// quick capture, so the same flick works in all three + flows
@Composable
private fun AudienceSwitcher(
    palette: CalendarPalette,
    audience: EventAudience,
    onSelect: (EventAudience) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.pillBg)
            // only on an actual change: onSelect resets the 'also add to my calendar' default, so
            // re-selecting the side you're already on would quietly undo that tick
            .swipeToSwitch(
                onSwipeLeft = { if (audience != EventAudience.Mine) onSelect(EventAudience.Mine) },
                onSwipeRight = { if (audience != EventAudience.Together) onSelect(EventAudience.Together) },
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        AudienceSegment(
            palette = palette,
            label = stringResource(R.string.cal_for_myself),
            icon = Icons.Rounded.Person,
            selected = audience == EventAudience.Mine,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(EventAudience.Mine) },
        )
        AudienceSegment(
            palette = palette,
            label = stringResource(R.string.cal_for_someone),
            icon = Icons.Rounded.Diversity3,
            selected = audience == EventAudience.Together,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(EventAudience.Together) },
        )
    }
}

@Composable
private fun AudienceSegment(
    palette: CalendarPalette,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // animated so a swipe glides the selection across, a colour that teleports mid-drag reads
    // as a glitch rather than as a control responding
    val fill by animateColorAsState(
        if (selected) palette.lavender else Color.Transparent,
        label = "audience_fill",
    )
    val content by animateColorAsState(
        if (selected) Color.White else palette.muted,
        label = "audience_content",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(fill)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = content,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

// pick connections for this event, used for both 'also add to' (optional, Mine mode) and
// 'who is this for' (required, Together mode). the trailing note is load-bearing, not
// decoration: people reasonably assume sharing an event means sharing a calendar, and saying
// plainly that they see only this one event is what stops it feeling like an overreach
@Composable
private fun AudiencePickerSection(
    palette: CalendarPalette,
    targets: List<CalendarViewModel.ShareTarget>,
    selected: Set<String>,
    heading: String,
    required: Boolean,
    addToMyCalendar: Boolean,
    showMineToggle: Boolean,
    onToggle: (String) -> Unit,
    onToggleAddToMine: (Boolean) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.Diversity3,
                contentDescription = null,
                tint = palette.lavender,
                modifier = Modifier.size(17.dp),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = heading,
                color = palette.ink,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            if (selected.isNotEmpty()) {
                Spacer(Modifier.width(7.dp))
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(palette.lavender)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "${selected.size}",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(targets, key = { it.userId }) { target ->
                val isOn = target.userId in selected
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(30.dp))
                        .background(if (isOn) palette.lavender else palette.pillBg)
                        .clickable { onToggle(target.userId) }
                        .padding(start = 5.dp, end = 12.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(
                                if (isOn) Color.White.copy(alpha = 0.25f)
                                else palette.lavender.copy(alpha = 0.16f),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isOn) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(15.dp),
                            )
                        } else {
                            Text(
                                text = target.name.take(1).uppercase(),
                                color = palette.lavender,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = target.name,
                        color = if (isOn) Color.White else palette.ink,
                        fontSize = 12.5.sp,
                        fontWeight = if (isOn) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        // in Together mode the picker is the one required field up here, so say so rather than
        // leaving the user staring at a disabled save button
        if (required && selected.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.cal_pick_person),
                color = palette.muted,
                fontSize = 11.sp,
            )
        }

        if (selected.isNotEmpty() && showMineToggle) {
            Spacer(Modifier.height(12.dp))

            // opting into your own calendar too. off by default here, the usual intent is it isn't yours
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.pillBg)
                    .clickable { onToggleAddToMine(!addToMyCalendar) }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (addToMyCalendar) palette.lavender
                            else Color.Transparent,
                        )
                        .border(
                            width = 1.5.dp,
                            color = if (addToMyCalendar) palette.lavender else palette.muted,
                            shape = RoundedCornerShape(6.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (addToMyCalendar) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.cal_add_mine_too),
                        color = palette.ink,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (addToMyCalendar) {
                            stringResource(R.string.cal_on_your_day)
                        } else {
                            stringResource(R.string.cal_only_they)
                        },
                        color = palette.muted,
                        fontSize = 11.sp,
                    )
                }
            }

        }

        // shown in both modes: once anyone is selected the user deserves to know how far this reaches
        if (selected.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.cal_they_see),
                color = palette.muted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

// an hour after the given time, clamped to 23:59 so it never wraps past midnight
private fun oneHourAfter(hour: Int, minute: Int): String {
    return if (hour + 1 >= 24) "23:59" else "%02d:%02d".format(hour + 1, minute)
}

private fun extractHourMinute(iso: String): String {
    val tIdx = iso.indexOf('T')
    if (tIdx < 0 || iso.length < tIdx + 6) return ""
    return iso.substring(tIdx + 1, tIdx + 6)
}

private fun parseHourMinute(hhmm: String): Pair<Int, Int>? {
    val parts = hhmm.split(":")
    val h = parts.getOrNull(0)?.toIntOrNull() ?: return null
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return null
    return h to m
}

// dialog text field
@Composable
fun CalendarDialogTextField(
    palette: CalendarPalette,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    imeAction: ImeAction = ImeAction.Default,
    onDone: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // caption label above the field, cleaner than a floating M3 label
        Text(
            text = label,
            color = palette.muted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = {
                Text(placeholder, color = palette.muted.copy(alpha = 0.5f), fontSize = 14.sp)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = palette.lavender,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = palette.lavender,
                focusedTextColor = palette.ink,
                unfocusedTextColor = palette.ink,
                focusedContainerColor = palette.pillBg.copy(alpha = 0.5f),
                unfocusedContainerColor = palette.pillBg.copy(alpha = 0.5f),
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(onDone = { onDone?.invoke() })
        )
    }
}
