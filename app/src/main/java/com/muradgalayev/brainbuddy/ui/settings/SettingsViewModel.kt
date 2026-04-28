package com.muradgalayev.brainbuddy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
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
    private val preferencesRepository: com.muradgalayev.brainbuddy.data.repository.PreferencesRepository
) : ViewModel() {

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

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

    fun signOut() {
        viewModelScope.launch {
            // capture current user id before signing out
            val currentUserId = authRepository.getCurrentUserId()
            authRepository.signOut()
            currentUserId?.let {
                // clear local tasks for the user that just signed out
                todoRepository.clearLocalForUser(it)
            }
            _loggedOut.value = true
        }
    }
}
