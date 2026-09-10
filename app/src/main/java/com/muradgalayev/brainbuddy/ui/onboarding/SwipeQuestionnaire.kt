package com.muradgalayev.brainbuddy.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.R
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.sin

@Composable
fun SwipeQuestionnaire(
    title: String,
    pageCount: Int,
    completed: List<Boolean>,
    onExit: () -> Unit,
    isSubmitting: Boolean,
    submitLabel: String,
    onSubmit: () -> Unit,
    onSaveExit: () -> Unit,
    // 'I'll do this later'. The screen saves the draft before leaving. Null while editing an
    // already-completed profile, where skipping means nothing.
    onSkip: (() -> Unit)? = null,
    pageContent: @Composable (Int) -> Unit,
) {
    var confirmSkip by remember { mutableStateOf(false) }
    val pager = rememberPagerState(initialPage = completed.indexOfFirst { !it }.coerceAtLeast(0)) { pageCount }
    val scope = rememberCoroutineScope()
    val growth by animateFloatAsState(
        targetValue = completed.count { it }.toFloat() / pageCount,
        animationSpec = spring(dampingRatio = .68f, stiffness = 90f),
        label = "treeGrowth",
    )
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp)) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Question ${pager.currentPage + 1} of $pageCount", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // deliberately never disabled by isSubmitting: if a save is hanging on a dead network, that's
            // exactly the moment someone needs to leave
            if (onSkip != null) {
                TextButton(
                    onClick = { confirmSkip = true },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Text(
                        "Skip",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            TextButton(
                onClick = onSaveExit,
                enabled = !isSubmitting,
                modifier = Modifier.align(Alignment.CenterEnd),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            }
        }
        QuestionRail(
            pageCount = pageCount,
            currentPage = pager.currentPage,
            completed = completed,
            onSeek = { page ->
                scope.launch {
                    // snapped, not animated: sliding across twenty pages is a long blur that arrives late, and the
                    // whole point of the rail is to be there already. neighbouring pages keep the animation,
                    // that's the Next button's job and there the movement is what shows the direction
                    if ((page - pager.currentPage).absoluteValue > 2) pager.scrollToPage(page)
                    else pager.animateScrollToPage(page)
                }
            },
        )
        HorizontalPager(state = pager, modifier = Modifier.weight(1f), beyondViewportPageCount = 1) { page ->
            val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
            Surface(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp).graphicsLayer {
                    alpha = 1f - offset * .35f
                    scaleX = 1f - offset * .06f
                    scaleY = 1f - offset * .06f
                    rotationY = (pager.currentPage - page + pager.currentPageOffsetFraction) * 5f
                    translationX = (pager.currentPage - page + pager.currentPageOffsetFraction) * 18.dp.toPx()
                    cameraDistance = 16f * density
                },
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
                shadowElevation = 3.dp,
            ) { pageContent(page) }
        }
        TreeGrowthCard(
            progress = growth,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 2.dp),
        )
        AnimatedVisibility(
            visible = !isSubmitting,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        if (pager.currentPage == 0) onExit()
                        else scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
                    },
                    modifier = Modifier.weight(.42f).height(50.dp),
                ) { Text("Back") }
                Button(
                    onClick = { if (pager.currentPage == pageCount - 1) onSubmit() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier.weight(1f).height(50.dp),
                ) {
                    Text(if (pager.currentPage == pageCount - 1) submitLabel else "Next", fontWeight = FontWeight.Bold)
                    if (pager.currentPage < pageCount - 1) { Spacer(Modifier.size(8.dp)); Icon(Icons.AutoMirrored.Rounded.ArrowForward, null) }
                }
            }
        }
    }

    // Confirm before leaving. The caller persists the answers first, so the next visit resumes
    // at the first unanswered page instead of making the user repeat their work.
    if (confirmSkip && onSkip != null) {
        AlertDialog(
            onDismissRequest = { confirmSkip = false },
            shape = RoundedCornerShape(26.dp),
            icon = {
                Icon(
                    Icons.Rounded.Schedule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text("Finish this later?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Your answers will be saved, and you can pick the survey back up any time from Settings. " +
                        "Myndora works without it — the assistant just won't be " +
                        "personalised to you yet."
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmSkip = false; onSkip() }) {
                    Text("Skip for now", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSkip = false }) {
                    Text("Keep going", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
        )
    }
}

// the progress bar, made navigable. a survey this long is not read once front to back: people
// go back to change an answer, skip ahead to see how much is left, and want the one question
// they know they left blank, and the only way to do any of that used to be swiping through
// everything in between. twenty-six swipes to reach the last question ends a survey.
// so the bar became the control. touch anywhere and drag: the card behind follows your finger,
// the header counts along with it, and the far right end is the last question. precision isn't
// needed because the result is visible while you move, which is what makes a drag the right
// gesture here, at twenty-seven pages each tick is a third of a fingertip wide.
// the ticks double as the answer sheet, solid where a question is answered and hollow where it
// isn't. that's the other reason people wanted to move around, and it costs nothing since
// completed was already being passed in for the tree
@Composable
private fun QuestionRail(
    pageCount: Int,
    currentPage: Int,
    completed: List<Boolean>,
    onSeek: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current

    // pointerInput keeps the handlers it was built with for the life of the node, so the lambdas
    // captured there would otherwise go on seeking against a stale page count
    val seek by rememberUpdatedState(onSeek)
    val pages by rememberUpdatedState(pageCount)
    val page by rememberUpdatedState(currentPage)

    var trackWidth by remember { mutableStateOf(1f) }

    fun report(x: Float) {
        val index = ((x / trackWidth) * pages).toInt().coerceIn(0, pages - 1)
        if (index != page) {
            // a tick per question passed, the same feedback a physical scrubber gives, and the thing that
            // makes a drag feel aimed rather than approximate
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            seek(index)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            // generous, because the ticks themselves are far below a touch target: the whole strip is the
            // control, not the 4dp of paint in the middle of it
            .height(28.dp)
            .onSizeChanged { trackWidth = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { report(it.x) }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    report(change.position.x)
                }
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(pageCount) { index ->
                val answered = completed.getOrElse(index) { false }
                val isCurrent = index == currentPage

                val height by animateDpAsState(
                    targetValue = if (isCurrent) 12.dp else 5.dp,
                    animationSpec = spring(dampingRatio = .7f, stiffness = 700f),
                    label = "railTick",
                )
                val color by animateColorAsState(
                    targetValue = when {
                        isCurrent -> colors.primary
                        answered -> colors.primary.copy(alpha = .45f)
                        else -> colors.outlineVariant
                    },
                    animationSpec = tween(220),
                    label = "railTickColor",
                )

                Box(
                    Modifier
                        .weight(1f)
                        .height(height)
                        .clip(RoundedCornerShape(3.dp))
                        .background(color)
                )
            }
        }
    }
}

@Composable
fun TreeGrowthCard(
    progress: Float,
    modifier: Modifier = Modifier,
    invitation: Boolean = false,
    completed: Boolean = false,
    onClick: (() -> Unit)? = null,
    // draws the contents without the card around them, for callers that supply their own chrome.
    // the home screen puts every tile in one shared surface, and two nested cards read as a mistake
    bare: Boolean = false,
) {
    val completedBackdrop = if (completed) {
        Modifier
            .clip(RoundedCornerShape(36.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .42f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0f),
                    ),
                ),
            )
    } else Modifier
    val interactiveModifier = (modifier.then(completedBackdrop)).let {
        if (onClick != null) it.clickable(onClick = onClick) else it
    }
    Surface(
        modifier = interactiveModifier,
        shape = RoundedCornerShape(if (completed) 30.dp else 24.dp),
        color = when {
            bare || completed -> Color.Transparent
            else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .24f)
        },
        border = null,
        shadowElevation = 0.dp,
    ) {
        if (completed) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GrowingTree(progress, Modifier.fillMaxWidth().height(190.dp))
                Spacer(Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = .14f),
                ) {
                    Text(
                        "✓  PROFILE COMPLETE",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Your tree is thriving",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    "Myndora knows you better now and can shape support around the way you focus, rest, and grow.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(112.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(.78f)) {
                    Text("Your focus tree", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        when {
                        invitation && progress < 1f -> "I’d love to know you better · ${(progress * 100).toInt()}%"
                        progress == 0f -> "Answer a question to bring it to life"
                        progress < .5f -> "Roots are growing · ${(progress * 100).toInt()}%"
                        progress < 1f -> "Your canopy is blooming · ${(progress * 100).toInt()}%"
                        else -> "Fully grown — beautiful work!"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (invitation && progress < 1f) {
                        Spacer(Modifier.height(8.dp))
                        Text("Continue profile  →", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
                GrowingTree(progress, Modifier.weight(1f).fillMaxSize())
            }
        }
    }
}

// the finished artwork stays visible in monochrome while animated colour rises like liquid
@Composable
private fun GrowingTree(progress: Float, modifier: Modifier = Modifier) {
    val grayscale = remember {
        ColorMatrix().apply { setToSaturation(0f) }
    }
    val waves = rememberInfiniteTransition(label = "treeLiquid")
    val phase by waves.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Restart),
        label = "treeLiquidWave",
    )
    Box(modifier, contentAlignment = Alignment.Center) {
        Image(
            painter = painterResource(R.drawable.onboarding_tree_full),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.colorMatrix(grayscale),
            alpha = .28f,
            modifier = Modifier.fillMaxSize(),
        )
        Image(
            painter = painterResource(R.drawable.onboarding_tree_full),
            contentDescription = "Your tree is ${kotlin.math.round(progress * 100).toInt()} percent colored",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize().drawWithContent {
                val waterLine = size.height * (1f - progress)
                val amplitude = if (progress in .01f..0.99f) 5.dp.toPx() else 0f
                val liquid = Path().apply {
                    moveTo(0f, waterLine)
                    val samples = 32
                    for (step in 0..samples) {
                        val x = size.width * step / samples
                        val y = waterLine + sin(phase + step * .55f) * amplitude
                        lineTo(x, y)
                    }
                    lineTo(size.width, size.height)
                    lineTo(0f, size.height)
                    close()
                }
                clipPath(liquid) { this@drawWithContent.drawContent() }
            },
        )
    }
}
