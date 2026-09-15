package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.muradgalayev.brainbuddy.data.contacts.DeviceContactsRepository
import com.muradgalayev.brainbuddy.data.health.HealthConnectManager
import com.muradgalayev.brainbuddy.data.mapper.toChallengeResult
import com.muradgalayev.brainbuddy.data.mapper.toBusyIntervalsByDate
import com.muradgalayev.brainbuddy.data.mapper.toDomain
import com.muradgalayev.brainbuddy.data.remote.SupabaseTogetherDataSource
import com.muradgalayev.brainbuddy.data.remote.dto.CalendarEventDto
import com.muradgalayev.brainbuddy.data.remote.dto.SharedWellnessDto
import com.muradgalayev.brainbuddy.data.remote.dto.TodoItemDto
import com.muradgalayev.brainbuddy.domain.model.ChallengeResult
import com.muradgalayev.brainbuddy.domain.model.Connection
import com.muradgalayev.brainbuddy.domain.model.ConnectionRelation
import com.muradgalayev.brainbuddy.domain.model.IncomingRequest
import com.muradgalayev.brainbuddy.domain.model.InviteLink
import com.muradgalayev.brainbuddy.domain.model.InvitePreview
import com.muradgalayev.brainbuddy.domain.model.MyndoraContact
import com.muradgalayev.brainbuddy.domain.model.OutgoingRequest
import com.muradgalayev.brainbuddy.domain.model.ShareScope
import com.muradgalayev.brainbuddy.domain.scheduling.BusyInterval
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import com.muradgalayev.brainbuddy.R

// how long a sender keeps checking for their invite being accepted, and how often. long enough to
// cover walking over and tapping accept, short enough not to poll all day for a link sent in a chat
private const val ACCEPTANCE_WATCH_MS = 10 * 60_000L
private const val ACCEPTANCE_POLL_MS = 4_000L

