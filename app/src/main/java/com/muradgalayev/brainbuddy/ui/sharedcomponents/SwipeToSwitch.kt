package com.muradgalayev.brainbuddy.ui.sharedcomponents

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import kotlin.math.abs

// drag a two-segment switch instead of aiming at the half you want. swiping right selects the
// right-hand option and swiping left the left-hand one, with the pill following your thumb
// rather than the content sliding under it. that's the opposite of a pager, and it's the right
// way round here because the thing being moved is the selector.
// aiming at one half of a small pill is a fiddly target, and it's fiddliest exactly when
// someone is in a hurry to write a thought down before it's gone. a swipe anywhere across the
// control hits it every time.
// fires once per gesture, at the moment the threshold is crossed, with a light haptic, so the
// switch happens under your thumb rather than after you let go
@Composable
fun Modifier.swipeToSwitch(
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    // comfortably past touch slop, comfortably short of half a phone's width
    val threshold = with(LocalDensity.current) { 44.dp.toPx() }

    return this.pointerInput(onSwipeLeft, onSwipeRight, threshold) {
        var travelled = 0f
        var fired = false
        detectHorizontalDragGestures(
            onDragStart = {
                travelled = 0f
                fired = false
            },
            onDragEnd = { fired = false },
            onDragCancel = { fired = false },
        ) { change, delta ->
            travelled += delta
            if (!fired && abs(travelled) > threshold) {
                fired = true
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                if (travelled > 0f) onSwipeRight() else onSwipeLeft()
            }
            change.consume()
        }
    }
}
