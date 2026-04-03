package com.muradgalayev.brainbuddy.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.muradgalayev.brainbuddy.R

sealed class Screen(
    val route: String,
    val label: String,
    val icon: Int
) {
    // Fixed — always in the navbar
    object Home : Screen("home", "Home", R.drawable.ic_house)
    object Activity : Screen("activity", "Activity", R.drawable.ic_activity)

    object Calendar : Screen("calendar", "Calendar", R.drawable.ic_calendar)
    object Settings : Screen("settings", "Settings", R.drawable.ic_settings)

    // Optional screens (toggled via Quick Access in Settings)
    object Todo : Screen("todo", "Todo", R.drawable.ic_todo)
    object Pomodoro : Screen("pomodoro", "Pomodoro", R.drawable.ic_timer)

    // Detail screens
    object TaskDetail : Screen("task_detail", "Task Detail", R.drawable.ic_check)

    // More — shown when overflow is needed
    object More : Screen("more", "More", R.drawable.ic_more)
}

val fixedNavItems = listOf(Screen.Home, Screen.Activity, Screen.Calendar, Screen.Settings)

val optionalNavItems = listOf(Screen.Todo, Screen.Pomodoro)
