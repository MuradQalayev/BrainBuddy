package com.muradgalayev.brainbuddy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.local.TextSpacing
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.data.repository.PreferencesRepository
import com.muradgalayev.brainbuddy.ui.theme.AppTheme
import com.muradgalayev.brainbuddy.ui.theme.CustomThemeSpec
import com.muradgalayev.brainbuddy.ui.theme.ThemeSelection
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

// just the appearance preferences, for the places that need them without the rest of Settings.
// shared by onboarding's first step and the accessibility panel on Home. both write the real
// setting rather than a draft, since a look chosen anywhere is the look everywhere immediately,
// which is also what makes either surface its own preview
@HiltViewModel
class AppearancePreferencesViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val preferencesRepository: PreferencesRepository,
    private val modeManager: com.muradgalayev.brainbuddy.data.local.ModeManager,
) : ViewModel() {

    // appearance is intentionally read-only while a mode overlay is in force
    val activeMode = modeManager.activeMode

    val themeMode = preferencesManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.System)

    val fontMode = preferencesManager.fontMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FontMode.DEFAULT)

    val fontSize = preferencesManager.fontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FontSize.Medium)

    val textSpacing = preferencesManager.textSpacing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TextSpacing.DEFAULT)

    val appTheme = preferencesManager.appTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeSelection.DEFAULT)

    val customThemeSpec = preferencesManager.customThemeSpec
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomThemeSpec())

    val readAloudTaps = preferencesManager.readAloudTaps
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setFontMode(mode: FontMode) {
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            preferencesRepository.setFontMode(mode)
        }
    }

    fun setFontSize(size: FontSize) {
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            preferencesRepository.setFontSize(size)
        }
    }

    fun setTextSpacing(spacing: TextSpacing) {
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            preferencesRepository.setTextSpacing(spacing)
        }
    }

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            preferencesManager.setAppTheme(theme)
        }
    }

    fun setCustomTheme(spec: CustomThemeSpec) {
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            preferencesManager.setCustomTheme(spec)
        }
    }

    fun setReadAloudTaps(enabled: Boolean) {
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            preferencesManager.setReadAloudTaps(enabled)
        }
    }
}
