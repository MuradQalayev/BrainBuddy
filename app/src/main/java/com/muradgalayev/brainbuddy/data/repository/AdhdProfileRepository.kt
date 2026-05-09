package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.muradgalayev.brainbuddy.data.mapper.toDomain
import com.muradgalayev.brainbuddy.data.mapper.toDto
import com.muradgalayev.brainbuddy.data.remote.dto.AdhdProfileDto
import com.muradgalayev.brainbuddy.domain.model.AdhdProfile
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private const val TAG = "AdhdProfileRepo"

@Singleton
class AdhdProfileRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    // Provider breaks the Hilt cycle: bootstrapper → this repo → bootstrapper.
    private val reminderBootstrapper: Provider<com.muradgalayev.brainbuddy.data.notifications.ReminderBootstrapper>,
    private val medicationTodoSyncer: MedicationTodoSyncer,
) {
    private val table = "adhd_profiles"

    @Volatile private var cachedProfile: AdhdProfile? = null

    /** Synchronous peek for instant first-frame rendering. Returns null on user mismatch. */
    fun peekProfile(): AdhdProfile? {
        val currentId = authRepository.getCurrentUserId() ?: return null
        val cached = cachedProfile ?: return null
        return if (cached.userId == currentId) cached else {
            cachedProfile = null
            null
        }
    }

    suspend fun getProfile(): AdhdProfile? {
        val userId = authRepository.getCurrentUserId() ?: return null
        return runCatching {
            supabaseClient
                .from(table)
                .select {
                    filter { eq("user_id", userId) }
                    limit(1)
                }
                .decodeList<AdhdProfileDto>()
                .firstOrNull()
                ?.toDomain()
        }.onSuccess { cachedProfile = it }
            .onFailure {
                Log.w(TAG, "getProfile failed: ${it.message}")
            }.getOrNull()
    }

    /**
     * True if the user has finished either survey. False on missing row, error,
     * or row that exists but isn't yet flagged complete. Splash uses this to
     * decide whether to gate onto the onboarding flow.
     */
    suspend fun isSurveyCompleted(): Boolean {
        val userId = authRepository.getCurrentUserId() ?: return false
        return runCatching {
            supabaseClient
                .from(table)
                .select(Columns.list("survey_completed")) {
                    filter { eq("user_id", userId) }
                    limit(1)
                }
                .decodeList<SurveyCompletedRow>()
                .firstOrNull()
                ?.surveyCompleted
                ?: false
        }.onFailure {
            Log.w(TAG, "isSurveyCompleted failed: ${it.message}")
        }.getOrDefault(false)
    }

    suspend fun saveProfile(profile: AdhdProfile) {
        val userId = authRepository.getCurrentUserId() ?: error("No logged-in user")
        val saved = profile.copy(userId = userId, surveyCompleted = true)
        supabaseClient.from(table).upsert(saved.toDto())
        cachedProfile = saved
        runCatching { reminderBootstrapper.get().rescheduleMorningSummary() }
        runCatching { medicationTodoSyncer.sync(saved) }
    }
}

@kotlinx.serialization.Serializable
private data class SurveyCompletedRow(
    @kotlinx.serialization.SerialName("survey_completed")
    val surveyCompleted: Boolean = false,
)
