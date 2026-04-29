package com.muradgalayev.brainbuddy

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.ui.navigation.NavGraph
import com.muradgalayev.brainbuddy.ui.theme.BrainBuddyTheme
import dagger.hilt.android.AndroidEntryPoint
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject
import com.muradgalayev.brainbuddy.data.local.FontSize

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var supabaseClient: SupabaseClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        safeHandleDeeplinks(intent)
        enableEdgeToEdge()
        setContent {
            val themeMode by preferencesManager.themeMode.collectAsState(initial = ThemeMode.System)
            val fontMode by preferencesManager.fontMode.collectAsState(initial = FontMode.Classic)
            val enabledNavItems by preferencesManager.enabledNavItems.collectAsState(initial = emptySet())
            val fontSize by preferencesManager.fontSize.collectAsState(initial = FontSize.Medium)

            BrainBuddyTheme(themeMode = themeMode, fontMode = fontMode,fontSize = fontSize) {
                val navController = rememberNavController()

                NavGraph(
                    navController = navController,
                    enabledOptionalRoutes = enabledNavItems
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        safeHandleDeeplinks(intent)
    }

    // Supabase's handleDeeplinks throws if the OAuth redirect URL has no
    // #access_token fragment (e.g. PKCE code flow, user-cancelled link, error
    // redirect). Swallow it so the app doesn't crash on a callback we can't parse.
    private fun safeHandleDeeplinks(intent: Intent?) {
        if (intent == null) return
        try {
            supabaseClient.handleDeeplinks(intent)
        } catch (t: Throwable) {
            Log.w("MainActivity", "Ignored unparseable auth deep link: ${intent.data}", t)
        }
    }
}