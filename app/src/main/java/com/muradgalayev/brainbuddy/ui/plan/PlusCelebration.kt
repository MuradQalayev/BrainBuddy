package com.muradgalayev.brainbuddy.ui.plan

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
import androidx.compose.material3.MaterialTheme
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val DurationMs = 1600

// one sparkle: where it goes, how big, when it leaves, and how fast it spins
private data class Spark(
    val angle: Float,
    val distance: Float,
    val radius: Float,
    val delay: Float,
    val spin: Float,
    val silver: Boolean,
)

// the moment Plus turns on: rings pushing out from the button, a wash of light, and a scatter of
// silver and accent sparks falling back down. draws over everything and takes no touches, so the
// screen stays usable while it plays. only ever shown when motion is allowed
@Composable
fun PlusCelebration(origin: Offset?, onFinished: () -> Unit) {
    if (origin == null) return
    val accents = MaterialTheme.myndoraAccents
    val progress = remember(origin) { Animatable(0f) }
    val sparks = remember(origin) {
        val random = Random(origin.x.toInt() * 31 + origin.y.toInt())
        List(22) { index ->
            Spark(
                // spread evenly round the circle with a little jitter, so it never looks like a clock face
                angle = (index / 22f) * (2 * PI).toFloat() + random.nextFloat() * 0.26f,
                distance = 0.45f + random.nextFloat() * 0.75f,
                radius = 2.5f + random.nextFloat() * 4.5f,
                delay = random.nextFloat() * 0.18f,
                spin = (random.nextFloat() - 0.5f) * 220f,
                silver = index % 2 == 0,
            )
        }
    }

    LaunchedEffect(origin) {
        progress.animateTo(1f, tween(DurationMs, easing = LinearEasing))
        onFinished()
    }

    Canvas(Modifier.fillMaxSize()) {
        val p = progress.value
        val reach = size.minDimension * 0.62f

        // the wash: a short bloom of accent light under everything else
        val glow = (1f - p * 2.2f).coerceAtLeast(0f)
        if (glow > 0f) {
            val radius = reach * (0.3f + FastOutSlowInEasing.transform(p.coerceAtMost(0.6f) / 0.6f) * 0.9f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accents.accent.copy(alpha = 0.3f * glow), Color.Transparent),
                    center = origin,
                    radius = radius,
                ),
                radius = radius,
                center = origin,
            )
        }

        // two rings, the second half a beat behind, both thinning as they go
        drawRing(origin, p, reach, accents.accent)
        drawRing(origin, p - 0.14f, reach * 0.8f, Color(0xFFD6DBE3))

        sparks.forEach { spark ->
            val t = ((p - spark.delay) / (1f - spark.delay)).coerceIn(0f, 1f)
            if (t <= 0f || t >= 1f) return@forEach
            val eased = FastOutSlowInEasing.transform(t)
            val travel = reach * spark.distance * eased
            val x = origin.x + cos(spark.angle) * travel
            // gravity on the way out, so they arc instead of firing off in a straight star
            val y = origin.y + sin(spark.angle) * travel + reach * 0.35f * t * t
            val alpha = (1f - t * t).coerceIn(0f, 1f)
            val color = if (spark.silver) Color(0xFFEDF0F4) else accents.accentEnd
            drawSpark(
                center = Offset(x, y),
                radius = spark.radius.dp.toPx() * (1f - t * 0.35f),
                rotation = spark.spin * t,
                color = color.copy(alpha = alpha),
            )
        }
    }
}

// an expanding hoop that fades as it grows. negative progress means it hasn't started yet
private fun DrawScope.drawRing(center: Offset, progress: Float, reach: Float, color: Color) {
    if (progress <= 0f || progress >= 1f) return
    val eased = FastOutSlowInEasing.transform(progress)
    val alpha = (1f - progress) * (1f - progress)
    drawCircle(
        color = color.copy(alpha = 0.55f * alpha),
        radius = reach * eased,
        center = center,
        style = Stroke(width = (3.5f * (1f - progress) + 0.5f).dp.toPx()),
    )
}

// four-point sparkle: a diamond pulled in at the waist by control points at its centre
private fun DrawScope.drawSpark(center: Offset, radius: Float, rotation: Float, color: Color) {
    val path = Path().apply {
        moveTo(center.x, center.y - radius)
        quadraticBezierTo(center.x, center.y, center.x + radius, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y + radius)
        quadraticBezierTo(center.x, center.y, center.x - radius, center.y)
        quadraticBezierTo(center.x, center.y, center.x, center.y - radius)
        close()
    }
    rotate(degrees = rotation, pivot = center) {
        drawPath(path, color)
    }
}
