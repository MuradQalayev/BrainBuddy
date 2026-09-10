package com.muradgalayev.brainbuddy.ui.accessibility

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessibilityNew
import androidx.compose.material.icons.rounded.Brightness4
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.data.local.TextSpacing
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.ui.settings.AppearancePreferencesViewModel
import com.muradgalayev.brainbuddy.ui.theme.AppTheme
import com.muradgalayev.brainbuddy.ui.theme.fontFamilyOf
import com.muradgalayev.brainbuddy.ui.modes.modeAccentColor
import com.muradgalayev.brainbuddy.ui.modes.modeIcon

// a tab on the right edge that opens the four things people change when a screen is hard to
// use: brightness, colour, typeface, text size, and whether taps are read aloud. it exists
// because those settings are needed at the moment reading gets difficult, and the path to
// them today is Settings, Customization, scroll. anything four steps away is a setting people
// give up on rather than adjust as the day changes.
// deliberately not a second copy of the Customization page: no previews, no explanations, no
// custom palette builder. those belong where someone has sat down to decide how the app looks.
// this is for changing it in two seconds and getting back to what you were doing, which is
// why it opens out of the tab rather than sliding up from somewhere else
@Composable
fun AccessibilityQuickAccess(
    modifier: Modifier = Modifier,
    viewModel: AppearancePreferencesViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val fontMode by viewModel.fontMode.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val textSpacing by viewModel.textSpacing.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val readAloudTaps by viewModel.readAloudTaps.collectAsState()
    val activeMode by viewModel.activeMode.collectAsState()
    val animate = animationsOn()

    var open by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize()) {
        // only present while open, so the tab never eats a tap meant for the screen behind it
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(tween(if (animate) 180 else 0)),
            exit = fadeOut(tween(if (animate) 140 else 0)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { open = false },
                    )
            )
        }

        AnimatedVisibility(
            visible = !open,
            enter = fadeIn(tween(if (animate) 200 else 0)) +
                scaleIn(tween(if (animate) 220 else 0), initialScale = if (animate) 0.7f else 1f),
            exit = fadeOut(tween(if (animate) 120 else 0)) +
                scaleOut(tween(if (animate) 140 else 0), targetScale = if (animate) 0.7f else 1f),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            EdgeTab(onClick = { open = true })
        }

        AnimatedVisibility(
            visible = open,
            // grown from the tab's own position, the right edge halfway down, so the panel reads as the
            // tab unfolding rather than as a card that arrived from elsewhere
            enter = if (animate) {
                scaleIn(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
                    initialScale = 0.55f,
                    transformOrigin = TransformOrigin(1f, 0.5f),
                ) + fadeIn(tween(160))
            } else {
                fadeIn(tween(0))
            },
            exit = if (animate) {
                scaleOut(
                    animationSpec = tween(180, easing = FastOutSlowInEasing),
                    targetScale = 0.55f,
                    transformOrigin = TransformOrigin(1f, 0.5f),
                ) + fadeOut(tween(140))
            } else {
                fadeOut(tween(0))
            },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            QuickPanel(
                themeMode = themeMode,
                fontMode = fontMode,
                fontSize = fontSize,
                textSpacing = textSpacing,
                appThemeId = appTheme.id,
                readAloudTaps = readAloudTaps,
                activeMode = activeMode,
                onThemeMode = viewModel::setThemeMode,
                onAppTheme = viewModel::setAppTheme,
                onFontMode = viewModel::setFontMode,
                onFontSize = viewModel::setFontSize,
                onTextSpacing = viewModel::setTextSpacing,
                onReadAloud = viewModel::setReadAloudTaps,
                onClose = { open = false },
            )
        }
    }
}

// the handle: half a pill, flush with the screen edge, so it reads as pullable
@Composable
private fun EdgeTab(onClick: () -> Unit) {
    val shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
    val colors = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .shadow(8.dp, shape, clip = false)
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .border(1.dp, colors.outlineVariant.copy(alpha = 0.6f), shape)
            .clickable(onClick = speaking("Accessibility", onClick))
            .padding(start = 9.dp, end = 7.dp, top = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.AccessibilityNew,
            contentDescription = "Accessibility settings",
            tint = colors.primary,
            modifier = Modifier.size(21.dp),
        )
    }
}

