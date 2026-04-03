package com.muradgalayev.brainbuddy.ui.todo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.muradgalayev.brainbuddy.ui.sharedcomponents.ColorOption
import com.muradgalayev.brainbuddy.ui.sharedcomponents.TimePickerDialog
import com.muradgalayev.brainbuddy.ui.sharedcomponents.TimePickerField


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
    val focusManager = LocalFocusManager.current
    var selectedCategory by remember { mutableStateOf("personal") }
    var selectedColor by remember { mutableStateOf("blue") }

    // Entrance animations
    val slideOffset = remember { Animatable(300f) }
    val cardAlpha = remember { Animatable(0f) }
    val scrimAlpha = remember { Animatable(0f) }

    // Staggered section visibility
    val sectionAlphas = remember { List(6) { Animatable(0f) } }
    val sectionOffsets = remember { List(6) { Animatable(30f) } }

    LaunchedEffect(Unit) {
        // Scrim fade in
        launch { scrimAlpha.animateTo(1f, tween(250)) }
        // Card slides up with spring
        launch {
            slideOffset.animateTo(
                0f,
                spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)
            )
        }
        launch { cardAlpha.animateTo(1f, tween(200)) }
        // Stagger each section
        sectionAlphas.forEachIndexed { i, anim ->
            launch {
                delay(150L + i * 60L)
                launch { anim.animateTo(1f, tween(300)) }
                launch {
                    sectionOffsets[i].animateTo(
                        0f,
                        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                    )
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(scrimAlpha.value),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset { IntOffset(0, slideOffset.value.toInt()) }
                    .graphicsLayer { alpha = cardAlpha.value }
                    .clip(RoundedCornerShape(32.dp))
                    .background(palette.cardBg)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    // Header — section 0
                    Box(
                        modifier = Modifier
                            .staggerAnim(sectionAlphas[0].value, sectionOffsets[0].value)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "New Task",
                                color = palette.ink,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(36.dp),
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = palette.pillBg
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = palette.muted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Text fields — section 1
                    Column(
                        modifier = Modifier
                            .staggerAnim(sectionAlphas[1].value, sectionOffsets[1].value)
                    ) {
                        DialogTextField(
                            palette = palette,
                            value = title,
                            onValueChange = { title = it },
                            label = "Task name",
                            placeholder = "e.g. Yoga practice",
                            imeAction = ImeAction.Next
                        )
                        Spacer(Modifier.height(16.dp))
                        DialogTextField(
                            palette = palette,
                            value = description,
                            onValueChange = { description = it },
                            label = "Description (optional)",
                            placeholder = "e.g. Morning stretch routine",
                            imeAction = ImeAction.Next
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Time section — section 2
                    Column(
                        modifier = Modifier
                            .staggerAnim(sectionAlphas[2].value, sectionOffsets[2].value)
                    ) {
                        SectionLabel(palette, "Time")
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimePickerField(
                                value = startTime,
                                label = "Start",
                                placeholder = "09:00",
                                mutedColor = palette.muted,
                                accentColor = palette.lavender,
                                borderColor = palette.dialogBorder,
                                textColor = palette.ink,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    focusManager.clearFocus()
                                    showStartPicker = true
                                }
                            )
                            TimePickerField(
                                value = startTime,
                                label = "Start",
                                placeholder = "09:00",
                                mutedColor = palette.muted,
                                accentColor = palette.lavender,
                                borderColor = palette.dialogBorder,
                                textColor = palette.ink,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    focusManager.clearFocus()
                                    showStartPicker = true
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Category — section 3
                    Column(
                        modifier = Modifier
                            .staggerAnim(sectionAlphas[3].value, sectionOffsets[3].value)
                    ) {
                        SectionLabel(palette, "Category")
                        Spacer(Modifier.height(10.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(end = 8.dp)
                        ) {
                            items(
                                listOf("work", "education", "personal", "sport", "health")
                            ) { category ->
                                CategoryChip(
                                    palette = palette,
                                    label = category.replaceFirstChar { it.uppercase() },
                                    selected = selectedCategory == category,
                                    onClick = { selectedCategory = category }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Color — section 4
                    Column(
                        modifier = Modifier
                            .staggerAnim(sectionAlphas[4].value, sectionOffsets[4].value)
                    ) {
                        SectionLabel(palette, "Color")
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ColorOption(
                                color = palette.taskRed,
                                selected = selectedColor == "red",
                                onClick = { selectedColor = "red" }
                            )
                            ColorOption(
                                color = palette.taskBlue,
                                selected = selectedColor == "blue",
                                onClick = { selectedColor = "blue" }
                            )
                            ColorOption(
                                color = palette.taskYellow,
                                selected = selectedColor == "yellow",
                                onClick = { selectedColor = "yellow" }
                            )
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    // Buttons — section 5
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggerAnim(sectionAlphas[5].value, sectionOffsets[5].value),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                "Cancel",
                                color = palette.muted,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    onConfirm(title, description, startTime, endTime, selectedCategory, selectedColor)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.lavender
                            ),
                            enabled = title.isNotBlank()
                        ) {
                            Text(
                                "Add Task",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTaskDialog(
    palette: TodoPalette,
    task: TaskEditData,
    onDismiss: () -> Unit,
    onSave: (id: String, title: String, description: String, startTime: String, endTime: String, priority: String, color: String, category: String) -> Unit,
    onDelete: (id: String) -> Unit
) {
    var title by remember { mutableStateOf(task.title) }
    var description by remember { mutableStateOf(task.description) }
    var startTime by remember { mutableStateOf(task.startTime) }
    var endTime by remember { mutableStateOf(task.endTime) }
    val focusManager = LocalFocusManager.current
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    var selectedColor by remember { mutableStateOf(task.color) }

    // Entrance animations
    val slideOffset = remember { Animatable(300f) }
    val cardAlpha = remember { Animatable(0f) }
    val scrimAlpha = remember { Animatable(0f) }

    val sectionAlphas = remember { List(5) { Animatable(0f) } }
    val sectionOffsets = remember { List(5) { Animatable(30f) } }

    LaunchedEffect(Unit) {
        launch { scrimAlpha.animateTo(1f, tween(250)) }
        launch {
            slideOffset.animateTo(
                0f,
                spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)
            )
        }
        launch { cardAlpha.animateTo(1f, tween(200)) }
        sectionAlphas.forEachIndexed { i, anim ->
            launch {
                delay(150L + i * 60L)
                launch { anim.animateTo(1f, tween(300)) }
                launch {
                    sectionOffsets[i].animateTo(
                        0f,
                        spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow)
                    )
                }
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(scrimAlpha.value),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .offset { IntOffset(0, slideOffset.value.toInt()) }
                    .graphicsLayer { alpha = cardAlpha.value }
                    .clip(RoundedCornerShape(32.dp))
                    .background(palette.cardBg)
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp)
                ) {
                    // Header — section 0
                    Box(
                        modifier = Modifier
                            .staggerAnim(sectionAlphas[0].value, sectionOffsets[0].value)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Edit Task",
                                color = palette.ink,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = { onDelete(task.id) },
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = palette.flagRed.copy(alpha = 0.1f)
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Delete task",
                                        tint = palette.flagRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = onDismiss,
                                    modifier = Modifier.size(36.dp),
                                    colors = IconButtonDefaults.iconButtonColors(
                                        containerColor = palette.pillBg
                                    )
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "Close",
                                        tint = palette.muted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Text fields — section 1
                    Column(
                        modifier = Modifier
                            .staggerAnim(sectionAlphas[1].value, sectionOffsets[1].value)
                    ) {
                        DialogTextField(
                            palette = palette,
                            value = title,
                            onValueChange = { title = it },
                            label = "Task name",
                            placeholder = "e.g. Yoga practice",
                            imeAction = ImeAction.Next
                        )
                        Spacer(Modifier.height(16.dp))
                        DialogTextField(
                            palette = palette,
                            value = description,
                            onValueChange = { description = it },
                            label = "Description (optional)",
                            placeholder = "e.g. Morning stretch routine",
                            imeAction = ImeAction.Next
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    // Time — section 2
                    Column(
                        modifier = Modifier
                            .staggerAnim(sectionAlphas[2].value, sectionOffsets[2].value)
                    ) {
                        SectionLabel(palette, "Time")
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimePickerField(
                                value = startTime,
                                label = "Start",
                                placeholder = "09:00",
                                mutedColor = palette.muted,
                                accentColor = palette.lavender,
                                borderColor = palette.dialogBorder,
                                textColor = palette.ink,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    focusManager.clearFocus()
                                    showStartPicker = true
                                }
                            )
                            TimePickerField(
                                value = startTime,
                                label = "Start",
                                placeholder = "09:00",
                                mutedColor = palette.muted,
                                accentColor = palette.lavender,
                                borderColor = palette.dialogBorder,
                                textColor = palette.ink,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    focusManager.clearFocus()
                                    showStartPicker = true
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // Color — section 3
                    Column(
                        modifier = Modifier
                            .staggerAnim(sectionAlphas[3].value, sectionOffsets[3].value)
                    ) {
                        SectionLabel(palette, "Color")
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ColorOption(
                                color = palette.taskRed,
                                selected = selectedColor == "red",
                                onClick = { selectedColor = "red" }
                            )
                            ColorOption(
                                color = palette.taskBlue,
                                selected = selectedColor == "blue",
                                onClick = { selectedColor = "blue" }
                            )
                            ColorOption(
                                color = palette.taskYellow,
                                selected = selectedColor == "yellow",
                                onClick = { selectedColor = "yellow" }
                            )
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    // Buttons — section 4
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .staggerAnim(sectionAlphas[4].value, sectionOffsets[4].value),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                "Cancel",
                                color = palette.muted,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    onSave(task.id, title, description, startTime, endTime, task.priority, selectedColor, task.category)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.lavender
                            ),
                            enabled = title.isNotBlank()
                        ) {
                            Text(
                                "Save",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
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

// ── Shared components ──────────────────────────────────────────────────

private fun Modifier.staggerAnim(alpha: Float, offsetY: Float): Modifier =
    this
        .graphicsLayer {
            this.alpha = alpha
            translationY = offsetY
        }

@Composable
private fun SectionLabel(palette: TodoPalette, text: String) {
    Text(
        text = text,
        color = palette.muted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.5.sp
    )
}


@Composable
private fun CategoryChip(
    palette: TodoPalette,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) palette.lavender else palette.pillBg,
        animationSpec = tween(250),
        label = "chipBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else palette.ink,
        animationSpec = tween(250),
        label = "chipText"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
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
        modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() })
    )
}

