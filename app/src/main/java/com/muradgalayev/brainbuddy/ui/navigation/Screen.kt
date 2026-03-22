package com.muradgalayev.brainbuddy.ui.navigation

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Home : Screen("home")
    object Todo : Screen("todo")
    object Tools : Screen("tools")
    object Settings : Screen("settings")
}