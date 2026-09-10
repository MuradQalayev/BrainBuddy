package com.muradgalayev.brainbuddy.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloseFullscreen
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.OpenInFull
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn

// edit mode for the home screen. Home is the screen people open by reflex, and reflex is the
// whole point, so what belongs on it can't be a decision Myndora makes on the user's behalf.
// someone who never looks at step counts shouldn't have to scroll past them forever, and
// someone who lives in the capture box should be able to make it the only thing there.
// what edit mode offers is arrangement: switch a tile off, put it back, change its width, or
// drag it somewhere else. the starting layout still carries the ranking, and this is where a
// user disagrees with it

// hold a tile to start customising. watches the Initial pass instead of taking the gesture,
// because every tile already has its own taps: a detector that consumed the press would break
// starting a focus session, and one that waited its turn would never see a press landing on a
// button. once the hold fires the rest of that gesture is swallowed, so letting go doesn't
// also trigger whatever was under the finger
@Composable
fun Modifier.longPressToCustomise(enabled: Boolean, onLongPress: () -> Unit): Modifier {
    // both read live from inside the gesture loop. keying pointerInput on either would restart
    // the detector on every recomposition, and Home recomposes every ten seconds on its own clock,
    // so a press held across one would simply be dropped
    val armed by rememberUpdatedState(enabled)
    val fire by rememberUpdatedState(onLongPress)

    return this.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!armed) return@awaitEachGesture

            val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                // null from here means the gesture was cancelled, usually a scroll, and appending Unit keeps
                // that distinguishable from the timeout itself
                waitForUpOrCancellation(PointerEventPass.Initial)
                Unit
            } == null

            if (held) {
                fire()
                // swallowing the rest is what stops the release also firing whatever was under the finger. it
                // only works because this node stays mounted through the state change the hold just caused,
                // hence `enabled` rather than mounting and unmounting the modifier itself
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    event.changes.forEach { it.consume() }
                } while (event.changes.any { it.pressed })
            }
        }
    }
}

// a slow tilt, alternating direction per tile so the screen reads as editable
@Composable
fun Modifier.wiggle(active: Boolean, index: Int): Modifier {
    if (!active || !animationsOn()) return this
    val transition = rememberInfiniteTransition(label = "home_wiggle")
    val angle by transition.animateFloat(
        initialValue = -0.7f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "home_wiggle_angle",
    )
    return this.rotate(if (index % 2 == 0) angle else -angle)
}

// the remove badge that hangs off a tile's corner while editing
@Composable
fun RemoveBadge(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val motionDuration = if (animationsOn()) 180 else 0
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(tween(motionDuration)) + fadeIn(tween(motionDuration)),
        exit = scaleOut(tween(if (animationsOn()) 120 else 0)) +
            fadeOut(tween(if (animationsOn()) 120 else 0)),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(HomePillShape)
                .background(MaterialTheme.colorScheme.error)
                .border(2.dp, MaterialTheme.colorScheme.background, HomePillShape)
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Close,
                "Remove from home",
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

// a tile that's switched off, holding its place while editing. it keeps the slot rather than
// letting the layout close over it, so switching things on and off doesn't shuffle everything
// under your finger, and a tile comes back exactly where it was
@Composable
fun GhostTile(widget: HomeWidget, onRestore: () -> Unit, modifier: Modifier = Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier
            .clip(HomeCardShape)
            .dashedBorder(muted.copy(alpha = .38f))
            .clickable(onClick = onRestore)
            .padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(widget.icon, null, tint = muted.copy(alpha = .7f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(6.dp))
        Text(
            widget.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = muted,
        )
        Text(
            "Tap to add",
            style = MaterialTheme.typography.labelSmall,
            color = muted.copy(alpha = .75f),
        )
    }
}

// the width control on a tile's corner while editing. an arrow pair rather than a label,
// because it's the same control in both directions and naming the states makes people read
// before they can act
@Composable
fun ResizeBadge(
    visible: Boolean,
    span: WidgetSpan,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val motionDuration = if (animationsOn()) 180 else 0
    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(tween(motionDuration)) + fadeIn(tween(motionDuration)),
        exit = scaleOut(tween(if (animationsOn()) 120 else 0)) +
            fadeOut(tween(if (animationsOn()) 120 else 0)),
        modifier = modifier,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(HomePillShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(2.dp, MaterialTheme.colorScheme.background, HomePillShape)
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onToggle()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (span == WidgetSpan.Full) Icons.Rounded.CloseFullscreen else Icons.Rounded.OpenInFull,
                if (span == WidgetSpan.Full) "Make half width" else "Make full width",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

// the grab bar along a tile's bottom edge while editing. dragging is on a handle rather than
// on the tile itself: the tile already answers a tap, and a press that means two different
// things depending on how far it travels is what turns into accidental deletions
@Composable
fun DragHandle(visible: Boolean, dragging: Boolean, modifier: Modifier = Modifier) {
    val motionDuration = if (animationsOn()) 180 else 0
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(motionDuration)),
        exit = fadeOut(tween(motionDuration)),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .clip(HomePillShape)
                .background(
                    if (dragging) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                )
                .border(2.dp, MaterialTheme.colorScheme.background, HomePillShape)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.DragIndicator,
                "Drag to reorder",
                tint = if (dragging) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

private fun Modifier.dashedBorder(color: Color): Modifier = drawWithContent {
    drawContent()
    drawRoundRect(
        color = color,
        size = Size(size.width, size.height),
        cornerRadius = CornerRadius(28.dp.toPx()),
        style = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()), 0f),
        ),
        topLeft = Offset.Zero,
    )
}

// the bar at the foot of home: enters edit mode, and gets you back out of it
@Composable
fun CustomiseBar(
    editing: Boolean,
    locked: Boolean = false,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val haptics = LocalHapticFeedback.current
    val highlighted = editing && !locked
    Row(
        modifier
            .clip(HomePillShape)
            .background(if (highlighted) accent else MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, if (highlighted) Color.Transparent else homeCardBorder(), HomePillShape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when {
                locked -> Icons.Rounded.Lock
                editing -> Icons.Rounded.Check
                else -> Icons.Rounded.Tune
            },
            null,
            tint = if (highlighted) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(17.dp),
        )
        Spacer(Modifier.width(7.dp))
        Text(
            when {
                locked -> "Home layout locked"
                editing -> "Done"
                else -> "Customise home"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (highlighted) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
