package com.muradgalayev.brainbuddy.ui.onboarding

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SectionTitle(index: Int?, title: String, subtitle: String? = null) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (index != null) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(50))
                        .background(colors.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = index.toString(),
                        color = colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.size(10.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
        }
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(start = if (index != null) 34.dp else 0.dp),
            )
        }
    }
}

@Composable
fun <T> ChipRow(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(count = options.size, key = { options[it].hashCode() }) { idx ->
            val option = options[idx]
            ChoiceChip(
                label = label(option),
                selected = selected == option,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
fun <T> ChipGrid(
    options: List<T>,
    selected: Set<T>,
    label: (T) -> String,
    onToggle: (T) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            ChoiceChip(
                label = label(option),
                selected = option in selected,
                onClick = { onToggle(option) },
            )
        }
    }
}

@Composable
fun <T> SingleChipGrid(
    options: List<T>,
    selected: T?,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            ChoiceChip(
                label = label(option),
                selected = selected == option,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.surfaceContainer,
        label = "chipBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) colors.onPrimary else colors.onSurface,
        label = "chipFg",
    )
    val border by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.7f),
        label = "chipBorder",
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(width = 1.dp, color = border, shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.size(6.dp))
        }
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

enum class UsernameAvailability { Idle, Checking, Available, Taken, Invalid }

@Composable
fun UsernameField(
    value: String,
    availability: UsernameAvailability,
    onChange: (String) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val helper = when (availability) {
        UsernameAvailability.Idle -> "3–20 characters · letters, numbers, _"
        UsernameAvailability.Checking -> "Checking…"
        UsernameAvailability.Available -> "Available"
        UsernameAvailability.Taken -> "Already taken"
        UsernameAvailability.Invalid -> "Use 3–20 letters, numbers or underscores"
    }
    val helperColor = when (availability) {
        UsernameAvailability.Available -> Color(0xFF8AAE7E)
        UsernameAvailability.Taken, UsernameAvailability.Invalid -> colors.error
        else -> colors.onSurfaceVariant
    }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = colors.primary,
                unfocusedBorderColor = colors.outlineVariant,
                focusedLabelColor = colors.primary,
                cursorColor = colors.primary,
            ),
            label = { Text("Username") },
            placeholder = { Text("e.g. focus_owl") },
            trailingIcon = {
                when (availability) {
                    UsernameAvailability.Checking -> CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = colors.primary,
                    )
                    UsernameAvailability.Available -> Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color(0xFF8AAE7E),
                    )
                    else -> {}
                }
            },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = helper,
            color = helperColor,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

fun isUsernameSyntaxValid(username: String): Boolean {
    val v = username.trim()
    if (v.length !in 3..20) return false
    return v.all { it.isLetterOrDigit() || it == '_' }
}
