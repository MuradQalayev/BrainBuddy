package com.muradgalayev.brainbuddy.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { Light, Dark, System }
enum class FontMode { Classic, Modern, Rounded }

enum class FontSize { Small, Medium, Large }
@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val fontSizeKey = stringPreferencesKey("font_size")
    private val fontKey = stringPreferencesKey("font_mode")
    private val enabledNavKey = stringSetPreferencesKey("enabled_nav_items")
    private val focusModeKey = booleanPreferencesKey("focus_mode_enabled")
    private val simplifiedWorkspaceKey = booleanPreferencesKey("simplified_workspace")
    // Per-user survey completion, cached so Splash never blocks on a Supabase round-trip.
    private fun surveyCompletedKey(userId: String) =
        booleanPreferencesKey("survey_completed_$userId")

    // The currently active AI conversation. Null / absent means "no active chat —
    // next message will start a new conversation".
    private fun activeConversationKey(userId: String) =
        stringPreferencesKey("active_ai_conversation_$userId")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[themeKey]) {
            "Light" -> ThemeMode.Light
            "Dark" -> ThemeMode.Dark
            else -> ThemeMode.System
        }
    }
    val fontSize: Flow<FontSize> = context.dataStore.data.map { prefs ->
        when (prefs[fontSizeKey]) {
            "Small" -> FontSize.Small
            "Large" -> FontSize.Large
            else -> FontSize.Medium
        }
    }

    val fontMode: Flow<FontMode> = context.dataStore.data.map { prefs ->
        when (prefs[fontKey]) {
            "Modern" -> FontMode.Modern
            "Rounded" -> FontMode.Rounded
            else -> FontMode.Classic
        }
    }

    val enabledNavItems: Flow<Set<String>> = context.dataStore.data.map { prefs ->
        prefs[enabledNavKey] ?: emptySet()
    }

    val focusModeEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[focusModeKey] ?: false
    }

    val simplifiedWorkspace: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[simplifiedWorkspaceKey] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = mode.name
        }
    }
    suspend fun setFontSize(size: FontSize) {
        context.dataStore.edit { prefs ->
            prefs[fontSizeKey] = size.name
        }
    }

    suspend fun setFontMode(mode: FontMode) {
        context.dataStore.edit { prefs ->
            prefs[fontKey] = mode.name
        }
    }

    suspend fun setNavItemEnabled(route: String, enabled: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[enabledNavKey] ?: emptySet()
            prefs[enabledNavKey] = if (enabled) current + route else current - route
        }
    }

    suspend fun setEnabledNavItems(items: Set<String>) {
        context.dataStore.edit { prefs ->
            prefs[enabledNavKey] = items
        }
    }

    suspend fun setFocusModeEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[focusModeKey] = enabled
        }
    }

    suspend fun setSimplifiedWorkspace(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[simplifiedWorkspaceKey] = enabled
        }
    }

    /**
     * Three-state read: `true` (completed), `false` (explicitly not completed),
     * or `null` (unknown — never resolved on this device). Splash uses `null`
     * as the signal to consult Supabase before routing.
     */
    suspend fun getSurveyCompletedCached(userId: String): Boolean? {
        val key = surveyCompletedKey(userId)
        return context.dataStore.data.map { it[key] }.first()
    }

    suspend fun setSurveyCompletedCached(userId: String, completed: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[surveyCompletedKey(userId)] = completed
        }
    }

    fun activeConversationIdFlow(userId: String): Flow<String?> {
        val key = activeConversationKey(userId)
        return context.dataStore.data.map { it[key] }
    }

    suspend fun setActiveConversationId(userId: String, id: String?) {
        context.dataStore.edit { prefs ->
            val key = activeConversationKey(userId)
            if (id == null) prefs.remove(key) else prefs[key] = id
        }
    }
}