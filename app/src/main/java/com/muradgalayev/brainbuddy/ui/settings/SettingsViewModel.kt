package com.muradgalayev.brainbuddy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val themeMode = preferencesManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.System)

    val fontMode = preferencesManager.fontMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FontMode.Classic)

    val enabledNavItems = preferencesManager.enabledNavItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val focusModeEnabled = preferencesManager.focusModeEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun setFontMode(mode: FontMode) {
        viewModelScope.launch { preferencesManager.setFontMode(mode) }
    }

    fun toggleNavItem(route: String, enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setNavItemEnabled(route, enabled) }
    }

    fun toggleFocusMode(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setFocusModeEnabled(enabled) }
    }
}