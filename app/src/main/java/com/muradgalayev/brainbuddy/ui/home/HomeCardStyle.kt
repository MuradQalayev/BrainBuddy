package com.muradgalayev.brainbuddy.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents

// the shared look of the home tiles. three things do the work, and they're worth naming because
// they're what separate a screen of coloured rectangles from a screen that looks made on purpose:
// - a hairline, not a shadow. a 1dp outline reads crisply at any brightness, where a drop shadow
//   turns to grey mud in dark mode, and it's what the rest of the app already does.
// - a tint, not a fill. each tile carries a barely-there wash of its own accent, falling off
//   towards the bottom as if lit from above. flat surfaceContainer everywhere makes a layout
//   feel like a spreadsheet.
// - nested radii. inner elements curve less than the tile holding them; matching radii at
//   different sizes is the detail that makes corners look wrong without anyone saying why
val HomeCardShape = RoundedCornerShape(28.dp)
val HomeInnerShape = RoundedCornerShape(18.dp)
val HomePillShape = RoundedCornerShape(999.dp)

@Composable
fun homeCardBorder(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)

@Composable
private fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < .5f

// lit from the top in the tile's own accent. deliberately almost invisible
@Composable
fun homeCardWash(accent: Color): Brush = Brush.verticalGradient(
    listOf(accent.copy(alpha = if (isDarkTheme()) .10f else .055f), Color.Transparent)
)

// a home tile. handles the surface, the hairline, the wash and the press, so no card has to
// remember to do those four things the same way as its neighbours. every tile squeezes under a
// finger, tappable or not: a tile that doesn't react reads as a picture of a tile
@Composable
fun HomeCard(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.myndoraAccents.accent,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val animate = animationsOn()
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed && animate) .965f else 1f,
        // quick in, springy out: the release overshoots a touch, which is the part that feels alive
        animationSpec = if (pressed) {
            spring(dampingRatio = .8f, stiffness = Spring.StiffnessMedium)
        } else {
            spring(dampingRatio = .45f, stiffness = Spring.StiffnessMediumLow)
        },
        label = "home_card_press",
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .observePress { pressed = it }
            .border(1.dp, homeCardBorder(), HomeCardShape),
        shape = HomeCardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(homeCardWash(accent))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                    } else {
                        Modifier
                    }
                ),
            content = content,
        )
    }
}

// watches a press on the Initial pass without consuming it, so every button inside the tile still
// gets its tap. a finger that travels past touch slop is a scroll or a drag, and lets go of the squeeze
private fun Modifier.observePress(onPressed: (Boolean) -> Unit): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        onPressed(true)
        val slop = viewConfiguration.touchSlop
        try {
            while (true) {
                val change = awaitPointerEvent(PointerEventPass.Initial)
                    .changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                if ((change.position - down.position).getDistance() > slop) break
            }
        } finally {
            onPressed(false)
        }
    }
}

// a slow, small swell for something that should look alive while it waits. still with reduce motion on
@Composable
fun Modifier.breathing(amount: Float = .05f, periodMs: Int = 2400): Modifier {
    if (!animationsOn()) return this
    val t by rememberInfiniteTransition(label = "breathing").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breath",
    )
    return graphicsLayer {
        val s = 1f + amount * t
        scaleX = s
        scaleY = s
    }
}

// an expanding, fading ring behind something, the 'this is live' signal. place it first in a Box
// with matchParentSize. draws nothing with reduce motion on
@Composable
fun PulseRing(
    color: Color,
    shape: Shape,
    modifier: Modifier = Modifier,
    grow: Float = .45f,
    periodMs: Int = 1800,
) {
    if (!animationsOn()) return
    val t by rememberInfiniteTransition(label = "pulse_ring").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(periodMs, easing = LinearOutSlowInEasing), RepeatMode.Restart),
        label = "pulse",
    )
    Box(
        modifier
            .graphicsLayer {
                val s = 1f + grow * t
                scaleX = s
                scaleY = s
                alpha = (1f - t) * .45f
            }
            .background(color, shape),
    )
}

// small, tracked, upper-case. the quiet half of a heading pair
@Composable
fun HomeSectionLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        letterSpacing = 1.3.sp,
        maxLines = 1,
    )
}

// fixed-width digits. a countdown re-rendering every few seconds shifts sideways as 1s and 4s
// swap places, and the eye reads that twitch as the layout being unstable. tabular figures pin it
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
