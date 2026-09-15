package com.muradgalayev.brainbuddy.ui.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FormatLineSpacing
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.ui.settings.PillOption
import com.muradgalayev.brainbuddy.ui.settings.SectionLabel
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.data.local.TextSpacing
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

@Composable
fun AppearanceRow(
    themeMode: ThemeMode,
    fontMode: FontMode,
    expanded: Boolean,
    fontSize: FontSize,
    textSpacing: TextSpacing,
    onToggle: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onFontChange: (FontMode) -> Unit,
    onFontSizeChange: (FontSize) -> Unit,
    onTextSpacingChange: (TextSpacing) -> Unit,
    embedded: Boolean = false,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "chevron"
    )

    Column {
        // the collapse header, hidden when embedded on a dedicated page
        if (!embedded) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(24.dp)
                ),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 0.dp,
            onClick = onToggle
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .heightIn(min = 60.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Rounded.Apps,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_appearance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.size(2.dp))
                    Text(
                        text = "${themeMode.name} · ${fontMode.name} · ${fontSize.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .size(22.dp)
                        .rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        }

        // floating panel below
        AnimatedVisibility(
            visible = expanded || embedded,
            enter = expandVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                expandFrom = Alignment.Top
            ) + fadeIn(animationSpec = tween(200)),
            exit = shrinkVertically(
                animationSpec = tween(200),
                shrinkTowards = Alignment.Top
            ) + fadeOut(animationSpec = tween(150))
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(28.dp)
                    ),
                shape = RoundedCornerShape(28.dp),
                // neutral surface with no primary tint, so the page background stays calm
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp
            ) {
                Column(
                    modifier = Modifier.padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // theme
                    SectionLabel(icon = Icons.Outlined.Brightness4, label = stringResource(R.string.common_theme))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PillOption(
                            label = stringResource(R.string.common_light),
                            icon = Icons.Outlined.LightMode,
                            selected = themeMode == ThemeMode.Light,
                            onClick = { onThemeChange(ThemeMode.Light) },
                            modifier = Modifier.weight(1f)
                        )
                        PillOption(
                            label = stringResource(R.string.common_dark),
                            icon = Icons.Outlined.DarkMode,
                            selected = themeMode == ThemeMode.Dark,
                            onClick = { onThemeChange(ThemeMode.Dark) },
                            modifier = Modifier.weight(1f)
                        )
                        PillOption(
                            label = stringResource(R.string.common_auto),
                            icon = Icons.Outlined.Brightness4,
                            selected = themeMode == ThemeMode.System,
                            onClick = { onThemeChange(ThemeMode.System) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // font
                    SectionLabel(icon = Icons.Outlined.TextFields, label = stringResource(R.string.settings_font))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PillOption(
                            label = "Arial",
                            selected = fontMode == FontMode.Arial,
                            onClick = { onFontChange(FontMode.Arial) },
                            modifier = Modifier.weight(1f)
                        )
                        PillOption(
                            label = stringResource(R.string.settings_font_dyslexic),
                            selected = fontMode == FontMode.OpenDyslexic,
                            onClick = { onFontChange(FontMode.OpenDyslexic) },
                            modifier = Modifier.weight(1f)
                        )
                        PillOption(
                            label = "Atkinson",
                            selected = fontMode == FontMode.Atkinson,
                            onClick = { onFontChange(FontMode.Atkinson) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // font size
                    SectionLabel(icon = Icons.Outlined.TextFields, label = stringResource(R.string.settings_font_size))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PillOption(
                            label = stringResource(R.string.common_small),
                            selected = fontSize == FontSize.Small,
                            onClick = { onFontSizeChange(FontSize.Small) },
                            modifier = Modifier.weight(1f)
                        )
                        PillOption(
                            label = stringResource(R.string.common_medium),
                            selected = fontSize == FontSize.Medium,
                            onClick = { onFontSizeChange(FontSize.Medium) },
                            modifier = Modifier.weight(1f)
                        )
                        PillOption(
                            label = stringResource(R.string.common_large),
                            selected = fontSize == FontSize.Large,
                            onClick = { onFontSizeChange(FontSize.Large) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // text spacing, directly under size because the two are usually tried together: 'still hard to
                    // read' is answered by one or the other, and which one only becomes clear by trying both
                    SectionLabel(icon = Icons.Outlined.FormatLineSpacing, label = stringResource(R.string.settings_text_spacing))
                    Text(
                        text = stringResource(R.string.settings_text_spacing_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PillOption(
                            label = stringResource(R.string.spacing_normal),
                            selected = textSpacing == TextSpacing.Normal,
                            onClick = { onTextSpacingChange(TextSpacing.Normal) },
                            modifier = Modifier.weight(1f)
                        )
                        PillOption(
                            label = stringResource(R.string.spacing_relaxed),
                            selected = textSpacing == TextSpacing.Relaxed,
                            onClick = { onTextSpacingChange(TextSpacing.Relaxed) },
                            modifier = Modifier.weight(1f)
                        )
                        PillOption(
                            label = stringResource(R.string.spacing_loose),
                            selected = textSpacing == TextSpacing.Loose,
                            onClick = { onTextSpacingChange(TextSpacing.Loose) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
