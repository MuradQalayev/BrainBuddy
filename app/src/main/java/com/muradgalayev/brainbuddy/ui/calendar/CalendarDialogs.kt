package com.muradgalayev.brainbuddy.ui.calendar

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import com.muradgalayev.brainbuddy.ui.sharedcomponents.ColorOption
import com.muradgalayev.brainbuddy.ui.sharedcomponents.TimePickerDialog
import com.muradgalayev.brainbuddy.ui.sharedcomponents.TimePickerField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    palette: CalendarPalette,
    selectedDate: LocalDate,
    existingEvent: CalendarEvent? = null,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        description: String,
        startTime: String,
        endTime: String,
        location: String,
        color: String,
        link: String,
    ) -> Unit
) {
    val isEditing = existingEvent != null
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
                Text(
                    text = if (isEditing) "Edit Event" else "New Event",
                    color = palette.ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = formattedDate,
                    color = palette.lavender,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(20.dp))

                CalendarDialogTextField(
                    palette = palette,
                    value = title,
                    onValueChange = { title = it },
                    label = "Event name",
                    placeholder = "e.g. Team standup",
                    imeAction = ImeAction.Next
                )

                Spacer(Modifier.height(14.dp))

                CalendarDialogTextField(
                    palette = palette,
                    value = description,
                    onValueChange = { description = it },
                    label = "Description (optional)",
                    placeholder = "e.g. Weekly sync with the team",
                    imeAction = ImeAction.Next
                )

                Spacer(Modifier.height(14.dp))

                CalendarDialogTextField(
                    palette = palette,
                    value = location,
                    onValueChange = { location = it },
                    label = "Location (optional)",
                    placeholder = "e.g. Office, Zoom, Park…",
                    imeAction = ImeAction.Next
                )

                Spacer(Modifier.height(14.dp))

                CalendarDialogTextField(
                    palette = palette,
                    value = link,
                    onValueChange = { link = it },
                    label = "Link (optional)",
                    placeholder = "https://meet.google.com/…",
                    imeAction = ImeAction.Next
                )

                Spacer(Modifier.height(18.dp))

                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        text = "Time",
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

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)) {
                        TimePickerField(
                            value = startTime,
                            label = "Start",
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
                            label = "End",
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
                    !timesProvided -> "Pick a start and end time"
                    !endAfterStart -> "End time must be after start time"
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
                    text = "Color",
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

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = palette.muted, fontWeight = FontWeight.Medium)
                    }

                    Spacer(Modifier.width(8.dp))

                    val canSave = title.isNotBlank() && timeError == null
                    TextButton(
                        enabled = canSave,
                        onClick = {
                            onConfirm(
                                title,
                                description,
                                startTime,
                                endTime,
                                location,
                                selectedColor,
                                link
                            )
                        }
                    ) {
                        Text(
                            if (isEditing) "Save Changes" else "Add Event",
                            color = if (canSave) palette.lavender else palette.muted.copy(alpha = 0.5f),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        val (h0, m0) = parseHourMinute(startTime) ?: (9 to 0)
        TimePickerDialog(
            title = "Select start time",
            initialHour = h0,
            initialMinute = m0,
            onConfirm = { h, m ->
                startTime = "%02d:%02d".format(h, m)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }

    if (showEndPicker) {
        val (h0, m0) = parseHourMinute(endTime) ?: (10 to 0)
        TimePickerDialog(
            title = "Select end time",
            initialHour = h0,
            initialMinute = m0,
            onConfirm = { h, m ->
                endTime = "%02d:%02d".format(h, m)
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }
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

/* ── Dialog TextField ── */
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
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        placeholder = { Text(placeholder, color = palette.muted.copy(alpha = 0.5f), fontSize = 14.sp) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = palette.lavender,
            unfocusedBorderColor = palette.dialogBorder,
            focusedLabelColor = palette.lavender,
            unfocusedLabelColor = palette.muted,
            cursorColor = palette.lavender,
            focusedTextColor = palette.ink,
            unfocusedTextColor = palette.ink,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        ),
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() })
    )
}
