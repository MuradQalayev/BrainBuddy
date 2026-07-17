package com.muradgalayev.brainbuddy.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import com.muradgalayev.brainbuddy.R

// iOS-style accents for the redesigned bar (calm UI)
private val NavBarBg = Color(0xFF2A2A2A)        // soft black pill (not pure)
private val NavOrange = Color(0xFFD97A3D)       // accent orange
private val NavInactive = Color(0xFFEDE7DF)     // warm off-white for inactive icons
private val AiRing = Color(0xFF1F1F1F)          // soft dark ring around AI button

@Composable
fun BottomNavBar(
    items: List<Screen>,
    overflowItems: List<Screen>,
    currentRoute: String?,
    onItemClick: (Screen) -> Unit,
    onAiClick: () -> Unit = {},
    isAiOpen: Boolean = false,
    hasActiveAiChat: Boolean = false,
    modifier: Modifier = Modifier
) {
    var moreExpanded by remember { mutableStateOf(false) }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val leftItems = items.take(2)
    val rightItems = items.drop(2)

    Box(modifier = modifier.fillMaxWidth()) {
        // Scrim behind overflow panel
        if (moreExpanded) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { moreExpanded = false }
                    )
                    .zIndex(1f)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(2f)
        ) {
            // Overflow panel — slides up from the bar
            AnimatedVisibility(
                visible = moreExpanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    expandFrom = Alignment.Bottom
                ) + fadeIn(tween(200)),
                exit = shrinkVertically(
                    animationSpec = tween(180),
                    shrinkTowards = Alignment.Bottom
                ) + fadeOut(tween(120))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 16.dp,
                                shape = RoundedCornerShape(20.dp),
                                ambientColor = Color.Black.copy(alpha = 0.06f),
                                spotColor = Color.Black.copy(alpha = 0.10f)
                            ),
                        shape = RoundedCornerShape(20.dp),
                        color = if (isDark)
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        else
                            MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            overflowItems.forEachIndexed { index, screen ->
                                val isActive = currentRoute == screen.route
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            if (isActive)
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            moreExpanded = false
                                            onItemClick(screen)
                                        }
                                        .padding(horizontal = 16.dp, vertical = 13.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isActive)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else
                                            MaterialTheme.colorScheme.surfaceContainer,
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                painter = painterResource(id = screen.icon),
                                                contentDescription = screen.label,
                                                tint = if (isActive)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Text(
                                        text = screen.label,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isActive)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                // Subtle divider between items
                                if (index < overflowItems.size - 1) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp)
                                            .height(0.5.dp)
                                            .background(
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)
                                            )
                                    )
                                }
                            }
                        }
                    }

                    // Arrow pointing down toward the More/Settings button (right side)
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 40.dp)
                            .width(16.dp)
                            .height(8.dp)
                    ) {
                        val arrowColor = if (isDark)
                            Color(0xFF3F3F3F) // matches surfaceContainerHigh dark
                        else
                            Color(0xFFFFFBF6) // matches surface light

                        val path = androidx.compose.ui.graphics.Path().apply {
                            moveTo(0f, 0f)
                            lineTo(size.width / 2f, size.height)
                            lineTo(size.width, 0f)
                            close()
                        }
                        drawPath(path, arrowColor)
                    }
                }
            }

            // Pill-shaped near-black bar with the AI button inline as the middle item
            val pillShape = RoundedCornerShape(percent = 50)

            val aiRotation by animateFloatAsState(
                targetValue = if (isAiOpen) 180f else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "aiRotation"
            )
            val aiScale by animateFloatAsState(
                targetValue = if (isAiOpen) 1.08f else 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                label = "aiScale"
            )

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .shadow(
                        elevation = 18.dp,
                        shape = pillShape,
                        ambientColor = Color.Black.copy(alpha = 0.18f),
                        spotColor = Color.Black.copy(alpha = 0.32f)
                    ),
                shape = pillShape,
                color = NavBarBg,
                tonalElevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val handleClick: (Screen) -> Unit = { screen ->
                        if (screen is Screen.More) {
                            moreExpanded = !moreExpanded
                        } else {
                            moreExpanded = false
                            onItemClick(screen)
                        }
                    }
                    val moreSelected: (Screen) -> Boolean = { screen ->
                        screen is Screen.More &&
                            (moreExpanded || overflowItems.any { it.route == currentRoute })
                    }

                    InteractiveMenuRow(
                        items = leftItems,
                        currentRoute = currentRoute,
                        isMoreSelected = moreSelected,
                        onItemClick = handleClick,
                        accentColor = NavOrange,
                        inactiveColor = NavInactive,
                        modifier = Modifier.weight(2f)
                    )

                    // Inline AI button — same level as the other items
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .scale(aiScale)
                                .clip(CircleShape)
                                .background(Color.White)
                                .border(width = 2.dp, color = AiRing, shape = CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = onAiClick
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ai),
                                contentDescription = "AI Assistant",
                                tint = NavOrange,
                                modifier = Modifier
                                    .size(26.dp)
                                    .rotate(aiRotation)
                            )
                        }
                        // Active-chat badge — a small "1" pinned to the top-right
                        // of the AI circle. Only visible when the user has an
                        // ongoing conversation with messages.
                        if (hasActiveAiChat) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = (-2).dp, y = 2.dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(NavOrange)
                                    .border(1.5.dp, Color.White, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "1",
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    InteractiveMenuRow(
                        items = rightItems,
                        currentRoute = currentRoute,
                        isMoreSelected = moreSelected,
                        onItemClick = handleClick,
                        accentColor = NavOrange,
                        inactiveColor = NavInactive,
                        modifier = Modifier.weight(2f)
                    )
                }
            }
        }
    }
}

