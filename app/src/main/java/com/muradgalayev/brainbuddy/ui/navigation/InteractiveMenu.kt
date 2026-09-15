package com.muradgalayev.brainbuddy.ui.navigation

import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

// Compose port of the modern-mobile-menu InteractiveMenu. the selected-item highlight is
// deliberately not drawn here: a per-item pill meant the outgoing one faded out while the
// incoming one faded in, so during a hold-and-drag the highlight vanished between slots. the
// bar draws a single shared highlight that slides between slots instead, and each item only
// reports where its slot is and renders its icon
@Composable
fun InteractiveMenuRow(
    items: List<Screen>,
    currentRoute: String?,
    isMoreSelected: (Screen) -> Boolean,
    onItemClick: (Screen) -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    inactiveColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    slotIndexOffset: Int = 0,
    onSlotBounds: (Int, LayoutCoordinates) -> Unit = { _, _ -> },
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items.forEachIndexed { index, screen ->
            val active = isMoreSelected(screen) || currentRoute == screen.route
            InteractiveMenuItem(
                screen = screen,
                active = active,
                accentColor = accentColor,
                inactiveColor = inactiveColor,
                onClick = { onItemClick(screen) },
                onSlotBounds = { onSlotBounds(slotIndexOffset + index, it) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

// corner radius shared by the item slots and the sliding highlight
val NavSelectionShape = RoundedCornerShape(14.dp)

// the single highlight that travels between nav slots. drawn by the bar itself, behind the item
// row, so it can cross the whole bar including behind the centre AI button, in one continuous
// move rather than blinking out and back in
@Composable
fun NavSelectionIndicator(
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val glossBrush = Brush.verticalGradient(
        colors = listOf(
            accentColor.copy(alpha = 0.38f),
            accentColor.copy(alpha = 0.20f),
            accentColor.copy(alpha = 0.10f)
        )
    )
    val sheenBrush = Brush.verticalGradient(
        colors = listOf(
            Color.White.copy(alpha = 0.28f),
            Color.White.copy(alpha = 0.0f)
        )
    )
    Box(
        modifier = modifier
            .clip(NavSelectionShape)
            .background(glossBrush)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.18f),
                shape = NavSelectionShape
            )
    ) {
        // top sheen, a thin highlight strip that gives the glossy look
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(18.dp)
                .background(sheenBrush)
        )
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
    onSlotBounds: (LayoutCoordinates) -> Unit = {},
) {
    val iconBounce = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            iconBounce.snapTo(-3f)
            iconBounce.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
            )
        } else {
            iconBounce.snapTo(0f)
        }
    }

    val iconColor by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = spring(
            dampingRatio = .88f,
            stiffness = Spring.StiffnessLow,
        ),
        label = "iconColor"
    )
    val resolvedIconColor = lerpColor(inactiveColor, accentColor, iconColor)

    val selectedScale by animateFloatAsState(
        targetValue = if (active) 1f else .94f,
        animationSpec = spring(
            dampingRatio = .92f,
            stiffness = 170f,
        ),
        label = "selectedItemScale",
    )

    Box(
        modifier = modifier
            .clip(NavSelectionShape)
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
                // reported before the graphicsLayer, so the scale animation never feeds back into the slot
                // geometry the sliding highlight is aiming at. the bar turns these into its own coordinates
                .onGloballyPositioned { coords -> onSlotBounds(coords) }
                .graphicsLayer {
                    scaleX = selectedScale
                    scaleY = selectedScale
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = screen.icon),
                contentDescription = stringResource(screen.labelRes),
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
