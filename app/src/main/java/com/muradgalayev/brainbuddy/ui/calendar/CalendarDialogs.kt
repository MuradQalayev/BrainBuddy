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
import com.muradgalayev.brainbuddy.ui.sharedcomponents.ColorOption
import com.muradgalayev.brainbuddy.ui.sharedcomponents.TimePickerDialog
import com.muradgalayev.brainbuddy.ui.sharedcomponents.TimePickerField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    palette: CalendarPalette,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        description: String,
        startTime: String,
        endTime: String,
        category: String,
        color: String
    ) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf("personal") }
    var selectedColor by remember { mutableStateOf("blue") }

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
                    text = "New Task",
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
                    label = "Task name",
                    placeholder = "e.g. Yoga practice",
                    imeAction = ImeAction.Next
                )

                Spacer(Modifier.height(14.dp))

                CalendarDialogTextField(
                    palette = palette,
                    value = description,
                    onValueChange = { description = it },
                    label = "Description (optional)",
                    placeholder = "e.g. Morning stretch routine",
                    imeAction = ImeAction.Next
                )

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "Time",
                    color = palette.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

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

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "Category",
                    color = palette.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(10.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf("work", "education", "personal", "sport", "health")) { category ->
                        val selected = selectedCategory == category
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) palette.lavender else palette.pillBg)
                                .clickable { selectedCategory = category }
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = category.replaceFirstChar { it.uppercase() },
                                color = if (selected) Color.White else palette.ink,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Text(
                    text = "Color",
                    color = palette.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(10.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ColorOption(
                        color = Color(0xFFC41E3A),
                        selected = selectedColor == "red",
                        onClick = { selectedColor = "red" }
                    )
                    ColorOption(
                        color = Color(0xFF82C8FF),
                        selected = selectedColor == "blue",
                        onClick = { selectedColor = "blue" }
                    )
                    ColorOption(
                        color = Color(0xFFFFF9B9),
                        selected = selectedColor == "yellow",
                        onClick = { selectedColor = "yellow" }
                    )
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

                    TextButton(
                        onClick = {
                            if (title.isNotBlank()) {
                                onConfirm(
                                    title,
                                    description,
                                    startTime,
                                    endTime,
                                    selectedCategory,
                                    selectedColor
                                )
                            }
                        }
                    ) {
                        Text("Add Task", color = palette.lavender, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showStartPicker) {
        TimePickerDialog(
            title = "Select start time",
            initialHour = 9,
            initialMinute = 0,
            onConfirm = { h, m ->
                startTime = "%02d:%02d".format(h, m)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }

    if (showEndPicker) {
        TimePickerDialog(
            title = "Select end time",
            initialHour = 10,
            initialMinute = 0,
            onConfirm = { h, m ->
                endTime = "%02d:%02d".format(h, m)
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }
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
