package com.muradgalayev.brainbuddy.ui.settings

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.google.GoogleCalendarAuthClient
import com.muradgalayev.brainbuddy.data.google.GoogleCalendarTokenStore
import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.data.repository.ExportResult
import com.muradgalayev.brainbuddy.data.repository.GoogleCalendarRepository
import com.muradgalayev.brainbuddy.data.repository.UsernameTakenException
import com.muradgalayev.brainbuddy.data.sync.CalendarSyncScheduler
import com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability
import com.muradgalayev.brainbuddy.ui.onboarding.isUsernameSyntaxValid
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val authRepository: AuthRepository,
    private val todoRepository: com.muradgalayev.brainbuddy.data.repository.TodoRepository,
    private val calendarRepository: com.muradgalayev.brainbuddy.data.repository.CalendarRepository,
    private val pomodoroRepository: com.muradgalayev.brainbuddy.data.repository.PomodoroRepository,
    private val preferencesRepository: com.muradgalayev.brainbuddy.data.repository.PreferencesRepository,
    private val googleCalendarRepository: GoogleCalendarRepository,
    private val googleCalendarAuthClient: GoogleCalendarAuthClient,
    private val googleCalendarTokenStore: GoogleCalendarTokenStore,
    private val calendarSyncScheduler: CalendarSyncScheduler,
) : ViewModel() {

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    private val _exportingToCalendar = MutableStateFlow(false)
    val exportingToCalendar: StateFlow<Boolean> = _exportingToCalendar.asStateFlow()

    private val _calendarExportMessage = MutableStateFlow<String?>(null)
    val calendarExportMessage: StateFlow<String?> = _calendarExportMessage.asStateFlow()

    private val _calendarAuthorizationRequest = MutableStateFlow<PendingIntent?>(null)
    val calendarAuthorizationRequest: StateFlow<PendingIntent?> =
        _calendarAuthorizationRequest.asStateFlow()

    // Reactive — flips when SyncCoordinator pulls the linked email from Supabase
    // after sign-in, or when the user connects/disconnects locally.
    val linkedGoogleEmail: StateFlow<String?> = googleCalendarTokenStore.linkedEmail

    // Seed from AuthRepository's in-memory cached profile if it's been fetched
    // before in this process — otherwise fall back to whatever the JWT metadata
    // gave us. This means on re-entry to Settings within the same session we
    // start with the FULL profile already visible, no refetch flash.
    private val _profile = MutableStateFlow(seedProfileFromCache())

    private fun seedProfileFromCache(): UserProfile {
        val cached = authRepository.peekProfile()
        return UserProfile(
            email = cached?.email ?: authRepository.getCurrentUserEmail(),
            name = cached?.displayName ?: authRepository.getCurrentUserFullName(),
            username = cached?.username ?: authRepository.getCurrentUserUsername(),
            avatarUrl = cached?.avatarUrl ?: authRepository.getCurrentUserAvatarUrl(),
        )
    }

    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _profileSaving = MutableStateFlow(false)
    val profileSaving: StateFlow<Boolean> = _profileSaving.asStateFlow()

    private val _profileSaveMessage = MutableStateFlow<String?>(null)
    val profileSaveMessage: StateFlow<String?> = _profileSaveMessage.asStateFlow()

    private val _usernameAvailability = MutableStateFlow(UsernameAvailability.Idle)
    val usernameAvailability: StateFlow<UsernameAvailability> = _usernameAvailability.asStateFlow()
    private var usernameCheckJob: Job? = null

    init {
        // Stale-while-revalidate: only hit Supabase on the very first entry
        // after app start (when there's nothing in the process-level cache).
        // On subsequent re-entries the constructor already seeded from cache,
        // and SyncCoordinator keeps the cache warm on network reconnects.
        if (authRepository.peekProfile() == null) {
            refreshProfile()
        }
        // linkedGoogleEmail is a reactive StateFlow from the token store, and
        // SyncCoordinator refreshes it after sign-in / on reconnect. No need
        // to fire a network call here — that was the source of the flash.
    }

    private fun refreshLinkedGoogleEmail() {
        viewModelScope.launch {
            val remote = runCatching { authRepository.getLinkedGoogleEmail() }.getOrNull()
                ?: return@launch
            if (remote != googleCalendarTokenStore.getLinkedEmail()) {
                googleCalendarTokenStore.saveLinkedEmail(remote)
            }
        }
    }
    // Legacy convenience getters retained for any other callers that read these.
    val userEmail: String?
        get() = _profile.value.email

    val userFullName: String?
        get() = _profile.value.name

    val fontSize = preferencesManager.fontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FontSize.Medium)
    val themeMode = preferencesManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.System)

    val fontMode = preferencesManager.fontMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FontMode.Classic)

    val enabledNavItems = preferencesManager.enabledNavItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val focusModeEnabled = preferencesManager.focusModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val simplifiedWorkspace = preferencesManager.simplifiedWorkspace
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesRepository.setThemeMode(mode) }
    }

    fun setFontMode(mode: FontMode) {
        viewModelScope.launch { preferencesRepository.setFontMode(mode) }
    }

    fun toggleNavItem(route: String, enabled: Boolean) {
        viewModelScope.launch {
            // update local set fully via repository
            // read the current set once and compute the new set
            val set = preferencesManager.enabledNavItems.firstOrNull() ?: emptySet()
            val newSet = if (enabled) set + route else set - route
            preferencesRepository.setEnabledNavItems(newSet)
        }
    }

    fun toggleFocusMode(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setFocusModeEnabled(enabled) }
    }

    fun toggleSimplifiedWorkspace(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setSimplifiedWorkspace(enabled) }
    }
    fun setFontSize(fontSize: FontSize) {
        viewModelScope.launch {
            preferencesRepository.setFontSize(fontSize)
        }
    }

    fun exportToGoogleCalendar() {
        if (_exportingToCalendar.value) return
        _exportingToCalendar.value = true
        viewModelScope.launch {
            val first = googleCalendarRepository.exportAllEvents()
            if (first !is ExportResult.NeedsGoogleSignIn) {
                publishExportResult(first)
                _exportingToCalendar.value = false
                return@launch
            }
            try {
                when (val step = googleCalendarAuthClient.requestAuthorization()) {
                    is GoogleCalendarAuthClient.AuthorizationStep.AccessToken -> {
                        refreshLinkedEmail(step.token)
                        publishExportResult(googleCalendarRepository.exportAllEvents())
                        _exportingToCalendar.value = false
                    }
                    is GoogleCalendarAuthClient.AuthorizationStep.NeedsUserConsent -> {
                        // Hand the PendingIntent to the screen; the screen will
                        // launch it and call onCalendarAuthorizationResult().
                        // Keep _exportingToCalendar=true so the spinner stays
                        // until the user completes (or cancels) consent.
                        _calendarAuthorizationRequest.value = step.pendingIntent
                    }
                }
            } catch (e: Exception) {
                _calendarExportMessage.value =
                    "Could not connect Google account: ${e.message ?: "unknown error"}"
                _exportingToCalendar.value = false
            }
        }
    }

    fun consumeCalendarAuthorizationRequest() {
        _calendarAuthorizationRequest.value = null
    }

    fun onCalendarAuthorizationResult(data: Intent?) {
        viewModelScope.launch {
            val token = googleCalendarAuthClient.extractFromActivityResult(data)
            if (token == null) {
                _calendarExportMessage.value = "Calendar connection cancelled"
                _exportingToCalendar.value = false
                return@launch
            }
            refreshLinkedEmail(token)
            publishExportResult(googleCalendarRepository.exportAllEvents())
            _exportingToCalendar.value = false
        }
    }

    private suspend fun refreshLinkedEmail(token: String) {
        val email = googleCalendarAuthClient.fetchAndStoreUserEmail(token)
        // linkedGoogleEmail flows from the token store now — no need to write it here.
        if (email != null) {
            // First successful connect — kick off the twice-daily background
            // sync. KEEP policy makes repeat calls a no-op.
            calendarSyncScheduler.schedulePeriodic()
        }
    }

    private fun publishExportResult(result: ExportResult) {
        _calendarExportMessage.value = when (result) {
            is ExportResult.Success -> {
                val parts = mutableListOf<String>()
                if (result.pushed > 0) parts += "${result.pushed} added"
                if (result.alreadyExisted > 0) parts += "${result.alreadyExisted} already there"
                if (result.failed > 0) parts += "${result.failed} failed"
                val core = if (parts.isEmpty()) "Nothing to export" else parts.joinToString(", ")
                val email = linkedGoogleEmail.value
                if (email != null && parts.isNotEmpty()) "$core → $email" else core
            }
            ExportResult.NeedsGoogleSignIn ->
                "Connect a Google account to enable Calendar export"
        }
    }

    fun disconnectGoogleCalendar() {
        viewModelScope.launch {
            // Explicit user action — revoke Google grant AND null out the Supabase column
            // so signing in on another device won't resurrect the link.
            googleCalendarAuthClient.signOut()
            runCatching { authRepository.setLinkedGoogleEmail(null) }
            calendarSyncScheduler.cancel()
            _calendarExportMessage.value = "Google Calendar disconnected"
        }
    }

    fun clearCalendarExportMessage() {
        _calendarExportMessage.value = null
    }

    fun signOut() {
        viewModelScope.launch {
            // Capture user id BEFORE signing out so we know which local caches to clear.
            val currentUserId = authRepository.getCurrentUserId()
            authRepository.signOut()
            // Important: don't call googleCalendarAuthClient.signOut() here — that revokes
            // the Google consent on this device, which forces a full re-auth (with picker
            // and consent screen) after Supabase login. We just want to log out of Supabase;
            // the Google link stays in the profiles table and is re-hydrated on next sign-in.
            googleCalendarTokenStore.clearForSupabaseSignOut()
            calendarSyncScheduler.cancel()
            currentUserId?.let {
                // Clear local tasks + calendar events + pomodoro sessions for the user
                // that just signed out so the next user doesn't see stale data.
                todoRepository.clearLocalForUser(it)
                calendarRepository.clearLocalForUser(it)
                pomodoroRepository.clearLocalForUser(it)
            }
            _loggedOut.value = true
        }
    }

    /**
     * Debounced availability check driven by the edit dialog. Skips the network call
     * when the field matches the user's current username — that's their own row, not
     * a conflict.
     */
    fun onEditingUsername(raw: String) {
        usernameCheckJob?.cancel()
        val trimmed = raw.trim()
        val current = _profile.value.username?.trim().orEmpty()

        when {
            trimmed.isEmpty() -> {
                _usernameAvailability.value = UsernameAvailability.Idle
                return
            }
            trimmed.equals(current, ignoreCase = true) -> {
                // Same as current → treated as available so Save isn't blocked.
                _usernameAvailability.value = UsernameAvailability.Available
                return
            }
            !isUsernameSyntaxValid(trimmed) -> {
                _usernameAvailability.value = UsernameAvailability.Invalid
                return
            }
        }

        _usernameAvailability.value = UsernameAvailability.Checking
        usernameCheckJob = viewModelScope.launch {
            delay(400)
            val available = authRepository.isUsernameAvailable(trimmed)
            _usernameAvailability.value =
                if (available) UsernameAvailability.Available else UsernameAvailability.Taken
        }
    }

    fun updateProfile(name: String, username: String) {
        if (_profileSaving.value) return

        val cleanName = name.trim()
        val cleanUsername = username.trim()

        if (cleanName.isBlank()) {
            _profileSaveMessage.value = "Name cannot be empty"
            return
        }
        // Block save when we already know it's taken/invalid. The DB unique index
        // is still the ultimate guard — this just avoids the round-trip.
        when (_usernameAvailability.value) {
            UsernameAvailability.Taken -> {
                _profileSaveMessage.value = "That username is already taken"
                return
            }
            UsernameAvailability.Invalid -> {
                _profileSaveMessage.value = "Use 3–20 letters, numbers or underscores"
                return
            }
            UsernameAvailability.Checking -> {
                _profileSaveMessage.value = "Still checking username, try again in a moment"
                return
            }
            else -> Unit
        }

        _profileSaving.value = true

        viewModelScope.launch {
            try {
                val updatedProfile = authRepository.updateProfile(
                    displayName = cleanName,
                    username = cleanUsername
                )

                _profile.value = UserProfile(
                    email = updatedProfile.email ?: _profile.value.email ?: authRepository.getCurrentUserEmail(),
                    name = updatedProfile.displayName ?: cleanName,
                    username = updatedProfile.username ?: cleanUsername,
                    avatarUrl = updatedProfile.avatarUrl ?: _profile.value.avatarUrl
                )

                _profileSaveMessage.value = "Profile updated"
            } catch (e: UsernameTakenException) {
                // The DB rejected via the unique index — either the local check said
                // "available" incorrectly (RLS masked the row) or someone else grabbed
                // the name between check and save. Both surface the same message.
                _usernameAvailability.value = UsernameAvailability.Taken
                _profileSaveMessage.value = "That username is already taken"
            } catch (e: Exception) {
                _profileSaveMessage.value = "Couldn't save: ${e.message ?: "unknown error"}"
            } finally {
                _profileSaving.value = false
            }
        }
    }

    fun clearProfileSaveMessage() {
        _profileSaveMessage.value = null
    }


    private fun refreshProfile() {
        viewModelScope.launch {
            val fresh = runCatching { authRepository.ensureProfileExists() }.getOrElse {
                _profileSaveMessage.value = "Couldn't load profile: ${it.message ?: "unknown error"}"
                return@launch
            }
            val current = _profile.value
            val next = UserProfile(
                email = fresh.email ?: current.email,
                name = fresh.displayName ?: current.name,
                username = fresh.username ?: current.username,
                avatarUrl = fresh.avatarUrl ?: current.avatarUrl,
            )
            // Skip the write when the fresh value matches what's already shown.
            // Prevents any recomposition even in theory — MutableStateFlow also
            // deduplicates on ==, but making it explicit documents the intent.
            if (next != current) _profile.value = next
        }
    }
}

data class UserProfile(
    val email: String?,
    val name: String?,
    val username: String?,
    val avatarUrl: String?
)
