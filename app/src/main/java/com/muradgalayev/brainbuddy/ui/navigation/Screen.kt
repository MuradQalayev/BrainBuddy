package com.muradgalayev.brainbuddy.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val label: String,
    val icon: ImageVector
) {
    // Fixed — always in the navbar
    object Home : Screen("home", "Home", Icons.Rounded.Home)
    object Activity : Screen("activity", "Activity", Icons.Rounded.FavoriteBorder)
    object Calendar : Screen("calendar", "Calendar", Icons.Rounded.CalendarMonth)
    object Settings : Screen("settings", "Settings", Icons.Rounded.Settings)

    // Optional screens (not in nav bar)
    object Todo : Screen("todo", "Todo", Icons.Rounded.CheckCircle)
    object TaskDetail : Screen("task_detail", "Task Detail", Icons.Rounded.CheckCircle)

    // More — shown when overflow is needed
    object More : Screen("more", "More", Icons.Rounded.MoreHoriz)
}

val fixedNavItems = listOf(Screen.Home, Screen.Activity, Screen.Calendar, Screen.Settings)

val optionalNavItems = listOf<Screen>()
