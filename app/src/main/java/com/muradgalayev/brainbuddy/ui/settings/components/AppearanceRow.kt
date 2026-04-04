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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.TextFields
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
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.ui.settings.PillOption
import com.muradgalayev.brainbuddy.ui.settings.SectionLabel
import com.muradgalayev.brainbuddy.data.local.FontSize


@Composable
fun AppearanceRow(
    themeMode: ThemeMode,
    fontMode: FontMode,
    expanded: Boolean,
    fontSize: FontSize,
    onToggle: () -> Unit,
    onThemeChange: (ThemeMode) -> Unit,
    onFontChange: (FontMode) -> Unit,
    onFontSizeChange: (FontSize) -> Unit
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
        // The row itself
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            onClick = onToggle
        ) {
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Outlined.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))



                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Appearance",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
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
                        .size(24.dp)
                        .rotate(chevronRotation),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Floating panel below
        AnimatedVisibility(
            visible = expanded,
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
                    .padding(top = 8.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                    ),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    // Theme
                    SectionLabel(icon = Icons.Outlined.Brightness4, label = "Theme")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PillOption(
                            label = "Light",
                            icon = Icons.Outlined.LightMode,
                            selected = themeMode == ThemeMode.Light,
                            onClick = { onThemeChange(ThemeMode.Light) },
                            modifier = Modifier.weight(1f)
                        )
                        PillOption(
                            label = "Dark",
                            icon = Icons.Outlined.DarkMode,
                            selected = themeMode == ThemeMode.Dark,
                            onClick = { onThemeChange(ThemeMode.Dark) },
                            modifier = Modifier.weight(1f)
                        )
                        PillOption(
                            label = "Auto",
                            icon = Icons.Outlined.Brightness4,
                            selected = themeMode == ThemeMode.System,
                            onClick = { onThemeChange(ThemeMode.System) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Font
                    SectionLabel(icon = Icons.Outlined.TextFields, label = "Font")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PillOption(
                            label = "Classic",
                            selected = fontMode == FontMode.Classic,
                            onClick = { onFontChange(FontMode.Classic) },
                            modifier = Modifier.weight(1f)
                        )
                        PillOption(
                            label = "Modern",
                            selected = fontMode == FontMode.Modern,
                            onClick = { onFontChange(FontMode.Modern) },
                            modifier = Modifier.weight(1f)
                        )
                        PillOption(
                            label = "Rounded",
                            selected = fontMode == FontMode.Rounded,
                            onClick = { onFontChange(FontMode.Rounded) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Font Size
                    SectionLabel(icon = Icons.Outlined.TextFields, label = "Font Size")

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PillOption(
                            label = "Small",
                            selected = fontSize == FontSize.Small,
                            onClick = { onFontSizeChange(FontSize.Small) },
                            modifier = Modifier.weight(1f)
                        )
                        PillOption(
                            label = "Medium",
                            selected = fontSize == FontSize.Medium,
                            onClick = { onFontSizeChange(FontSize.Medium) },
                            modifier = Modifier.weight(1f)
                        )
                        PillOption(
                            label = "Large",
                            selected = fontSize == FontSize.Large,
                            onClick = { onFontSizeChange(FontSize.Large) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
