package com.muradgalayev.brainbuddy.ui.activity

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.domain.model.Medication
import com.muradgalayev.brainbuddy.domain.model.MedicationSlot
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class MedicationDose(
    val medication: Medication,
    val slot: String,
    val time: String,
    val accent: Color,
)

private val MEDICATION_COLORS = listOf(
    Color(0xFF8BB33F),
    Color(0xFF28A8C4),
    Color(0xFFC66A9F),
    Color(0xFFD99039),
    Color(0xFF786CC8),
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedicationsScreen(
    onBack: () -> Unit,
    // null opens an empty editor, an id opens that medication
    onEditMedication: (String?) -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val medications by viewModel.medications.collectAsState()
    val inTodo by viewModel.medicationInTodo.collectAsState()
    val inCalendar by viewModel.medicationInCalendar.collectAsState()
    val logs by viewModel.medicationDoseLogs.collectAsState()
    val today = remember { LocalDate.now() }
    var selectedDateText by rememberSaveable { mutableStateOf(today.toString()) }
    val selectedDate = remember(selectedDateText) { LocalDate.parse(selectedDateText) }
    val doses = remember(medications, selectedDate) {
        medications.filter { it.isScheduledOn(selectedDate.dayOfWeek) }.flatMap { medication ->
            val medicationAccent = MEDICATION_COLORS[medications.indexOfFirst { it.id == medication.id }.coerceAtLeast(0) % MEDICATION_COLORS.size]
            medication.slots.distinct().map { slot ->
                MedicationDose(medication, slot, medicationTime(slot), medicationAccent)
            }
        }.sortedBy { medicationOrder(it.slot) }
    }
    val pending = doses.filter { doseLogKey(selectedDate, it) !in logs }
    val completed = doses.filter { doseLogKey(selectedDate, it) in logs }
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val accent = Color(0xFF16A7C8)
    val pageBackground = if (dark) Color(0xFF101416) else Color(0xFFF7F8FA)
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(pageBackground).statusBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 16.dp, 20.dp, 40.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WellnessBackButton(onBack)
                Text(
                    "Medications",
                    Modifier.weight(1f).padding(start = 14.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.size(44.dp))
            }
        }
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                AnimatedContent(targetState = selectedDate, label = "selected_medication_date") { date ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (date == today) "Today, ${date.format(DateTimeFormatter.ofPattern("MMMM d", Locale.getDefault()))}"
                            else date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (date == today) Text(date.format(DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            MedicationWeek(today, selectedDate, medications, logs, accent) {
                selectedDateText = it.toString()
            }
        }

        if (medications.isNotEmpty()) {
            item {
                DestinationRow(
                    inTodo = inTodo,
                    inCalendar = inCalendar,
                    accent = accent,
                    onToggleTodo = { viewModel.setMedicationDestination(todo = it, calendar = null) },
                    onToggleCalendar = { viewModel.setMedicationDestination(todo = null, calendar = it) },
                )
            }
        }

        if (medications.isEmpty()) {
            item { EmptyMedicationCard({ onEditMedication(null) }, accent) }
        } else {
            item { SectionTitle(if (selectedDate == today) "Today's log" else if (selectedDate > today) "Upcoming doses" else "Dose log", "${completed.size} of ${doses.size} taken") }
            if (pending.isEmpty() && doses.isNotEmpty()) {
                item {
                    // a green panel to say 'nothing left to do' is a box drawing attention to the absence of work.
                    // the tick carries it
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = Color(0xFF27A56C))
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                "All done for today",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "You logged every scheduled dose.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else {
                pending.forEach { dose ->
                    item(key = "pending-${dose.medication.id}-${dose.slot}") {
                        Box(Modifier.animateItem()) {
                            DoseCard(dose, false, canLog = selectedDate <= today) { viewModel.toggleMedicationDose(doseLogKey(selectedDate, dose)) }
                        }
                    }
                }
            }

            if (completed.isNotEmpty()) {
                item { SectionTitle("Logged", "Tap a check to undo") }
                completed.forEach { dose ->
                    item(key = "logged-${dose.medication.id}-${dose.slot}") {
                        Box(Modifier.animateItem()) {
                            DoseCard(dose, true, canLog = selectedDate <= today) { viewModel.toggleMedicationDose(doseLogKey(selectedDate, dose)) }
                        }
                    }
                }
            }

            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Your medications", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Add", Modifier.clickable { onEditMedication(null) }.padding(10.dp), color = accent, fontWeight = FontWeight.Medium)
                }
            }
            medications.forEachIndexed { index, medication ->
                item(key = "medicine-${medication.id}") { MedicationOverviewCard(medication, index) { onEditMedication(medication.id) } }
            }
        }
    }
}

@Composable
private fun MedicationWeek(
    today: LocalDate,
    selectedDate: LocalDate,
    medications: List<Medication>,
    logs: Set<String>,
    accent: Color,
    onDateSelected: (LocalDate) -> Unit,
) {
    val dates = remember(today) { (-3L..3L).map(today::plusDays) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            dates.forEach { date ->
                val scheduled = medications.filter { it.isScheduledOn(date.dayOfWeek) }.sumOf { it.slots.distinct().size }
                val logged = logs.count { it.startsWith("$date|") }
                val targetProgress = if (scheduled == 0) 0f else (logged.toFloat() / scheduled).coerceIn(0f, 1f)
                val progress by androidx.compose.animation.core.animateFloatAsState(targetProgress, androidx.compose.animation.core.tween(450), label = "day_progress_$date")
                val selectedBackground by animateColorAsState(
                    if (date == selectedDate) accent.copy(alpha = .16f) else Color.Transparent,
                    androidx.compose.animation.core.tween(260),
                    label = "selected_day_$date",
                )
                Column(
                    modifier = Modifier.width(43.dp).clip(RoundedCornerShape(18.dp)).background(selectedBackground).clickable { onDateSelected(date) }.padding(vertical = 6.dp).animateContentSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(date.format(DateTimeFormatter.ofPattern("EE", Locale.getDefault())).take(1), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.size(38.dp), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.fillMaxSize()) {
                            if (progress >= .999f) {
                                drawCircle(accent)
                            } else {
                                drawCircle(accent.copy(alpha = .10f))
                                if (progress > 0f) drawArc(accent, -90f, progress * 360f, false, style = Stroke(4.dp.toPx(), cap = StrokeCap.Butt))
                            }
                        }
                        Text("${date.dayOfMonth}", fontWeight = if (date == selectedDate) FontWeight.SemiBold else FontWeight.Normal, style = MaterialTheme.typography.bodySmall)
                    }
                    if (date == selectedDate) Box(Modifier.size(5.dp).clip(CircleShape).background(accent)) else Spacer(Modifier.height(5.dp))
                }
            }
        }
    }
}

