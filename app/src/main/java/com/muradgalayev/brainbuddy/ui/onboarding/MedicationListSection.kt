package com.muradgalayev.brainbuddy.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.domain.model.Medication
import com.muradgalayev.brainbuddy.domain.model.MedicationSlot
import com.muradgalayev.brainbuddy.domain.model.MedicationUnit
import androidx.compose.ui.text.input.KeyboardType
import com.muradgalayev.brainbuddy.ui.sharedcomponents.TimePickerDialog
import com.muradgalayev.brainbuddy.ui.sharedcomponents.TimePickerField
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

@Composable
fun MedicationListSection(
    medications: List<Medication>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onNameChange: (String, String) -> Unit,
    onDoseChange: (String, String) -> Unit,
    // the unit the dose is measured in, see MedicationUnit
    onDoseUnitChange: (String, MedicationUnit) -> Unit = { _, _ -> },
    onSlotToggle: (String, MedicationSlot) -> Unit,
    // N4, the exact clock time, HH:mm
    onTimeChange: (String, String) -> Unit = { _, _ -> },
    // N4, as-needed rather than on a daily schedule
    onToggleAsNeeded: (String) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(
            visible = medications.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                medications.forEach { med ->
                    MedicationCard(
                        med = med,
                        onRemove = { onRemove(med.id) },
                        onNameChange = { onNameChange(med.id, it) },
                        onDoseChange = { onDoseChange(med.id, it) },
                        onDoseUnitChange = { onDoseUnitChange(med.id, it) },
                        onSlotToggle = { onSlotToggle(med.id, it) },
                        onTimeChange = { onTimeChange(med.id, it) },
                        onToggleAsNeeded = { onToggleAsNeeded(med.id) },
                    )
                }
            }
        }

        AddMedicationButton(onClick = onAdd, hasItems = medications.isNotEmpty())
    }
}

@Composable
private fun MedicationCard(
    med: Medication,
    onRemove: () -> Unit,
    onNameChange: (String) -> Unit,
    onDoseChange: (String) -> Unit,
    onDoseUnitChange: (MedicationUnit) -> Unit,
    onSlotToggle: (MedicationSlot) -> Unit,
    onTimeChange: (String) -> Unit,
    onToggleAsNeeded: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var showTimePicker by remember { mutableStateOf(false) }
    var showUnitMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = colors.primary.copy(alpha = 0.14f),
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            imageVector = Icons.Rounded.Medication,
                            contentDescription = null,
                            tint = colors.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (med.timesPerDay > 0) stringResource(R.string.med_times_per_day, med.timesPerDay) else stringResource(R.string.med_not_scheduled),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = stringResource(R.string.common_remove),
                        tint = colors.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = med.name,
                    onValueChange = onNameChange,
                    label = { Text(stringResource(R.string.med_name)) },
                    placeholder = { Text("Methylphenidate") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(2f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        cursorColor = colors.primary,
                    ),
                )
                // amount and unit split apart. one '20mg' field looked simpler but produced '20 mg', '20mg',
                // '20 MG' and 'twenty' from four users, none of which can be compared or summed later
                OutlinedTextField(
                    value = med.dose,
                    onValueChange = onDoseChange,
                    label = { Text(stringResource(R.string.med_dose)) },
                    placeholder = { Text("20") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        cursorColor = colors.primary,
                    ),
                )
                Box {
                    val selected = MedicationUnit.fromKey(med.doseUnit)
                    SlotChip(
                        label = selected?.let { stringResource(it.labelRes) } ?: stringResource(R.string.med_unit_placeholder),
                        selected = selected != null,
                        onClick = { showUnitMenu = true },
                        modifier = Modifier.width(88.dp).height(56.dp),
                    )
                    DropdownMenu(
                        expanded = showUnitMenu,
                        onDismissRequest = { showUnitMenu = false },
                    ) {
                        MedicationUnit.entries.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(stringResource(unit.labelRes)) },
                                onClick = {
                                    onDoseUnitChange(unit)
                                    showUnitMenu = false
                                },
                            )
                        }
                    }
                }
            }

            // N4, daily or as-needed. asked first because it decides whether the rest of this card means
            // anything: an as-needed medication has no schedule to give, and prompting for one invites a
            // made-up answer
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SlotChip(
                    label = stringResource(R.string.med_every_day),
                    selected = !med.asNeeded,
                    onClick = { if (med.asNeeded) onToggleAsNeeded() },
                    modifier = Modifier.weight(1f),
                )
                SlotChip(
                    label = stringResource(R.string.med_as_needed),
                    selected = med.asNeeded,
                    onClick = { if (!med.asNeeded) onToggleAsNeeded() },
                    modifier = Modifier.weight(1f),
                )
            }

            if (!med.asNeeded) {
                Text(
                    text = stringResource(R.string.med_when),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MedicationSlot.entries.forEach { slot ->
                        SlotChip(
                            label = stringResource(slot.labelRes),
                            selected = slot.key in med.slots,
                            onClick = { onSlotToggle(slot) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                // N4, the exact time, optional on top of the rough slot. stimulant onset and wear-off are
                // among the strongest predictors of real focus through the day, and 'morning' spans four
                // hours in which a dose could peak anywhere
                // dose could peak anywhere.
                TimePickerField(
                    value = med.times.firstOrNull().orEmpty(),
                    label = stringResource(R.string.med_exact_time),
                    placeholder = "08:00",
                    mutedColor = colors.onSurfaceVariant,
                    accentColor = colors.primary,
                    borderColor = colors.outlineVariant,
                    textColor = colors.onSurface,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showTimePicker = true },
                )
            }
        }
    }

    if (showTimePicker) {
        val parts = med.times.firstOrNull()?.split(":")
        TimePickerDialog(
            title = stringResource(R.string.med_when_take),
            initialHour = parts?.getOrNull(0)?.toIntOrNull() ?: 8,
            initialMinute = parts?.getOrNull(1)?.toIntOrNull() ?: 0,
            onConfirm = { h, m ->
                onTimeChange("%02d:%02d".format(h, m))
                showTimePicker = false
            },
            onDismiss = { showTimePicker = false },
        )
    }
}

@Composable
private fun SlotChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val bg = if (selected) colors.primary.copy(alpha = 0.18f) else colors.surfaceContainerHighest
    val fg = if (selected) colors.primary else colors.onSurfaceVariant
    val borderColor = if (selected) colors.primary.copy(alpha = 0.5f)
    else colors.outlineVariant.copy(alpha = 0.6f)

    Surface(
        modifier = modifier
            .height(36.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        color = bg,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = fg,
            )
        }
    }
}

@Composable
private fun AddMedicationButton(onClick: () -> Unit, hasItems: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = colors.primary.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.35f)),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (hasItems) stringResource(R.string.med_add_another) else stringResource(R.string.med_add),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
            )
        }
    }
}
