package com.muradgalayev.brainbuddy.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

data class SharedWellnessSnapshot(
    val stepsLast7Days: Long? = null,
    val lastSleepHours: Double? = null,
    val exerciseMinutesLast7Days: Long? = null,
    val restingHeartRateBpm: Long? = null,
    val latestHeartRateBpm: Long? = null,
    val caloriesBurnedToday: Long? = null,
)

@Serializable
private data class ProfileShareDto(
    @SerialName("patient_id") val patientId: String,
    @SerialName("recipient_id") val recipientId: String,
    val active: Boolean = true,
    @SerialName("revoked_at") val revokedAt: String? = null,
    @SerialName("shared_steps_7d") val sharedSteps7d: Long? = null,
    @SerialName("shared_last_sleep_hours") val sharedLastSleepHours: Double? = null,
    @SerialName("shared_exercise_minutes_7d") val sharedExerciseMinutes7d: Long? = null,
    @SerialName("shared_resting_heart_rate_bpm") val sharedRestingHeartRateBpm: Long? = null,
    @SerialName("shared_latest_heart_rate_bpm") val sharedLatestHeartRateBpm: Long? = null,
    @SerialName("shared_calories_today") val sharedCaloriesToday: Long? = null,
    @SerialName("wellness_period_ended_at") val wellnessPeriodEndedAt: String? = null,
)

@Singleton
class ProfileSharingRepository @Inject constructor(private val supabase: SupabaseClient) {
    suspend fun shareProfileWith(
        recipientId: String,
        wellness: SharedWellnessSnapshot,
    ): Result<Unit> = runCatching {
        val patientId = supabase.auth.currentUserOrNull()?.id ?: error("Sign in to share your profile")
        supabase.from("profile_shares").upsert(
            ProfileShareDto(
                patientId = patientId,
                recipientId = recipientId,
                sharedSteps7d = wellness.stepsLast7Days,
                sharedLastSleepHours = wellness.lastSleepHours,
                sharedExerciseMinutes7d = wellness.exerciseMinutesLast7Days,
                sharedRestingHeartRateBpm = wellness.restingHeartRateBpm,
                sharedLatestHeartRateBpm = wellness.latestHeartRateBpm,
                sharedCaloriesToday = wellness.caloriesBurnedToday,
                wellnessPeriodEndedAt = java.time.Instant.now().toString(),
            ),
        ) {
            onConflict = "patient_id,recipient_id"
        }
        Unit
    }
}
