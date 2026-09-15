package com.muradgalayev.brainbuddy.ui.settings.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.ui.theme.ThemePalette
import kotlinx.coroutines.delay
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// a living miniature of the workspace, painted in whichever palette it's handed. swatch rows
// answer 'what colour is this theme', which isn't the question people actually have, namely
// 'what will my app look like'. showing the real arrangement makes the choice checkable
// without applying it and navigating away to find out.
// the motion is deliberately slow and small. it exists so the accent is seen doing the things
// it will actually do, a ring filling, a task completing, the AI button breathing, rather than
// sitting still as a rectangle. fast or large movement here would be a poor advertisement for
// an app whose whole point is not being agitating.
// everything is drawn from palette rather than MaterialTheme, so a preview can show a theme
// that isn't the active one
@Composable
fun ThemePreviewCard(
    palette: ThemePalette,
    modifier: Modifier = Modifier,
) {
    // eased so flicking between themes reads as one surface changing colour, matching what the
    // real switch does, rather than snapping between unrelated pictures
    val spec = tween<Color>(320)
    val bg by animateColorAsState(palette.background, spec, label = "prevBg")
    val surface by animateColorAsState(palette.surface, spec, label = "prevSurface")
    val container by animateColorAsState(palette.container, spec, label = "prevContainer")
    val accent by animateColorAsState(palette.accent, spec, label = "prevAccent")
    val accentEnd by animateColorAsState(palette.accentEnd, spec, label = "prevAccentEnd")
    val accentContainer by animateColorAsState(palette.accentContainer, spec, label = "prevAccentBox")
    val support by animateColorAsState(palette.support, spec, label = "prevSupport")
    val supportContainer by animateColorAsState(palette.supportContainer, spec, label = "prevSupportBox")
    val onAccent by animateColorAsState(palette.onAccent, spec, label = "prevOnAccent")
    val ink by animateColorAsState(palette.onSurface, spec, label = "prevInk")
    val muted by animateColorAsState(palette.onSurfaceVariant, spec, label = "prevMuted")
    val outline by animateColorAsState(palette.outlineVariant, spec, label = "prevOutline")

    val motion = rememberInfiniteTransition(label = "previewMotion")

    // the ring creeps forward and back rather than looping 0 to 1. a repeating reset reads as
    // loading, a slow drift reads as time passing, which is what the ring means
    val ringSweep by motion.animateFloat(
        initialValue = 0.58f,
        targetValue = 0.86f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringSweep",
    )

    val glow by motion.animateFloat(
        initialValue = 0.10f,
        targetValue = 0.26f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "ringGlow",
    )

    val haloScale by motion.animateFloat(
        initialValue = 1f,
        targetValue = 1.55f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aiHalo",
    )

    // the task row ticks itself off and resets, so the accent is shown in its most common role,
    // marking something done, rather than only as decoration
    var taskDone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(2600)
            taskDone = true
            delay(2200)
            taskDone = false
        }
    }
    val checkFill by animateFloatAsState(
        targetValue = if (taskDone) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "checkFill",
    )

    // a small settle when the palette changes, so a theme tap feels answered by the preview rather
    // than only recoloured
    val settle = remember { Animatable(1f) }
    LaunchedEffect(palette.accent, palette.background) {
        settle.snapTo(0.97f)
        settle.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(232.dp)
            .scale(settle.value)
            .clip(RoundedCornerShape(22.dp))
            .background(bg)
            .border(1.dp, outline, RoundedCornerShape(22.dp))
            .padding(11.dp),
    ) {
        Column(Modifier.fillMaxSize()) {
            // status strip: sells the 'this is a screen' framing cheaply
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MiniText("9:41", muted, 7.5f, FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                repeat(3) {
                    Box(
                        Modifier
                            .padding(start = 3.dp)
                            .size(3.dp)
                            .clip(CircleShape)
                            .background(muted.copy(alpha = 0.5f))
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    MiniText(stringResource(R.string.preview_good_morning), ink, 11f, FontWeight.Bold)
                    Spacer(Modifier.height(2.dp))
                    MiniText(stringResource(R.string.preview_three_things), muted, 8.5f)
                }
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(accentContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
                }
            }

            Spacer(Modifier.height(9.dp))

            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // focus ring: the app's most colour-carrying element
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        listOf(accent.copy(alpha = glow), Color.Transparent)
                                    )
                                )
                        )
                        Canvas(Modifier.size(58.dp)) {
                            val stroke = 6.dp.toPx()
                            val radius = (size.minDimension - stroke) / 2f
                            val topLeft = Offset(
                                (size.width - radius * 2) / 2f,
                                (size.height - radius * 2) / 2f,
                            )
                            val arcSize = Size(radius * 2, radius * 2)
                            drawArc(
                                color = muted.copy(alpha = 0.16f),
                                startAngle = -90f, sweepAngle = 360f, useCenter = false,
                                topLeft = topLeft, size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                            drawArc(
                                brush = Brush.sweepGradient(listOf(accent, accentEnd, accent)),
                                startAngle = -90f, sweepAngle = 360f * ringSweep, useCenter = false,
                                topLeft = topLeft, size = arcSize,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                            )
                        }
                        MiniText("18:24", ink, 10.5f, FontWeight.Bold)
                    }
                }

                // two cards: accent and support side by side
                Column(
                    Modifier.fillMaxHeight().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MiniCard(accentContainer, accent, stringResource(R.string.together_scope_calendar), ink, Modifier.weight(1f))
                    MiniCard(supportContainer, support, stringResource(R.string.preview_tasks), ink, Modifier.weight(1f))
                }
            }

            Spacer(Modifier.height(9.dp))

            // task row: where most of the app's text actually lives
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(container)
                    .padding(horizontal = 8.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(13.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            lerpColor(support.copy(alpha = 0f), support, checkFill)
                        )
                        .border(
                            width = 1.2.dp,
                            color = if (checkFill > 0.5f) support else muted.copy(alpha = 0.45f),
                            shape = RoundedCornerShape(4.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = onAccent,
                        modifier = Modifier.size(10.dp).scale(checkFill),
                    )
                }

                Spacer(Modifier.width(8.dp))

                MiniText(
                    stringResource(R.string.preview_read_chapter),
                    ink,
                    9f,
                    modifier = Modifier.alpha(1f - 0.45f * checkFill),
                )

                Spacer(Modifier.weight(1f))

                Box(
                    Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Brush.horizontalGradient(listOf(accent, accentEnd)))
                        .padding(horizontal = 9.dp, vertical = 3.5.dp),
                ) {
                    MiniText(stringResource(R.string.common_start), onAccent, 8f, FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(7.dp))

            // nav pill, with the AI button that carries the accent
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .background(surface)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                NavDot(muted)
                NavDot(muted)
                Box(contentAlignment = Alignment.Center) {
                    // halo expands and fades the way a pulse ring does, drawing the eye to the accent without
                    // anything actually moving position
                    Box(
                        Modifier
                            .size(17.dp)
                            .scale(haloScale)
                            .alpha(((1.55f - haloScale) / 0.55f).coerceIn(0f, 1f) * 0.45f)
                            .clip(CircleShape)
                            .background(accent)
                    )
                    Box(
                        Modifier
                            .size(17.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(accent, accentEnd)))
                    )
                }
                NavDot(muted)
                NavDot(muted)
            }
        }
    }
}

@Composable
private fun NavDot(color: Color) {
    Box(Modifier.size(6.dp).clip(CircleShape).background(color.copy(alpha = 0.55f)))
}

@Composable
private fun MiniCard(
    bg: Color,
    dot: Color,
    title: String,
    titleColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(dot))
        Spacer(Modifier.height(5.dp))
        MiniText(title, titleColor, 9f, FontWeight.SemiBold)
    }
}

private fun lerpColor(from: Color, to: Color, t: Float): Color =
    androidx.compose.ui.graphics.lerp(from, to, t.coerceIn(0f, 1f))

// fixed-size text, deliberately not using the app's typography scale. the preview is a scale
// model of the UI, not part of it: if it inherited the user's font size setting, Large type
// would burst the miniature layout while the real screens it depicts stayed fine
@Composable
private fun MiniText(
    text: String,
    color: Color,
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.Text(
        text = text,
        color = color,
        fontSize = size.sp,
        fontWeight = weight,
        maxLines = 1,
        modifier = modifier,
    )
}
