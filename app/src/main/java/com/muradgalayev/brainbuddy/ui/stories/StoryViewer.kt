package com.muradgalayev.brainbuddy.ui.stories

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.muradgalayev.brainbuddy.data.repository.Story
import com.muradgalayev.brainbuddy.data.repository.safeStoryLink
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import kotlinx.coroutines.delay
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// how long a story holds before advancing itself
private const val STORY_DURATION_MS = 6_000L

// full-screen story viewer. deliberately conventional: progress bars along the top, tap right
// for next, tap left for back, drag down to leave. this is the one screen that should teach
// nobody anything, the gesture vocabulary is already in everyone's hands from other apps, and
// inventing a better one here would just mean the content goes unread.
// auto-advance is off under reduce-motion, where a story that moves on by itself is a timer
// the reader didn't set and can't see coming
@Composable
fun StoryViewer(
    stories: List<Story>,
    startIndex: Int,
    onSeen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (stories.isEmpty()) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // grows into place instead of cutting to full screen. starting at 0.88 rather than 0 keeps it
        // an expansion rather than a zoom from nothing, so it reads as the circle opening up
        var entered by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { entered = true }
        val animate = animationsOn()
        val enter by animateFloatAsState(
            targetValue = if (entered) 1f else 0f,
            animationSpec = tween(
                durationMillis = if (animate) 260 else 0,
                easing = FastOutSlowInEasing,
            ),
            label = "story_enter",
        )

        Box(
            Modifier
                .fillMaxSize()
                .scale(0.88f + 0.12f * enter)
                .alpha(enter),
        ) {
            StoryPager(
                stories = stories,
                startIndex = startIndex.coerceIn(0, stories.lastIndex),
                onSeen = onSeen,
                onDismiss = onDismiss,
            )
        }
    }
}

@Composable
private fun StoryPager(
    stories: List<Story>,
    startIndex: Int,
    onSeen: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var index by remember { mutableIntStateOf(startIndex) }
    val current = stories.getOrNull(index) ?: return
    val autoAdvance = animationsOn()

    // held while a finger is down: reading shouldn't be on a clock the reader can't pause, and
    // press-and-hold to pause is the same gesture every other stories UI uses
    var paused by remember { mutableStateOf(false) }

    // restarts on every index change, which is what resets the progress bar
    var elapsed by remember(index) { mutableFloatStateOf(0f) }

    LaunchedEffect(current.id) { onSeen(current.id) }

    LaunchedEffect(index, paused, autoAdvance) {
        if (!autoAdvance || paused) return@LaunchedEffect
        val step = 50L
        while (elapsed < 1f) {
            delay(step)
            elapsed = (elapsed + step.toFloat() / STORY_DURATION_MS).coerceAtMost(1f)
        }
        if (index < stories.lastIndex) index++ else onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(stories.size) {
                detectTapGestures(
                    onPress = {
                        paused = true
                        tryAwaitRelease()
                        paused = false
                    },
                    onTap = { offset ->
                        // left third goes back, the rest forward, weighted that way because forward is almost every tap
                        val backZone = size.width / 3f
                        if (offset.x < backZone) {
                            if (index > 0) index-- else elapsed = 0f
                        } else {
                            if (index < stories.lastIndex) index++ else onDismiss()
                        }
                    },
                )
            }
            .pointerInput(Unit) {
                var dragged = 0f
                detectVerticalDragGestures(
                    onDragEnd = {
                        // a deliberate pull, not a stray finger
                        if (dragged > 120f) onDismiss()
                        dragged = 0f
                    },
                    onVerticalDrag = { _, amount -> dragged += amount },
                )
            },
    ) {
        StoryImage(story = current)

        // the caption sits on a scrim rather than on the photo: without it a light image makes white
        // text unreadable, which is precisely when a caption matters most
        if (current.caption != null || current.linkUrl != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.72f),
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 34.dp),
            ) {
                current.caption?.let { caption ->
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                }
                current.linkUrl?.let { link ->
                    if (current.caption != null) Spacer(Modifier.height(14.dp))
                    StoryLinkSticker(
                        url = link,
                        label = current.linkLabel,
                        onOpened = onDismiss,
                    )
                }
            }
        }

        StoryProgressBars(
            count = stories.size,
            index = index,
            progress = elapsed,
            autoAdvance = autoAdvance,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun StoryLinkSticker(
    url: String,
    label: String?,
    onOpened: () -> Unit,
) {
    val context = LocalContext.current
    val host = remember(url) {
        runCatching { java.net.URI(url).host?.removePrefix("www.") }.getOrNull()
    }
    val text = label?.take(60) ?: host ?: stringResource(R.string.story_open_link)

    Surface(
        onClick = {
            if (openStoryLink(context, url)) onOpened()
        },
        shape = RoundedCornerShape(18.dp),
        color = Color.White,
        contentColor = Color.Black,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.size(10.dp))
            Icon(
                Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = stringResource(R.string.story_open_link),
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun openStoryLink(context: Context, rawUrl: String): Boolean {
    val url = safeStoryLink(rawUrl) ?: return false
    return runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE),
        )
    }.isSuccess
}

@Composable
private fun StoryImage(story: Story) {
    var loading by remember(story.id) { mutableStateOf(true) }

    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(story.imageUrl)
            .crossfade(true)
            .build(),
        contentDescription = story.caption ?: stringResource(R.string.story_cd),
        contentScale = ContentScale.Fit,
        modifier = Modifier.fillMaxSize(),
        onSuccess = { loading = false },
        onError = { loading = false },
    )

    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
        }
    }
}

// one bar per story: filled behind, draining in front, empty ahead. bars rather than a single
// one because the count is the useful part, 'two more after this' is what decides whether
// someone starts reading at all
@Composable
private fun StoryProgressBars(
    count: Int,
    index: Int,
    progress: Float,
    autoAdvance: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(count) { i ->
            val fill = when {
                i < index -> 1f
                i > index -> 0f
                // with auto-advance off there's no elapsing time to show, so the current bar is simply full
                // rather than frozen at zero. its own flag, not progress == 0: every story starts at 0, and
                // reading that as 'full' filled each new bar up before it dropped back to start
                !autoAdvance -> 1f
                else -> progress
            }
            val animatedFill by animateFloatAsState(
                targetValue = fill,
                animationSpec = tween(durationMillis = 90, easing = LinearEasing),
                label = "story_bar_$i",
            )
            Box(
                Modifier
                    .weight(1f)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.28f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(animatedFill)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White),
                )
            }
        }
    }
}
