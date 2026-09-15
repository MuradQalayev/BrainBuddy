package com.muradgalayev.brainbuddy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// wire shapes for the Myndora Together RPCs. each mirrors one function's RETURNS TABLE in the
// 20260808 migration, so keep them in step if that file changes.
// note what's absent: no DTO carries challenge_answer_hash. the addressee has no SELECT policy
// on connection_requests at all, so the hash never reaches a client, and answers are checked
// inside respond_to_connection_request()

@Serializable
data class ConnectionDto(
    @SerialName("connection_id") val connectionId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("display_name") val displayName: String? = null,
    val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val relation: String = "OTHER",
    @SerialName("granted_by_me") val grantedByMe: List<String> = emptyList(),
    @SerialName("granted_to_me") val grantedToMe: List<String> = emptyList(),
    @SerialName("connected_at") val connectedAt: String? = null,
)

// one row of list_deactivated_connections()
@Serializable
data class DeactivatedUserDto(
    @SerialName("user_id") val userId: String,
)

@Serializable
data class IncomingRequestDto(
    val id: String,
    @SerialName("requester_id") val requesterId: String,
    @SerialName("display_name") val displayName: String? = null,
    val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val relation: String = "OTHER",
    @SerialName("challenge_kind") val challengeKind: String = "QUESTION",
    @SerialName("challenge_question") val challengeQuestion: String? = null,
    @SerialName("attempts_left") val attemptsLeft: Int = 0,
    val status: String = "PENDING",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class OutgoingRequestDto(
    val id: String,
    @SerialName("addressee_id") val addresseeId: String,
    @SerialName("display_name") val displayName: String? = null,
    val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val relation: String = "OTHER",
    val status: String = "PENDING",
    @SerialName("created_at") val createdAt: String? = null,
)

// return of respond_to_connection_request()
@Serializable
data class ChallengeResponseDto(
    val ok: Boolean = false,
    val status: String = "PENDING",
    @SerialName("attempts_left") val attemptsLeft: Int = 0,
)

// the wellness snapshot a connection shared with me, read from profile_shares
@Serializable
data class SharedWellnessDto(
    @SerialName("patient_id") val patientId: String,
    @SerialName("shared_steps_7d") val steps7d: Long? = null,
    @SerialName("shared_last_sleep_hours") val lastSleepHours: Double? = null,
    @SerialName("shared_exercise_minutes_7d") val exerciseMinutes7d: Long? = null,
    @SerialName("shared_resting_heart_rate_bpm") val restingHeartRateBpm: Long? = null,
    @SerialName("shared_latest_heart_rate_bpm") val latestHeartRateBpm: Long? = null,
    @SerialName("shared_calories_today") val caloriesToday: Long? = null,
    @SerialName("wellness_period_ended_at") val periodEndedAt: String? = null,
)

// return of create_connection_invite(). the token is shown once, then shared
@Serializable
data class InviteTokenDto(
    val token: String,
    @SerialName("expires_at") val expiresAt: String? = null,
)

// return of peek_connection_invite(): who is inviting, and is the link usable
@Serializable
data class InvitePreviewDto(
    @SerialName("inviter_id") val inviterId: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    val username: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    val relation: String = "OTHER",
    val valid: Boolean = false,
    // why it can't be used, when valid is false
    val reason: String? = null,
)

// Server match for one client-generated SHA-256 E.164 phone hash. No raw number or contact name
// crosses the API boundary.
@Serializable
data class ContactMatchDto(
    @SerialName("user_id") val userId: String,
    @SerialName("phone_hash") val phoneHash: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

// one merged busy block from myndora_free_busy(). three numbers and nothing else,
// deliberately, since this is the shape a connection's day is allowed to have when they've
// granted AVAILABILITY. no title field to accidentally start reading, and no id to correlate
// blocks across days
@Serializable
data class FreeBusySlotDto(
    val day: String,
    @SerialName("start_minute") val startMinute: Int,
    @SerialName("end_minute") val endMinute: Int,
)
