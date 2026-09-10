package com.muradgalayev.brainbuddy.ui.todo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp


@Composable
fun AvatarPlaceholder(palette: TodoPalette, size: Dp = 56.dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(palette.avatarBg, palette.avatarBg.copy(alpha = 0.7f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size * 0.72f)) {
            drawCircle(color = palette.avatarFace, radius = this.size.minDimension * 0.42f)
            drawCircle(
                color = palette.avatarEyes,
                radius = this.size.minDimension * 0.20f,
                center = Offset(this.size.width * 0.38f, this.size.height * 0.52f)
            )
            drawCircle(
                color = palette.avatarEyes,
                radius = this.size.minDimension * 0.20f,
                center = Offset(this.size.width * 0.62f, this.size.height * 0.52f)
            )
            val pupilColor = if (palette.bg.luminance() < 0.5f) Color(0xFFFAFAF9) else Color(0xFF1C1917)
            drawCircle(
                color = pupilColor,
                radius = this.size.minDimension * 0.06f,
                center = Offset(this.size.width * 0.38f, this.size.height * 0.54f)
            )
            drawCircle(
                color = pupilColor,
                radius = this.size.minDimension * 0.06f,
                center = Offset(this.size.width * 0.62f, this.size.height * 0.54f)
            )
            drawLine(
                color = Color(0xFFFDBA74),
                start = Offset(this.size.width * 0.32f, this.size.height * 0.18f),
                end = Offset(this.size.width * 0.18f, this.size.height * 0.04f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color(0xFFFDBA74),
                start = Offset(this.size.width * 0.68f, this.size.height * 0.18f),
                end = Offset(this.size.width * 0.82f, this.size.height * 0.04f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = Color(0xFFFDBA74),
                radius = this.size.minDimension * 0.045f,
                center = Offset(this.size.width * 0.17f, this.size.height * 0.03f)
            )
            drawCircle(
                color = Color(0xFFFDBA74),
                radius = this.size.minDimension * 0.045f,
                center = Offset(this.size.width * 0.83f, this.size.height * 0.03f)
            )
        }
    }
}