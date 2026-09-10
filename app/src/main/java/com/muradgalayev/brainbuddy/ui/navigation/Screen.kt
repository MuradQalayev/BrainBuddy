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
    // fixed, always in the navbar
    object Home : Screen("home", "Home", R.drawable.ic_house)
    object Activity : Screen("activity", "Workspace", R.drawable.ic_activity)
    object Calendar : Screen("calendar", "Calendar", R.drawable.ic_calendar)
    object Settings : Screen("settings", "Settings", R.drawable.ic_settings)

    // optional screens, toggled via Quick Access in Settings
    object Todo : Screen("todo", "Todo", R.drawable.ic_todo)
    object Pomodoro : Screen("pomodoro", "Pomodoro", R.drawable.ic_timer)
    object ShapeFlow : Screen("shape_flow", "Shape Flow", R.drawable.ic_activity)

    // detail screens
    object TaskDetail : Screen("task_detail", "Task Detail", R.drawable.ic_check)
    object TaskBreakdown : Screen("task_breakdown", "Breakdown", R.drawable.ic_calendar)
    object Health : Screen("health", "Health", R.drawable.health)
    object WellnessSummary : Screen("wellness_summary", "Health Connect", R.drawable.health)
    object MedicationEdit : Screen("medication_edit", "Medication", R.drawable.health)
    object ActivityGoals : Screen("wellness_activity_goals", "Activity Goals", R.drawable.health)
    object WellnessEdit : Screen("wellness_edit", "Edit Summary", R.drawable.health)
    object Medications : Screen("wellness_medications", "Medications", R.drawable.health)
    object Modes : Screen("modes", "Modes", R.drawable.ic_settings)
    object ModeEdit : Screen("mode_edit", "Mode", R.drawable.ic_settings)

    // auth
    object Auth : Screen("auth", "Login", R.drawable.ic_ai)

    // splash
    object Splash : Screen("splash", "Splash", R.drawable.ic_ai)

    // onboarding: appearance first, then the ADHD profile setup
    object OnboardingAppearance : Screen("onboarding_appearance", "Make it yours", R.drawable.ic_ai)
    object OnboardingChoice : Screen("onboarding_choice", "Choose Setup", R.drawable.ic_ai)
    object QuickSetup : Screen("onboarding_quick", "Quick Setup", R.drawable.ic_ai)
    object DeepDive : Screen("onboarding_deep", "Deep Dive", R.drawable.ic_ai)

    // settings sub-pages
    object EditProfile : Screen("settings_edit_profile", "Edit Profile", R.drawable.ic_ai)
    object Customization : Screen("settings_customization", "Customization", R.drawable.ic_ai)
    object NotificationSettings : Screen("settings_notifications", "Notifications", R.drawable.ic_ai)
    object AiSettings : Screen("settings_ai", "Myndora AI", R.drawable.ic_ai)
    object LinkedAccounts : Screen("settings_linked_accounts", "Linked accounts", R.drawable.ic_ai)
    object LinkedDevices : Screen("settings_linked_devices", "Linked devices", R.drawable.ic_ai)

    // Myndora Together: connections, requests, per-person sharing
    object Together : Screen("together", "Myndora Together", R.drawable.ic_ai)
    object AddConnection : Screen("together_add", "Add someone", R.drawable.ic_ai)
    object ConnectionProfile : Screen("together_profile", "Connection", R.drawable.ic_ai)

    // care nearby: map and list of clinics, pharmacies, ASL
    object CareNearby : Screen("care_nearby", "Find Care", R.drawable.ic_ai)
    object Reservation : Screen("reservation", "Book appointment", R.drawable.ic_calendar)
    object ReservationHistory : Screen("reservation_history", "Appointments", R.drawable.ic_calendar)

    // more, shown when overflow is needed
    object More : Screen("more", "More", R.drawable.ic_more)
}

val fixedNavItems = listOf(Screen.Home, Screen.Activity, Screen.Calendar, Screen.Settings)

// which bottom-nav tab each detail screen belongs to. the navbar highlights by exact route
// match and falls back to the first tab for anything it doesn't recognise, so every detail
// screen lit up Home no matter which tab you had opened it from. back navigation was always
// correct, only the highlight lied
private val detailRouteParents: Map<String, String> = mapOf(
    // reached from the Workspace
    Screen.Todo.route to Screen.Activity.route,
    Screen.Pomodoro.route to Screen.Activity.route,
    Screen.ShapeFlow.route to Screen.Activity.route,
    Screen.Health.route to Screen.Activity.route,
    Screen.WellnessSummary.route to Screen.Activity.route,
    Screen.MedicationEdit.route to Screen.Activity.route,
    Screen.WellnessEdit.route to Screen.Activity.route,
    Screen.ActivityGoals.route to Screen.Activity.route,
    Screen.Medications.route to Screen.Activity.route,
    Screen.CareNearby.route to Screen.Activity.route,
    Screen.Reservation.route to Screen.Activity.route,
    Screen.ReservationHistory.route to Screen.Activity.route,
    Screen.Together.route to Screen.Activity.route,
    Screen.AddConnection.route to Screen.Activity.route,
    Screen.ConnectionProfile.route to Screen.Activity.route,

    // reached from the Calendar
    Screen.TaskBreakdown.route to Screen.Calendar.route,
    Screen.Modes.route to Screen.Settings.route,
    Screen.ModeEdit.route to Screen.Modes.route,
    Screen.TaskDetail.route to Screen.Calendar.route,

    // reached from Settings
    Screen.EditProfile.route to Screen.Settings.route,
    Screen.Customization.route to Screen.Settings.route,
    Screen.NotificationSettings.route to Screen.Settings.route,
    Screen.AiSettings.route to Screen.Settings.route,
    Screen.LinkedAccounts.route to Screen.Settings.route,
    Screen.LinkedDevices.route to Screen.Settings.route,
)

// the tab that should look selected for a route. strips query arguments first, since routes
// are registered as patterns like task_detail?taskId={taskId} and an exact-string lookup
// would never match. unknown routes come back unchanged rather than defaulting to a tab, so
// a new screen shows no false highlight until it's mapped here
fun tabRouteFor(route: String?): String? {
    if (route == null) return null
    var current = route.substringBefore('?')
    val visited = mutableSetOf<String>()
    while (visited.add(current)) {
        current = detailRouteParents[current] ?: return current
    }
    // a bad parent cycle should highlight nothing rather than an unrelated tab
    return null
}

// Todo and Pomodoro stay available from the workspace but are deliberately kept out of the
// bottom navigation, to reduce choices and visual distraction
val optionalNavItems: List<Screen> = emptyList()
