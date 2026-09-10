package com.muradgalayev.brainbuddy.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.domain.model.AppMode
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.accessibility.speaking
import com.muradgalayev.brainbuddy.ui.modes.modeAccentColor
import com.muradgalayev.brainbuddy.ui.modes.modeIcon

// the mode tile: what's running, and every other option stacked under it. the list is always
// open rather than behind a chevron. hiding it kept the tile to one line but cost two taps to
// switch and left the card mostly empty; open, the space goes to the choice itself and each
// option keeps the line saying what it actually changes, which is the only basis anyone has
// for picking one.
// the tile therefore grows by a row per mode. that's the accepted cost of this shape: it's a
// tile the user can turn off or resize, and someone with eight modes has said by building them
// that switching matters to them.
// the modes come from the user's own list rather than a fixed enum. 'no mode' is offered as an
// explicit choice, because without it there's no way to stop applying one short of finding a
// mode that overrides nothing
@Composable
fun HomeModeTile(
    modes: List<AppMode>,
    activeMode: AppMode?,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val animate = animationsOn()
    val colorDuration = if (animate) 420 else 0
    val controlDuration = if (animate) 240 else 0
    val accent by animateColorAsState(
        targetValue = modeAccentColor(activeMode?.accent),
        animationSpec = tween(colorDuration),
        label = "mode_accent",
    )

    val title = activeMode?.name ?: "No mode"

    HomeCard(modifier = modifier, accent = accent) {
        Column(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 11.dp)
                    .semantics { contentDescription = "Mode: $title" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusLed(color = accent)
                Spacer(Modifier.width(10.dp))
                RollingTitle(title = title, accent = accent)
            }

            // stacked, and always open. the tile carries the full choice rather than a chevron over it:
            // switching is one tap, and each option has room for the line saying what it changes
            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                // only the modes you aren't in, the one you are in is the line above
                modes.filter { it.id != activeMode?.id }.forEach { option ->
                    ModeOptionRow(
                        icon = { tint ->
                            Icon(modeIcon(option.icon), null, tint = tint, modifier = Modifier.size(15.dp))
                        },
                        title = option.name,
                        blurb = option.summaryLine(),
                        onClick = { onSelect(option.id) },
                    )
                }
                if (activeMode != null) {
                    ModeOptionRow(
                        icon = { tint ->
                            Icon(Icons.Rounded.Tune, null, tint = tint, modifier = Modifier.size(15.dp))
                        },
                        title = "No mode",
                        blurb = "Your own settings",
                        onClick = { onSelect(null) },
                    )
                }
                ModeOptionRow(
                    icon = { tint ->
                        Icon(Icons.Rounded.Tune, null, tint = tint, modifier = Modifier.size(15.dp))
                    },
                    title = if (modes.isEmpty()) "Create a mode" else "Manage modes",
                    blurb = "Edit, schedule, create",
                    onClick = onManage,
                )
            }
        }
    }
}

// a short 'what this changes' line, built from whatever the mode actually overrides
private fun AppMode.summaryLine(): String {
    val parts = buildList {
        overrides.ringer?.let { add(it.name.lowercase().replaceFirstChar(Char::uppercase)) }
        if (overrides.doNotDisturb == true) add("Do Not Disturb")
        if (overrides.simplifiedWorkspace == true) add("Simplified")
        if (overrides.hiddenHomeWidgets != null) add("Own home screen")
    }
    return parts.take(2).joinToString(" \u00b7 ").ifBlank { "Switched by hand" }
}

// one of the modes you could switch to. says what it does, because that's the whole choice
@Composable
private fun ModeOptionRow(
    icon: @Composable (Color) -> Unit,
    title: String,
    blurb: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HomeInnerShape)
            .clickable(onClick = speaking(title, onClick))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.onSurfaceVariant.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            icon(colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(11.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
        Spacer(Modifier.width(9.dp))
        Text(
            text = blurb,
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// the mode's name, rolling. the row is a fixed height and clipped so the two names pass each
// other inside it, without which the strip would grow and shrink by a line height on every
// switch, exactly the kind of layout twitch this app spends its effort avoiding
@Composable
private fun RollingTitle(title: String, accent: Color) {
    // read outside the transition lambda: that lambda isn't composable, so the setting has to be
    // resolved here and captured
    val animate = animationsOn()
    Box(Modifier.height(26.dp).clip(HomePillShape), contentAlignment = Alignment.CenterStart) {
        AnimatedContent(
            targetState = title,
            transitionSpec = {
                if (!animate) {
                    fadeIn(tween(0)).togetherWith(fadeOut(tween(0)))
                } else {
                    val slide = tween<IntOffset>(durationMillis = 260, easing = FastOutSlowInEasing)
                    (slideInVertically(slide) { height -> height } + fadeIn(tween(220)))
                        .togetherWith(
                            slideOutVertically(slide) { height -> -height } + fadeOut(tween(160))
                        )
                }
            },
            label = "mode_name",
        ) { current ->
            Text(
                text = current,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// slow, shallow pulse, a status light idling rather than an alert. held still under reduce
// motion, where a permanent pulse is precisely what the setting exists to stop
@Composable
private fun StatusLed(color: Color) {
    val glow: State<Float>? = if (animationsOn()) {
        val transition = rememberInfiniteTransition(label = "mode_led")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "mode_led_glow",
        )
    } else {
        null
    }

    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f * (glow?.value ?: 1f)))
        )
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = glow?.value ?: 1f))
        )
    }
}