// where doses show up besides this screen. toggles rather than a share button, because this is
// an ongoing relationship and not a one-off copy: turning one off has to remove what it
// previously wrote, and a button that only ever adds leaves stale rows behind the moment a
// prescription changes. both destinations rebuild on every tap
@Composable
private fun DestinationRow(
    inTodo: Boolean,
    inCalendar: Boolean,
    accent: Color,
    onToggleTodo: (Boolean) -> Unit,
    onToggleCalendar: (Boolean) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            "Also show doses in",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DestinationChip("To-do", inTodo, accent) { onToggleTodo(!inTodo) }
            DestinationChip("Calendar", inCalendar, accent) { onToggleCalendar(!inCalendar) }
        }
    }
}

@Composable
private fun DestinationChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) accent.copy(alpha = .16f) else colors.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(Icons.Rounded.Check, null, Modifier.size(15.dp), tint = accent)
            Spacer(Modifier.size(6.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) accent else colors.onSurfaceVariant,
        )
    }
}

@Composable
private fun DoseCard(dose: MedicationDose, logged: Boolean, canLog: Boolean, onToggle: () -> Unit) {
    val accent = dose.accent
    val colors = MaterialTheme.colorScheme
    // no card around the row. it used to be Surface > Row > Surface + Surface: three containers to
    // show one dose. the outer card was doing nothing the spacing between rows wasn't already
    // doing, and stacking tinted panels is what dates a list fastest. what's left is a coloured
    // mark, the words, and the one thing you can press
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (logged) .12f else .20f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Medication,
                null,
                Modifier.size(21.dp),
                tint = accent.copy(alpha = if (logged) .55f else 1f),
            )
        }

        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                "${dose.time} · ${dose.slot.replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Text(
                dose.medication.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                // taken doses fade rather than move to a differently-coloured card. same list, quieter, which
                // is what done should look like
                color = colors.onSurface.copy(alpha = if (logged) .45f else 1f),
            )
            if (dose.medication.doseLabel.isNotBlank()) {
                Text(
                    dose.medication.doseLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant.copy(alpha = if (logged) .5f else 1f),
                )
            }
        }

        // the only container that earns its place: it's the tap target, and it has to look pressable
        Box(
            Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(
                    when {
                        logged -> accent
                        canLog -> colors.surfaceContainerHighest
                        else -> colors.surfaceContainerHighest.copy(alpha = .4f)
                    }
                )
                .then(if (canLog) Modifier.clickable(onClick = onToggle) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (logged) Icons.Rounded.Check else Icons.Rounded.Add,
                if (!canLog) "Future dose cannot be logged" else if (logged) "Undo dose" else "Log dose",
                Modifier.size(20.dp),
                tint = if (logged) Color.White else accent.copy(alpha = if (canLog) 1f else .35f),
            )
        }
    }
}

@Composable
private fun MedicationOverviewCard(medication: Medication, index: Int, onEdit: () -> Unit) {
    val ink = MEDICATION_COLORS[index % MEDICATION_COLORS.size]
    val colors = MaterialTheme.colorScheme
    // the 112dp block of colour down the left was the most dated thing on this screen, a
    // decorative panel carrying no information the small tinted icon doesn't. gone, along with
    // the card and the white circle inside it
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onEdit)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(46.dp).clip(CircleShape).background(ink.copy(alpha = .18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Medication, null, Modifier.size(22.dp), tint = ink)
        }
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text(
                medication.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            val frequency = when (medication.timesPerDay) {
                0 -> "As needed"
                1 -> "Once a day"
                else -> "${medication.timesPerDay} times a day"
            }
            Text(
                listOfNotNull(
                    medication.doseLabel.takeIf { it.isNotBlank() },
                    frequency,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, subtitle: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EmptyMedicationCard(onEdit: () -> Unit, accent: Color) {
    Surface(modifier = Modifier.clickable(onClick = onEdit), shape = RoundedCornerShape(28.dp), color = accent.copy(alpha = .12f)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Rounded.Medication, null, Modifier.size(42.dp), tint = accent)
            Spacer(Modifier.height(12.dp))
            Text("Add your medications", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
            Text("Set the dose and how many times you take it each day.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun doseLogKey(date: LocalDate, dose: MedicationDose) = "$date|${dose.medication.id}|${dose.slot.lowercase()}"

private fun medicationOrder(slot: String) = when (slot.lowercase()) {
    "morning" -> 0
    "afternoon" -> 1
    "evening" -> 2
    "night" -> 3
    else -> 4
}

private fun medicationTime(slot: String) = when (slot.lowercase()) {
    "morning" -> "08:00"
    "afternoon" -> "13:00"
    "evening" -> "18:00"
    "night" -> "22:00"
    else -> "Any time"
}
