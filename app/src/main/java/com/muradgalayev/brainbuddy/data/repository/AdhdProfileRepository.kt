package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
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
    private val preferencesManager: PreferencesManager,
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
     * Resolves whether the current user has completed the survey.
     *
     *  - If the local cache has a value (true/false), returns it immediately (fast path).
     *  - If unknown (fresh install, new device, cache cleared), hits Supabase once and
     *    persists the answer for next launch.
     *  - If unknown AND the network fails, returns false so we default to the
     *    onboarding flow — better to re-do the survey than skip it wrongly.
     */
    suspend fun isSurveyCompletedCached(): Boolean {
        val userId = authRepository.getCurrentUserId() ?: return false
        preferencesManager.getSurveyCompletedCached(userId)?.let { return it }
        // Unknown → resolve from Supabase and cache the answer for next time.
        val remote = fetchRemoteSurveyCompleted(userId) ?: return false
        preferencesManager.setSurveyCompletedCached(userId, remote)
        return remote
    }

    /** Pulls the flag from Supabase and writes it into the local cache. */
    suspend fun refreshSurveyCompletedCache() {
        val userId = authRepository.getCurrentUserId() ?: return
        val remote = fetchRemoteSurveyCompleted(userId) ?: return
        preferencesManager.setSurveyCompletedCached(userId, remote)
    }

    /** Null return means "network failed / row missing" — caller decides fallback. */
    private suspend fun fetchRemoteSurveyCompleted(userId: String): Boolean? {
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
            Log.w(TAG, "fetchRemoteSurveyCompleted failed: ${it.message}")
        }.getOrNull()
    }

    suspend fun saveProfile(profile: AdhdProfile) {
        val userId = authRepository.getCurrentUserId() ?: error("No logged-in user")
        val saved = profile.copy(userId = userId, surveyCompleted = true)
        supabaseClient.from(table).upsert(saved.toDto())
        cachedProfile = saved
        // Splash reads from this cache, so keep it in lockstep with the remote write.
        preferencesManager.setSurveyCompletedCached(userId, true)
        runCatching { reminderBootstrapper.get().rescheduleMorningSummary() }
        runCatching { medicationTodoSyncer.sync(saved) }
    }
}

@kotlinx.serialization.Serializable
private data class SurveyCompletedRow(
    @kotlinx.serialization.SerialName("survey_completed")
    val surveyCompleted: Boolean = false,
)