@Composable
private fun QuickPanel(
    themeMode: ThemeMode,
    fontMode: FontMode,
    fontSize: FontSize,
    textSpacing: TextSpacing,
    appThemeId: String,
    readAloudTaps: Boolean,
    activeMode: com.muradgalayev.brainbuddy.domain.model.AppMode?,
    onThemeMode: (ThemeMode) -> Unit,
    onAppTheme: (AppTheme) -> Unit,
    onFontMode: (FontMode) -> Unit,
    onFontSize: (FontSize) -> Unit,
    onTextSpacing: (TextSpacing) -> Unit,
    onReadAloud: (Boolean) -> Unit,
    onClose: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier
            .padding(end = 10.dp)
            .width(286.dp)
            .shadow(20.dp, RoundedCornerShape(26.dp), clip = false),
        shape = RoundedCornerShape(26.dp),
        color = colors.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AccessibilityNew,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Spacer(Modifier.width(9.dp))
                Text(
                    text = "Accessibility",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(onClick = speaking("Close", onClose)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint = colors.onSurfaceVariant,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            if (activeMode != null) {
                val accent = modeAccentColor(activeMode.accent)
                Spacer(Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = accent.copy(alpha = .12f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = .28f)),
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(accent.copy(alpha = .16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                modeIcon(activeMode.icon),
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(22.dp),
                            )
                            Icon(
                                Icons.Rounded.Lock,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.align(Alignment.BottomEnd).size(14.dp),
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${activeMode.name} mode is active",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.onSurface,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Appearance changes aren't available in this mode. Turn it off or edit the mode first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                    }
                }
                return@Column
            }

            RowLabel("Brightness")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrightnessButton(Icons.Rounded.LightMode, "Light", themeMode == ThemeMode.Light) {
                    onThemeMode(ThemeMode.Light)
                }
                BrightnessButton(Icons.Rounded.DarkMode, "Dark", themeMode == ThemeMode.Dark) {
                    onThemeMode(ThemeMode.Dark)
                }
                BrightnessButton(Icons.Rounded.Brightness4, "Auto", themeMode == ThemeMode.System) {
                    onThemeMode(ThemeMode.System)
                }
            }

            RowLabel("Colour")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTheme.entries.forEach { theme ->
                    ThemeDot(
                        theme = theme,
                        selected = theme.id == appThemeId,
                        onClick = { onAppTheme(theme) },
                    )
                }
            }

            RowLabel("Typeface")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FontChip("Arial", FontMode.Arial, fontMode == FontMode.Arial) {
                    onFontMode(FontMode.Arial)
                }
                FontChip("Atkinson", FontMode.Atkinson, fontMode == FontMode.Atkinson) {
                    onFontMode(FontMode.Atkinson)
                }
                FontChip("Dyslexic", FontMode.OpenDyslexic, fontMode == FontMode.OpenDyslexic) {
                    onFontMode(FontMode.OpenDyslexic)
                }
            }

            RowLabel("Text size")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // the glyph is the control: each button shows the size it sets, so the choice needs no words
                // and no preview
                SizeButton(13.sp, FontSize.Small, fontSize == FontSize.Small) {
                    onFontSize(FontSize.Small)
                }
                SizeButton(17.sp, FontSize.Medium, fontSize == FontSize.Medium) {
                    onFontSize(FontSize.Medium)
                }
                SizeButton(22.sp, FontSize.Large, fontSize == FontSize.Large) {
                    onFontSize(FontSize.Large)
                }
            }

            RowLabel("Line spacing")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SpacingButton(2.dp, "Normal", textSpacing == TextSpacing.Normal) {
                    onTextSpacing(TextSpacing.Normal)
                }
                SpacingButton(5.dp, "Relaxed", textSpacing == TextSpacing.Relaxed) {
                    onTextSpacing(TextSpacing.Relaxed)
                }
                SpacingButton(8.dp, "Loose", textSpacing == TextSpacing.Loose) {
                    onTextSpacing(TextSpacing.Loose)
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceContainerHighest.copy(alpha = 0.7f))
                    .clickable(onClick = speaking("Speak what I tap") { onReadAloud(!readAloudTaps) })
                    .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.RecordVoiceOver,
                    contentDescription = null,
                    tint = if (readAloudTaps) colors.primary else colors.onSurfaceVariant,
                    modifier = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Speak what I tap",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colors.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = readAloudTaps,
                    onCheckedChange = onReadAloud,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = colors.onPrimary,
                        checkedTrackColor = colors.primary,
                        checkedBorderColor = colors.primary,
                    ),
                )
            }
        }
    }
}

