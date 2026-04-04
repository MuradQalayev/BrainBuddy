package com.muradgalayev.brainbuddy

import android.os.Bundle
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
import javax.inject.Inject
import com.muradgalayev.brainbuddy.data.local.FontSize

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var preferencesManager: PreferencesManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}