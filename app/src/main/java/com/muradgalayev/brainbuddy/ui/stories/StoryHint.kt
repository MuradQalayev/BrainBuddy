package com.muradgalayev.brainbuddy.ui.stories

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.accessibility.speaking
import kotlinx.coroutines.delay
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// how long each line holds before the other one takes its turn
private const val SWAP_INTERVAL_MS = 3_400L

// the one-line notice under the greeting that says there's something to pull for. the tray is
// hidden behind a gesture nobody was taught, which is the right trade for something that must
// never nag, but it does mean an unpulled story is an unread story forever. this is the
// smallest thing that fixes that: a quiet line alternating between naming the gesture and
// naming what's behind it, so it teaches on one beat and reports on the next.
// it's tappable as well. making discovery depend on performing a gesture correctly the first
// time is exactly the kind of small failure this app shouldn't hand someone: the pull is the
// nice way in, the tap is the reliable one.
// shown only while there's something unseen and the tray is closed
@Composable
fun StoryHint(
    unseenCount: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (unseenCount <= 0) return

    val colors = MaterialTheme.colorScheme
    val animate = animationsOn()

    val countLine = if (unseenCount == 1) stringResource(R.string.story_one) else stringResource(R.string.story_many, unseenCount)
    val nudgeLine = stringResource(R.string.story_pull_down)

    // under reduce-motion the line doesn't alternate at all: text that swaps itself on a timer is
    // movement the reader didn't ask for, and the count is the half worth keeping
    var showCount by remember { mutableStateOf(!animate) }
    LaunchedEffect(animate, unseenCount) {
        if (!animate) return@LaunchedEffect
        while (true) {
            delay(SWAP_INTERVAL_MS)
            showCount = !showCount
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(colors.primary.copy(alpha = 0.10f))
            .clickable(onClick = speaking(countLine, onOpen))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // a live dot, not a number badge. it marks that something is there without counting it at you
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(colors.primary)
        )

        AnimatedContent(
            targetState = if (showCount) countLine else nudgeLine,
            transitionSpec = {
                if (!animate) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    val slide = tween<IntOffset>(260, easing = FastOutSlowInEasing)
                    (slideInVertically(slide) { it } + fadeIn(tween(200)))
                        .togetherWith(slideOutVertically(slide) { -it } + fadeOut(tween(150)))
                }
            },
            label = "story_hint_line",
        ) { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
            )
        }

        Icon(
            imageVector = Icons.Rounded.KeyboardArrowDown,
            contentDescription = null,
            tint = colors.primary.copy(alpha = 0.75f),
            modifier = Modifier.size(15.dp),
        )
    }
}
