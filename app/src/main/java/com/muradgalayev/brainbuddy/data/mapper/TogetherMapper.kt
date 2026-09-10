package com.muradgalayev.brainbuddy.data.mapper

import com.muradgalayev.brainbuddy.data.remote.dto.ChallengeResponseDto
import com.muradgalayev.brainbuddy.data.remote.dto.ConnectionDto
import com.muradgalayev.brainbuddy.data.remote.dto.FreeBusySlotDto
import com.muradgalayev.brainbuddy.data.remote.dto.IncomingRequestDto
import com.muradgalayev.brainbuddy.data.remote.dto.OutgoingRequestDto
import com.muradgalayev.brainbuddy.domain.model.ChallengeKind
import com.muradgalayev.brainbuddy.domain.model.ChallengeResult
import com.muradgalayev.brainbuddy.domain.model.Connection
import com.muradgalayev.brainbuddy.domain.model.ConnectionRelation
import com.muradgalayev.brainbuddy.domain.model.IncomingRequest
import com.muradgalayev.brainbuddy.domain.model.OutgoingRequest
import com.muradgalayev.brainbuddy.domain.model.ShareScope
import com.muradgalayev.brainbuddy.domain.scheduling.BusyInterval
import com.muradgalayev.brainbuddy.domain.scheduling.MINUTES_PER_DAY
import java.time.LocalDate

// wire to domain for Myndora Together. unknown enum values are dropped rather than throwing:
// a scope added to the DB ahead of an app update should render as nothing extra, not crash

private fun List<String>.toScopes(): Set<ShareScope> =
    mapNotNull { ShareScope.fromRemote(it) }.toSet()

fun ConnectionDto.toDomain(): Connection = Connection(
    connectionId = connectionId,
    userId = userId,
    displayName = displayName,
    username = username,
    avatarUrl = avatarUrl,
    relation = ConnectionRelation.fromRemote(relation),
    grantedByMe = grantedByMe.toScopes(),
    grantedToMe = grantedToMe.toScopes(),
    connectedAt = connectedAt,
)

fun IncomingRequestDto.toDomain(): IncomingRequest = IncomingRequest(
    id = id,
    requesterId = requesterId,
    displayName = displayName,
    username = username,
    avatarUrl = avatarUrl,
    relation = ConnectionRelation.fromRemote(relation),
    challengeKind = if (challengeKind.equals("CODE", ignoreCase = true)) {
        ChallengeKind.CODE
    } else {
        ChallengeKind.QUESTION
    },
    challengeQuestion = challengeQuestion,
    attemptsLeft = attemptsLeft,
    locked = status.equals("LOCKED", ignoreCase = true),
)

// outcome of answering a challenge. a wrong answer is a normal result, not a failure: it
// carries the remaining attempt budget so the UI can say '3 tries left' instead of showing an
// error. only the server decides correctness, this just reads its verdict
fun ChallengeResponseDto.toChallengeResult(): ChallengeResult = when {
    ok -> ChallengeResult.Accepted
    status.equals("LOCKED", ignoreCase = true) -> ChallengeResult.Locked
    else -> ChallengeResult.Wrong(attemptsLeft.coerceAtLeast(0))
}

fun OutgoingRequestDto.toDomain(): OutgoingRequest = OutgoingRequest(
    id = id,
    addresseeId = addresseeId,
    displayName = displayName,
    username = username,
    avatarUrl = avatarUrl,
    relation = ConnectionRelation.fromRemote(relation),
    locked = status.equals("LOCKED", ignoreCase = true),
)

// a connection's shared free/busy, bucketed by date and marked opaque so the scheduling engine
// treats the blocks as walls it must not describe. the title is a constant rather than a
// redaction of something we received: no title ever crosses the wire.
// rows that can't be understood are dropped rather than guessed at, since a block we can't
// place is not one we can safely ignore or invent a position for, and dropping it only ever
// costs a suggestion
fun List<FreeBusySlotDto>.toBusyIntervalsByDate(): Map<LocalDate, List<BusyInterval>> =
    mapNotNull { slot ->
        val date = runCatching { LocalDate.parse(slot.day.take(10)) }.getOrNull()
            ?: return@mapNotNull null
        val start = slot.startMinute.coerceIn(0, MINUTES_PER_DAY)
        val end = slot.endMinute.coerceIn(0, MINUTES_PER_DAY)
        if (end <= start) return@mapNotNull null
        date to BusyInterval(
            startMinutes = start,
            endMinutes = end,
            title = BusyInterval.SHARED_LABEL,
            opaque = true,
        )
    }.groupBy({ it.first }, { it.second })
