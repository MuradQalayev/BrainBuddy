package com.muradgalayev.brainbuddy.ui.pomodoro

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Group
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.muradgalayev.brainbuddy.data.repository.FocusSession
import com.muradgalayev.brainbuddy.ui.accessibility.speaking
import kotlinx.coroutines.delay

// focus together, the body-doubling surface on the Pomodoro screen. three states, only one of
// them on screen at a time: someone asked you (the invite, with Join and Not now), a session
// is running (who else is in it and how long is left), or neither and you have people who
// allow it (the invite button).
// nothing here says what anyone is working on, because nothing upstream carries it. what's
// shared is that a person is present and a countdown they are both inside, which is the entire
// mechanism body doubling runs on.
// the whole card is absent when no connection has granted FOCUS: a permanent 'invite a friend'
// button for someone with no friends on the app is a small recurring failure on the screen
// they use when they're already struggling
@Composable
fun FocusTogetherCard(
    modifier: Modifier = Modifier,
    focusMinutes: Int,
    onOpenRoom: (String) -> Unit = {},
    viewModel: FocusTogetherViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showSheet by remember { mutableStateOf(false) }
    val invite = state.invites.firstOrNull()
    val waiting = state.waitingSession
    val active = state.activeSession

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.40f),
    ) {
        // tight. this sits between the ring and the Start button, which is the most valuable space on
        // the screen and belongs to the timer. the card is an offer, and an offer shouldn't be the
        // size of the thing it interrupts
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            when {
                // Join opens the room rather than accepting in place: saying yes and then watching a one-line
                // strip for the other person is the part that needs a screen of its own
                invite != null -> InviteRow(
                    session = invite,
                    onJoin = { onOpenRoom(invite.id) },
                    onDecline = { viewModel.decline(invite.id) },
                )
                waiting != null -> WaitingRow(
                    session = waiting,
                    onCancel = { onOpenRoom(waiting.id) },
                )
                // tapping re-enters rather than leaving. backing out of the room is a navigation, not a
                // decision, and the session is still running so the way back has to be obvious
                active != null -> ActiveRow(
                    session = active,
                    onOpen = { onOpenRoom(active.id) },
                )
                else -> InvitePicker(
                    people = state.candidates,
                    sending = state.sending,
                    onOpenSheet = { showSheet = true },
                )
            }

            state.error?.let { message ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (showSheet) {
        CreateFocusRoomSheet(
            people = state.candidates,
            defaultMinutes = focusMinutes,
            sending = state.sending,
            onCreate = { ids, minutes -> viewModel.invite(ids, minutes) },
            onDismiss = { showSheet = false },
        )
    }

    // straight into the room you just made. creating one and then being left on the timer screen
    // to find your own way in is the moment an invite gets sent and then forgotten by the sender
    LaunchedEffect(state.createdSessionId) {
        val id = state.createdSessionId ?: return@LaunchedEffect
        showSheet = false
        viewModel.consumeCreatedSession()
        onOpenRoom(id)
    }
}

// I've tapped start, someone else hasn't yet. named as waiting on a person rather than showing
// a spinner, because a spinner reads as the app working and this is the app doing nothing at
// all: the delay is entirely someone else's, and saying so is what stops it feeling broken
@Composable
private fun WaitingRow(session: FocusSession, onCancel: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PresenceDot()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "Waiting for ${session.pendingCount} to tap start",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "${session.focusMinutes} min · starts when everyone's in",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
            )
        }
        TextButton(onClick = speaking("Cancel", onCancel)) { Text("Cancel") }
    }
}

