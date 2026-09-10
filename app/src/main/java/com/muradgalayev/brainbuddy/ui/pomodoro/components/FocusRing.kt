package com.muradgalayev.brainbuddy.ui.pomodoro.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private val RingStroke = 14.dp

// how far either side of the stroke still counts as grabbing the ring
private val RingTouchSlop = 32.dp

// the countdown ring. beyond drawing progress it is the timer's only running-state control:
// drag the knob backwards to cut a session short, forwards to hand back time you'd skipped, or
// all the way round to finish. that replaced the old +/-5 min buttons, which forced you to
// think in fixed increments.
// onScrub is called continuously with the dragged-to fraction and returns whether the drag has
// reached the end; onScrubReleased fires once on lift with that verdict, so the caller only
// commits to finishing when the finger comes up there
@Composable
fun FocusRing(
    progress: Float,
    arcColor: Color,
    arcColorEnd: Color,
    trackColor: Color,
    glowAlpha: Float,
    scrubEnabled: Boolean,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Boolean,
    onScrubReleased: (atEnd: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (scrubbing: Boolean, atEnd: Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    var scrubbing by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableFloatStateOf(0f) }
    var atEnd by remember { mutableStateOf(false) }

    // the gesture lambdas are created once per scrubEnabled flip but read these on every pointer
    // event, so they have to see the latest values rather than captured ones
    val currentProgress by rememberUpdatedState(progress)
    val currentOnStart by rememberUpdatedState(onScrubStart)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnRelease by rememberUpdatedState(onScrubReleased)

    // while dragging, render the finger's position directly. routing through the caller's animated
    // progress would leave the knob trailing the touch by an animation frame
    val shownProgress = if (scrubbing) scrubProgress else progress

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(scrubEnabled) {
                if (!scrubEnabled) return@pointerInput

                val strokePx = RingStroke.toPx()
                val slopPx = RingTouchSlop.toPx()
                var lastAngle = 0f

                detectDragGestures(
                    onDragStart = { start ->
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val radius = (minOf(size.width, size.height) - strokePx) / 2f
                        // only grabs that land on the ring itself scrub, the middle of the circle stays free for the
                        // tap-to-set-duration target
                        if (abs((start - center).getDistance() - radius) <= slopPx) {
                            scrubbing = true
                            atEnd = false
                            scrubProgress = currentProgress
                            lastAngle = angleFraction(start, center)
                            currentOnStart()
                        }
                    },
                    onDrag = { change, _ ->
                        if (!scrubbing) return@detectDragGestures
                        change.consume()

                        val center = Offset(size.width / 2f, size.height / 2f)
                        val angle = angleFraction(change.position, center)

                        // accumulate deltas instead of using the angle outright: a drag past 12 o'clock wraps 0.99 to
                        // 0.01, which would otherwise read as a full rewind. anything over half a turn in one event
                        // is that wrap, not a real movement
                        var delta = angle - lastAngle
                        if (delta > 0.5f) delta -= 1f
                        if (delta < -0.5f) delta += 1f
                        lastAngle = angle

                        scrubProgress = (scrubProgress + delta).coerceIn(0f, 1f)
                        val reachedEnd = currentOnScrub(scrubProgress)
                        if (reachedEnd != atEnd) {
                            atEnd = reachedEnd
                            if (reachedEnd) {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        }
                    },
                    onDragEnd = {
                        if (!scrubbing) return@detectDragGestures
                        scrubbing = false
                        currentOnRelease(atEnd)
                        atEnd = false
                    },
                    onDragCancel = {
                        if (!scrubbing) return@detectDragGestures
                        scrubbing = false
                        // a cancelled gesture is not a decision to end the session
                        currentOnRelease(false)
                        atEnd = false
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.86f)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            arcColor.copy(alpha = glowAlpha + 0.10f),
                            arcColor.copy(alpha = 0.05f),
                            Color.Transparent,
                        )
                    )
                )
        )

        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = RingStroke.toPx()
            val radius = (size.minDimension - strokeWidth) / 2f
            val topLeft = Offset(
                (size.width - radius * 2) / 2f,
                (size.height - radius * 2) / 2f,
            )
            val arcSize = Size(radius * 2, radius * 2)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            drawArc(
                brush = Brush.sweepGradient(listOf(arcColor, arcColorEnd, arcColor)),
                startAngle = -90f,
                sweepAngle = 360f * shownProgress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )

            if (scrubEnabled) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val knobAngle = Math.toRadians((-90f + 360f * shownProgress).toDouble())
                val knob = Offset(
                    center.x + radius * cos(knobAngle).toFloat(),
                    center.y + radius * sin(knobAngle).toFloat(),
                )
                val knobRadius = strokeWidth * if (scrubbing) 0.95f else 0.72f

                // halo grows on touch so the knob reads as held under a fingertip
                drawCircle(
                    color = arcColor.copy(alpha = if (scrubbing) 0.28f else 0.16f),
                    radius = knobRadius * 1.9f,
                    center = knob,
                )
                drawCircle(color = Color.White, radius = knobRadius, center = knob)
                drawCircle(
                    color = if (atEnd) arcColorEnd else arcColor,
                    radius = knobRadius * 0.5f,
                    center = knob,
                )
            }
        }

        content(scrubbing, atEnd)
    }
}

// touch position as a fraction of a full turn, measured clockwise from 12 o'clock
private fun angleFraction(point: Offset, center: Offset): Float {
    val degrees = Math.toDegrees(
        atan2((point.y - center.y).toDouble(), (point.x - center.x).toDouble())
    ).toFloat()
    // atan2 puts 0 at 3 o'clock, and the arc is drawn from -90, so rotate to match
    return ((((degrees + 90f) % 360f) + 360f) % 360f) / 360f
}
