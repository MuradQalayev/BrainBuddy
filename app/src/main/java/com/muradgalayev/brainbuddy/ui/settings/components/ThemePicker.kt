package com.muradgalayev.brainbuddy.ui.settings.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.ui.theme.AppTheme
import com.muradgalayev.brainbuddy.ui.theme.CustomThemeSpec
import com.muradgalayev.brainbuddy.ui.theme.ThemePalette
import com.muradgalayev.brainbuddy.ui.theme.ThemeSelection
import com.muradgalayev.brainbuddy.ui.theme.Vividness
import com.muradgalayev.brainbuddy.ui.theme.customPalette
import com.muradgalayev.brainbuddy.ui.theme.hslColor
import kotlinx.coroutines.delay
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// roughly the editor's height, so how far below the preview also has to be on screen
private val EditorReach = 300.dp

// colour theme picker: a live miniature of the app on top, the built-ins below, build-your-own
// under that. the preview is the point, swatches say what colours a theme contains but only
// seeing them arranged as the actual UI answers whether it's comfortable to look at
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemePicker(
    selected: ThemeSelection,
    customSpec: CustomThemeSpec,
    themeMode: ThemeMode,
    onSelectBuiltIn: (AppTheme) -> Unit,
    onCustomChange: (CustomThemeSpec) -> Unit,
    modifier: Modifier = Modifier,
    // building your own palette is a Plus feature. the built-ins stay free, and a locked row opens
    // the plan screen rather than doing nothing, so the lock explains itself in one tap.
    // defaults to unlocked for callers with no plan to consult, like onboarding
    customUnlocked: Boolean = true,
    onCustomLocked: () -> Unit = {},
) {
    // the preview resolves light/dark exactly the way the app does, or it wouldn't match
    val systemDark = isSystemInDarkTheme()
    val previewDark = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> systemDark
    }

    val customSelected = selected.id == ThemeSelection.CUSTOM_ID
    var customExpanded by remember { mutableStateOf(customSelected && customUnlocked) }
    // losing Plus mid-session must not leave the editor open over a theme that can't be edited
    LaunchedEffect(customUnlocked) { if (!customUnlocked) customExpanded = false }

    // the editor sits directly under the preview, and opening it scrolls the preview to the top:
    // otherwise every hue drag happens with the thing being previewed off-screen above, and you
    // have to scroll up after each adjustment to see what you did
    val previewRequester = remember { BringIntoViewRequester() }
    // counted rather than keyed on customExpanded, so the scroll only happens when the user opens
    // the editor, not when the screen opens with a custom theme already active
    var openRequests by remember { mutableStateOf(0) }
    var previewSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    LaunchedEffect(openRequests) {
        if (openRequests > 0) {
            // let the expand animation lay the editor out first, so the scroll uses its full height
            delay(260)
            // asking for the preview plus the space the editor takes pins it to the top rather than
            // nudging it barely into view, which would leave the sliders under the fold
            if (previewSize != IntSize.Zero) {
                previewRequester.bringIntoView(
                    Rect(
                        left = 0f,
                        top = 0f,
                        right = previewSize.width.toFloat(),
                        bottom = previewSize.height + with(density) { EditorReach.toPx() },
                    )
                )
            }
            // a no-op when the combined block is taller than the screen, so fall back to the preview alone
            previewRequester.bringIntoView()
        }
    }

    // dragging a hue updates this local draft, not the stored setting. writing on every drag frame
    // would hammer DataStore and restart the app-wide theme transition dozens of times a second;
    // the draft keeps the preview live and the commit happens on release
    var draft by remember(customSpec) { mutableStateOf(customSpec) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                text = stringResource(R.string.theme_color_theme),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(R.string.theme_color_theme_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(14.dp))

            ThemePreviewCard(
                palette = if (customSelected) {
                    customPalette(draft.accentHue, draft.supportHue, draft.vividness, previewDark)
                } else {
                    selected.palette(previewDark)
                },
                modifier = Modifier
                    .bringIntoViewRequester(previewRequester)
                    .onSizeChanged { previewSize = it },
            )

            // the editor lives here, right under the preview, rather than after the list it's opened from:
            // hue dragging is only meaningful while you can watch the result
            AnimatedVisibility(
                visible = customExpanded,
                enter = fadeIn(tween(200)) + expandVertically(tween(240)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(200)),
            ) {
                CustomThemeEditor(
                    spec = draft,
                    onDraft = { draft = it },
                    onCommit = onCustomChange,
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AppTheme.entries.forEach { theme ->
                    ThemeRow(
                        palette = theme.palette(previewDark),
                        label = stringResource(theme.labelRes),
                        blurb = stringResource(theme.blurbRes),
                        selected = selected.id == theme.id,
                        onClick = {
                            // collapsing the editor pulls the whole list upward, so follow it with the preview rather than
                            // leaving the user looking at rows that jumped under their finger
                            if (customExpanded) {
                                customExpanded = false
                                openRequests++
                            }
                            onSelectBuiltIn(theme)
                        },
                    )
                }

                val customPreview = customPalette(
                    accentHue = draft.accentHue,
                    supportHue = draft.supportHue,
                    vividness = draft.vividness,
                    dark = previewDark,
                )

                ThemeRow(
                    palette = customPreview,
                    label = stringResource(R.string.theme_yours),
                    blurb = when {
                        !customUnlocked -> stringResource(R.string.plan_colors_locked)
                        customSelected -> stringResource(R.string.theme_yours_adjust)
                        else -> stringResource(R.string.theme_yours_build)
                    },
                    selected = customSelected,
                    locked = !customUnlocked,
                    onClick = {
                        if (!customUnlocked) {
                            onCustomLocked()
                            return@ThemeRow
                        }
                        customExpanded = true
                        openRequests++
                        onCustomChange(draft)
                    },
                )
            }
        }
    }
}

