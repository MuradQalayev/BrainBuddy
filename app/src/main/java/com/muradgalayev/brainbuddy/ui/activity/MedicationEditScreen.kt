package com.muradgalayev.brainbuddy.ui.activity

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbTwilight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.domain.model.Medication
import com.muradgalayev.brainbuddy.domain.model.MedicationSlot
import com.muradgalayev.brainbuddy.domain.model.MedicationUnit
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// one medication, one screen. the editor this replaces put every medication into a single
// 520dp scrolling sheet, so changing one dose meant finding it among the others in a window a
// third the height of the phone, with the keyboard covering most of what was left. editing is
// rare and consequential (a wrong dose here is worse than a wrong anything else in this app)
// so it gets the whole screen, one thing at a time, and a Save you have to mean.
// medicationId is null when adding. nothing is written until Save, so backing out of a
// half-filled form leaves no half-medication behind
@Composable
fun MedicationEditScreen(
    medicationId: String?,
    onBack: () -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val medications by viewModel.medications.collectAsState()
    val existing = remember(medications, medicationId) {
        medications.firstOrNull { it.id == medicationId }
    }

    // keyed on the loaded record so the form fills itself in once the profile arrives, rather
    // than opening empty and then overwriting whatever was typed in the meantime
    var draft by remember(existing?.id) { mutableStateOf(existing ?: Medication()) }
    var saving by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    val accent = Color(0xFF16A7C8)
    val canSave = draft.name.isNotBlank() && !saving

    fun commit(list: List<Medication>) {
        saving = true
        failed = false
        viewModel.saveMedications(list) { success ->
            saving = false
            if (success) onBack() else failed = true
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WellnessBackButton(onBack)
            Text(
                text = if (existing == null) stringResource(R.string.med_new) else draft.name.ifBlank { stringResource(R.string.deep_medication) },
                modifier = Modifier.weight(1f).padding(start = 14.dp),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            if (existing != null) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = .5f))
                        .clickable { confirmDelete = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.med_delete),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            Section(stringResource(R.string.med_name)) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    placeholder = { Text(stringResource(R.string.med_name_hint)) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Section(stringResource(R.string.med_dose)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = draft.dose,
                        onValueChange = { input ->
                            // digits and one separator only. a dose is the one field here where a typo is genuinely
                            // dangerous, so the field refuses anything that isn't a number rather than accepting it and
                            // failing silently later
                            val cleaned = input.filter { it.isDigit() || it == '.' || it == ',' }
                                .replace(',', '.')
                            if (cleaned.count { it == '.' } <= 1) draft = draft.copy(dose = cleaned)
                        },
                        placeholder = { Text("10") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.width(120.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = draft.doseLabel(androidx.compose.ui.platform.LocalContext.current.resources).ifBlank { stringResource(R.string.med_no_dose) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (draft.dose.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                        else accent,
                    )
                }
                Spacer(Modifier.height(10.dp))
                ChipFlow {
                    MedicationUnit.entries.forEach { unit ->
                        SelectChip(
                            label = unit.label,
                            selected = draft.doseUnit == unit.key,
                            accent = accent,
                            onClick = {
                                draft = draft.copy(
                                    doseUnit = if (draft.doseUnit == unit.key) "" else unit.key,
                                )
                            },
                        )
                    }
                }
            }

            Section(stringResource(R.string.common_when)) {
                ChipFlow {
                    MedicationSlot.entries.forEach { slot ->
                        SelectChip(
                            label = slot.label,
                            icon = slotIcon(slot),
                            selected = slot.key in draft.slots,
                            accent = accent,
                            onClick = {
                                draft = draft.copy(
                                    slots = if (slot.key in draft.slots) draft.slots - slot.key
                                    else draft.slots + slot.key,
                                )
                            },
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.med_only_needed), fontWeight = FontWeight.SemiBold)
                        Text(
                            stringResource(R.string.med_only_needed_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = draft.asNeeded,
                        onCheckedChange = { draft = draft.copy(asNeeded = it) },
                    )
                }
            }

            // an as-needed medication has no schedule to speak of, so the day picker would be asking a
            // question that has no answer
            AnimatedVisibility(visible = !draft.asNeeded) {
                Section(stringResource(R.string.med_days)) {
                    val active = draft.daysOfWeek.ifEmpty { (1..7).toList() }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (1..7).forEach { day ->
                            DayToggle(
                                day = day,
                                selected = day in active,
                                accent = accent,
                                onClick = {
                                    val updated = if (day in active) active - day else (active + day).sorted()
                                    // never let the last day be switched off: a medication scheduled on no days is one that
                                    // silently disappears from every list in the app
                                    draft = draft.copy(daysOfWeek = updated.ifEmpty { active })
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (active.size == 7) stringResource(R.string.mode_every_day)
                        else active.sorted().joinToString(", ") { dayLabel(it) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (failed) {
                Text(
                    stringResource(R.string.med_save_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(4.dp))
        }

        Surface(color = MaterialTheme.colorScheme.background) {
            Button(
                onClick = {
                    val cleaned = draft.copy(name = draft.name.trim())
                    val updated = if (existing == null) medications + cleaned
                    else medications.map { if (it.id == cleaned.id) cleaned else it }
                    commit(updated)
                },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
                    .height(54.dp)
                    .navigationBarsPadding(),
            ) {
                Text(
                    text = when {
                        saving -> stringResource(R.string.common_saving)
                        existing == null -> stringResource(R.string.med_add_btn)
                        else -> stringResource(R.string.common_save_changes)
                    },
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = RoundedCornerShape(26.dp),
            title = { Text(stringResource(R.string.med_delete_confirm, existing.name.ifBlank { stringResource(R.string.med_this_medication) }), fontWeight = FontWeight.Bold) },
            text = { Text(stringResource(R.string.med_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        commit(medications.filterNot { it.id == existing.id })
                    },
                ) { Text(stringResource(R.string.common_delete), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.together_keep), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun SelectChip(
    label: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    icon: ImageVector? = null,
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = .16f) else colors.surfaceContainerHigh,
        animationSpec = tween(200),
        label = "chipBg",
    )
    val foreground by animateColorAsState(
        targetValue = if (selected) accent else colors.onSurfaceVariant,
        animationSpec = tween(200),
        label = "chipFg",
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) accent else colors.outlineVariant.copy(alpha = .6f),
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = foreground, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = foreground,
        )
    }
}

@Composable
private fun DayToggle(
    day: Int,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val background by animateColorAsState(
        targetValue = if (selected) accent else colors.surfaceContainerHigh,
        animationSpec = tween(200),
        label = "dayBg",
    )

    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = dayLabel(day).take(1),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else colors.onSurfaceVariant,
        )
    }
}

private fun dayLabel(day: Int): String =
    DayOfWeek.of(day).getDisplayName(TextStyle.SHORT, Locale.getDefault())

private fun slotIcon(slot: MedicationSlot): ImageVector = when (slot) {
    MedicationSlot.Morning -> Icons.Rounded.WbTwilight
    MedicationSlot.Afternoon -> Icons.Rounded.LightMode
    MedicationSlot.Evening -> Icons.Rounded.Bedtime
    MedicationSlot.Night -> Icons.Rounded.NightsStay
}
