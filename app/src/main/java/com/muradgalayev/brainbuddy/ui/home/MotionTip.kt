package com.muradgalayev.brainbuddy.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.accessibility.speaking
import com.muradgalayev.brainbuddy.ui.navigation.LocalNavBarInset
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// what the tip hangs off: the card it sits under, and the moving thing inside it that the ring
// goes around. both in root coordinates, because the card scrolls and the tip follows it.
// assembled with derivedStateOf from the two rects the card reports, so the reads stay in the
// layout and draw phases where they belong
data class MotionAnchor(val card: Rect, val spot: Rect)

@Composable
fun rememberMotionAnchor(card: State<Rect?>, spot: State<Rect?>): State<MotionAnchor?> =
    remember(card, spot) {
        derivedStateOf {
            val cardBounds = card.value
            val spotBounds = spot.value
            if (cardBounds != null && spotBounds != null) MotionAnchor(cardBounds, spotBounds) else null
        }
    }

private val TipShape = RoundedCornerShape(22.dp)
private val ArrowWidth = 20.dp
private val ArrowHeight = 10.dp
private val SideMargin = 20.dp

// Reduce motion is buried in Settings and named for a thing people don't have a word for, so it
// goes unfound by exactly the people it was built for. this asks them once, in the middle of the
// movement, pointing at a piece of it: the breathing bolt when nothing is coming up, and the nav
// bar when the card is full of the day instead. one answer, either way, and it never asks again.
//
// positions are read in the layout and draw phases rather than in composition, so the tip can
// follow a scrolling card without recomposing the page under it every frame
@Composable
fun BoxScope.MotionTip(
    anchor: State<MotionAnchor?>,
    origin: State<Offset>,
    onTurnOn: () -> Unit,
    onKeep: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val accent = MaterialTheme.myndoraAccents.accent
    val animate = animationsOn()
    val navInset = LocalNavBarInset.current
    val maxArrowX = with(LocalConfiguration.current) { screenWidthDp.dp - SideMargin * 2 - ArrowWidth }

    // derived, so the tip recomposes when the anchor appears or goes away and not on every pixel
    // the page scrolls
    val pointsAtSpot by remember { derivedStateOf { anchor.value != null } }

    // it arrives a beat after the screen settles, and leaves under its own power before the
    // preference write takes it away, so neither end is a jump cut
    var shown by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf<(() -> Unit)?>(null) }
    LaunchedEffect(Unit) {
        delay(550)
        shown = true
    }
    LaunchedEffect(leaving) {
        leaving?.let {
            delay(if (animate) 220 else 0)
            it()
        }
    }
    val entry by animateFloatAsState(
        targetValue = if (shown && leaving == null) 1f else 0f,
        animationSpec = tween(if (animate) 380 else 0, easing = FastOutSlowInEasing),
        label = "motion_tip_entry",
    )

    // the ring breathes at the same rate as the bolt it surrounds: it is a sample of the thing
    // being offered, not decoration
    val pulse = if (animate) {
        rememberInfiniteTransition(label = "motion_tip").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "motion_tip_pulse",
        )
    } else {
        null
    }

    if (pointsAtSpot) {
        Canvas(Modifier.fillMaxSize()) {
            val spot = anchor.value?.spot ?: return@Canvas
            val corner = origin.value
            val grow = 4.dp.toPx() + 5.dp.toPx() * (pulse?.value ?: 0.5f)
            val ring = Rect(
                offset = Offset(spot.left - corner.x, spot.top - corner.y),
                size = Size(spot.width, spot.height),
            ).inflate(grow)
            drawRoundRect(
                color = accent.copy(alpha = 0.55f * (1f - (pulse?.value ?: 0.4f) * 0.7f) * entry),
                topLeft = ring.topLeft,
                size = ring.size,
                cornerRadius = CornerRadius(18.dp.toPx()),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }

    // under the card it points at, or above the nav bar it points down at.
    // measured rather than offset, so the clamp can use the tip's real height: at a large font
    // size this card is tall, and a tip that hangs off the bottom of the screen helps nobody
    val placement = if (pointsAtSpot) {
        Modifier
            .align(Alignment.TopStart)
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints)
                val floor = (constraints.maxHeight - placeable.height - navInset.roundToPx())
                    .coerceAtLeast(0)
                val wanted = (anchor.value?.card?.bottom ?: 0f) - origin.value.y + 12.dp.toPx()
                layout(placeable.width, placeable.height) {
                    placeable.place(0, wanted.roundToInt().coerceIn(0, floor))
                }
            }
    } else {
        Modifier
            .align(Alignment.BottomStart)
            .padding(bottom = navInset + 6.dp)
    }

    Column(
        modifier = placement
            .fillMaxWidth()
            .padding(horizontal = SideMargin)
            .graphicsLayer {
                alpha = entry
                translationY = (1f - entry) * 16.dp.toPx() * if (pointsAtSpot) -1f else 1f
            },
    ) {
        if (pointsAtSpot) {
            Arrow(
                pointingUp = true,
                color = colors.surface,
                modifier = Modifier.offset {
                    val spot = anchor.value?.spot
                    val centre = (spot?.center?.x ?: 0f) - origin.value.x
                    val x = centre - SideMargin.toPx() - ArrowWidth.toPx() / 2f
                    IntOffset(x.coerceIn(0f, maxArrowX.toPx()).roundToInt(), 0)
                },
            )
        }

        Surface(shape = TipShape, color = colors.surface, shadowElevation = 10.dp, tonalElevation = 3.dp) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(accent.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Animation, null, tint = accent, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.motion_tip_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (pointsAtSpot) {
                        stringResource(R.string.motion_tip_body_spot)
                    } else {
                        stringResource(R.string.motion_tip_body_bar)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val keepLabel = stringResource(R.string.motion_tip_keep)
                    Text(
                        text = keepLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable(role = Role.Button, onClick = speaking(keepLabel) { leaving = onKeep })
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                    val turnOnLabel = stringResource(R.string.motion_tip_turn_on)
                    Text(
                        text = turnOnLabel,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(accent, MaterialTheme.myndoraAccents.accentEnd),
                                )
                            )
                            .clickable(role = Role.Button, onClick = speaking(turnOnLabel) { leaving = onTurnOn })
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    )
                }
            }
        }

        if (!pointsAtSpot) {
            Arrow(
                pointingUp = false,
                color = colors.surface,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

// the little beak joining the card to whatever it is talking about
@Composable
private fun Arrow(pointingUp: Boolean, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier.size(ArrowWidth, ArrowHeight)) {
        val path = Path().apply {
            if (pointingUp) {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
            } else {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
            }
            close()
        }
        drawPath(path, color)
    }
}
