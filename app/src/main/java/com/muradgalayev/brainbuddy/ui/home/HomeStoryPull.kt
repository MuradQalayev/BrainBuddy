package com.muradgalayev.brainbuddy.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.min

// how far home has to be dragged past its top before the stories open
private val PULL_THRESHOLD = 96.dp

// drag resistance. below 1 the indicator trails the finger, so the gesture has weight and
// passing the threshold is something you feel rather than trip over, which is the whole point
// of putting the stories behind a pull instead of a button
private const val DRAG_RESISTANCE = 0.55f

// the overscroll-at-the-top gesture that reveals stories. pull home down past its top and the
// buddy fills up, let go past the threshold and the stories open. it's a pull-to-refresh in
// shape but doing the opposite job: refresh is a chore you perform on content you already
// have, this is an invitation you can decline by simply not pulling. nothing badges, nothing
// notifies, and with no stories to show the gesture doesn't exist at all
class StoryPullState(
    private val thresholdPx: Float,
    private val scope: CoroutineScope,
    private val enabled: () -> Boolean,
    private val onTrigger: () -> Unit,
) {
    private val offset = Animatable(0f)

    // 0f untouched, 1f at the point where releasing opens the stories
    val progress: Float get() = (offset.value / thresholdPx).coerceIn(0f, 1f)

    val connection: NestedScrollConnection = object : NestedScrollConnection {

        // dragging back up unwinds our own offset before the list is allowed to scroll, otherwise the
        // indicator stays stretched open while the content moves underneath it
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y >= 0f || offset.value <= 0f) return Offset.Zero
            val consumed = min(-available.y, offset.value)
            scope.launch { offset.snapTo(offset.value - consumed) }
            return Offset(0f, -consumed)
        }

        // only what the scrollable couldn't use, i.e. already at the top with the finger still going down
        override fun onPostScroll(
            consumed: Offset,
            available: Offset,
            source: NestedScrollSource,
        ): Offset {
            if (!enabled() || available.y <= 0f || source != NestedScrollSource.Drag) {
                return Offset.Zero
            }
            scope.launch { offset.snapTo(offset.value + available.y * DRAG_RESISTANCE) }
            return Offset(0f, available.y)
        }

        // release. fires on the way out rather than the moment the threshold is crossed, so a pull
        // that goes too far can still be taken back by dragging up again before letting go
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (offset.value <= 0f) return Velocity.Zero
            val triggered = offset.value >= thresholdPx
            offset.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
            if (triggered) onTrigger()
            // the drag is spent either way, and letting the fling through would scroll home the instant
            // the stories opened over it
            return available
        }
    }
}

@Composable
fun rememberStoryPullState(
    enabled: () -> Boolean,
    onTrigger: () -> Unit,
): StoryPullState {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val thresholdPx = with(density) { PULL_THRESHOLD.toPx() }
    return remember(thresholdPx) {
        StoryPullState(
            thresholdPx = thresholdPx,
            scope = scope,
            enabled = enabled,
            onTrigger = onTrigger,
        )
    }
}

// the buddy, filling up as you pull. drawn rather than animated from an asset so the fill can
// be a clip on the same path as the outline: the level rises inside the silhouette instead of
// a bar creeping over a logo, which is what makes it read as filling rather than loading.
// sized by progress too, but only slightly, and it reaches full size exactly when releasing
// would open the stories, so the size is a second reading of the same signal
@Composable
fun StoryPullIndicator(
    progress: Float,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    if (progress <= 0.01f) return

    val scale = 0.55f + 0.45f * progress
    Box(modifier.size(52.dp * scale)) {
        Canvas(Modifier.size(52.dp * scale)) {
            val path = buddyBlobPath(size)

            // the empty vessel
            drawPath(path, color = accent.copy(alpha = 0.14f))

            // the level, clipped to the same path by drawing inside a rect exposing only the bottom of it
            clipRect(top = size.height * (1f - progress)) {
                drawPath(path, color = accent.copy(alpha = 0.85f))
            }

            drawPath(
                path,
                color = accent.copy(alpha = 0.55f + 0.45f * progress),
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

// a rounded two-lobed silhouette, the buddy's head simplified. deliberately a separate path
// from HomeBuddy's: that one carries a face, an antenna and moods, none of which mean
// anything at 30dp behind a finger
private fun buddyBlobPath(size: Size): Path {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val halfW = w * 0.44f
    val halfH = h * 0.40f

    return Path().apply {
        // left side, up and over the left lobe
        moveTo(cx - halfW, cy + halfH * 0.35f)
        cubicTo(
            cx - halfW * 1.08f, cy - halfH * 0.55f,
            cx - halfW * 0.62f, cy - halfH * 1.30f,
            cx - halfW * 0.10f, cy - halfH * 0.92f,
        )
        // the dip between the lobes, then the right lobe
        cubicTo(
            cx + halfW * 0.42f, cy - halfH * 1.34f,
            cx + halfW * 1.10f, cy - halfH * 0.58f,
            cx + halfW, cy + halfH * 0.35f,
        )
        // along the jaw and back
        cubicTo(
            cx + halfW * 0.92f, cy + halfH * 1.12f,
            cx - halfW * 0.92f, cy + halfH * 1.12f,
            cx - halfW, cy + halfH * 0.35f,
        )
        close()
    }
}
