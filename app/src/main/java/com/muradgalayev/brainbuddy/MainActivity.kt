package com.muradgalayev.brainbuddy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.muradgalayev.brainbuddy.ui.navigation.NavGraph
import com.muradgalayev.brainbuddy.ui.theme.BrainBuddyTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BrainBuddyTheme {
                val navController = rememberNavController()
                NavGraph(navController = navController)
            }
        }
    }
}