@Composable
private fun CustomThemeEditor(
    spec: CustomThemeSpec,
    onDraft: (CustomThemeSpec) -> Unit,
    onCommit: (CustomThemeSpec) -> Unit,
) {
    Column(Modifier.padding(top = 16.dp)) {
        Text(
            text = stringResource(R.string.theme_your_colors),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            // worth saying plainly: people expect a picker that lets them make something unreadable, and
            // are otherwise surprised when their exact colour shifts
            text = stringResource(R.string.theme_your_colors_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(14.dp))

        HueRow(
            label = stringResource(R.string.theme_main_color),
            hue = spec.accentHue,
            vividness = spec.vividness,
            onHueChange = { onDraft(spec.copy(accentHue = it)) },
            onCommit = { onCommit(spec.copy(accentHue = it)) },
        )

        Spacer(Modifier.height(14.dp))

        HueRow(
            label = stringResource(R.string.theme_second_color),
            hue = spec.supportHue,
            vividness = spec.vividness,
            onHueChange = { onDraft(spec.copy(supportHue = it)) },
            onCommit = { onCommit(spec.copy(supportHue = it)) },
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.theme_intensity),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Vividness.entries.forEach { option ->
                val isSelected = option == spec.vividness
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                    onClick = { onCommit(spec.copy(vividness = option)) },
                ) {
                    Box(
                        Modifier.padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(option.labelRes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HueRow(
    label: String,
    hue: Float,
    vividness: Vividness,
    onHueChange: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(hslColor(hue, vividness.saturation, 0.5f))
            )
        }
        Spacer(Modifier.height(7.dp))
        HueSlider(hue = hue, onHueChange = onHueChange, onCommit = onCommit)
    }
}

private val TrackHeight = 26.dp
private val ThumbSize = 22.dp

// only hue is user-controlled, see customPalette for why the rest is derived
@Composable
private fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    onCommit: (Float) -> Unit,
) {
    val density = LocalDensity.current
    var trackWidthPx by remember { mutableStateOf(1f) }

    // pointerInput(Unit) builds its gesture handlers once and keeps them, so the lambdas captured
    // there never see a later recomposition's values. without this, dragging the second hue would
    // commit a spec still carrying the old first hue, silently undoing the previous adjustment
    val currentHue by rememberUpdatedState(hue)
    val currentOnHueChange by rememberUpdatedState(onHueChange)
    val currentOnCommit by rememberUpdatedState(onCommit)

    // the full wheel in 13 stops. fewer and the interpolation visibly skips hues, which makes the
    // strip a poor map of what you're actually selecting
    val spectrum = remember {
        Brush.horizontalGradient((0..12).map { hslColor(it * 30f, 0.72f, 0.5f) })
    }

    fun report(x: Float): Float {
        val next = ((x / trackWidthPx).coerceIn(0f, 1f)) * 360f
        currentOnHueChange(next)
        return next
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TrackHeight)
            .onSizeChanged { trackWidthPx = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures {
                    currentOnCommit(report(it.x))
                }
            }
            .pointerInput(Unit) {
                var latestGestureHue = currentHue
                detectHorizontalDragGestures(
                    onDragStart = { latestGestureHue = currentHue },
                    onDragEnd = { currentOnCommit(latestGestureHue) },
                    onDragCancel = { currentOnCommit(latestGestureHue) },
                ) { change, _ ->
                    change.consume()
                    latestGestureHue = report(change.position.x)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(TrackHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(spectrum)
        )

        val thumbOffset: Dp = with(density) {
            val usable = trackWidthPx - ThumbSize.toPx()
            ((hue / 360f) * usable).coerceAtLeast(0f).toDp()
        }

        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(ThumbSize)
                .clip(CircleShape)
                .background(Color.White)
                .padding(3.dp)
                .clip(CircleShape)
                .background(hslColor(hue, 0.72f, 0.5f))
        )
    }
}

@Composable
private fun ThemeRow(
    palette: ThemePalette,
    label: String,
    blurb: String,
    selected: Boolean,
    onClick: () -> Unit,
    locked: Boolean = false,
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) palette.accent else palette.outlineVariant,
        animationSpec = tween(250),
        label = "themeRowBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = tween(250),
        label = "themeRowBorderWidth",
    )
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.99f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "themeRowScale",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .border(borderWidth, borderColor, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        // the row wears the theme it offers, not the one currently applied
        color = palette.container,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Swatch(palette.accent, palette.accentEnd, palette.support)

            Spacer(Modifier.width(13.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.onSurface,
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            locked -> palette.accent.copy(alpha = 0.16f)
                            selected -> palette.accent
                            else -> Color.Transparent
                        },
                    )
                    .border(
                        width = if (selected || locked) 0.dp else 1.5.dp,
                        color = palette.onSurfaceVariant.copy(alpha = 0.35f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    locked -> Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = stringResource(R.string.plan_locked_cd),
                        tint = palette.accent,
                        modifier = Modifier.size(13.dp),
                    )
                    selected -> Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.common_selected),
                        tint = palette.onAccent,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

// accent gradient plus the supporting colour, the whole theme at a glance
@Composable
private fun Swatch(accent: Color, accentEnd: Color, support: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(accent, accentEnd)))
        )
        Spacer(Modifier.width(5.dp))
        Box(
            modifier = Modifier
                .size(width = 14.dp, height = 30.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(support)
        )
    }
}
