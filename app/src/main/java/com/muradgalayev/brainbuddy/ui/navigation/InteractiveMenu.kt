package com.muradgalayev.brainbuddy.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

/**
 * Compose port of the "modern-mobile-menu" InteractiveMenu.
 *
 * Active item: icon + label side-by-side, with an underline that grows to match the
 * label's measured width. Inactive items: just the icon. When an item becomes active,
 * its icon plays a short bounce.
 */
@Composable
fun InteractiveMenuRow(
    items: List<Screen>,
    currentRoute: String?,
    isMoreSelected: (Screen) -> Boolean,
    onItemClick: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEach { screen ->
            val active = isMoreSelected(screen) || currentRoute == screen.route
            InteractiveMenuItem(
                screen = screen,
                active = active,
                accentColor = accentColor,
                inactiveColor = inactiveColor,
                onClick = { onItemClick(screen) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun InteractiveMenuItem(
    screen: Screen,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    val iconBounce = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            iconBounce.snapTo(0f)
            iconBounce.animateTo(
                targetValue = 0f,
                animationSpec = keyframes {
                    durationMillis = 700
                    0f at 0
                    -8f at 140
                    0f at 280
                    -3f at 420
                    0f at 560
                }
            )
        } else {
            iconBounce.snapTo(0f)
        }
    }

    val iconColor by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(220),
        label = "iconColor"
    )
    val resolvedIconColor = lerpColor(inactiveColor, accentColor, iconColor)

    val pillBg by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(240),
        label = "pillBg"
    )

    val glossBrush = Brush.verticalGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.38f * pillBg),
            accentColor.copy(alpha = 0.20f * pillBg),
            accentColor.copy(alpha = 0.10f * pillBg)
        )
    )
    val sheenBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.28f * pillBg),
            Color.White.copy(alpha = 0.0f)
        )
    )

    val selectionShape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .clip(selectionShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(selectionShape)
                .background(glossBrush)
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.18f * pillBg),
                    shape = selectionShape
                ),
            contentAlignment = Alignment.Center
        ) {
            // Top sheen — a thin highlight strip that gives the glossy look.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(18.dp)
                    .background(sheenBrush)
            )
            Icon(
                painter = painterResource(id = screen.icon),
                contentDescription = screen.label,
                tint = resolvedIconColor,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer { translationY = iconBounce.value }
            )
        }
    }
}

private fun lerpColor(start: Color, end: Color, fraction: Float): Color {
    val f = fraction.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * f,
        green = start.green + (end.green - start.green) * f,
        blue = start.blue + (end.blue - start.blue) * f,
        alpha = start.alpha + (end.alpha - start.alpha) * f
    )
}