// Myndora Together: connections, follow requests with a verification challenge, and the
// per-scope permissions each side grants.
// Together state is small, remote-only, and always read fresh, with no Room mirror. caching a
// permission locally would mean a revoke could sit unnoticed on this device, and 'she can
// still add to my calendar an hour after I turned it off' is exactly the bug worth avoiding.
// what is cached is the last successful read, purely so the screen can seed instantly instead
// of flashing empty.
// the server is the authority on every rule here. the app never decides whether a write is
// allowed, it attempts it and RLS accepts or rejects
@Singleton
class TogetherRepository @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val remote: SupabaseTogetherDataSource,
    private val authRepository: AuthRepository,
    private val profileSharingRepository: ProfileSharingRepository,
    private val healthConnectManager: HealthConnectManager,
    private val deviceContactsRepository: DeviceContactsRepository,
    private val togetherNotifier: com.muradgalayev.brainbuddy.data.notifications.TogetherNotifier,
) {
    // accepting an invite happens on the other phone and the server pushes nothing back, so after
    // minting one the sender watches its own connection list for a while and announces whoever turns up
    private val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var acceptanceWatch: Job? = null
    private val _newlyConnected = MutableSharedFlow<Connection>(extraBufferCapacity = 4)
    val newlyConnected: SharedFlow<Connection> = _newlyConnected.asSharedFlow()

    // people this user accepted themselves. they're new connections too, but nobody accepted anything of ours
    private val selfAccepted = mutableSetOf<String>()

    private val _connections = MutableStateFlow<List<Connection>>(emptyList())
    val connections: StateFlow<List<Connection>> = _connections.asStateFlow()

    private val _incoming = MutableStateFlow<List<IncomingRequest>>(emptyList())
    val incomingRequests: StateFlow<List<IncomingRequest>> = _incoming.asStateFlow()

    private val _outgoing = MutableStateFlow<List<OutgoingRequest>>(emptyList())
    val outgoingRequests: StateFlow<List<OutgoingRequest>> = _outgoing.asStateFlow()

    // true once a refresh has landed, so the UI can tell empty from not loaded
    @Volatile
    var hasLoadedOnce: Boolean = false
        private set

    // reads

    // pulls connections and both request queues. each is independent, so one failing (the user is
    // offline mid-scroll, say) leaves the others intact rather than blanking the whole screen
    suspend fun refresh(): Result<Unit> = runCatching {
        if (authRepository.getCurrentUserId() == null) return@runCatching
        runCatching { fetchConnections() }
            .onSuccess { _connections.value = it }
            .onFailure { Log.w(TAG, "listConnections failed (${it::class.java.simpleName})") }
        runCatching { remote.listIncomingRequests().map { it.toDomain() } }
            .onSuccess { _incoming.value = it }
            .onFailure { Log.w(TAG, "listIncomingRequests failed (${it::class.java.simpleName})") }
        runCatching { remote.listOutgoingRequests().map { it.toDomain() } }
            .onSuccess { _outgoing.value = it }
            .onFailure { Log.w(TAG, "listOutgoingRequests failed (${it::class.java.simpleName})") }
        hasLoadedOnce = true
    }

    fun connection(userId: String): Connection? = _connections.value.firstOrNull { it.userId == userId }

    // connections plus which of them are deactivated. the second read is best-effort: if it fails,
    // everyone shows as active rather than the whole list failing
    private suspend fun fetchConnections(): List<Connection> {
        val list = remote.listConnections().map { it.toDomain() }
        val deactivated = runCatching { remote.listDeactivatedConnections().toSet() }.getOrDefault(emptySet())
        return if (deactivated.isEmpty()) list else list.map { it.copy(deactivated = it.userId in deactivated) }
    }

    // Matches local numbers to verified auth.users phones. Only SHA-256 hashes cross the network;
    // names and raw numbers are joined back onto the result inside this process.
    suspend fun discoverMyndoraContacts(): Result<List<MyndoraContact>> = runCatching {
        val localContacts = deviceContactsRepository.loadPhoneContacts()
        if (localContacts.isEmpty()) return@runCatching emptyList()

        val localByHash = localContacts.associateBy { it.phoneHash }
        val connectedIds = _connections.value.mapTo(mutableSetOf()) { it.userId }
        remote.matchContacts(localByHash.keys.toList()).mapNotNull { match ->
            localByHash[match.phoneHash]?.let { local ->
                MyndoraContact(
                    userId = match.userId,
                    contactName = local.displayName,
                    phoneNumber = local.phoneNumber,
                    avatarUrl = match.avatarUrl,
                    isConnected = match.userId in connectedIds,
                )
            }
        }.distinctBy { it.userId }
            .sortedBy { it.contactName.lowercase() }
    }

    // answers a challenge. a wrong answer is a normal outcome, not an error: it comes back as
    // ChallengeResult.Wrong with the remaining budget, so the UI can say '3 tries left'
    suspend fun answerChallenge(requestId: String, answer: String): Result<ChallengeResult> =
        runCatching {
            val result = remote.respondToRequest(requestId, answer).toChallengeResult()
            refresh()
            result
        }

    suspend fun declineRequest(requestId: String): Result<Unit> = runCatching {
        remote.declineRequest(requestId)
        refresh()
    }.mapCatching { }

    suspend fun cancelRequest(requestId: String): Result<Unit> = runCatching {
        remote.cancelOutgoingRequest(requestId)
        refresh()
    }.mapCatching { }

    // invite links

    // a token from a tapped myndora://connect link, waiting for the Together screen to pick it up.
    // held here rather than passed as a nav argument, so the link survives the app being
    // cold-started by the tap, before any nav graph exists to receive it
    private val _incomingInviteToken = MutableStateFlow<String?>(null)
    val incomingInviteToken: StateFlow<String?> = _incomingInviteToken.asStateFlow()

    // called from the activity for every deep link, ignores anything not ours
    fun onDeepLink(url: String?) {
        tokenFromUrl(url)?.let { _incomingInviteToken.value = it }
    }

    // for the 'Have an invite?' field. most chat apps won't make a myndora:// link tappable, so the
    // person invited copies the message and pastes it. false when there's no invite in it
    fun openPastedInvite(text: String): Boolean {
        val token = tokenFromPastedText(text) ?: return false
        _incomingInviteToken.value = token
        return true
    }

    // read-and-clear, so one tapped link opens one confirmation
    fun consumeInviteToken(): String? {
        val token = _incomingInviteToken.value
        _incomingInviteToken.value = null
        return token
    }

    // mints a single-use invite and returns the shareable link. this replaced the
    // security-question challenge: a question like 'what is my surname?' is knowledge-based
    // authentication and the answer is usually public, which is why NIST SP 800-63B dropped KBA
    // as an authenticator. a random 128-bit token carried over a channel the two people already
    // trust is an actual secret, and it expires
    suspend fun createInviteLink(
        relation: ConnectionRelation,
        validHours: Int = DEFAULT_INVITE_HOURS,
    ): Result<InviteLink> = runCatching {
        val dto = remote.createInvite(relation.name, validHours)
        watchForAcceptance()
        InviteLink(token = dto.token, url = inviteUrl(dto.token), expiresAt = dto.expiresAt)
    }

    // the first successful read is the baseline, never the cached list: a stale cache would announce
    // someone who connected days ago as if they had just accepted
    private fun watchForAcceptance() {
        acceptanceWatch?.cancel()
        acceptanceWatch = watchScope.launch {
            var known: Set<String>? = null
            val until = System.currentTimeMillis() + ACCEPTANCE_WATCH_MS
            while (System.currentTimeMillis() < until) {
                val list = runCatching { fetchConnections() }.getOrNull()
                if (list != null) {
                    known?.let { seen ->
                        list.filter { it.userId !in seen && it.userId !in selfAccepted }
                            .forEach(::announceConnected)
                    }
                    known = list.mapTo(mutableSetOf()) { it.userId }
                    if (_connections.value != list) _connections.value = list
                }
                delay(ACCEPTANCE_POLL_MS)
            }
        }
    }

    // a banner when someone in the app is listening, a notification when nobody is
    private fun announceConnected(connection: Connection) {
        if (_newlyConnected.subscriptionCount.value > 0) {
            _newlyConnected.tryEmit(connection)
        } else {
            togetherNotifier.notifyConnected(connection.name, connection.userId)
        }
    }

    // who is inviting me, without consuming the invite
    suspend fun peekInvite(token: String): Result<InvitePreview> = runCatching {
        val dto = remote.peekInvite(token)
            ?: return@runCatching InvitePreview(
                valid = false,
                reason = context.getString(R.string.together_invite_invalid),
            )
        InvitePreview(
            inviterId = dto.inviterId,
            name = dto.displayName?.takeIf { it.isNotBlank() }
                ?: dto.username?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.together_a_user),
            username = dto.username,
            avatarUrl = dto.avatarUrl,
            relation = ConnectionRelation.fromRemote(dto.relation),
            valid = dto.valid,
            reason = dto.reason,
        )
    }

    suspend fun redeemInvite(token: String): Result<Unit> = runCatching {
        remote.redeemInvite(token)
        refresh()
        _connections.value.mapTo(selfAccepted) { it.userId }
    }.mapCatching { }

    suspend fun revokeInviteLinks(): Result<Unit> = runCatching { remote.revokeInvites() }

    // permissions

    // grants or revokes one scope for one connection. turning WELLNESS on also pushes a fresh
    // snapshot, because the row in profile_shares is what the other side actually reads and a
    // grant with no row behind it would show them an empty card. turning it off revokes the row
    // as well as the grant, so the data stops being readable rather than just going stale
    suspend fun setPermission(
        granteeId: String,
        scope: ShareScope,
        granted: Boolean,
    ): Result<Unit> = runCatching {
        remote.setPermission(granteeId, scope.name, granted)
        if (scope == ShareScope.WELLNESS && granted) {
            runCatching { pushWellnessSnapshot(granteeId) }
                .onFailure { Log.w(TAG, "Wellness snapshot push failed (${it::class.java.simpleName})") }
        }
        refresh()
    }.mapCatching { }

    // builds the snapshot from Health Connect's current state. only aggregates leave the device,
    // never raw records, which is the same line the AI personalisation path draws
    private suspend fun pushWellnessSnapshot(recipientId: String) {
        healthConnectManager.refresh()
        val health = healthConnectManager.state.value
        val snapshot = SharedWellnessSnapshot(
            stepsLast7Days = health.stepsLast7Days,
            lastSleepHours = health.lastSleepHours,
            exerciseMinutesLast7Days = health.exerciseMinutesThisWeek,
            restingHeartRateBpm = health.restingHeartRateBpm,
            latestHeartRateBpm = health.latestHeartRateBpm,
            caloriesBurnedToday = health.caloriesBurnedToday,
        )
        profileSharingRepository.shareProfileWith(recipientId, snapshot).getOrThrow()
    }

    // refreshes an already-granted snapshot so the other side isn't reading last week
    suspend fun refreshWellnessSnapshots(): Result<Unit> = runCatching {
        val recipients = _connections.value
            .filter { ShareScope.WELLNESS in it.grantedByMe }
            .map { it.userId }
        for (recipient in recipients) {
            runCatching { pushWellnessSnapshot(recipient) }
                .onFailure { Log.w(TAG, "Snapshot refresh failed (${it::class.java.simpleName})") }
        }
    }

    suspend fun removeConnection(userId: String): Result<Unit> = runCatching {
        remote.removeConnection(userId)
        refresh()
    }.mapCatching { }

    // acting on a connection's data

    // puts an event on someone else's calendar. created_by is set to me, which is both what the
    // INSERT policy checks and what keeps my later reads of this event scoped to the ones I
    // authored. fails if they haven't granted CALENDAR, and the rejection comes from RLS rather
    // than from a check here, so a stale local permission can't slip a write through
    suspend fun createEventFor(
        ownerId: String,
        title: String,
        description: String,
        startTime: LocalDateTime,
        endTime: LocalDateTime,
        location: String = "",
        color: String = "blue",
    ): Result<Unit> = runCatching {
        val me = authRepository.getCurrentUserId() ?: error("Sign in to add to their calendar")
        remote.insertEventForOther(
            CalendarEventDto(
                id = UUID.randomUUID().toString(),
                userId = ownerId,
                title = title,
                description = description,
                startTime = startTime.toString(),
                endTime = endTime.toString(),
                location = location,
                color = color,
                createdBy = me,
            ),
        )
        pingOwner(ownerId, KIND_CALENDAR)
    }

    suspend fun createTodoFor(
        ownerId: String,
        title: String,
        description: String,
        date: String,
        startTime: String,
        endTime: String,
        priority: String = "MEDIUM",
    ): Result<Unit> = runCatching {
        val me = authRepository.getCurrentUserId() ?: error("Sign in to add to their list")
        remote.insertTodoForOther(
            TodoItemDto(
                id = UUID.randomUUID().toString(),
                userId = ownerId,
                title = title,
                description = description,
                date = date,
                startTime = startTime,
                endTime = endTime,
                priority = priority,
                createdBy = me,
            ),
        )
        pingOwner(ownerId, KIND_TODO)
    }

    // only ever returns events I authored, enforced by RLS rather than by this filter
    suspend fun eventsICreatedFor(ownerId: String): Result<List<CalendarEventDto>> = runCatching {
        remote.listEventsICreatedFor(ownerId)
    }

    suspend fun todosICreatedFor(ownerId: String): Result<List<TodoItemDto>> = runCatching {
        remote.listTodosICreatedFor(ownerId)
    }

    suspend fun deleteEventICreated(eventId: String): Result<Unit> = runCatching {
        val owner = remote.deleteEventIAuthored(eventId)
        if (owner != null) pingOwner(owner, KIND_CALENDAR)
    }

    suspend fun deleteTodoICreated(todoId: String): Result<Unit> = runCatching {
        val owner = remote.deleteTodoIAuthored(todoId)
        if (owner != null) pingOwner(owner, KIND_TODO)
    }

    // tells the owner's phones to sync now instead of on their next launch. off the caller's
    // coroutine and best-effort: the write already landed, a missed push only means the old delay
    private fun pingOwner(ownerId: String, kind: String) {
        watchScope.launch {
            runCatching { remote.notifyTogetherChange(ownerId, kind) }
                .onFailure { Log.w(TAG, "Change push not sent (${it::class.java.simpleName})") }
        }
    }

    // availability

    // when a connection is already busy, as opaque blocks: hours and nothing else. this is the
    // whole of what AVAILABILITY buys. it exists so putting an event on someone's calendar can
    // route around their day instead of landing on top of it, and it is deliberately incapable of
    // more: the RPC returns three numbers per block, toBusyIntervalsByDate marks each one opaque,
    // and the engine refuses to say anything about an opaque block beyond scheduling around it.
    // a failure (no grant, offline, revoked mid-session) comes back as a failed Result, never as
    // an empty map. the caller has to be able to tell 'they have nothing on' from 'we don't know',
    // because only one of those two justifies proposing a time
    suspend fun availabilityFor(
        ownerId: String,
        from: LocalDate,
        to: LocalDate,
    ): Result<Map<LocalDate, List<BusyInterval>>> = runCatching {
        remote.freeBusy(ownerId, from.toString(), to.toString()).toBusyIntervalsByDate()
    }

    // null when they haven't granted WELLNESS, the row simply isn't selectable
    suspend fun wellnessSharedWithMe(ownerId: String): Result<SharedWellnessDto?> = runCatching {
        remote.getSharedWellness(ownerId)
    }

    // clears cached Together state on sign-out so it can't bleed into the next session
    fun clear() {
        _connections.value = emptyList()
        _incoming.value = emptyList()
        _outgoing.value = emptyList()
        hasLoadedOnce = false
    }

    companion object {
        private const val TAG = "TogetherRepository"

        // long enough to send and be opened, short enough that a stale link is dead
        const val DEFAULT_INVITE_HOURS = 24

        // what changed, for the sync-now push. must match KINDS in notify-together-change
        const val KIND_CALENDAR = "calendar"
        const val KIND_TODO = "todo"

        // the deep link an invite travels as, handled by the myndora scheme filter in the manifest.
        // the existing brainbuddy:// auth callback is untouched, so Supabase and Google OAuth keep working
        fun inviteUrl(token: String): String = "myndora://connect?token=$token"

        // pulls the token back out of a tapped link. null when it isn't ours
        fun tokenFromUrl(url: String?): String? {
            val raw = url?.trim().orEmpty()
            if (!raw.startsWith("myndora://connect")) return null
            return raw.substringAfter("token=", "")
                .substringBefore('&')
                .takeIf { it.isNotBlank() }
        }

        // a pasted invite: our link anywhere in a forwarded message, or the bare token on its own.
        // still strict, a link to anything else or a stray word resolves to null. the server is
        // what decides whether the token is real
        fun tokenFromPastedText(text: String?): String? {
            val raw = text?.trim().orEmpty()
            if (raw.isEmpty()) return null
            INVITE_LINK.find(raw)?.let { return tokenFromUrl(it.value) }
            return raw.takeIf { BARE_TOKEN.matches(it) }
        }

        private val INVITE_LINK = Regex("""myndora://connect\S*""")

        // base64url, and at least as long as a 96-bit token so ordinary words never qualify
        private val BARE_TOKEN = Regex("""[A-Za-z0-9_-]{16,}""")

    }
}
