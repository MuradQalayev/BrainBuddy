package com.muradgalayev.brainbuddy.ui.together

import com.muradgalayev.brainbuddy.ui.utils.resolve
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.domain.model.Connection
import com.muradgalayev.brainbuddy.domain.model.ConnectionRelation
import com.muradgalayev.brainbuddy.domain.model.IncomingRequest
import com.muradgalayev.brainbuddy.domain.model.OutgoingRequest
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// which half of the hub is showing
private enum class TogetherTab(@androidx.annotation.StringRes val labelRes: Int) { People(R.string.together_tab_people), Requests(R.string.together_tab_requests) }

// the Together hub. two tabs rather than one long scroll, because the two jobs here are
// unrelated: 'who am I connected to' is browsing, 'someone is waiting on me' is a task.
// Requests auto-opens when something is pending, so an unanswered request can't sit unseen
// behind a tab the user never taps
@Composable
fun TogetherScreen(
    onBack: () -> Unit,
    onAddSomeone: () -> Unit,
    onOpenConnection: (String) -> Unit,
    viewModel: TogetherViewModel = hiltViewModel(),
) {
    val connections by viewModel.connections.collectAsState()
    val incoming by viewModel.incomingRequests.collectAsState()
    val outgoing by viewModel.outgoingRequests.collectAsState()
    val answers by viewModel.answers.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val message by viewModel.message.collectAsState()

    val inviteToken by viewModel.incomingInviteToken.collectAsState()
    val pendingInvite by viewModel.pendingInvite.collectAsState()

    var tab by remember { mutableStateOf(TogetherTab.People) }
    var qrOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }
    // a tapped myndora://connect link lands here, resolve it into a confirmation
    LaunchedEffect(inviteToken) {
        inviteToken?.let { viewModel.onInviteLinkOpened(it) }
    }
    // land on Requests the first time something needs answering
    LaunchedEffect(incoming.isNotEmpty()) {
        if (incoming.isNotEmpty()) tab = TogetherTab.Requests
    }

    val pendingCount = incoming.size + outgoing.size

    TogetherScaffold(
        title = stringResource(R.string.together_title),
        onBack = onBack,
        trailing = {
            IconButton(onClick = { qrOpen = true }) {
                Icon(
                    Icons.Rounded.QrCode2,
                    contentDescription = stringResource(R.string.together_qr_open),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) {
        TogetherStatsHero(
            connections = connections,
            pendingRequests = incoming.size,
        )

        Button(
            onClick = onAddSomeone,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(18.dp),
        ) {
            Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.together_add_someone), fontWeight = FontWeight.Bold)
        }

        SegmentedTabs(
            selected = tab,
            peopleCount = connections.size,
            requestCount = pendingCount,
            attention = incoming.isNotEmpty(),
            onSelect = { tab = it },
        )

        when (tab) {
            TogetherTab.People -> when {
                connections.isNotEmpty() -> {
                    for (connection in connections) {
                        ConnectionCard(
                            connection = connection,
                            onClick = { onOpenConnection(connection.userId) },
                        )
                    }
                }
                refreshing -> LoadingBlock()
                else -> EmptyPeopleState(onAddSomeone = onAddSomeone)
            }

            TogetherTab.Requests -> {
                if (incoming.isEmpty() && outgoing.isEmpty()) {
                    EmptyRequestsState()
                }
                if (incoming.isNotEmpty()) {
                    TogetherSectionHeader(
                        title = stringResource(R.string.together_waiting_for_you),
                        accent = MaterialTheme.colorScheme.tertiary,
                    )
                    for (request in incoming) {
                        IncomingRequestCard(
                            request = request,
                            state = answers[request.id] ?: AnswerState(),
                            onAnswerChange = { viewModel.onAnswerChange(request.id, it) },
                            onSubmit = { viewModel.submitAnswer(request.id) },
                            onDecline = { viewModel.declineRequest(request.id) },
                        )
                    }
                }
                if (outgoing.isNotEmpty()) {
                    TogetherSectionHeader(
                        title = stringResource(R.string.together_sent_by_you),
                        accent = MaterialTheme.colorScheme.outline,
                    )
                    for (request in outgoing) {
                        OutgoingRequestCard(
                            request = request,
                            onCancel = { viewModel.cancelOutgoing(request.id) },
                        )
                    }
                }
            }
        }

        message?.let { text ->
            LaunchedEffect(text) {
                kotlinx.coroutines.delay(2600)
                viewModel.consumeMessage()
            }
            InlineToast(text)
        }
    }

    if (qrOpen) MyQrCodeSheet(onDismiss = { qrOpen = false })

    pendingInvite?.let { pending ->
        InviteConfirmationDialog(
            pending = pending,
            onAccept = viewModel::acceptPendingInvite,
            onDismiss = viewModel::dismissPendingInvite,
        )
    }
}

