package com.muradgalayev.brainbuddy.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun VoiceWaveform(
    isListening: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 32,
    level: Float = 1f,
    activeColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    idleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
) {
    // smooth the live mic level so the wave rises and falls naturally instead of jittering
    val smoothLevel by animateFloatAsState(
        targetValue = level.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 120),
        label = "voice_level",
    )
    val infiniteTransition = rememberInfiniteTransition(label = "voice_wave")

    // several phase offsets for a richer, organic wave
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase2"
    )
    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase3"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        val barWidth = 3.dp.toPx()
        val corner = barWidth / 2f
        val totalBars = barCount.coerceAtMost(
            ((size.width + 4.dp.toPx()) / (barWidth + 4.dp.toPx())).toInt()
        )
        if (totalBars <= 0) return@Canvas

        val totalWidth = totalBars * barWidth + (totalBars - 1) * 4.dp.toPx()
        val startX = (size.width - totalWidth) / 2f

        val centerY = size.height / 2f
        val minBarHeight = 4.dp.toPx()
        val maxBarHeight = size.height * 0.9f

        for (i in 0 until totalBars) {
            val x = startX + i * (barWidth + 4.dp.toPx())
            val ratio = i.toFloat() / (totalBars - 1).coerceAtLeast(1)

            val animatedAmp = if (isListening) {
                // three overlapping sine waves for organic movement
                val w1 = kotlin.math.sin((ratio * Math.PI * 3) + phase1).toFloat()
                val w2 = kotlin.math.sin((ratio * Math.PI * 5) + phase2).toFloat() * 0.5f
                val w3 = kotlin.math.sin((ratio * Math.PI * 7) + phase3).toFloat() * 0.3f
                val combined = (w1 + w2 + w3) / 1.8f  // normalize
                // gentle centre-weighted envelope so the edges are slightly shorter
                val envelope = 0.6f + 0.4f * kotlin.math.sin((ratio * Math.PI).toFloat())
                val wave = ((combined + 1f) / 2f * envelope)
                // scale by live mic loudness: near-flat when silent, tall when speaking
                (wave * smoothLevel).coerceIn(0.06f, 1f)
            } else {
                // idle: small static bars
                val idle = 0.08f + 0.06f * kotlin.math.sin((ratio * Math.PI * 4).toFloat())
                idle
            }

            val height = minBarHeight + ((maxBarHeight - minBarHeight) * animatedAmp)
            val top = centerY - height / 2f

            drawRoundRect(
                color = if (isListening) activeColor else idleColor,
                topLeft = Offset(x, top),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(corner, corner)
            )
        }
    }
}
