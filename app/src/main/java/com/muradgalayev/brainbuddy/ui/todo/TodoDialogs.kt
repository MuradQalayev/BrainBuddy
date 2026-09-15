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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.muradgalayev.brainbuddy.ui.together.NoConnectionsNotice
import com.muradgalayev.brainbuddy.ui.sharedcomponents.ColorOption
import com.muradgalayev.brainbuddy.ui.sharedcomponents.TimePickerDialog
import com.muradgalayev.brainbuddy.ui.sharedcomponents.TimePickerField
import com.muradgalayev.brainbuddy.ui.sharedcomponents.swipeToSwitch
import com.muradgalayev.brainbuddy.data.local.entity.TodoPriority
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTaskDialog(
    palette: TodoPalette,
    // connections who have opened their list to me. empty still shows the switcher
    shareTargets: List<TodoViewModel.ShareTarget> = emptyList(),
    // opens the add-connection flow from the empty-state notice
    onConnectPeople: () -> Unit = {},
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        description: String,
        startTime: String,
        endTime: String,
        category: String,
        color: String,
        date: String,
        priority: TodoPriority,
        alsoAddFor: Set<String>,
        addToMyList: Boolean,
    ) -> Unit
) {
    var audience by remember { mutableStateOf(TodoAudience.Mine) }
    var alsoAddFor by remember { mutableStateOf(emptySet<String>()) }
    // always true in Mine mode, opt-in in Together where the task usually isn't yours
    var addToMyList by remember { mutableStateOf(true) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("") }
    var endTime by remember { mutableStateOf("") }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    var selectedCategory by remember { mutableStateOf("personal") }
    var selectedColor by remember { mutableStateOf("blue") }
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var selectedPriority by remember { mutableStateOf(TodoPriority.MEDIUM) }
    var showMoreOptions by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // entrance animations
    val slideOffset = remember { Animatable(300f) }
    val cardAlpha = remember { Animatable(0f) }
    val scrimAlpha = remember { Animatable(0f) }

    // staggered section visibility
    val sectionAlphas = remember { List(6) { Animatable(0f) } }
    val sectionOffsets = remember { List(6) { Animatable(30f) } }

    LaunchedEffect(Unit) {
        // scrim fade in
        launch { scrimAlpha.animateTo(1f, tween(250)) }
        // card slides up with a spring
        launch {
            slideOffset.animateTo(
                0f,
                spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow)
            )
        }
        launch { cardAlpha.animateTo(1f, tween(200)) }
        // stagger each section
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
                .alpha(scrimAlpha.value)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .heightIn(max = 720.dp)
                    .offset { IntOffset(0, slideOffset.value.toInt()) }
                    .graphicsLayer { alpha = cardAlpha.value }
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 22.dp, bottomEnd = 22.dp))
                    .background(palette.cardBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 14.dp)
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(42.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(palette.muted.copy(alpha = .32f)),
                    )
                    Spacer(Modifier.height(14.dp))
                    // header, section 0
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
                                text = if (audience == TodoAudience.Together) {
                                    stringResource(R.string.todo_task_for_someone)
                                } else {
                                    stringResource(R.string.todo_new_task)
                                },
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
                                    contentDescription = stringResource(R.string.common_close),
                                    tint = palette.muted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // who is this for, above the form like the calendar dialog: the audience changes what the
                    // task is, so it isn't a detail to settle at the end. shown even with nobody connected,
                    // picking 'For someone' then explains the one missing step
                    Spacer(Modifier.height(18.dp))
                    TodoAudienceSwitcher(
                        palette = palette,
                        audience = audience,
                        onSelect = { next ->
                            audience = next
                            addToMyList = next == TodoAudience.Mine
                            if (next == TodoAudience.Mine) alsoAddFor = emptySet()
                        },
                    )
                    if (audience == TodoAudience.Together) {
                        Spacer(Modifier.height(16.dp))
                        if (shareTargets.isEmpty()) {
                            NoConnectionsNotice(
                                kind = com.muradgalayev.brainbuddy.ui.together.SharedItemKind.TASK,
                                accent = palette.lavender,
                                ink = palette.ink,
                                muted = palette.muted,
                                onConnect = {
                                    // close first, the dialog sits above the nav host and would strand itself over the new screen
                                    onDismiss()
                                    onConnectPeople()
                                },
                            )
                        } else {
                            TodoAudiencePicker(
                                palette = palette,
                                targets = shareTargets,
                                selected = alsoAddFor,
                                heading = stringResource(R.string.cal_who_for),
                                required = true,
                                addToMyList = addToMyList,
                                showMineToggle = true,
                                onToggle = { id ->
                                    alsoAddFor =
                                        if (id in alsoAddFor) alsoAddFor - id else alsoAddFor + id
                                },
                                onToggleAddToMine = { addToMyList = it },
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // text fields, section 1
                    Column(
                        modifier = Modifier
                            .staggerAnim(sectionAlphas[1].value, sectionOffsets[1].value)
                    ) {
                        DialogTextField(
                            palette = palette,
                            value = title,
                            onValueChange = { title = it },
                            label = stringResource(R.string.todo_task_name),
                            placeholder = stringResource(R.string.todo_task_name_hint),
                            imeAction = ImeAction.Next
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    SectionLabel(palette, stringResource(R.string.todo_date))
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            CategoryChip(palette, stringResource(R.string.common_today), selectedDate == LocalDate.now()) {
                                selectedDate = LocalDate.now()
                            }
                        }
                        item {
                            CategoryChip(palette, stringResource(R.string.common_tomorrow), selectedDate == LocalDate.now().plusDays(1)) {
                                selectedDate = LocalDate.now().plusDays(1)
                            }
                        }
                        item {
                            CategoryChip(
                                palette,
                                if (selectedDate in listOf(LocalDate.now(), LocalDate.now().plusDays(1)))
                                    stringResource(R.string.todo_pick_date) else selectedDate.format(DateTimeFormatter.ofPattern("d MMM")),
                                selectedDate > LocalDate.now().plusDays(1) || selectedDate < LocalDate.now(),
                            ) {
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, day -> selectedDate = LocalDate.of(year, month + 1, day) },
                                    selectedDate.year,
                                    selectedDate.monthValue - 1,
                                    selectedDate.dayOfMonth,
                                ).show()
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    SectionLabel(palette, stringResource(R.string.common_priority))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TodoPriority.entries.forEach { priority ->
                            CategoryChip(
                                palette,
                                priority.name.lowercase().replaceFirstChar(Char::uppercase),
                                selectedPriority == priority,
                            ) { selectedPriority = priority }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // time, section 2
                    Column(
                        modifier = Modifier
                            .staggerAnim(sectionAlphas[2].value, sectionOffsets[2].value)
                    ) {
                        SectionLabel(palette, stringResource(R.string.cal_time))
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimePickerField(
                                value = startTime,
                                label = stringResource(R.string.common_start),
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
                                value = endTime,
                                label = stringResource(R.string.cal_end),
                                placeholder = "10:00",
                                mutedColor = palette.muted,
                                accentColor = palette.lavender,
                                borderColor = palette.dialogBorder,
                                textColor = palette.ink,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    focusManager.clearFocus()
                                    showEndPicker = true
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    // category, section 3
                    Column(
                        modifier = Modifier
                            .staggerAnim(sectionAlphas[3].value, sectionOffsets[3].value)
                    ) {
                        SectionLabel(palette, stringResource(R.string.todo_category))
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

                    TextButton(onClick = { showMoreOptions = !showMoreOptions }) {
                        Text(if (showMoreOptions) stringResource(R.string.todo_hide_options) else stringResource(R.string.common_more_options))
                    }
                    AnimatedVisibility(showMoreOptions) {
                        Column {
                            DialogTextField(
                                palette = palette,
                                value = description,
                                onValueChange = { description = it },
                                label = stringResource(R.string.cal_description_optional),
                                placeholder = stringResource(R.string.todo_description_hint),
                                imeAction = ImeAction.Done,
                            )
                            Spacer(Modifier.height(18.dp))
                            SectionLabel(palette, stringResource(R.string.cal_color))
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                ColorOption(palette.taskRed, selectedColor == "red") { selectedColor = "red" }
                                ColorOption(palette.taskBlue, selectedColor == "blue") { selectedColor = "blue" }
                                ColorOption(palette.taskYellow, selectedColor == "yellow") { selectedColor = "yellow" }
                            }
                        }
                    }

                    // also add to a connection's list: the 'for me, and copy it to them' case, as in the calendar
                    if (shareTargets.isNotEmpty() && audience == TodoAudience.Mine) {
                        Spacer(Modifier.height(22.dp))
                        TodoAudiencePicker(
                            palette = palette,
                            targets = shareTargets,
                            selected = alsoAddFor,
                            heading = stringResource(R.string.cal_also_add_to),
                            required = false,
                            addToMyList = true,
                            showMineToggle = false,
                            onToggle = { id ->
                                alsoAddFor =
                                    if (id in alsoAddFor) alsoAddFor - id else alsoAddFor + id
                            },
                            onToggleAddToMine = {},
                        )
                    }

                    Spacer(Modifier.height(28.dp))

                    // buttons, section 5
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
                                stringResource(R.string.common_cancel),
                                color = palette.muted,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    val safeStart = startTime.takeIf(String::isNotBlank)?.let {
                                        val parts = it.split(":")
                                        normalizedTaskStart(parts.getOrNull(0)?.toIntOrNull() ?: 9, parts.getOrNull(1)?.toIntOrNull() ?: 0)
                                    }.orEmpty()
                                    onConfirm(title, description, safeStart, ensureEndAfterStart(safeStart, endTime), selectedCategory, selectedColor, selectedDate.toString(), selectedPriority, alsoAddFor, addToMyList)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = palette.lavender
                            ),
                            enabled = com.muradgalayev.brainbuddy.ui.together.canSaveForAudience(
                                hasTitle = title.isNotBlank(),
                                hasTimeError = false,
                                addToMine = addToMyList,
                                selectedCount = alsoAddFor.size,
                                isTogetherMode = audience == TodoAudience.Together,
                            )
                        ) {
                            val targetName = shareTargets
                                .firstOrNull { it.userId in alsoAddFor }?.name
                            Text(
                                when {
                                    alsoAddFor.isEmpty() -> stringResource(R.string.todo_add_task_btn)
                                    !addToMyList && alsoAddFor.size == 1 && targetName != null ->
                                        stringResource(R.string.cal_add_for, targetName)
                                    !addToMyList -> stringResource(R.string.cal_add_for_people, alsoAddFor.size)
                                    alsoAddFor.size == 1 -> stringResource(R.string.cal_add_for_both)
                                    else -> stringResource(R.string.todo_add_for_lists, alsoAddFor.size + 1)
                                },
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
        // opens on what's already there. reopening the dial back at 09:00 reads as the app having
        // forgotten, and made a five-minute correction mean dialling the whole way round again
        val (h0, m0) = parseHourMinute(startTime) ?: (9 to 0)
        TimePickerDialog(
            title = stringResource(R.string.cal_select_start),
            initialHour = h0,
            initialMinute = m0,
            onConfirm = { h, m ->
                startTime = normalizedTaskStart(h, m)
                endTime = ensureEndAfterStart(startTime, endTime)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        // current end, else an hour after the start, else 10:00
        val (h0, m0) = parseHourMinute(endTime)
            ?: parseHourMinute(startTime)?.let { ((it.first + 1).coerceAtMost(23)) to it.second }
            ?: (10 to 0)
        TimePickerDialog(
            title = stringResource(R.string.cal_select_end),
            initialHour = h0,
            initialMinute = m0,
            onConfirm = { h, m ->
                endTime = ensureEndAfterStart(startTime, "%02d:%02d".format(h, m))
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
    onSave: (id: String, title: String, description: String, startTime: String, endTime: String, priority: String, color: String, category: String, date: String) -> Unit,
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
    var selectedCategory by remember { mutableStateOf(task.category) }
    var selectedPriority by remember {
        mutableStateOf(TodoPriority.entries.firstOrNull { it.name == task.priority } ?: TodoPriority.MEDIUM)
    }
    var selectedDate by remember { mutableStateOf(runCatching { LocalDate.parse(task.date) }.getOrDefault(LocalDate.now())) }
    var showMoreOptions by remember { mutableStateOf(task.description.isNotBlank()) }
    val context = LocalContext.current

    // entrance animations
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
                .alpha(scrimAlpha.value)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .heightIn(max = 720.dp)
                    .offset { IntOffset(0, slideOffset.value.toInt()) }
                    .graphicsLayer { alpha = cardAlpha.value }
                    .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 22.dp, bottomEnd = 22.dp))
                    .background(palette.cardBg)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    )
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 14.dp)
                ) {
                    Box(
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .width(42.dp)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(palette.muted.copy(alpha = .32f)),
                    )
                    Spacer(Modifier.height(14.dp))
                    // header, section 0
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
                                text = stringResource(R.string.todo_edit_task),
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
                                        contentDescription = stringResource(R.string.todo_delete_task),
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
                                        contentDescription = stringResource(R.string.common_close),
                                        tint = palette.muted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // text fields, section 1
                    Column(
                        modifier = Modifier
                            .staggerAnim(sectionAlphas[1].value, sectionOffsets[1].value)
                    ) {
                        DialogTextField(
                            palette = palette,
                            value = title,
                            onValueChange = { title = it },
                            label = stringResource(R.string.todo_task_name),
                            placeholder = stringResource(R.string.todo_task_name_hint),
                            imeAction = ImeAction.Next
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    SectionLabel(palette, stringResource(R.string.todo_date))
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item { CategoryChip(palette, stringResource(R.string.common_today), selectedDate == LocalDate.now()) { selectedDate = LocalDate.now() } }
                        item { CategoryChip(palette, stringResource(R.string.common_tomorrow), selectedDate == LocalDate.now().plusDays(1)) { selectedDate = LocalDate.now().plusDays(1) } }
                        item {
                            CategoryChip(
                                palette,
                                if (selectedDate in listOf(LocalDate.now(), LocalDate.now().plusDays(1)))
                                    stringResource(R.string.todo_pick_date) else selectedDate.format(DateTimeFormatter.ofPattern("d MMM")),
                                selectedDate > LocalDate.now().plusDays(1) || selectedDate < LocalDate.now(),
                            ) {
                                android.app.DatePickerDialog(
                                    context,
                                    { _, year, month, day -> selectedDate = LocalDate.of(year, month + 1, day) },
                                    selectedDate.year,
                                    selectedDate.monthValue - 1,
                                    selectedDate.dayOfMonth,
                                ).show()
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    SectionLabel(palette, stringResource(R.string.common_priority))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TodoPriority.entries.forEach { priority ->
                            CategoryChip(
                                palette,
                                priority.name.lowercase().replaceFirstChar(Char::uppercase),
                                selectedPriority == priority,
                            ) { selectedPriority = priority }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // time, section 2
                    Column(
                        modifier = Modifier
                            .staggerAnim(sectionAlphas[2].value, sectionOffsets[2].value)
                    ) {
                        SectionLabel(palette, stringResource(R.string.cal_time))
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimePickerField(
                                value = startTime,
                                label = stringResource(R.string.common_start),
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
                                value = endTime,
                                label = stringResource(R.string.cal_end),
                                placeholder = "10:00",
                                mutedColor = palette.muted,
                                accentColor = palette.lavender,
                                borderColor = palette.dialogBorder,
                                textColor = palette.ink,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    focusManager.clearFocus()
                                    showEndPicker = true
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    SectionLabel(palette, stringResource(R.string.todo_category))
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("work", "education", "personal", "sport", "health")) { category ->
                            CategoryChip(
                                palette,
                                category.replaceFirstChar(Char::uppercase),
                                selectedCategory == category,
                            ) { selectedCategory = category }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    TextButton(onClick = { showMoreOptions = !showMoreOptions }) {
                        Text(if (showMoreOptions) stringResource(R.string.todo_hide_options) else stringResource(R.string.common_more_options))
                    }
                    AnimatedVisibility(showMoreOptions) {
                        Column {
                            DialogTextField(
                                palette = palette,
                                value = description,
                                onValueChange = { description = it },
                                label = stringResource(R.string.cal_description_optional),
                                placeholder = stringResource(R.string.todo_description_hint),
                                imeAction = ImeAction.Done,
                            )
                            Spacer(Modifier.height(18.dp))
                            SectionLabel(palette, stringResource(R.string.cal_color))
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                ColorOption(palette.taskRed, selectedColor == "red") { selectedColor = "red" }
                                ColorOption(palette.taskBlue, selectedColor == "blue") { selectedColor = "blue" }
                                ColorOption(palette.taskYellow, selectedColor == "yellow") { selectedColor = "yellow" }
                            }
                        }
                    }

                    Spacer(Modifier.height(28.dp))

                    // buttons, section 4
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
                                stringResource(R.string.common_cancel),
                                color = palette.muted,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp
                            )
                        }
                        Button(
                            onClick = {
                                if (title.isNotBlank()) {
                                    val safeStart = startTime.takeIf(String::isNotBlank)?.let {
                                        val parts = it.split(":")
                                        normalizedTaskStart(parts.getOrNull(0)?.toIntOrNull() ?: 9, parts.getOrNull(1)?.toIntOrNull() ?: 0)
                                    }.orEmpty()
                                    onSave(task.id, title, description, safeStart, ensureEndAfterStart(safeStart, endTime), selectedPriority.name, selectedColor, selectedCategory, selectedDate.toString())
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
                                stringResource(R.string.common_save),
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
        // opens on what's already there. reopening the dial back at 09:00 reads as the app having
        // forgotten, and made a five-minute correction mean dialling the whole way round again
        val (h0, m0) = parseHourMinute(startTime) ?: (9 to 0)
        TimePickerDialog(
            title = stringResource(R.string.cal_select_start),
            initialHour = h0,
            initialMinute = m0,
            onConfirm = { h, m ->
                startTime = normalizedTaskStart(h, m)
                endTime = ensureEndAfterStart(startTime, endTime)
                showStartPicker = false
            },
            onDismiss = { showStartPicker = false }
        )
    }
    if (showEndPicker) {
        // current end, else an hour after the start, else 10:00
        val (h0, m0) = parseHourMinute(endTime)
            ?: parseHourMinute(startTime)?.let { ((it.first + 1).coerceAtMost(23)) to it.second }
            ?: (10 to 0)
        TimePickerDialog(
            title = stringResource(R.string.cal_select_end),
            initialHour = h0,
            initialMinute = m0,
            onConfirm = { h, m ->
                endTime = ensureEndAfterStart(startTime, "%02d:%02d".format(h, m))
                showEndPicker = false
            },
            onDismiss = { showEndPicker = false }
        )
    }
}

// shared components

private fun Modifier.staggerAnim(alpha: Float, offsetY: Float): Modifier =
    this
        .graphicsLayer {
            this.alpha = alpha
            translationY = offsetY
        }

// HH:mm to (hour, minute), or null when it's blank or malformed
private fun parseHourMinute(value: String): Pair<Int, Int>? {
    val parts = value.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].trim().toIntOrNull() ?: return null
    val minute = parts[1].trim().toIntOrNull() ?: return null
    if (hour !in 0..23 || minute !in 0..59) return null
    return hour to minute
}

private fun normalizedTaskStart(hour: Int, minute: Int): String {
    val total = (hour * 60 + minute).coerceAtMost(23 * 60 + 29)
    return "%02d:%02d".format(total / 60, total % 60)
}

private fun ensureEndAfterStart(start: String, proposedEnd: String): String {
    fun minutes(value: String): Int? {
        val parts = value.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        return hour * 60 + minute
    }

    val startMinutes = minutes(start) ?: return proposedEnd
    val endMinutes = minutes(proposedEnd)
    if (endMinutes != null && endMinutes > startMinutes) return proposedEnd

    // keep tasks inside the same day, so near midnight use the latest possible end
    val corrected = (startMinutes + 30).coerceAtMost(23 * 60 + 59)
    return "%02d:%02d".format(corrected / 60, corrected % 60)
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

// Myndora Together: who is this task for?

// whether a new task is mine, or one I'm creating for a connection
enum class TodoAudience { Mine, Together }

// two-up switch at the top of the add dialog, mirroring the calendar's AudienceSwitcher so
// the two + flows behave identically. that includes the swipe: right for someone else,
// left for myself
@Composable
private fun TodoAudienceSwitcher(
    palette: TodoPalette,
    audience: TodoAudience,
    onSelect: (TodoAudience) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.pillBg)
            // only on an actual change: onSelect resets the 'also add to my list' default, so
            // re-selecting the side you're already on would quietly undo that tick
            .swipeToSwitch(
                onSwipeLeft = { if (audience != TodoAudience.Mine) onSelect(TodoAudience.Mine) },
                onSwipeRight = { if (audience != TodoAudience.Together) onSelect(TodoAudience.Together) },
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TodoAudienceSegment(
            palette = palette,
            label = stringResource(R.string.cal_for_myself),
            icon = Icons.Rounded.Person,
            selected = audience == TodoAudience.Mine,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(TodoAudience.Mine) },
        )
        TodoAudienceSegment(
            palette = palette,
            label = stringResource(R.string.cal_for_someone),
            icon = Icons.Rounded.Diversity3,
            selected = audience == TodoAudience.Together,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(TodoAudience.Together) },
        )
    }
}

@Composable
private fun TodoAudienceSegment(
    palette: TodoPalette,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    // animated for the same reason as the calendar's: a swipe should glide, not teleport
    val fill by animateColorAsState(
        if (selected) palette.lavender else Color.Transparent,
        label = "todo_audience_fill",
    )
    val content by animateColorAsState(
        if (selected) Color.White else palette.muted,
        label = "todo_audience_content",
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

// person chips, used for both 'also add to' (optional) and 'who is this for' (required).
// the closing note states the limit: sharing one task must not read as sharing the list
@Composable
private fun TodoAudiencePicker(
    palette: TodoPalette,
    targets: List<TodoViewModel.ShareTarget>,
    selected: Set<String>,
    heading: String,
    required: Boolean,
    addToMyList: Boolean,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.pillBg)
                    .clickable { onToggleAddToMine(!addToMyList) }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (addToMyList) palette.lavender else Color.Transparent)
                        .border(
                            width = 1.5.dp,
                            color = if (addToMyList) palette.lavender else palette.muted,
                            shape = RoundedCornerShape(6.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (addToMyList) {
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
                        text = stringResource(R.string.todo_add_mine_too),
                        color = palette.ink,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = if (addToMyList) {
                            stringResource(R.string.todo_on_your_list)
                        } else {
                            stringResource(R.string.todo_only_they)
                        },
                        color = palette.muted,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        if (selected.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.todo_they_see),
                color = palette.muted,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        }
    }
}
