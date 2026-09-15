package com.muradgalayev.brainbuddy.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

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
    label: @Composable (T) -> String,
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
    label: @Composable (T) -> String,
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

// multi-select with a ceiling, for questions that ask for 'up to N'. once the cap is reached
// the remaining options dim and stop responding, rather than silently swapping out an earlier
// pick: being told 'three is the maximum' after the fact, by watching a previous answer vanish,
// is how people end up with a selection they didn't make
@Composable
fun <T> CappedChipGrid(
    options: List<T>,
    selected: List<T>,
    max: Int,
    label: @Composable (T) -> String,
    onToggle: (T) -> Unit,
) {
    val atCap = selected.size >= max
    Column(modifier = Modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                val isSelected = option in selected
                ChoiceChip(
                    label = label(option),
                    selected = isSelected,
                    enabled = isSelected || !atCap,
                    onClick = { onToggle(option) },
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (atCap) stringResource(R.string.survey_cap_reached, max)
            else stringResource(R.string.survey_cap_count, selected.size, max),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// set by SwipeQuestionnaire for the page on screen. a no-op anywhere else, so the grids behave the
// same outside the questionnaire
val LocalAdvanceQuestion = compositionLocalOf<() -> Unit> { {} }

// one answer, so picking it is the whole question and the page moves on by itself. advanceOn
// holds it back for answers that open a follow-up on the same page
@Composable
fun <T> SingleChipGrid(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    advanceOn: (T) -> Boolean = { true },
) {
    val advance = LocalAdvanceQuestion.current
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            ChoiceChip(
                label = label(option),
                selected = selected == option,
                onClick = {
                    onSelect(option)
                    if (advanceOn(option)) advance()
                },
            )
        }
    }
}

@Composable
fun ChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    // false dims the chip and stops it responding, see CappedChipGrid
    enabled: Boolean = true,
) {
    val colors = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        targetValue = when {
            selected -> colors.primary
            enabled -> colors.surfaceContainer
            else -> colors.surfaceContainer.copy(alpha = .45f)
        },
        label = "chipBg",
    )
    val fg by animateColorAsState(
        targetValue = when {
            selected -> colors.onPrimary
            enabled -> colors.onSurface
            else -> colors.onSurface.copy(alpha = .38f)
        },
        label = "chipFg",
    )
    val border by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.7f),
        label = "chipBorder",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.035f else 1f,
        animationSpec = spring(dampingRatio = .55f, stiffness = 420f),
        label = "chipScale",
    )

    Row(
        modifier = Modifier
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .border(width = 1.dp, color = border, shape = RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick)
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

@Composable
fun LabeledTextField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = colors.primary,
            unfocusedBorderColor = colors.outlineVariant,
            focusedLabelColor = colors.primary,
            cursorColor = colors.primary,
            focusedContainerColor = colors.surface,
            unfocusedContainerColor = colors.surface,
        ),
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
    )
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
        UsernameAvailability.Idle -> stringResource(R.string.username_hint)
        UsernameAvailability.Checking -> stringResource(R.string.common_checking)
        UsernameAvailability.Available -> stringResource(R.string.username_available)
        UsernameAvailability.Taken -> stringResource(R.string.username_taken)
        UsernameAvailability.Invalid -> stringResource(R.string.username_invalid)
    }
    val helperColor = when (availability) {
        UsernameAvailability.Available -> Color(0xFF0D9488)
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
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
            ),
            label = { Text(stringResource(R.string.username_label)) },
            placeholder = { Text(stringResource(R.string.username_placeholder)) },
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
                        tint = Color(0xFF0D9488),
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

// age ranges are stored as their English text, so only the one with words in it needs translating
@Composable
fun ageRangeLabel(range: String): String =
    if (range == "Under 18") stringResource(R.string.age_under_18) else range

fun isUsernameSyntaxValid(username: String): Boolean {
    val v = username.trim()
    if (v.length !in 3..20) return false
    return v.all { it.isLetterOrDigit() || it == '_' }
}
