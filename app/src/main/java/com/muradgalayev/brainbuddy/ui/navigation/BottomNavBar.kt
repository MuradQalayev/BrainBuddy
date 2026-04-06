package com.muradgalayev.brainbuddy.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.muradgalayev.brainbuddy.ui.theme.AiButtonDark
import com.muradgalayev.brainbuddy.ui.theme.AiButtonDarkEnd
import com.muradgalayev.brainbuddy.ui.theme.AiButtonLight
import com.muradgalayev.brainbuddy.ui.theme.AiButtonLightEnd
import androidx.compose.ui.res.painterResource
import com.muradgalayev.brainbuddy.R

@Composable
fun BottomNavBar(
    items: List<Screen>,
    overflowItems: List<Screen>,
    currentRoute: String?,
    onItemClick: (Screen) -> Unit,
    onAiClick: () -> Unit = {},
    isAiOpen: Boolean = false,
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
                            Color(0xFF282B34) // matches surfaceContainerHigh dark
                        else
                            Color.White // matches surface light

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

            // Main navbar + floating AI button
            val fabSize = 58.dp
            val notchShape = NavBarNotchShape(
                fabSize = fabSize,
                notchGap = 8.dp,
                cornerRadius = 0.dp
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                // The pill-shaped navbar with concave notch
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp) // room for FAB to poke above
                        .shadow(
                            elevation = 12.dp,
                            shape = notchShape,
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f),
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        ),
                    shape = notchShape,
                    color = if (isDark)
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
                    else
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    tonalElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side items (Home, Activity)
                        leftItems.forEach { screen ->
                            val isMore = screen is Screen.More
                            val selected = if (isMore) {
                                moreExpanded || overflowItems.any { it.route == currentRoute }
                            } else {
                                currentRoute == screen.route
                            }
                            NavBarItem(
                                screen = screen,
                                selected = selected,
                                isDark = isDark,
                                onClick = {
                                    if (isMore) {
                                        moreExpanded = !moreExpanded
                                    } else {
                                        moreExpanded = false
                                        onItemClick(screen)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Center gap for the floating AI button
                        Spacer(modifier = Modifier.weight(1f))

                        // Right side items (Calendar, Settings/More)
                        rightItems.forEach { screen ->
                            val isMore = screen is Screen.More
                            val selected = if (isMore) {
                                moreExpanded || overflowItems.any { it.route == currentRoute }
                            } else {
                                currentRoute == screen.route
                            }
                            NavBarItem(
                                screen = screen,
                                selected = selected,
                                isDark = isDark,
                                onClick = {
                                    if (isMore) {
                                        moreExpanded = !moreExpanded
                                    } else {
                                        moreExpanded = false
                                        onItemClick(screen)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                val aiGradient = if (isDark) {
                    Brush.linearGradient(listOf(AiButtonDark, AiButtonDarkEnd))
                } else {
                    Brush.linearGradient(listOf(AiButtonLight, AiButtonLightEnd))
                }

                val aiRotation by animateFloatAsState(
                    targetValue = if (isAiOpen) 180f else 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "aiRotation"
                )
                val aiScale by animateFloatAsState(
                    targetValue = if (isAiOpen) 1.12f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "aiScale"
                )

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = 8.dp) // sink FAB into the notch
                        .zIndex(3f)
                        .size(fabSize)
                        .scale(aiScale)
                        .shadow(
                            elevation = 16.dp,
                            shape = RoundedCornerShape(18.dp),
                            ambientColor = if (isDark) AiButtonDark.copy(alpha = 0.3f)
                            else AiButtonLight.copy(alpha = 0.25f),
                            spotColor = if (isDark) AiButtonDarkEnd.copy(alpha = 0.4f)
                            else AiButtonLightEnd.copy(alpha = 0.35f)
                        )
                        .clip(CircleShape)
                        .background(brush = aiGradient)
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
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .rotate(aiRotation)
                    )
                }
            }
        }
    }
}

@Composable
private fun NavBarItem(
    screen: Screen,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    val pillColor by animateColorAsState(
        targetValue = if (selected) {
            if (isDark) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
        } else Color.Transparent,
        animationSpec = tween(250),
        label = "pill"
    )

    val iconColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(250),
        label = "iconColor"
    )

    val textColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(250),
        label = "textColor"
    )

    val pillWidth by animateDpAsState(
        targetValue = if (selected) 56.dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "pillWidth"
    )

    val iconOffset by animateDpAsState(
        targetValue = if (selected) (-2).dp else 0.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "iconOffset"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.scale(scale)
        ) {
            Box(
                modifier = Modifier.size(width = 56.dp, height = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(pillWidth)
                        .height(32.dp)
                        .clip(CircleShape)
                        .background(pillColor)
                )
                Icon(
//                    imageVector = screen.icon,
                    painter = painterResource(id = screen.icon),
                    contentDescription = screen.label,
                    tint = iconColor,
                    modifier = Modifier
                        .size(22.dp)
                        .offset(y = iconOffset)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = screen.label,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

/**
 * Rounded-rect shape with a smooth concave notch at the top center for the FAB.
 */
private class NavBarNotchShape(
    private val fabSize: Dp,
    private val notchGap: Dp,
    private val cornerRadius: Dp
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val fabR = with(density) { fabSize.toPx() } / 2f
        val gap = with(density) { notchGap.toPx() }
        val cr = with(density) { cornerRadius.toPx() }

        val notchR = fabR + gap + with(density) { 2.dp.toPx() }
        val depth = notchR * 0.9f
        val spread = notchR * 0.6f

        val cx = size.width / 2f

        val path = Path().apply {
            // ── Top-left corner ──
            moveTo(0f, cr)
            arcTo(Rect(0f, 0f, cr * 2, cr * 2), 180f, 90f, false)

            // ── Top edge → left side of notch ──
            lineTo(cx - notchR - spread, 0f)

            // ── Smooth curve into notch (left) ──
            cubicTo(
                x1 = cx - notchR,  y1 = 0f,
                x2 = cx - fabR,    y2 = depth,
                x3 = cx,           y3 = depth
            )
            // ── Smooth curve out of notch (right) ──
            cubicTo(
                x1 = cx + fabR,    y1 = depth,
                x2 = cx + notchR,  y2 = 0f,
                x3 = cx + notchR + spread, y3 = 0f
            )

            // ── Top edge → top-right corner ──
            lineTo(size.width - cr, 0f)
            arcTo(Rect(size.width - cr * 2, 0f, size.width, cr * 2), 270f, 90f, false)

            // ── Right edge ──
            lineTo(size.width, size.height - cr)
            arcTo(
                Rect(size.width - cr * 2, size.height - cr * 2, size.width, size.height),
                0f, 90f, false
            )

            // ── Bottom edge ──
            lineTo(cr, size.height)
            arcTo(Rect(0f, size.height - cr * 2, cr * 2, size.height), 90f, 90f, false)

            close()
        }

        return Outline.Generic(path)
    }
}