// section headings, small enough to be scanned past rather than read
@Composable
private fun RowLabel(text: String) {
    Spacer(Modifier.height(14.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 0.8.sp,
    )
    Spacer(Modifier.height(7.dp))
}

@Composable
private fun BrightnessButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motionDuration = if (animationsOn()) 240 else 0
    val background by animateColorAsState(
        targetValue = if (selected) colors.primary.copy(alpha = 0.16f) else colors.surfaceContainerHighest,
        animationSpec = tween(motionDuration),
        label = "a11y_brightness_bg",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.onSurfaceVariant,
        animationSpec = tween(motionDuration),
        label = "a11y_brightness_fg",
    )

    Box(
        modifier = Modifier
            .size(width = 76.dp, height = 48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(background)
            .clickable(onClick = speaking(label, onClick)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(18.dp))
    }
}

// a theme, as the two colours it actually is. cheaper to read than its name
@Composable
private fun ThemeDot(theme: AppTheme, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val dark = colors.background.luminanceIsDark()
    val palette = theme.palette(dark)

    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(
                if (selected) colors.primary.copy(alpha = 0.16f) else Color.Transparent
            )
            .clickable(onClick = speaking(theme.label, onClick)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(if (selected) 22.dp else 26.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(palette.accent, palette.support)))
                .border(
                    width = 1.dp,
                    color = colors.outlineVariant.copy(alpha = 0.5f),
                    shape = CircleShape,
                )
        )
    }
}

@Composable
private fun FontChip(label: String, mode: FontMode, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val motionDuration = if (animationsOn()) 240 else 0
    val background by animateColorAsState(
        targetValue = if (selected) colors.primary.copy(alpha = 0.16f) else colors.surfaceContainerHighest,
        animationSpec = tween(motionDuration),
        label = "a11y_font_bg",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.onSurface,
        animationSpec = tween(motionDuration),
        label = "a11y_font_fg",
    )

    Column(
        modifier = Modifier
            .size(width = 76.dp, height = 48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(background)
            .clickable(onClick = speaking(label, onClick)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // shown in the face it selects, since a list of type names set in the current font tells you
        // nothing about the one you're about to choose
        Text(
            text = "Aa",
            fontFamily = fontFamilyOf(mode),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
        )
        Text(
            text = label,
            fontFamily = fontFamilyOf(mode),
            fontSize = 9.sp,
            color = tint.copy(alpha = 0.75f),
            maxLines = 1,
        )
    }
}

@Composable
private fun SizeButton(
    glyphSize: androidx.compose.ui.unit.TextUnit,
    size: FontSize,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motionDuration = if (animationsOn()) 240 else 0
    val background by animateColorAsState(
        targetValue = if (selected) colors.primary.copy(alpha = 0.16f) else colors.surfaceContainerHighest,
        animationSpec = tween(motionDuration),
        label = "a11y_size_bg",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.onSurfaceVariant,
        animationSpec = tween(motionDuration),
        label = "a11y_size_fg",
    )

    Box(
        modifier = Modifier
            .size(width = 76.dp, height = 48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(background)
            .clickable(onClick = speaking(size.name, onClick)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "A",
            // fixed rather than scaled: the button has to keep showing the size it sets even once that
            // size is applied to everything else
            fontSize = glyphSize,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = tint,
        )
    }
}

// three bars at the gap this option sets, the same 'the glyph is the control' idea as
// SizeButton. spacing is hard to name (is Loose more or less than Relaxed?) and easy to see,
// so the picture carries it and the word is only a label
@Composable
private fun SpacingButton(
    gap: androidx.compose.ui.unit.Dp,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motionDuration = if (animationsOn()) 240 else 0
    val background by animateColorAsState(
        targetValue = if (selected) colors.primary.copy(alpha = 0.16f) else colors.surfaceContainerHighest,
        animationSpec = tween(motionDuration),
        label = "a11y_spacing_bg",
    )
    val tint by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.onSurfaceVariant,
        animationSpec = tween(motionDuration),
        label = "a11y_spacing_fg",
    )

    Column(
        modifier = Modifier
            .size(width = 76.dp, height = 48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(background)
            .clickable(onClick = speaking(label, onClick)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(gap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            repeat(3) {
                Box(
                    Modifier
                        .size(width = 26.dp, height = 2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(tint)
                )
            }
        }
    }
}

private fun Color.luminanceIsDark(): Boolean =
    (0.299f * red + 0.587f * green + 0.114f * blue) < 0.5f