@Composable
private fun InviteRow(
    session: FocusSession,
    onJoin: () -> Unit,
    onDecline: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        PresenceDot()
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = "${session.hostName} wants to focus with you",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                // the clock starts on joining, not on inviting, or the number reads as time already lost
                text = "${session.focusMinutes} min · starts when you join",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // decline stays quiet and Join is the filled one. two identical text buttons made the choice
        // look symmetrical, when accepting is the whole point and declining is the escape hatch
        TextButton(
            onClick = speaking("Not now", onDecline),
            modifier = Modifier.weight(1f),
        ) {
            Text("Not now", color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = speaking("Join", onJoin),
            modifier = Modifier.weight(1f).height(40.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Text("Join", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActiveRow(session: FocusSession, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme

    // deliberately no countdown here. the ring directly above this card is already showing the
    // time, driven by the same session, and two clocks on one screen each ticking locally from
    // its own seed drift apart by a second or two and then disagree in front of the user. the
    // ring owns the number, this row owns who with
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = speaking("Open the focus room", onOpen))
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PresenceDot()
        Spacer(Modifier.width(10.dp))
        Text(
            text = buildString {
                append(
                    if (session.phase == com.muradgalayev.brainbuddy.data.repository
                            .FocusPhase.BREAK
                    ) "On a break with " else "Focusing with ",
                )
                append(session.hostName)
                if (session.joinedCount > 2) append(" +${session.joinedCount - 2}")
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSecondaryContainer,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "\u203a",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSecondaryContainer.copy(alpha = 0.6f),
        )
    }
}

// the resting state: a pill under the ring showing who you could work alongside. faces before
// words, a stack of the people you actually know says what this does faster than the label
// can, and it's the same avatar-stack idiom the Together card on the workspace uses.
// tapping opens a sheet rather than expanding a list here, because choosing a length is part
// of the invite and there's no room for that on a strip wedged between the ring and Start
@Composable
private fun InvitePicker(
    people: List<FocusCandidate>,
    sending: Boolean,
    onOpenSheet: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !sending, onClick = speaking("Focus together", onOpenSheet))
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (people.isEmpty()) {
            androidx.compose.material3.Icon(
                imageVector = Icons.Rounded.Group,
                contentDescription = null,
                tint = colors.onSecondaryContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(18.dp),
            )
        } else {
            AvatarStack(people.take(3))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (sending) "Creating\u2026" else "Focus together",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSecondaryContainer,
            )
            if (people.isEmpty()) {
                Text(
                    text = "Add someone in Together to work alongside them",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSecondaryContainer.copy(alpha = 0.7f),
                )
            }
        }
        Text(
            text = "\u203a",
            style = MaterialTheme.typography.titleMedium,
            color = colors.onSecondaryContainer.copy(alpha = 0.6f),
        )
    }
}

// overlapping faces, newest first. same idiom as the workspace Together card
@Composable
private fun AvatarStack(people: List<FocusCandidate>) {
    Row {
        people.forEachIndexed { index, person ->
            Box(Modifier.offset(x = if (index == 0) 0.dp else (-8 * index).dp)) {
                Avatar(person, size = 26.dp, dimmed = !person.allowed, ringed = true)
            }
        }
    }
}

@Composable
private fun Avatar(
    person: FocusCandidate,
    size: androidx.compose.ui.unit.Dp,
    dimmed: Boolean,
    ringed: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val base = Modifier
        .size(size)
        .clip(CircleShape)
        .background(colors.primary.copy(alpha = if (dimmed) 0.15f else 0.30f))
    val shaped = if (ringed) {
        base.border(1.5.dp, colors.secondaryContainer, CircleShape)
    } else {
        base
    }

    Box(shaped, contentAlignment = Alignment.Center) {
        if (person.avatarUrl != null) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(person.avatarUrl).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size).clip(CircleShape),
            )
        } else {
            // an initial, not a generic silhouette, so two connections can be told apart
            Text(
                text = person.name.take(1).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSecondaryContainer.copy(alpha = if (dimmed) 0.5f else 1f),
            )
        }
    }
}

@Composable
private fun PresenceDot() {
    Box(
        Modifier
            .size(9.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    )
}
