package com.muradgalayev.brainbuddy.ui.sharedcomponents

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp


@Composable
fun ColorOption(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) color else Color.Transparent,
        animationSpec = tween(250),
        label = "colorBorder"
    )

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(
                width = 2.5.dp,
                color = borderColor,
                shape = CircleShape
            )
            .padding(4.dp)
            .clip(CircleShape)
            .background(color)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = 0.5f,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(tween(150)),
            exit = scaleOut(tween(100)) + fadeOut(tween(100))
        ) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}