package com.muradgalayev.brainbuddy.ui.calendar

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.util.lerp
import kotlin.math.min

// one-time 'meet the Calendar AI' intro. a round shape bursts fast out of the middle of the
// screen, then rockets up into the AI button, shrinking into its logo and firing a quick
// highlight ring on landing. targetCenter and targetRadius are in root pixels
@Composable
fun CalendarAiIntroOverlay(
    targetCenter: Offset,
    targetRadius: Float,
    color: Color,
    onFinished: () -> Unit,
) {
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 780, easing = LinearEasing),
        )
        onFinished()
    }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val p = progress.value
        val start = Offset(size.width / 2f, size.height / 2f)
        val bigRadius = min(size.width, size.height) * 0.24f

        // phase 1 (0 to 0.28): burst outward from the middle. phase 2 (0.28 to 0.9): fly to the AI
        // button while shrinking into it. phase 3 (0.9 to 1): fade the orb and pulse a ring on the logo
        val burst = (p / 0.28f).coerceIn(0f, 1f)
        val travelRaw = ((p - 0.28f) / 0.62f).coerceIn(0f, 1f)
        val travel = FastOutSlowInEasing.transform(travelRaw)
        val land = ((p - 0.9f) / 0.1f).coerceIn(0f, 1f)

        val center = Offset(
            x = lerp(start.x, targetCenter.x, travel),
            y = lerp(start.y, targetCenter.y, travel),
        )
        val radius = if (p < 0.28f) {
            bigRadius * burst
        } else {
            lerp(bigRadius, targetRadius, travel)
        }
        val alpha = 1f - land

        if (alpha > 0f) {
            // soft outer glow trailing the orb
            drawCircle(
                color = color.copy(alpha = alpha * 0.22f),
                radius = radius * 1.7f,
                center = center,
            )
            drawCircle(
                color = color.copy(alpha = alpha),
                radius = radius,
                center = center,
            )
        }

        // highlight ring blooming out of the AI logo as the orb lands
        if (land > 0f) {
            drawCircle(
                color = color.copy(alpha = (1f - land) * 0.9f),
                radius = targetRadius + land * targetRadius * 2.2f,
                center = targetCenter,
                style = Stroke(width = targetRadius * 0.35f),
            )
        }
    }
}
