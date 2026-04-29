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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val authRepository: AuthRepository,
    private val todoRepository: com.muradgalayev.brainbuddy.data.repository.TodoRepository,
    private val preferencesRepository: com.muradgalayev.brainbuddy.data.repository.PreferencesRepository,
    private val googleCalendarRepository: GoogleCalendarRepository,
    private val googleCalendarAuthClient: GoogleCalendarAuthClient,
    private val googleCalendarTokenStore: GoogleCalendarTokenStore
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

    private val _linkedGoogleEmail = MutableStateFlow(googleCalendarTokenStore.getLinkedEmail())
    val linkedGoogleEmail: StateFlow<String?> = _linkedGoogleEmail.asStateFlow()

    val userEmail: String?
        get() = authRepository.getCurrentUserEmail()

    val userFullName: String?
        get() = authRepository.getCurrentUserFullName()

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
            val first = googleCalendarRepository.exportAllTodos()
            if (first !is ExportResult.NeedsGoogleSignIn) {
                publishExportResult(first)
                _exportingToCalendar.value = false
                return@launch
            }
            try {
                when (val step = googleCalendarAuthClient.requestAuthorization()) {
                    is GoogleCalendarAuthClient.AuthorizationStep.AccessToken -> {
                        refreshLinkedEmail(step.token)
                        publishExportResult(googleCalendarRepository.exportAllTodos())
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
            publishExportResult(googleCalendarRepository.exportAllTodos())
            _exportingToCalendar.value = false
        }
    }

    private suspend fun refreshLinkedEmail(token: String) {
        val email = googleCalendarAuthClient.fetchAndStoreUserEmail(token)
        if (email != null) _linkedGoogleEmail.value = email
    }

    private fun publishExportResult(result: ExportResult) {
        _calendarExportMessage.value = when (result) {
            is ExportResult.Success -> {
                val parts = mutableListOf<String>()
                if (result.pushed > 0) parts += "${result.pushed} added"
                if (result.alreadyExisted > 0) parts += "${result.alreadyExisted} already there"
                if (result.failed > 0) parts += "${result.failed} failed"
                val core = if (parts.isEmpty()) "Nothing to export" else parts.joinToString(", ")
                val email = _linkedGoogleEmail.value
                if (email != null && parts.isNotEmpty()) "$core → $email" else core
            }
            ExportResult.NeedsGoogleSignIn ->
                "Connect a Google account to enable Calendar export"
        }
    }

    fun disconnectGoogleCalendar() {
        viewModelScope.launch {
            googleCalendarAuthClient.signOut()
            _linkedGoogleEmail.value = null
            _calendarExportMessage.value = "Google Calendar disconnected"
        }
    }

    fun clearCalendarExportMessage() {
        _calendarExportMessage.value = null
    }

    fun signOut() {
        viewModelScope.launch {
            // capture current user id before signing out
            val currentUserId = authRepository.getCurrentUserId()
            authRepository.signOut()
            googleCalendarAuthClient.signOut()
            _linkedGoogleEmail.value = null
            currentUserId?.let {
                // clear local tasks for the user that just signed out
                todoRepository.clearLocalForUser(it)
            }
            _loggedOut.value = true
        }
    }
}
