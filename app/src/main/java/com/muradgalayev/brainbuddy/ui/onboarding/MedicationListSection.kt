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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.domain.model.Medication
import com.muradgalayev.brainbuddy.domain.model.MedicationSlot

@Composable
fun MedicationListSection(
    medications: List<Medication>,
    onAdd: () -> Unit,
    onRemove: (String) -> Unit,
    onNameChange: (String, String) -> Unit,
    onDoseChange: (String, String) -> Unit,
    onSlotToggle: (String, MedicationSlot) -> Unit,
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
                        onSlotToggle = { onSlotToggle(med.id, it) },
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
    onSlotToggle: (MedicationSlot) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.6f)),
        shadowElevation = 1.dp,
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
                    text = if (med.timesPerDay > 0) "${med.timesPerDay}/day" else "Not scheduled",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Remove",
                        tint = colors.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = med.name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    placeholder = { Text("Methylphenidate") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(2f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        cursorColor = colors.primary,
                    ),
                )
                OutlinedTextField(
                    value = med.dose,
                    onValueChange = onDoseChange,
                    label = { Text("Dose") },
                    placeholder = { Text("20mg") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        cursorColor = colors.primary,
                    ),
                )
            }

            Text(
                text = "When?",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MedicationSlot.entries.forEach { slot ->
                    SlotChip(
                        label = slot.label,
                        selected = slot.key in med.slots,
                        onClick = { onSlotToggle(slot) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
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
                text = if (hasItems) "Add another" else "Add a medication",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
            )
        }
    }
}
