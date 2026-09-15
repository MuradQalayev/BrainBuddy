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
import androidx.compose.material.icons.rounded.AutoAwesome
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
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
import androidx.compose.ui.graphics.Color
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
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// focus together, the body-doubling surface on the Pomodoro screen. this card is the three
// states that need attention: someone asked you (Join and Not now), a room waiting on the others,
// or one in progress. with none of those it draws nothing, and the offer is FocusTogetherPill
// in the timer card instead.
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
    showSheet: Boolean,
    onDismissSheet: () -> Unit,
    onOpenRoom: (String) -> Unit = {},
    viewModel: FocusTogetherViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val invite = state.invites.firstOrNull()
    val waiting = state.waitingSession
    val active = state.activeSession

    if (invite != null || waiting != null || active != null || state.error != null) Surface(
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
            }

            state.error?.let { message ->
                if (invite != null || waiting != null || active != null) Spacer(Modifier.height(8.dp))
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
            onDismiss = onDismissSheet,
        )
    }

    // straight into the room you just made. creating one and then being left on the timer screen
    // to find your own way in is the moment an invite gets sent and then forgotten by the sender
    LaunchedEffect(state.createdSessionId) {
        val id = state.createdSessionId ?: return@LaunchedEffect
        onDismissSheet()
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
                text = stringResource(R.string.focus_waiting_start, session.pendingCount),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = stringResource(R.string.focus_starts_everyone, session.focusMinutes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
            )
        }
        TextButton(onClick = speaking(stringResource(R.string.common_cancel), onCancel)) { Text(stringResource(R.string.common_cancel)) }
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
                text = stringResource(R.string.focus_wants_with_you, session.hostName),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                // the clock starts on joining, not on inviting, or the number reads as time already lost
                text = stringResource(R.string.focus_starts_join, session.focusMinutes),
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
            onClick = speaking(stringResource(R.string.common_not_now), onDecline),
            modifier = Modifier.weight(1f),
        ) {
            Text(stringResource(R.string.common_not_now), color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = speaking(stringResource(R.string.focus_join), onJoin),
            modifier = Modifier.weight(1f).height(40.dp),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Text(stringResource(R.string.focus_join), fontWeight = FontWeight.Bold)
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
            .clickable(onClick = speaking(stringResource(R.string.focus_open_room), onOpen))
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
                    ) stringResource(R.string.focus_break_with) else stringResource(R.string.focus_focusing_with),
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

// the resting offer, a pill next to the ambient sound one and cut to match it. faces before
// words: a stack of the people you actually know says what this does faster than the label.
// tapping opens the sheet, because choosing a length is part of the invite
@Composable
fun FocusTogetherPill(
    people: List<FocusCandidate>,
    sending: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // Free account: a Plus sparkle, and the tap opens the plan instead of the sheet
    locked: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val label = if (sending) stringResource(R.string.focus_creating) else stringResource(R.string.focus_together_short)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = colors.onSurface.copy(alpha = 0.06f),
        enabled = !sending,
        onClick = speaking(stringResource(R.string.together_scope_focus), onClick),
    ) {
        Row(
            modifier = Modifier.padding(start = if (people.isEmpty()) 14.dp else 8.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (people.isEmpty()) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.Group,
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            } else {
                AvatarStack(people.take(3), size = 22.dp, ringColor = colors.surfaceContainerHighest)
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant,
            )
            if (locked) {
                Spacer(Modifier.width(6.dp))
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = stringResource(R.string.plan_locked_cd),
                    tint = MaterialTheme.myndoraAccents.accent,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

// overlapping faces, newest first. same idiom as the workspace Together card
@Composable
private fun AvatarStack(
    people: List<FocusCandidate>,
    size: androidx.compose.ui.unit.Dp,
    ringColor: Color,
) {
    // overlap by a third of a face. offset alone would leave the row as wide as the unshifted faces
    Row(horizontalArrangement = Arrangement.spacedBy(-(size / 3))) {
        people.forEach { person ->
            Avatar(person, size = size, dimmed = !person.allowed, ringColor = ringColor)
        }
    }
}

@Composable
private fun Avatar(
    person: FocusCandidate,
    size: androidx.compose.ui.unit.Dp,
    dimmed: Boolean,
    ringColor: Color,
) {
    val colors = MaterialTheme.colorScheme
    // a ring in the colour behind the stack is what separates one face from the next
    val shaped = Modifier
        .size(size)
        .clip(CircleShape)
        .background(colors.primary.copy(alpha = if (dimmed) 0.15f else 0.30f))
        .border(1.5.dp, ringColor, CircleShape)

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
