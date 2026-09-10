package com.muradgalayev.brainbuddy.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.coroutines.launch
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.border
import androidx.compose.ui.res.painterResource
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.ui.theme.LocalAppTheme
import com.muradgalayev.brainbuddy.ui.accessibility.speaking
import com.muradgalayev.brainbuddy.ui.accessibility.speakingWith

// the bar is dark in both light and dark mode by design, it reads as a floating pill rather
// than a slab of chrome. its neutrals come from the active theme's dark palette so it stays
// in the same colour family whichever theme is on, and the accent is the dark-mode variant,
// the one tuned to sit on a near-black ground
private val NavInactive = Color(0xFFA8A29E) // warm grey, recedes behind the active tab
// locked AI button (survey not finished), colourless
private val AiDisabledBg = Color(0xFF292524) // muted fill, blends into the bar
private val AiDisabledBorder = Color(0xFF35302E)
private val AiDisabledIcon = Color(0xFF78716C)  // dim icon

// travel spec for the sliding highlight. just under critically damped, so it settles with a
// single small overshoot instead of ringing, and stiff enough to keep up with a dragging finger
private val IndicatorSpring = spring<Float>(dampingRatio = 0.82f, stiffness = 420f)

@Composable
fun BottomNavBar(
    items: List<Screen>,
    overflowItems: List<Screen>,
    currentRoute: String?,
    onItemClick: (Screen) -> Unit,
    onAiClick: () -> Unit = {},
    isAiOpen: Boolean = false,
    hasActiveAiChat: Boolean = false,
    aiEnabled: Boolean = true,
    onDragStateChanged: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // the connection is deliberately not reflected here. the button used to go grey and open a
    // different panel offline, so its appearance and its behaviour both changed under the user
    // on a flaky network. it always opens the assistant now, and the assistant offers the switch
    val speakingAi = speaking(
        if (aiEnabled) "AI assistant" else "AI assistant, locked until the survey is finished",
        onAiClick,
    )
    var moreExpanded by remember { mutableStateOf(false) }
    var dragIndex by remember { mutableStateOf<Int?>(null) }
    var barWidthPx by remember { mutableStateOf(1) }
    // geometry for the sliding highlight. slot bounds and the bar origin are both in root
    // coordinates, and the difference is what the highlight translates by
    val slotBounds = remember { mutableStateMapOf<Int, Rect>() }
    var barOrigin by remember { mutableStateOf(Offset.Zero) }
    var indicatorSlot by remember { mutableStateOf<Rect?>(null) }
    var indicatorPlaced by remember { mutableStateOf(false) }
    val indicatorX = remember { Animatable(0f) }
    val indicatorY = remember { Animatable(0f) }
    val density = LocalDensity.current
    // onGloballyPositioned fires every layout pass, writing only real changes keeps it out of the frame loop
    val reportSlotBounds = remember<(Int, Rect) -> Unit> {
        { index, rect -> if (slotBounds[index] != rect) slotBounds[index] = rect }
    }
    val haptics = LocalHapticFeedback.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // the pill is always dark, so it reads the theme's dark palette whatever mode the app is in
    val pillPalette = LocalAppTheme.current.palette(dark = true)
    val navBarBg = pillPalette.surface
    val navAccent = pillPalette.accent
    val aiRing = pillPalette.background
    val leftItems = items.take(2)
    val rightItems = items.drop(2)

    Box(modifier = modifier.fillMaxWidth()) {
        // scrim behind the overflow panel
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
            // overflow panel, slides up from the bar
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
                                // subtle divider between items
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

                    // arrow pointing down toward the More/Settings button
                    Canvas(
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(end = 40.dp)
                            .width(16.dp)
                            .height(8.dp)
                    ) {
                        val arrowColor = if (isDark)
                            Color(0xFF292524) // matches surfaceContainerHigh dark
                        else
                            Color(0xFFFFFFFF) // matches surface light

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

            // pill-shaped near-black bar with the AI button inline as the middle item
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
            val routeIndex = when {
                isAiOpen -> 2
                currentRoute == leftItems.getOrNull(0)?.route -> 0
                currentRoute == leftItems.getOrNull(1)?.route -> 1
                currentRoute == rightItems.getOrNull(0)?.route -> 3
                currentRoute == rightItems.getOrNull(1)?.route ||
                    overflowItems.any { it.route == currentRoute } -> 4
                else -> 0
            }
            val visualIndex = dragIndex ?: when {
                moreExpanded -> 4
                else -> routeIndex
            }
            val visualRoute = when (visualIndex) {
                0 -> leftItems.getOrNull(0)?.route
                1 -> leftItems.getOrNull(1)?.route
                3 -> rightItems.getOrNull(0)?.route
                4 -> rightItems.getOrNull(1)?.route
                else -> "__ai_preview__"
            }
            val selectIndex: (Int) -> Unit = { index ->
                when (index) {
                    0 -> leftItems.getOrNull(0)?.let(onItemClick)
                    1 -> leftItems.getOrNull(1)?.let(onItemClick)
                    // AI is tap-only, hold-and-drag skips the centre slot
                    2 -> Unit
                    3 -> rightItems.getOrNull(0)?.let(onItemClick)
                    4 -> rightItems.getOrNull(1)?.let { screen ->
                        if (screen is Screen.More) moreExpanded = !moreExpanded else onItemClick(screen)
                    }
                }
            }

            // sliding selection highlight. slots report their own bounds in root coordinates so it tracks
            // the real layout instead of re-deriving it from weights, and the bar's own origin turns
            // them into offsets we can translate by
            val targetSlot = slotBounds[visualIndex]
            LaunchedEffect(targetSlot, barOrigin) {
                val slot = targetSlot ?: return@LaunchedEffect
                indicatorSlot = slot
                val x = slot.left - barOrigin.x
                val y = slot.top - barOrigin.y
                if (!indicatorPlaced) {
                    // first layout: appear in place rather than flying in from the corner
                    indicatorX.snapTo(x)
                    indicatorY.snapTo(y)
                    indicatorPlaced = true
                } else {
                    launch { indicatorX.animateTo(x, IndicatorSpring) }
                    launch { indicatorY.animateTo(y, IndicatorSpring) }
                }
            }
            // the AI slot has no highlight of its own, so the box fades out in place there and fades
            // back in, from where it was left, on the way to a real tab
            val indicatorAlpha by animateFloatAsState(
                targetValue = if (indicatorPlaced && targetSlot != null) 1f else 0f,
                animationSpec = tween(160),
                label = "navIndicatorAlpha",
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
                color = navBarBg,
                tonalElevation = 0.dp
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { barWidthPx = it.width.coerceAtLeast(1) }
                        .onGloballyPositioned { barOrigin = it.positionInRoot() }
                        .pointerInput(items, aiEnabled) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    onDragStateChanged(true)
                                    val index = (offset.x / (barWidthPx / 5f)).toInt().coerceIn(0, 4)
                                    dragIndex = if (index == 2) routeIndex else index
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    if (index != 2 && index != routeIndex) selectIndex(index)
                                },
                                onDragCancel = {
                                    dragIndex = null
                                    onDragStateChanged(false)
                                },
                                onDragEnd = {
                                    dragIndex = null
                                    onDragStateChanged(false)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val index = (change.position.x / (barWidthPx / 5f)).toInt().coerceIn(0, 4)
                                    if (index != 2 && index != dragIndex) {
                                        dragIndex = index
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectIndex(index)
                                    }
                                },
                            )
                        }
                ) {
                    // one highlight for the whole bar, drawn first so it passes behind the AI button when
                    // travelling between the two halves. it slides to whichever slot is selected rather than
                    // cross-fading, so a hold-and-drag keeps it continuously on screen
                    indicatorSlot?.let { slot ->
                        NavSelectionIndicator(
                            accentColor = navAccent,
                            modifier = Modifier
                                .size(
                                    width = with(density) { slot.width.toDp() },
                                    height = with(density) { slot.height.toDp() },
                                )
                                .graphicsLayer {
                                    translationX = indicatorX.value
                                    translationY = indicatorY.value
                                    alpha = indicatorAlpha
                                },
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                    val handleClick: (Screen) -> Unit = speakingWith({ it.label }) { screen ->
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
                        currentRoute = visualRoute,
                        isMoreSelected = moreSelected,
                        onItemClick = handleClick,
                        accentColor = navAccent,
                        inactiveColor = NavInactive,
                        slotIndexOffset = 0,
                        onSlotBounds = reportSlotBounds,
                        modifier = Modifier.weight(2f)
                    )

                    // inline AI button, same level as the other items
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                      // wraps the circle and nothing else, so the badge below can hang off the button. pinned to
                      // the weighted slot instead it drifted with the slot's width, which is a fifth of the bar
                      Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .then(
                                    if (aiEnabled) Modifier.scale(aiScale) else Modifier,
                                )
                                .clip(CircleShape)
                                .background(
                                    if (aiEnabled) Color.White else AiDisabledBg
                                )
                                .border(
                                    width = 2.dp,
                                    color = if (aiEnabled) aiRing else AiDisabledBorder,
                                    shape = CircleShape,
                                )
                                .then(
                                    if (aiEnabled) {
                                        Modifier.clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            onClick = speakingAi,
                                        )
                                    } else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_ai),
                                contentDescription = if (aiEnabled) "AI Assistant"
                                else "AI Assistant (finish the survey to unlock)",
                                tint = if (aiEnabled) navAccent else AiDisabledIcon,
                                modifier = Modifier
                                    .size(26.dp)
                                    .rotate(if (aiEnabled) aiRotation else 0f)
                            )
                        }
                        // active-chat badge, a small 1 on the top-right of the AI circle. only shown when there's an
                        // ongoing conversation with messages in it
                        if (hasActiveAiChat && aiEnabled) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .offset(x = 2.dp, y = (-2).dp)
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(navAccent)
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
                    }

                    InteractiveMenuRow(
                        items = rightItems,
                        currentRoute = visualRoute,
                        isMoreSelected = moreSelected,
                        onItemClick = handleClick,
                        accentColor = navAccent,
                        inactiveColor = NavInactive,
                        // slot 2 is the AI button, so the right-hand row starts at 3, the same indices the drag
                        // gesture and routeIndex already speak in
                        slotIndexOffset = 3,
                        onSlotBounds = reportSlotBounds,
                        modifier = Modifier.weight(2f)
                    )
                    }
                }
            }
        }
    }
}
