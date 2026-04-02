package com.muradgalayev.brainbuddy.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { Light, Dark, System }
enum class FontMode { Classic, Modern, Rounded }

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val themeKey = stringPreferencesKey("theme_mode")
    private val fontKey = stringPreferencesKey("font_mode")
    private val enabledNavKey = stringSetPreferencesKey("enabled_nav_items")

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        when (prefs[themeKey]) {
            "Light" -> ThemeMode.Light
            "Dark" -> ThemeMode.Dark
            else -> ThemeMode.System
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

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = mode.name
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
}