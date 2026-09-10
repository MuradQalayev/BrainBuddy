package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.mapper.toDomain
import com.muradgalayev.brainbuddy.data.mapper.toDto
import com.muradgalayev.brainbuddy.data.remote.dto.AdhdProfileDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.muradgalayev.brainbuddy.domain.model.AdhdProfile
import com.muradgalayev.brainbuddy.domain.model.Medication
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

private const val TAG = "AdhdProfileRepo"
private const val PENDING_PUSH_KEY = "pending_push"

@Singleton
class AdhdProfileRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val authRepository: AuthRepository,
    // Provider breaks the Hilt cycle: bootstrapper -> this repo -> bootstrapper
    private val reminderBootstrapper: Provider<com.muradgalayev.brainbuddy.data.notifications.ReminderBootstrapper>,
    private val medicationTodoSyncer: MedicationTodoSyncer,
    private val preferencesManager: PreferencesManager,
    private val questionnaireReminderScheduler: com.muradgalayev.brainbuddy.data.notifications.QuestionnaireReminderScheduler,
    @ApplicationContext context: Context,
) {
    private val table = "adhd_profiles"

    // survives screen and ViewModel teardown, so a fire-and-forget skip write can't be cancelled
    // by the user navigating away the instant they tap it
    private val ioScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
    )

    private val profilePrefs = context.getSharedPreferences("adhd_profile_cache", Context.MODE_PRIVATE)
    @Volatile private var cachedProfile: AdhdProfile? = profilePrefs.getString("profile", null)?.let { raw ->
        runCatching { Json.decodeFromString(AdhdProfileDto.serializer(), raw).toDomain() }.getOrNull()
    }

    // the live profile, for screens that mustn't go stale. every consumer used to take its own
    // peekProfile() snapshot into a private MutableStateFlow and refresh it whenever that screen
    // happened to think of it, so editing a medication in the survey left the Workspace and the
    // wellness screens on the old list, and the two could disagree about the same fact.
    // cacheProfile() is the only writer and runs on every save, draft and remote fetch, so one
    // change propagates everywhere on the same frame
    private val _profile = kotlinx.coroutines.flow.MutableStateFlow(cachedProfile)
    val profile: kotlinx.coroutines.flow.StateFlow<AdhdProfile?> = _profile

    // just the medications, deduplicated, the most-observed slice of the profile
    val medications: Flow<List<Medication>> =
        _profile.map { it?.medications.orEmpty() }.distinctUntilChanged()

    private fun cacheProfile(profile: AdhdProfile?) {
        cachedProfile = profile
        _profile.value = profile
        if (profile == null) profilePrefs.edit().remove("profile").apply()
        else profilePrefs.edit().putString(
            "profile",
            Json.encodeToString(AdhdProfileDto.serializer(), profile.toDto()),
        ).apply()
    }

    // set when a local save couldn't reach Supabase. while it's set the cached profile is the
    // newer copy, so remote reads must not overwrite it and pushPendingProfile owes an upsert
    private var pendingPush: Boolean
        get() = profilePrefs.getBoolean(PENDING_PUSH_KEY, false)
        set(value) = profilePrefs.edit().putBoolean(PENDING_PUSH_KEY, value).apply()

    fun clearLocalCache() {
        cachedProfile?.userId?.let(questionnaireReminderScheduler::cancelForUser)
        _profile.value = null
        cacheProfile(null)
        pendingPush = false
    }

    // re-sends a profile that was edited while offline. called by SyncCoordinator on reconnect,
    // and a no-op when nothing is queued
    suspend fun pushPendingProfile() {
        if (!pendingPush) return
        val profile = cachedProfile ?: run { pendingPush = false; return }
        val userId = authRepository.getCurrentUserId() ?: return
        val result = runCatching { upsertProfile(profile.copy(userId = userId)) }
        if (result.isSuccess) {
            pendingPush = false
        } else {
            Log.w(TAG, "pushPendingProfile failed: ${result.exceptionOrNull()?.message}")
        }
    }

    // true once the user chose 'I'll do it later' on onboarding
    suspend fun isOnboardingSkipped(): Boolean {
        val userId = authRepository.getCurrentOrCachedUserId() ?: return false
        return preferencesManager.isOnboardingSkipped(userId)
    }

    // records the skip so Splash lets them into the app
    fun markOnboardingSkipped() {
        val userId = authRepository.getCurrentOrCachedUserId() ?: return
        ioScope.launch { preferencesManager.setOnboardingSkipped(userId, true) }
    }

    // synchronous peek for instant first-frame rendering. null on user mismatch
    fun peekProfile(): AdhdProfile? {
        val currentId = authRepository.getCurrentOrCachedUserId() ?: return null
        val cached = cachedProfile ?: return null
        return if (cached.userId == currentId) cached else {
            cachedProfile = null
            null
        }
    }

    suspend fun getProfile(): AdhdProfile? {
        val userId = authRepository.getCurrentOrCachedUserId() ?: return null
        val local = peekProfile()
        if (authRepository.getCurrentUserId() == null) return local
        // an edit made offline is newer than anything the server can hand back
        if (pendingPush && local != null) return local
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
        }.onSuccess { cacheProfile(it) }
            .onFailure {
                Log.w(TAG, "getProfile failed: ${it.message}")
            }.getOrNull() ?: local
    }

    // resolves whether the user has completed the survey. false for 'we don't know', which is the
    // right default for Splash: it has to route somewhere and would rather show the survey again
    // than skip it wrongly. anything that locks a feature (the AI assistant, the account banner)
    // must use surveyCompletedOrNull instead, so a dead connection isn't read as a no
    suspend fun isSurveyCompletedCached(): Boolean = surveyCompletedOrNull() ?: false

    // three-state: true, false, or null when the answer is genuinely unknown (no user id restored
    // yet, nothing cached, and the network didn't answer). the local cache and the cached profile
    // win, in that order, and a true from either is final, it can only have been written by a
    // completed survey. only when neither knows do we ask Supabase, and a failed request stays null
    suspend fun surveyCompletedOrNull(): Boolean? {
        val userId = authRepository.getCurrentOrCachedUserId() ?: return null
        val cached = preferencesManager.getSurveyCompletedCached(userId)
        if (cached == true) return true
        val local = peekProfile()?.surveyCompleted
        if (local == true) {
            cacheSurveyCompleted(userId, true)
            return true
        }
        // both agree it isn't done, or one says so and the other has nothing
        if (cached != null) return cached
        if (local != null) {
            cacheSurveyCompleted(userId, local)
            return local
        }
        // unknown, so resolve from Supabase and cache the answer for next time
        val remote = fetchRemoteSurveyCompleted(userId) ?: return null
        cacheSurveyCompleted(userId, remote)
        return remote
    }

    // pulls the flag from Supabase and writes it into the local cache
    suspend fun refreshSurveyCompletedCache() {
        val userId = authRepository.getCurrentOrCachedUserId() ?: return
        // a profile saved offline is on this device and not yet on the server, so the server's answer
        // is stale by definition. reading it here used to race pushPendingProfile() inside the same
        // sync pass and cache 'not completed' over a finished survey
        if (pendingPush) return
        val remote = fetchRemoteSurveyCompleted(userId) ?: return
        cacheSurveyCompleted(userId, remote)
    }

    // the flag is monotonic per user: a completed survey can't become uncompleted, so a false may
    // only ever fill in a blank. without this, one empty read (a row that hasn't synced, an RLS
    // hiccup, a response missing the column) permanently re-locks the AI for someone who is done
    private suspend fun cacheSurveyCompleted(userId: String, completed: Boolean) {
        if (!completed && preferencesManager.getSurveyCompletedCached(userId) == true) return
        preferencesManager.setSurveyCompletedCached(userId, completed)
    }

    // null means the network failed or the row is missing, and the caller decides the fallback
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

    // local first, remote second. the Supabase upsert used to run before the cache write, which
    // meant an edit made offline threw and was lost outright, nothing saved at all. now the
    // device always keeps the change and the server catches up via pushPendingProfile
    // rolls the medication to-do window forward, on app start. MedicationTodoSyncer writes a
    // fixed number of days ahead, so without something moving the window along it just runs out.
    // quiet and best-effort: no profile, no medications or a failed read all mean nothing to do
    suspend fun refreshMedicationTodos() {
        // cache first. getProfile() reaches Supabase, and a network round-trip in front of a toggle
        // made the doses appear a second or two after the tap, long enough to read as 'the button
        // didn't work' and be tapped again
        val profile = peekProfile()
            ?: runCatching { getProfile() }.getOrNull()
            ?: return
        runCatching { medicationTodoSyncer.sync(profile) }
            .onFailure { Log.w(TAG, "Medication to-do refresh failed: ${it.message}") }
    }

    suspend fun saveProfile(profile: AdhdProfile) {
        val userId = authRepository.getCurrentOrCachedUserId() ?: error("No logged-in user")
        val saved = profile.copy(userId = userId, surveyCompleted = true)
        cacheProfile(saved)
        questionnaireReminderScheduler.cancelForUser(userId)
        // Splash reads this cache, so keep it in lockstep with the local write
        preferencesManager.setSurveyCompletedCached(userId, true)
        runCatching { reminderBootstrapper.get().rescheduleMorningSummary() }
        runCatching { medicationTodoSyncer.sync(saved) }
        pendingPush = true
        // logged, not swallowed. a profile write that fails here is invisible to the user, the local
        // cache already has the change so the screen looks saved, and it used to be invisible in
        // logcat too, which is how a column that was never being written went unnoticed
        runCatching { upsertProfile(saved) }
            .onSuccess { pendingPush = false }
            .onFailure { Log.w(TAG, "saveProfile: remote upsert failed, queued for retry: ${it.message}", it) }
    }

    // saves in-progress answers without promoting the survey to a completed tier
    suspend fun saveDraft(profile: AdhdProfile) {
        val userId = authRepository.getCurrentOrCachedUserId() ?: error("No logged-in user")
        val draft = profile.copy(userId = userId)
        cacheProfile(draft)
        pendingPush = true
        runCatching { upsertProfile(draft) }
            .onSuccess { pendingPush = false }
            .onFailure { Log.w(TAG, "saveDraft: remote upsert failed, queued for retry: ${it.message}", it) }
    }

    // the one place a profile row is written. two things here are load-bearing, and both were
    // silently wrong before.
    // the payload is built with encodeDefaults = true. supabase-kt serialises with encodeDefaults
    // = false, and postgrest-kt derives the columns= parameter from the keys the payload actually
    // contains, so any field sitting at its declared default is dropped from the body and from
    // the column list, and Postgres never touches that column. deleting your last medication
    // encodes medication_name as the empty string, the DTO's default, so the column was excluded
    // and the old list survived on the server. every 'cleared back to empty' edit had that fate.
    // onConflict names the constraint to merge on. the DTO carries no primary key, so without it
    // an upsert against a table whose PK is a generated id conflicts with nothing and inserts a
    // second row for the user. reads take limit 1 with no ordering, so a later read can return
    // the older duplicate and cache it as truth. needs the unique index from the 20260812 migration
    private suspend fun upsertProfile(profile: AdhdProfile) {
        supabaseClient.from(table).upsert(profile.toProfileUpsertPayload()) {
            onConflict = USER_ID_CONFLICT
        }
    }
}

// every column this app owns, always, including the ones sitting at their default: that is
// the whole point. SERVER_OWNED_COLUMNS are dropped rather than sent as null
internal fun AdhdProfile.toProfileUpsertPayload(): JsonObject {
    val encoded = outboundJson
        .encodeToJsonElement(AdhdProfileDto.serializer(), toDto())
        .jsonObject
    return JsonObject(encoded - SERVER_OWNED_COLUMNS)
}

private const val USER_ID_CONFLICT = "user_id"

// written by Postgres, not by us. sending our null would clobber the real value
private val SERVER_OWNED_COLUMNS = setOf("updated_at")

private val outboundJson = Json { encodeDefaults = true }

@kotlinx.serialization.Serializable
private data class SurveyCompletedRow(
    @kotlinx.serialization.SerialName("survey_completed")
    val surveyCompleted: Boolean = false,
)