// shown before redeeming, never after: an invite is single-use, so accepting the wrong link
// silently burns it and leaves the sender wondering why nothing happened
@Composable
private fun InviteConfirmationDialog(
    pending: PendingInvite,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val preview = pending.preview
    val usable = preview?.valid == true

    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!pending.accepting) onDismiss() },
        shape = RoundedCornerShape(26.dp),
        title = {
            Text(
                when {
                    pending.loading -> stringResource(R.string.together_opening_invite)
                    usable -> stringResource(R.string.together_invited_you, preview.name)
                    else -> stringResource(R.string.together_invite_unusable)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (pending.loading) {
                    CircularProgressIndicator(modifier = Modifier.size(26.dp), strokeWidth = 2.dp)
                    return@Column
                }
                if (usable) {
                    TogetherAvatar(name = preview.name, avatarUrl = preview.avatarUrl, size = 64.dp)
                    Spacer(Modifier.height(12.dp))
                    preview.username?.let {
                        Text(
                            "@$it",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        stringResource(R.string.together_invite_body, stringResource(preview.relation.labelRes).lowercase()),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                pending.error?.let {
                    if (usable) Spacer(Modifier.height(10.dp))
                    Text(
                        it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (usable) colors.error else colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {
            if (usable) {
                Button(
                    onClick = onAccept,
                    enabled = !pending.accepting,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    if (pending.accepting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.together_connect), fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_close)) }
            }
        },
        dismissButton = {
            if (usable) {
                TextButton(onClick = onDismiss, enabled = !pending.accepting) { Text(stringResource(R.string.common_not_now)) }
            }
        },
    )
}

// gradient header with an avatar stack and the two counts worth knowing at a glance: how many
// people are here, and how many are waiting on you. per-scope tallies belong on each profile,
// next to the switches that change them
@Composable
private fun TogetherStatsHero(
    connections: List<Connection>,
    pendingRequests: Int,
) {
    val colors = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.tertiary.copy(alpha = .26f),
                        colors.surfaceContainer,
                        colors.primaryContainer.copy(alpha = .42f),
                    ),
                ),
            )
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (connections.isEmpty()) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(colors.surface.copy(alpha = .72f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Diversity3,
                        contentDescription = null,
                        tint = colors.tertiary,
                        modifier = Modifier.size(27.dp),
                    )
                }
            } else {
                TogetherAvatarStack(connections = connections)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (connections.isEmpty()) stringResource(R.string.together_your_people) else stringResource(R.string.together_small_world),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = when {
                        pendingRequests > 0 ->
                            if (pendingRequests == 1) stringResource(R.string.together_waiting_one)
                            else stringResource(R.string.together_waiting_many, pendingRequests)
                        connections.isEmpty() ->
                            stringResource(R.string.together_connect_hint)
                        else ->
                            connections.take(3).joinToString(", ") { it.name }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        // two counts, not five. the sharing tallies restated what each connection card already shows
        if (connections.isNotEmpty() || pendingRequests > 0) {
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(stringResource(R.string.together_stat_connected), connections.size, Modifier.weight(1f))
                StatTile(stringResource(R.string.together_stat_pending), pendingRequests, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surface.copy(alpha = .62f))
            .padding(vertical = 12.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$value",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            fontSize = 10.sp,
        )
    }
}

// overlapping faces, capped so the row can't outgrow its container
@Composable
private fun TogetherAvatarStack(connections: List<Connection>) {
    val colors = MaterialTheme.colorScheme
    val shown = connections.take(3)
    val overflow = connections.size - shown.size
    Row(horizontalArrangement = Arrangement.spacedBy((-14).dp)) {
        shown.forEach { connection ->
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .padding(2.dp),
            ) {
                TogetherAvatar(
                    name = connection.name,
                    avatarUrl = connection.avatarUrl,
                    size = 44.dp,
                )
            }
        }
        if (overflow > 0) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.surface)
                    .padding(2.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .background(colors.onSurface.copy(alpha = .10f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "+$overflow",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// two-up switch with counts. the dot marks the tab that needs action
@Composable
private fun SegmentedTabs(
    selected: TogetherTab,
    peopleCount: Int,
    requestCount: Int,
    attention: Boolean,
    onSelect: (TogetherTab) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceVariant.copy(alpha = .40f))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TabPill(
            label = stringResource(TogetherTab.People.labelRes),
            count = peopleCount,
            selected = selected == TogetherTab.People,
            showDot = false,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(TogetherTab.People) },
        )
        TabPill(
            label = stringResource(TogetherTab.Requests.labelRes),
            count = requestCount,
            selected = selected == TogetherTab.Requests,
            showDot = attention,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(TogetherTab.Requests) },
        )
    }
}

@Composable
private fun TabPill(
    label: String,
    count: Int,
    selected: Boolean,
    showDot: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val elevation by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = tween(180),
        label = "tabPill",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(colors.surface.copy(alpha = elevation))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (count > 0) "$label · $count" else label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.onSurface else colors.onSurfaceVariant,
        )
        if (showDot) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(colors.tertiary),
            )
        }
    }
}

