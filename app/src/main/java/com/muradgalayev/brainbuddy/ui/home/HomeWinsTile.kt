package com.muradgalayev.brainbuddy.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// what you finished today. counting up, never down. deliberately not a streak and never a
// percentage of some ideal day: a number that can go down turns the home screen into a
// scoreboard you're losing, which is how ADHD task apps end up uninstalled. two done out of
// nine still reads 'two done'.
// the companion sits in the corner so the card carries the whole idea at a glance and tapping
// it is an obvious thing to do rather than a hidden feature. same character the full view
// shows, kept deliberately quiet at this size
@Composable
fun HomeWinsTile(
    done: Int,
    total: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = MaterialTheme.myndoraAccents
    val accent = accents.supportEnd

    HomeCard(modifier = modifier, accent = accent, onClick = onOpen) {
        // bottom-right corner, never under the number. a character behind text is a smudge, beside it
        // it's a face
        HomeBuddy(
            done = done,
            total = total,
            bodyStart = accents.accent,
            bodyEnd = accents.accentEnd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .fillMaxHeight(.55f)
                .aspectRatio(1f)
                .padding(end = 4.dp, bottom = 4.dp),
        )

        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EmojiEmotions, null, tint = accent, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(7.dp))
                HomeSectionLabel(stringResource(R.string.wins_caps), accent)
            }

            // keeps the wording clear of the corner the companion occupies. text running under a face is
            // the one way this layout can look like a mistake
            Column(Modifier.padding(end = 44.dp)) {
                // the number pops in on its own so finishing something is felt rather than just recorded, the
                // one place on this screen where a little fanfare is the point
                AnimatedContent(
                    targetState = done,
                    transitionSpec = {
                        (fadeIn(tween(220)) + scaleIn(tween(320, easing = FastOutSlowInEasing), initialScale = .6f))
                            .togetherWith(fadeOut(tween(140)))
                    },
                    label = "wins_count",
                ) { count ->
                    Text(
                        "$count",
                        style = MaterialTheme.typography.displaySmall.tabular(),
                        fontWeight = FontWeight.Bold,
                        color = accent,
                        maxLines = 1,
                    )
                }
                Text(
                    winsCaption(done, total),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    lineHeight = 15.sp,
                )
            }

            Text(
                stringResource(R.string.wins_say_hi),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent.copy(alpha = .75f),
                maxLines = 1,
                modifier = Modifier.padding(end = 44.dp),
            )
        }
    }
}

@Composable
private fun winsCaption(done: Int, total: Int): String = when {
    total == 0 -> stringResource(R.string.home_nothing_today)
    done == 0 -> stringResource(R.string.wins_nothing_yet)
    done == total -> stringResource(R.string.wins_all_done)
    else -> stringResource(R.string.wins_done_out_of, total)
}
