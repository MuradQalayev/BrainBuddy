package com.muradgalayev.brainbuddy.ui.pomodoro

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.muradgalayev.brainbuddy.data.repository.FocusRoomMember
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.accessibility.speaking
import kotlinx.coroutines.delay

// the focus room: the place between accepting and working. it exists because 'both tap start'
// needs somewhere to happen. doing it from a strip on the timer screen meant two people
// watching a line of text for a state change, with the rest of the screen offering things to
// do instead. a room is one job on one screen: see who's here, say you're ready, wait, go.
// the waiting state is the whole design problem. waiting on another person is the moment this
// feature is most likely to be abandoned, so the screen shows their face, shows exactly who
// it's waiting on, and never implies the app is stuck: a spinner would say 'working', when in
// fact nothing is happening and that's fine
@Composable
fun FocusRoomScreen(
    sessionId: String,
    onClose: () -> Unit,
    viewModel: FocusRoomViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(sessionId) { viewModel.open(sessionId) }
    LaunchedEffect(state.closed) { if (state.closed) onClose() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = speaking("Back, staying in the room", onClose)) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back — you stay in the room",
                )
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Focus room",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "${state.readyCount} of ${state.members.size} ready",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))

            // the countdown replaces the length once it's running, in the same spot, so the transition
            // reads as the thing starting rather than as the screen changing
            AnimatedContent(
                targetState = state.isRunning,
                transitionSpec = { fadeIn(tween(240)) togetherWith fadeOut(tween(160)) },
                label = "room_headline",
            ) { running ->
                if (running) {
                    LiveCountdown(
                        startSeconds = state.session?.secondsRemaining ?: 0,
                        totalSeconds = ((if (state.onBreak) {
                            state.session?.breakMinutes
                        } else {
                            state.session?.focusMinutes
                        }) ?: 25) * 60,
                    )
                } else {
                    RingedNumber(
                        progress = 0f,
                        label = "${state.session?.focusMinutes ?: 25}",
                        suffix = "min",
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    state.onBreak && state.isRunning -> "Break together \u2615"
                    state.isRunning -> "Focusing together"
                    state.everyoneReady -> "Starting…"
                    state.iAmReady -> waitingLine(state.members)
                    else -> "Tap ready when you're at your desk"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(36.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                state.members.forEach { member ->
                    MemberFace(member = member, pulsing = !member.isReady && !state.isRunning)
                }
            }

            Spacer(Modifier.weight(1f))

            if (!state.iAmReady) {
                Button(
                    onClick = speaking("I'm ready", viewModel::markReady),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("I'm ready", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                }
            } else if (!state.isRunning) {
                // deliberately not a disabled button. a greyed Ready invites repeated tapping at the one
                // moment nothing the user does will help; stating who we're waiting on is the useful thing
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(colors.secondaryContainer.copy(alpha = 0.5f))
                        .padding(vertical = 17.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "You're ready",
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSecondaryContainer,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            // explicitly worded, because this is the one control that can't be undone: leaving removes
            // you from the session for good, while the back arrow just closes the screen and keeps your
            // place. two very different actions that looked identical before
            TextButton(
                onClick = speaking("Leave the session", viewModel::leave),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (state.isRunning) "Leave the session" else "Not now, leave",
                    color = colors.error.copy(alpha = 0.85f),
                )
            }
            Spacer(Modifier.height(12.dp).then(Modifier.navigationBarsPadding()))
        }
    }
}

// 'Waiting for Ali' beats 'waiting for 1 person', it's a person so name them
private fun waitingLine(members: List<FocusRoomMember>): String {
    val notReady = members.filterNot { it.isReady }
    return when {
        notReady.isEmpty() -> "Starting…"
        notReady.size == 1 -> "Waiting for ${notReady.first().name}"
        else -> "Waiting for ${notReady.size} others"
    }
}

// a face, ringed when ready. not-ready faces breathe slowly. it's the one bit of motion on the
// screen and it carries meaning, that something is still expected of that person, so it stops
// the moment they're in. held still entirely under reduce motion
@Composable
private fun MemberFace(member: FocusRoomMember, pulsing: Boolean) {
    val colors = MaterialTheme.colorScheme
    val animate = animationsOn()

    val pulse = if (pulsing && animate) {
        val transition = rememberInfiniteTransition(label = "member_pulse")
        transition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.04f,
            animationSpec = infiniteRepeatable(
                animation = tween(1600, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "member_pulse_scale",
        ).value
    } else 1f

    val ring by animateFloatAsState(
        targetValue = if (member.isReady) 1f else 0f,
        animationSpec = tween(if (animate) 260 else 0),
        label = "member_ring",
    )

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Box(
                Modifier
                    .size(72.dp)
                    .scale(pulse)
                    .clip(CircleShape)
                    .background(colors.primary.copy(alpha = 0.18f))
                    .border(
                        width = (1 + 2 * ring).dp,
                        color = colors.primary.copy(alpha = 0.25f + 0.75f * ring),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (member.avatarUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(member.avatarUrl).crossfade(true).build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).clip(CircleShape),
                    )
                } else {
                    Text(
                        text = member.name.take(1).uppercase(),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                }
            }

            if (member.isReady) {
                Box(
                    Modifier
                        .alpha(ring)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(colors.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = null,
                        tint = colors.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = member.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (member.isReady) FontWeight.Bold else FontWeight.Normal,
            color = if (member.isReady) colors.onSurface else colors.onSurfaceVariant,
        )
    }
}

// counts down from the server's number. seeded once and ticked locally rather than re-read
// every poll: re-seeding would make the display jump backwards by a second whenever a poll
// landed slightly late, which on a number this size is very visible
@Composable
private fun LiveCountdown(startSeconds: Int, totalSeconds: Int) {
    var remaining by remember(startSeconds) { mutableIntStateOf(startSeconds) }
    LaunchedEffect(startSeconds) {
        while (remaining > 0) {
            delay(1_000)
            remaining -= 1
        }
    }
    RingedNumber(
        progress = if (totalSeconds <= 0) 0f else 1f - remaining.toFloat() / totalSeconds,
        label = "%d:%02d".format(remaining / 60, remaining % 60),
        suffix = null,
    )
}

// the number, inside a ring that fills as the session goes. the same shape as the timer
// screen's own ring, so arriving here from the Pomodoro tab doesn't feel like a different app.
// before the session starts it's drawn empty rather than hidden, since the frame is what makes
// the waiting state read as 'not started yet' instead of 'nothing here'
@Composable
private fun RingedNumber(progress: Float, label: String, suffix: String?) {
    val colors = MaterialTheme.colorScheme
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(if (animationsOn()) 600 else 0, easing = FastOutSlowInEasing),
        label = "room_ring",
    )

    Box(Modifier.size(196.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(196.dp)) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            drawArc(
                color = colors.primary.copy(alpha = 0.15f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = androidx.compose.ui.geometry.Size(
                    size.width - stroke, size.height - stroke,
                ),
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = stroke,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                ),
            )
            if (animated > 0f) {
                drawArc(
                    color = colors.primary,
                    startAngle = -90f,
                    sweepAngle = 360f * animated,
                    useCenter = false,
                    topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(
                        size.width - stroke, size.height - stroke,
                    ),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = stroke,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    ),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            if (suffix != null) {
                Text(
                    text = suffix,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}
