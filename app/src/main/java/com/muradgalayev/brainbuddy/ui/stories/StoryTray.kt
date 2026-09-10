package com.muradgalayev.brainbuddy.ui.stories

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.muradgalayev.brainbuddy.data.repository.Story
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.accessibility.speaking

private val CIRCLE_SIZE = 68.dp

// the row of story circles, revealed by pulling the home screen down. the pull opens this, not
// a story, and the difference matters: dropping someone straight into a full-screen message
// would make the gesture a commitment, and they'd stop using it the first time it opened
// something they didn't want. a tray is a menu, it shows what's there, how much of it there is
// and what's already been read, and asks for a second tap before it takes over the screen.
// sits under the greeting rather than above it, because the header tells you which screen
// you're on and that shouldn't move when new content arrives
@Composable
fun StoryTray(
    stories: List<Story>,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (stories.isEmpty()) return

    // drives the staggered entrance. flipped in a LaunchedEffect rather than starting true, so the
    // circles animate in on reveal instead of appearing fully formed the moment the tray composes
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        stories.forEachIndexed { index, story ->
            StoryCircle(
                story = story,
                index = index,
                shown = shown,
                onClick = { onOpen(index) },
            )
        }
    }
}

@Composable
private fun StoryCircle(
    story: Story,
    index: Int,
    shown: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val animate = animationsOn()

    // each circle lands a beat after the one before it, left to right, so the eye is led along the
    // row rather than having the whole thing arrive at once
    val appear by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (animate) 260 else 0,
            delayMillis = if (animate) index * 55 else 0,
            easing = FastOutSlowInEasing,
        ),
        label = "story_circle_appear",
    )

    // unseen gets the gradient ring, seen gets a flat quiet one. this is the whole reason the tray
    // beats a plain button: which ones are new is readable at a glance, with no number badge
    val ring = if (story.seen) {
        Brush.linearGradient(
            listOf(
                colors.onSurfaceVariant.copy(alpha = 0.28f),
                colors.onSurfaceVariant.copy(alpha = 0.28f),
            )
        )
    } else {
        Brush.sweepGradient(
            listOf(
                colors.primary,
                colors.tertiary,
                colors.secondary,
                colors.primary,
            )
        )
    }

    Column(
        modifier = Modifier
            .width(CIRCLE_SIZE + 12.dp)
            .scale(0.82f + 0.18f * appear)
            .alpha(appear),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(CIRCLE_SIZE)
                .border(width = if (story.seen) 1.5.dp else 2.5.dp, brush = ring, shape = CircleShape)
                .padding(if (story.seen) 4.dp else 5.dp)
                .clip(CircleShape)
                .clickable(onClick = speaking(story.title, onClick)),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(story.imageUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = story.title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (story.seen) FontWeight.Normal else FontWeight.SemiBold,
            color = if (story.seen) colors.onSurfaceVariant else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
