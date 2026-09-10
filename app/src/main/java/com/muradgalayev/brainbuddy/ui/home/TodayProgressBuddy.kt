package com.muradgalayev.brainbuddy.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// the companion, full size. motivational and nothing else: no counts to act on, no list, no
// button that changes anything, deliberately. it's the one surface in the app that asks for
// nothing, which is what makes it safe to open on a day that's going badly. see HomeBuddy for
// why the character can't be sad
@Composable
fun TodayProgressBuddyDialog(
    done: Int,
    total: Int,
    onDismiss: () -> Unit,
) {
    val accents = MaterialTheme.myndoraAccents
    val mood = buddyMood(done, total)
    val motion = rememberMotionEnabled()

    // a calm room rather than a stage: the background is the accent pulled a long way down, so the
    // character is the only bright thing on screen and nothing competes for attention
    val deep = Color(0xFF0B0B14)
    val top = lerp(accents.accent, deep, .84f)
    val bottom = lerp(accents.supportEnd, deep, .93f)

    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }
    val entrance by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(380, easing = FastOutSlowInEasing),
        label = "buddy_entrance",
    )

    val message = remember(done, total) { buddyMessage(done, total) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(top, bottom)))
                // anywhere. nothing in here is interactive, so making someone find the close button would be a puzzle
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Column(
                Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.CenterEnd) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = .10f))
                            .border(1.dp, Color.White.copy(alpha = .18f), CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Close",
                            tint = Color.White.copy(alpha = .8f),
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }

                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    HomeBuddy(
                        done = done,
                        total = total,
                        bodyStart = accents.accent,
                        bodyEnd = accents.accentEnd,
                        lively = true,
                        modifier = Modifier
                            .fillMaxSize()
                            .scale(.92f + .08f * entrance)
                            .alpha(entrance),
                    )
                    // one burst, on opening a finished day. it doesn't loop, a celebration that never stops is
                    // just weather
                    if (mood == BuddyMood.Beaming && motion) {
                        Confetti(accents.accent, accents.accentEnd, Modifier.fillMaxSize())
                    }
                }

                Column(
                    Modifier.fillMaxWidth().padding(bottom = 40.dp).alpha(entrance),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        message.headline,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        lineHeight = 32.sp,
                    )
                    Text(
                        message.line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = .74f),
                        textAlign = TextAlign.Center,
                        lineHeight = 21.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        if (total <= 0) "nothing scheduled" else "$done done today",
                        style = MaterialTheme.typography.labelMedium.tabular(),
                        color = Color.White.copy(alpha = .45f),
                        letterSpacing = 1.2.sp,
                    )
                }
            }
        }
    }
}

// a single fall of paper. runs once when it appears and then stops for good
@Composable
private fun Confetti(start: Color, end: Color, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, tween(2600, easing = FastOutSlowInEasing)) }
    val pieces = remember {
        List(26) {
            val random = Random(it * 977 + 13)
            ConfettiPiece(
                x = random.nextFloat(),
                drift = (random.nextFloat() - .5f) * .3f,
                delay = random.nextFloat() * .35f,
                spin = 1.5f + random.nextFloat() * 3f,
                size = .5f + random.nextFloat(),
                warm = random.nextBoolean(),
            )
        }
    }

    Canvas(modifier) {
        pieces.forEach { piece ->
            val local = ((progress.value - piece.delay) / (1f - piece.delay)).coerceIn(0f, 1f)
            if (local <= 0f) return@forEach
            val fall = local * local * .5f + local * .5f
            val width = size.minDimension * .022f * piece.size
            val centre = Offset(
                (piece.x + piece.drift * local) * size.width,
                -width + fall * (size.height + width * 2),
            )
            // turns edge-on as it falls, which is what stops it reading as a falling brick
            val face = sin(local * piece.spin * PI.toFloat() * 2f)
            drawOval(
                color = (if (piece.warm) start else end).copy(alpha = (1f - local * .55f).coerceIn(0f, 1f)),
                topLeft = Offset(centre.x - width / 2f, centre.y - width * .7f),
                size = Size(width * kotlin.math.abs(face).coerceAtLeast(.15f), width * 1.4f),
            )
        }
    }
}

private data class ConfettiPiece(
    val x: Float,
    val drift: Float,
    val delay: Float,
    val spin: Float,
    val size: Float,
    val warm: Boolean,
)

// headline and supporting line
data class BuddyMessage(val headline: String, val line: String)

// copy that can't turn into an accusation. the rules, all for the same reason, that someone
// opening this at 11pm having done one thing must not be told about the other eight:
// - nothing counts what's missing, ever;
// - the zero case is a welcome, and explicitly says the creature isn't waiting on you, because
//   'it's waiting for you!' is the exact sentence that makes an app unopenable on a bad day;
// - no 'keep it up', no streaks, no tomorrow. this is about today, and today is already fine
fun buddyMessage(done: Int, total: Int): BuddyMessage = when {
    total <= 0 -> BuddyMessage(
        "Out cold.",
        "Nothing on today, so it's having a nap. Sleeping is most of what it's good at.",
    )
    done <= 0 -> BuddyMessage(
        "Still asleep.",
        "It's not waiting on you — it's just asleep. It stirs at the first thing you finish, " +
            "and the smallest thing counts exactly the same as the biggest.",
    )
    done == 1 -> BuddyMessage(
        "One eye open.",
        "One thing did that. Starting is the whole hard part, and you're past it.",
    )
    done >= total && total == 1 -> BuddyMessage(
        "Look at it go.",
        "Everything today asked of you. That's the lot.",
    )
    done >= total -> BuddyMessage(
        "Look at it go.",
        "All $total of them. Sit with that for a second before you find the next thing.",
    )
    done * 2 >= total -> BuddyMessage(
        "Wide awake.",
        "$done in and it's bouncing. Whatever else today does, that part already happened.",
    )
    else -> BuddyMessage(
        "Awake, thanks to you.",
        "$done down. It stays awake for the rest of the day — nothing you do or don't do next " +
            "puts it back to sleep.",
    )
}
