package com.muradgalayev.brainbuddy.data.mapper

import com.muradgalayev.brainbuddy.ui.utils.resolve
import com.muradgalayev.brainbuddy.testing.TestStrings
import com.muradgalayev.brainbuddy.data.remote.dto.ChallengeResponseDto
import com.muradgalayev.brainbuddy.data.remote.dto.ConnectionDto
import com.muradgalayev.brainbuddy.data.remote.dto.IncomingRequestDto
import com.muradgalayev.brainbuddy.data.remote.dto.OutgoingRequestDto
import com.muradgalayev.brainbuddy.domain.model.ChallengeKind
import com.muradgalayev.brainbuddy.domain.model.ChallengeResult
import com.muradgalayev.brainbuddy.domain.model.ConnectionRelation
import com.muradgalayev.brainbuddy.domain.model.ShareScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// wire to domain decoding for Myndora Together. these guard the boundary where the RPCs' text
// columns become typed values. the interesting cases are the malformed ones: the DB can gain
// an enum value before the app knows about it, and a screen must degrade rather than crash
class TogetherMapperTest {

    // connections

    @Test
    fun `connection maps both permission directions independently`() {
        val dto = ConnectionDto(
            connectionId = "c1",
            userId = "u2",
            displayName = "Aylin",
            username = "aylin",
            relation = "PARTNER",
            grantedByMe = listOf("CALENDAR", "TODOS"),
            grantedToMe = listOf("WELLNESS"),
        )

        val connection = dto.toDomain()

        assertEquals(setOf(ShareScope.CALENDAR, ShareScope.TODOS), connection.grantedByMe)
        assertEquals(setOf(ShareScope.WELLNESS), connection.grantedToMe)
        assertEquals(ConnectionRelation.PARTNER, connection.relation)
    }

    @Test
    fun `granting a scope one way does not imply the other way`() {
        val dto = ConnectionDto(
            connectionId = "c1",
            userId = "u2",
            grantedByMe = listOf("CALENDAR"),
            grantedToMe = emptyList(),
        )

        val connection = dto.toDomain()

        // I opened my calendar to them, and that must not let me write to theirs
        assertTrue(connection.canGrant(ShareScope.CALENDAR))
        assertFalse(connection.canUse(ShareScope.CALENDAR))
    }

    @Test
    fun `unknown scope from a newer database is dropped, not fatal`() {
        val dto = ConnectionDto(
            connectionId = "c1",
            userId = "u2",
            grantedByMe = listOf("CALENDAR", "TELEPORTATION"),
        )

        val connection = dto.toDomain()

        assertEquals(setOf(ShareScope.CALENDAR), connection.grantedByMe)
    }

    @Test
    fun `unknown relation falls back to OTHER`() {
        val dto = ConnectionDto(connectionId = "c1", userId = "u2", relation = "SOULMATE")

        assertEquals(ConnectionRelation.OTHER, dto.toDomain().relation)
    }

    @Test
    fun `relation matching ignores case`() {
        val dto = ConnectionDto(connectionId = "c1", userId = "u2", relation = "family")

        assertEquals(ConnectionRelation.FAMILY, dto.toDomain().relation)
    }

    @Test
    fun `name falls back through username to a generic label`() {
        val withName = ConnectionDto(connectionId = "c", userId = "u", displayName = "Aylin")
        val blankName = ConnectionDto(connectionId = "c", userId = "u", displayName = "  ", username = "aylin")
        val neither = ConnectionDto(connectionId = "c", userId = "u")

        assertEquals("Aylin", withName.toDomain().name)
        assertEquals("aylin", blankName.toDomain().name)
        assertEquals("Myndora user", neither.toDomain().name)
    }

    // incoming requests

    @Test
    fun `incoming question request keeps the sender's wording as the prompt`() {
        val dto = IncomingRequestDto(
            id = "r1",
            requesterId = "u2",
            displayName = "Aylin",
            challengeKind = "QUESTION",
            challengeQuestion = "What is my surname?",
            attemptsLeft = 5,
        )

        val request = dto.toDomain()

        assertEquals(ChallengeKind.QUESTION, request.challengeKind)
        assertEquals("What is my surname?", request.prompt.resolve(TestStrings.en))
    }

    @Test
    fun `code request with no question gets a generated prompt naming the sender`() {
        val dto = IncomingRequestDto(
            id = "r1",
            requesterId = "u2",
            displayName = "Aylin",
            challengeKind = "CODE",
            challengeQuestion = null,
        )

        assertEquals("Enter the code Aylin gave you", dto.toDomain().prompt.resolve(TestStrings.en))
    }

    @Test
    fun `blank question falls back to the code prompt rather than showing nothing`() {
        val dto = IncomingRequestDto(
            id = "r1",
            requesterId = "u2",
            displayName = "Aylin",
            challengeKind = "QUESTION",
            challengeQuestion = "   ",
        )

        assertEquals("Enter the code Aylin gave you", dto.toDomain().prompt.resolve(TestStrings.en))
    }

    @Test
    fun `locked status is surfaced so the card can stop offering an answer field`() {
        val pending = IncomingRequestDto(id = "r", requesterId = "u", status = "PENDING")
        val locked = IncomingRequestDto(id = "r", requesterId = "u", status = "LOCKED")

        assertFalse(pending.toDomain().locked)
        assertTrue(locked.toDomain().locked)
    }

    @Test
    fun `outgoing request carries locked state for the withdraw prompt`() {
        val dto = OutgoingRequestDto(id = "r", addresseeId = "u", status = "LOCKED")

        assertTrue(dto.toDomain().locked)
    }

    // ── Challenge outcome ──

    @Test
    fun `correct answer is accepted`() {
        val dto = ChallengeResponseDto(ok = true, status = "ACCEPTED", attemptsLeft = 5)

        assertEquals(ChallengeResult.Accepted, dto.toChallengeResult())
    }

    @Test
    fun `wrong answer reports the remaining budget instead of failing`() {
        val dto = ChallengeResponseDto(ok = false, status = "PENDING", attemptsLeft = 3)

        assertEquals(ChallengeResult.Wrong(3), dto.toChallengeResult())
    }

    @Test
    fun `running out of attempts locks rather than reporting another wrong answer`() {
        val dto = ChallengeResponseDto(ok = false, status = "LOCKED", attemptsLeft = 0)

        assertEquals(ChallengeResult.Locked, dto.toChallengeResult())
    }

    @Test
    fun `a negative attempt count never reaches the UI`() {
        // Defensive: the UI pluralises off this number, and "-1 tries left" would be
        // a nonsense string rather than a crash, which is harder to notice.
        val dto = ChallengeResponseDto(ok = false, status = "PENDING", attemptsLeft = -2)

        assertEquals(ChallengeResult.Wrong(0), dto.toChallengeResult())
    }
}
