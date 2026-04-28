package com.muradgalayev.brainbuddy.data.repository

import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.data.remote.PreferencesDto
import com.muradgalayev.brainbuddy.data.remote.SupabasePreferencesDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesRepository @Inject constructor(
    private val prefsManager: PreferencesManager,
    private val remote: SupabasePreferencesDataSource,
    private val authRepository: AuthRepository
) {

    private fun getCurrentUserId(): String? = authRepository.getCurrentUserId()

    // Pull remote prefs for current user and apply locally
    suspend fun pullRemoteAndApply() {
        val userId = getCurrentUserId() ?: return
        val dto = remote.get(userId) ?: return
        withContext(Dispatchers.IO) {
            dto.themeMode?.let { prefsManager.setThemeMode(ThemeMode.valueOf(it)) }
            dto.fontMode?.let { prefsManager.setFontMode(FontMode.valueOf(it)) }
            dto.fontSize?.let { prefsManager.setFontSize(FontSize.valueOf(it)) }
            dto.focusModeEnabled?.let { prefsManager.setFocusModeEnabled(it) }
            dto.simplifiedWorkspace?.let { prefsManager.setSimplifiedWorkspace(it) }
            dto.enabledNavItems?.let { raw ->
                val set = if (raw.isBlank()) emptySet() else raw.split(",").toSet()
                prefsManager.setEnabledNavItems(set)
            }
        }
    }

    // Each setter updates local DataStore and pushes the change to Supabase (partial upsert)
    suspend fun setThemeMode(mode: ThemeMode) {
        prefsManager.setThemeMode(mode)
        val userId = getCurrentUserId() ?: return
        remote.upsert(PreferencesDto(userId = userId, themeMode = mode.name))
    }

    suspend fun setFontMode(mode: FontMode) {
        prefsManager.setFontMode(mode)
        val userId = getCurrentUserId() ?: return
        remote.upsert(PreferencesDto(userId = userId, fontMode = mode.name))
    }

    suspend fun setFontSize(size: FontSize) {
        prefsManager.setFontSize(size)
        val userId = getCurrentUserId() ?: return
        remote.upsert(PreferencesDto(userId = userId, fontSize = size.name))
    }

    suspend fun setFocusModeEnabled(enabled: Boolean) {
        prefsManager.setFocusModeEnabled(enabled)
        val userId = getCurrentUserId() ?: return
        remote.upsert(PreferencesDto(userId = userId, focusModeEnabled = enabled))
    }

    suspend fun setSimplifiedWorkspace(enabled: Boolean) {
        prefsManager.setSimplifiedWorkspace(enabled)
        val userId = getCurrentUserId() ?: return
        remote.upsert(PreferencesDto(userId = userId, simplifiedWorkspace = enabled))
    }

    suspend fun setEnabledNavItems(items: Set<String>) {
        prefsManager.setEnabledNavItems(items)
        val userId = getCurrentUserId() ?: return
        val raw = items.joinToString(",")
        remote.upsert(PreferencesDto(userId = userId, enabledNavItems = raw))
    }
}

