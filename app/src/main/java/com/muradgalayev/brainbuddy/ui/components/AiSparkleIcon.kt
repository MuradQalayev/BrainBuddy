package com.muradgalayev.brainbuddy.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

// animated AI sparkle: three four-point stars that twinkle in a staggered loop. recreated in
// Compose from the SMIL-animated SVG, which Android can't render directly. positions and sizes
// match the source 40x40 viewBox
@Composable
fun AiSparkleIcon(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "aiSparkle")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2267, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Canvas(modifier = modifier) {
        val s = size.minDimension / 40f
        // cx, cy, outer radius, base alpha, phase offset, staggered like the SVG
        drawSparkle(15.25f, 20.05f, 12.0f, 0.5f, phase, 0.00f, color, s) // main
        drawSparkle(29.87f, 10.31f, 7.2f, 0.3f, phase, 0.33f, color, s)  // top-right
        drawSparkle(27.37f, 30.95f, 6.0f, 0.2f, phase, 0.63f, color, s)  // bottom-right
    }
}

private fun DrawScope.drawSparkle(
    cx: Float,
    cy: Float,
    outerR: Float,
    baseAlpha: Float,
    phase: Float,
    offset: Float,
    color: Color,
    s: Float,
) {
    val local = (phase + offset) % 1f
    // smooth 0 to 1 to 0 twinkle bump over the cycle
    val bump = sin((local * PI).toFloat()).coerceAtLeast(0f)
    val scale = 0.85f + bump * 0.55f
    val alpha = (baseAlpha + bump * (1f - baseAlpha)).coerceIn(0f, 1f)

    val r = outerR * scale * s
    val innerR = r * 0.18f
    val center = Offset(cx * s, cy * s)
    val path = Path()
    for (i in 0 until 8) {
        val rad = if (i % 2 == 0) r else innerR
        val a = (-PI / 2 + i * PI / 4).toFloat()
        val x = center.x + rad * cos(a)
        val y = center.y + rad * sin(a)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path = path, color = color.copy(alpha = alpha))
}