@Composable
private fun LoadingBlock() {
    Box(Modifier.fillMaxWidth().padding(28.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
    }
}

@Composable
private fun EmptyPeopleState(onAddSomeone: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    TogetherCard {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.tertiary.copy(alpha = .14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Rounded.Diversity3,
                contentDescription = null,
                tint = colors.tertiary,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.together_nobody_yet),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.together_nobody_body),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onAddSomeone, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Rounded.PersonAdd, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(7.dp))
            Text(stringResource(R.string.together_invite_contact))
        }
    }
}

@Composable
private fun EmptyRequestsState() {
    TogetherCard {
        Text(
            stringResource(R.string.together_no_requests),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.together_no_requests_body),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InlineToast(text: String) {
    val colors = MaterialTheme.colorScheme
    AnimatedVisibility(
        visible = true,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.inverseSurface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.inverseOnSurface,
            )
        }
    }
}

// a request plus its challenge. the question shows as the sender wrote it, and the answer
// field is the only way through, there is deliberately no 'accept anyway'
@Composable
private fun IncomingRequestCard(
    request: IncomingRequest,
    state: AnswerState,
    onAnswerChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDecline: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    TogetherCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TogetherAvatar(name = request.name, avatarUrl = request.avatarUrl, size = 50.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    request.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                val wantsToConnect = stringResource(
                    R.string.together_wants_connect,
                    stringResource(request.relation.labelRes).lowercase(),
                )
                Text(
                    buildString {
                        request.username?.let { append("@$it • ") }
                        append(wantsToConnect)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (request.locked) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = colors.error,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.together_locked_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(onClick = onDecline) { Text(stringResource(R.string.common_dismiss)) }
            return@TogetherCard
        }

        // visually set apart, it's a quote from the sender rather than our own copy
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.tertiary.copy(alpha = .10f))
                .padding(14.dp),
        ) {
            Text(
                stringResource(R.string.together_their_question),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colors.tertiary,
                fontSize = 9.5.sp,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                request.prompt.resolve(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = state.answer,
            onValueChange = onAnswerChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(R.string.together_your_answer)) },
            singleLine = true,
            isError = state.error != null,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                imeAction = ImeAction.Done,
            ),
        )

        state.error?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error)
        }

        if (state.error == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.together_answer_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onSubmit,
                enabled = state.answer.isNotBlank() && !state.checking,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (state.checking) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text(stringResource(R.string.together_accept), fontWeight = FontWeight.Bold)
                }
            }
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(stringResource(R.string.together_decline))
            }
        }
    }
}

@Composable
private fun ConnectionCard(connection: Connection, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accent = connection.relation.accent()

    TogetherCard(onClick = onClick, accent = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // faded, not hidden: they're still a connection, just away for now
            Box(Modifier.alpha(if (connection.deactivated) .45f else 1f)) {
                TogetherAvatar(
                    name = connection.name,
                    avatarUrl = connection.avatarUrl,
                    size = 54.dp,
                    ring = accent,
                )
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    connection.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RelationChip(relation = connection.relation, accent = accent)
                    if (connection.deactivated) {
                        Spacer(Modifier.width(6.dp))
                        DeactivatedChip()
                    }
                    connection.username?.takeIf { it.isNotBlank() && !connection.deactivated }?.let {
                        Spacer(Modifier.width(7.dp))
                        Text(
                            "@$it",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // a target rather than a bare glyph: the chevron used to float against the card edge with
            // nothing to sit in, which read as decoration instead of 'there is more of this person here'
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = .12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

    }
}

// neutral grey on purpose: deactivated is a pause, not an error
@Composable
internal fun DeactivatedChip() {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(colors.onSurfaceVariant.copy(alpha = .14f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            stringResource(R.string.together_deactivated),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurfaceVariant,
            fontSize = 9.5.sp,
        )
    }
}

@Composable
private fun RelationChip(relation: ConnectionRelation, accent: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .background(accent.copy(alpha = .15f))
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(
            stringResource(relation.labelRes),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            fontSize = 9.5.sp,
        )
    }
}

// a hue per relation, so a scrolled list has shape to it rather than being one repeated grey
// card. drawn from theme roles rather than fixed colours, so it holds up in every theme
@Composable
private fun ConnectionRelation.accent(): Color = when (this) {
    ConnectionRelation.PARTNER -> MaterialTheme.colorScheme.tertiary
    ConnectionRelation.FAMILY -> MaterialTheme.colorScheme.secondary
    ConnectionRelation.FRIEND -> MaterialTheme.colorScheme.primary
    ConnectionRelation.OTHER -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun OutgoingRequestCard(request: OutgoingRequest, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    TogetherCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TogetherAvatar(name = request.name, avatarUrl = request.avatarUrl, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    request.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (request.locked) Icons.Rounded.Lock else Icons.Rounded.HourglassTop,
                        contentDescription = null,
                        tint = if (request.locked) colors.error else colors.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (request.locked) {
                            stringResource(R.string.together_out_of_tries)
                        } else {
                            stringResource(R.string.together_waiting_answer)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (request.locked) colors.error else colors.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onCancel) { Text(stringResource(R.string.together_withdraw)) }
        }
    }
}
