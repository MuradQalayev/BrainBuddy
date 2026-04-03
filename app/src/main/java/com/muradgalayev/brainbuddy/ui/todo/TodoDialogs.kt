package com.muradgalayev.brainbuddy.ui.todo

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    palette: TodoPalette,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        description: String,
        startTime: String,
        endTime: String,
        category: String
    ) -> Unit) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var selectedCategory by remember { mutableStateOf("personal") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = palette.cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "New Task",
                    color = palette.ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(20.dp))

                DialogTextField(
                    palette = palette,
                    value = title,
                    onValueChange = { title = it },
                    label = "Task name",
                    placeholder = "e.g. Yoga practice",
                    imeAction = ImeAction.Next
                )
                Spacer(Modifier.height(14.dp))

                DialogTextField(
                    palette = palette,
                    value = description,
                    onValueChange = { description = it },
                    label = "Description (optional)",
                    placeholder = "e.g. Morning stretch routine",
                    imeAction = ImeAction.Next
                )
                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        DialogTextField(
                            palette = palette,
                            value = startTime,
                            onValueChange = {},
                            label = "Start",
                            placeholder = "09:00",
                            imeAction = ImeAction.Next,
                            readOnly = true
                        )

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    focusManager.clearFocus()
                                    showStartPicker = true
                                }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        DialogTextField(
                            palette = palette,
                            value = endTime,
                            onValueChange = {},
                            label = "End",
                            placeholder = "10:00",
                            imeAction = ImeAction.Done,
                            readOnly = true
                        )

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    focusManager.clearFocus()
                                    showEndPicker = true
                                }
                        )
                    }
                }


                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Category",
                    color = palette.muted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(24.dp))

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(
                        listOf("work", "education", "personal", "sport", "health")
                    ) { category ->
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
                                onConfirm(title, description, startTime, endTime, selectedCategory)
                            }
                        }
                    ) {
                        Text("Add Task", color = palette.lavender, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(14.dp))

            }
        }

    }
    if (showStartPicker) {
        val startPickerState = rememberTimePickerState(
            initialHour = 9,
            initialMinute = 0,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startTime = "%02d:%02d".format(
                            startPickerState.hour,
                            startPickerState.minute
                        )
                        showStartPicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) {
                    Text("Cancel")
                }
            },
            text = {
                TimePicker(state = startPickerState)
            }
        )
    }
    if (showEndPicker) {
        val endPickerState = rememberTimePickerState(
            initialHour = 10,
            initialMinute = 0,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endTime = "%02d:%02d".format(
                            endPickerState.hour,
                            endPickerState.minute
                        )
                        showEndPicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) {
                    Text("Cancel")
                }
            },
            text = {
                TimePicker(state = endPickerState)
            }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskDialog(
    palette: TodoPalette,
    task: TaskEditData,
    onDismiss: () -> Unit,
    onSave: (id: String, title: String, description: String, startTime: String, endTime: String, priority: String, color: String,category: String) -> Unit,
    onDelete: (id: String) -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description) }
    var startTime by remember { mutableStateOf(task.startTime) }
    var endTime by remember { mutableStateOf(task.endTime) }
    val focusManager = LocalFocusManager.current
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = palette.cardBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Task",
                        color = palette.ink,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { onDelete(task.id) }) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "Delete task",
                            tint = palette.flagRed.copy(alpha = 0.8f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                DialogTextField(
                    palette = palette,
                    value = title,
                    onValueChange = { title = it },
                    label = "Task name",
                    placeholder = "e.g. Yoga practice",
                    imeAction = ImeAction.Next
                )
                Spacer(Modifier.height(14.dp))

                DialogTextField(
                    palette = palette,
                    value = description,
                    onValueChange = { description = it },
                    label = "Description (optional)",
                    placeholder = "e.g. Morning stretch routine",
                    imeAction = ImeAction.Next
                )
                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(modifier = Modifier.weight(1f)) {
                        DialogTextField(
                            palette = palette,
                            value = startTime,
                            onValueChange = {},
                            label = "Start",
                            placeholder = "09:00",
                            imeAction = ImeAction.Next,
                            readOnly = true
                        )

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    focusManager.clearFocus()
                                    showStartPicker = true
                                }
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        DialogTextField(
                            palette = palette,
                            value = endTime,
                            onValueChange = {},
                            label = "End",
                            placeholder = "10:00",
                            imeAction = ImeAction.Done,
                            readOnly = true
                        )

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable {
                                    focusManager.clearFocus()
                                    showEndPicker = true
                                }
                        )
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
                    TextButton(
                        onClick = {
                            if (title.isNotBlank()) {
                                onSave(task.id, title, description, startTime, endTime, task.priority, task.color, task.category)
                            }
                        }
                    ) {
                        Text("Save", color = palette.lavender, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

    }
    if (showStartPicker) {
        val startPickerState = rememberTimePickerState(
            initialHour = 9,
            initialMinute = 0,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showStartPicker = false },
            title = { Text("Select start time") },
            confirmButton = {
                TextButton(
                    onClick = {
                        startTime = "%02d:%02d".format(
                            startPickerState.hour,
                            startPickerState.minute
                        )
                        showStartPicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) {
                    Text("Cancel")
                }
            },
            text = {
                TimePicker(state = startPickerState)
            }
        )
    }
    if (showEndPicker) {
        val endPickerState = rememberTimePickerState(
            initialHour = 10,
            initialMinute = 0,
            is24Hour = true
        )

        AlertDialog(
            onDismissRequest = { showEndPicker = false },
            title = { Text("Select end time") },
            confirmButton = {
                TextButton(
                    onClick = {
                        endTime = "%02d:%02d".format(
                            endPickerState.hour,
                            endPickerState.minute
                        )
                        showEndPicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) {
                    Text("Cancel")
                }
            },
            text = {
                TimePicker(state = endPickerState)
            }
        )
    }
}

@Composable
fun DialogTextField(
    palette: TodoPalette,
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    imeAction: ImeAction = ImeAction.Default,
    readOnly: Boolean = false,
    onDone: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 13.sp) },
        placeholder = { Text(placeholder, color = palette.muted.copy(alpha = 0.5f), fontSize = 14.sp) },
        readOnly = readOnly,
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
