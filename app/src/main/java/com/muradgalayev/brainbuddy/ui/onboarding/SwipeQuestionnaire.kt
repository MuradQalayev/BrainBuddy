package com.muradgalayev.brainbuddy.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.CompositionLocalProvider
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.R
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue
import kotlin.math.sin
import androidx.compose.ui.res.stringResource

// how long a picked answer stays on screen before the next question slides in
private const val AUTO_ADVANCE_DELAY_MS = 320L

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
    val animate = animationsOn()
    // a single-choice answer moves on by itself, after a beat long enough to see the tick land. only
    // the page on screen can do it, and never off the end: finishing stays a deliberate tap
    val advanceFrom: (Int) -> (() -> Unit) = { page ->
        {
            if (page == pager.currentPage && page < pageCount - 1) {
                scope.launch {
                    delay(AUTO_ADVANCE_DELAY_MS)
                    if (pager.currentPage == page && !pager.isScrollInProgress) {
                        if (animate) pager.animateScrollToPage(page + 1) else pager.scrollToPage(page + 1)
                    }
                }
            }
        }
    }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp)) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.survey_question_of, pager.currentPage + 1, pageCount), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // deliberately never disabled by isSubmitting: if a save is hanging on a dead network, that's
            // exactly the moment someone needs to leave
            if (onSkip != null) {
                TextButton(
                    onClick = { confirmSkip = true },
                    modifier = Modifier.align(Alignment.CenterStart),
                ) {
                    Text(
                        stringResource(R.string.common_skip),
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
                    Text(stringResource(R.string.common_save), fontWeight = FontWeight.Bold)
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
            ) {
                CompositionLocalProvider(LocalAdvanceQuestion provides advanceFrom(page)) { pageContent(page) }
            }
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
                ) { Text(stringResource(R.string.common_back)) }
                Button(
                    onClick = { if (pager.currentPage == pageCount - 1) onSubmit() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
                    modifier = Modifier.weight(1f).height(50.dp),
                ) {
                    Text(if (pager.currentPage == pageCount - 1) submitLabel else stringResource(R.string.common_next), fontWeight = FontWeight.Bold)
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
            title = { Text(stringResource(R.string.survey_finish_later_title), fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    stringResource(R.string.survey_finish_later_body)
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmSkip = false; onSkip() }) {
                    Text(stringResource(R.string.survey_skip_for_now), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmSkip = false }) {
                    Text(stringResource(R.string.survey_keep_going), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                GrowingTree(progress, Modifier.fillMaxWidth().height(236.dp))
                Spacer(Modifier.height(4.dp))
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.tertiary.copy(alpha = .14f),
                ) {
                    Text(
                        stringResource(R.string.survey_profile_complete),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.survey_tree_thriving),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(7.dp))
                Text(
                    stringResource(R.string.survey_tree_thriving_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().height(148.dp).padding(start = 18.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(.76f)) {
                    Text(stringResource(R.string.survey_focus_tree), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        when {
                        invitation && progress < 1f -> stringResource(R.string.survey_tree_invite, (progress * 100).toInt())
                        progress == 0f -> stringResource(R.string.survey_tree_empty)
                        progress < .5f -> stringResource(R.string.survey_tree_roots, (progress * 100).toInt())
                        progress < 1f -> stringResource(R.string.survey_tree_canopy, (progress * 100).toInt())
                        else -> stringResource(R.string.survey_tree_full)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (invitation && progress < 1f) {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.survey_continue_profile), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(11.dp))
                    TreeProgressBar(progress)
                }
                GrowingTree(progress, Modifier.weight(1.08f).fillMaxHeight())
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
    // colour eases up to the new level instead of jumping, so answering a question reads as growth
    val filled by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "treeGrowth",
    )
    val waves = rememberInfiniteTransition(label = "treeLiquid")
    val phase by waves.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "treeLiquidWave",
    )
    // a slow breath and sway, rooted at the trunk. small enough to feel alive, not animated
    val breath by waves.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart),
        label = "treeBreath",
    )
    val leaf = MaterialTheme.colorScheme.tertiary
    val artwork = painterResource(R.drawable.onboarding_tree_full)
    // ContentScale.Fit letterboxes the artwork inside whatever slot it gets, so the colour has to
    // rise through the drawn picture, not through the layout box, or a short wide tile fills empty air
    fun DrawScope.artworkBounds(): Rect {
        val intrinsic = artwork.intrinsicSize
        val ratio = if (intrinsic.isSpecified && intrinsic.height > 0f) {
            intrinsic.width / intrinsic.height
        } else 1f
        val width: Float
        val height: Float
        if (size.width / size.height > ratio) {
            height = size.height
            width = height * ratio
        } else {
            width = size.width
            height = width / ratio
        }
        return Rect(
            offset = Offset((size.width - width) / 2f, (size.height - height) / 2f),
            size = Size(width, height),
        )
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        // halo behind the canopy that brightens as the profile fills in
        Box(
            Modifier.fillMaxSize().drawBehind {
                val tree = artworkBounds()
                val centre = Offset(tree.center.x, tree.top + tree.height * .44f)
                val radius = tree.minDimension * .70f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(leaf.copy(alpha = .22f * filled), Color.Transparent),
                        center = centre,
                        radius = radius,
                    ),
                    radius = radius,
                    center = centre,
                )
            }
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val swell = 1f + sin(breath) * .014f
                    scaleX = swell
                    scaleY = swell
                    rotationZ = sin(breath * .5f) * .8f
                    transformOrigin = TransformOrigin(.5f, 1f)
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = artwork,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.colorMatrix(grayscale),
                alpha = .28f,
                modifier = Modifier.fillMaxSize(),
            )
            Image(
                painter = artwork,
                contentDescription = stringResource(R.string.survey_tree_percent_cd, kotlin.math.round(progress * 100).toInt()),
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().drawWithContent {
                    val tree = artworkBounds()
                    val waterLine = tree.bottom - tree.height * filled
                    // keep the ripple proportional, or it swamps the tree in a small home tile
                    val amplitude =
                        if (filled in .01f..0.99f) minOf(5.dp.toPx(), tree.height * .035f) else 0f
                    val samples = 40
                    // two sines of different speeds, so the surface never repeats visibly
                    fun crestAt(step: Int): Float {
                        val ripple = sin(phase + step * .55f) * .7f + sin(phase * 1.7f - step * .21f) * .3f
                        return waterLine + ripple * amplitude
                    }
                    fun xAt(step: Int): Float = tree.left + tree.width * step / samples
                    val liquid = Path().apply {
                        moveTo(tree.left, crestAt(0))
                        for (step in 0..samples) lineTo(xAt(step), crestAt(step))
                        lineTo(tree.right, tree.bottom)
                        lineTo(tree.left, tree.bottom)
                        close()
                    }
                    clipPath(liquid) { this@drawWithContent.drawContent() }
                    if (amplitude > 0f) {
                        // a lit meniscus along the surface, the edge the eye follows as it rises
                        val crest = Path().apply {
                            moveTo(tree.left, crestAt(0))
                            for (step in 0..samples) lineTo(xAt(step), crestAt(step))
                        }
                        drawPath(crest, leaf.copy(alpha = .16f), style = Stroke(width = 5.dp.toPx()))
                        drawPath(crest, leaf.copy(alpha = .55f), style = Stroke(width = 1.5.dp.toPx()))
                    }
                },
            )
        }
    }
}

// how full the profile is, said once more in a way that reads at a glance
@Composable
private fun TreeProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val filled by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "treeBar",
    )
    Box(
        modifier
            .fillMaxWidth(.94f)
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .14f)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(filled)
                .clip(RoundedCornerShape(50))
                .background(
                    Brush.horizontalGradient(
                        listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary),
                    ),
                ),
        )
    }
}
