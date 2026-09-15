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
    @androidx.annotation.StringRes val labelRes: Int,
    val icon: Int
) {
    // fixed, always in the navbar
    object Home : Screen("home", R.string.home_title, R.drawable.ic_house)
    object Activity : Screen("activity", R.string.ws_title, R.drawable.ic_workspace)
    object Calendar : Screen("calendar", R.string.together_scope_calendar, R.drawable.ic_calendar)
    object Settings : Screen("settings", R.string.common_settings, R.drawable.ic_settings)

    // optional screens, toggled via Quick Access in Settings
    object Todo : Screen("todo", R.string.nav_todo, R.drawable.ic_todo)
    object Pomodoro : Screen("pomodoro", R.string.intake_coping_pomodoro, R.drawable.ic_timer)
    object ShapeFlow : Screen("shape_flow", R.string.nav_shape_flow, R.drawable.ic_activity)

    // detail screens
    object TaskDetail : Screen("task_detail", R.string.nav_task_detail, R.drawable.ic_check)
    object TaskBreakdown : Screen("task_breakdown", R.string.bd_title, R.drawable.ic_calendar)
    object Health : Screen("health", R.string.widget_health, R.drawable.health)
    object WellnessSummary : Screen("wellness_summary", R.string.settings_health_connect, R.drawable.health)
    object MedicationEdit : Screen("medication_edit", R.string.deep_medication, R.drawable.health)
    object ActivityGoals : Screen("wellness_activity_goals", R.string.nav_activity_goals, R.drawable.health)
    object WellnessEdit : Screen("wellness_edit", R.string.ws_edit_summary, R.drawable.health)
    object Medications : Screen("wellness_medications", R.string.ws_medications, R.drawable.health)
    object Modes : Screen("modes", R.string.settings_modes, R.drawable.ic_settings)
    object ModeEdit : Screen("mode_edit", R.string.widget_mode, R.drawable.ic_settings)

    // auth
    object Auth : Screen("auth", R.string.nav_login, R.drawable.ic_ai)

    // splash
    object Splash : Screen("splash", R.string.nav_splash, R.drawable.ic_ai)

    // onboarding: appearance first, then the ADHD profile setup
    object OnboardingAppearance : Screen("onboarding_appearance", R.string.setup_make_it_yours, R.drawable.ic_ai)
    object OnboardingChoice : Screen("onboarding_choice", R.string.nav_choose_setup, R.drawable.ic_ai)
    object QuickSetup : Screen("onboarding_quick", R.string.onboarding_quick_setup, R.drawable.ic_ai)
    object DeepDive : Screen("onboarding_deep", R.string.onboarding_deep_dive, R.drawable.ic_ai)

    // settings sub-pages
    object EditProfile : Screen("settings_edit_profile", R.string.settings_edit_profile_title, R.drawable.ic_ai)
    object Customization : Screen("settings_customization", R.string.settings_customization, R.drawable.ic_ai)
    object NotificationSettings : Screen("settings_notifications", R.string.settings_notifications, R.drawable.ic_ai)
    object AiSettings : Screen("settings_ai", R.string.ai_myndora_ai, R.drawable.ic_ai)
    object LinkedAccounts : Screen("settings_linked_accounts", R.string.settings_linked_accounts, R.drawable.ic_ai)
    object LinkedDevices : Screen("settings_linked_devices", R.string.settings_linked_devices, R.drawable.ic_ai)

    // Myndora Plus, the paid plan (beta). takes an optional feature key to highlight
    object Plan : Screen("plan", R.string.plan_name, R.drawable.ic_ai)

    // Myndora Together: connections, requests, per-person sharing
    object Together : Screen("together", R.string.together_title, R.drawable.ic_ai)
    object AddConnection : Screen("together_add", R.string.together_add_someone, R.drawable.ic_ai)
    object NearbyAdd : Screen("together_nearby", R.string.together_nearby_title, R.drawable.ic_ai)
    object ConnectionProfile : Screen("together_profile", R.string.together_connection, R.drawable.ic_ai)

    // care nearby: map and list of clinics, pharmacies, ASL
    object CareNearby : Screen("care_nearby", R.string.ws_find_care, R.drawable.ic_ai)
    object Reservation : Screen("reservation", R.string.nav_book_appointment, R.drawable.ic_calendar)
    object ReservationHistory : Screen("reservation_history", R.string.nav_appointments, R.drawable.ic_calendar)

    // more, shown when overflow is needed
    object More : Screen("more", R.string.nav_more, R.drawable.ic_more)
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
    Screen.NearbyAdd.route to Screen.Activity.route,
    Screen.ConnectionProfile.route to Screen.Activity.route,

    // reached from the Calendar
    Screen.TaskBreakdown.route to Screen.Calendar.route,
    Screen.Modes.route to Screen.Settings.route,
    Screen.ModeEdit.route to Screen.Modes.route,
    Screen.TaskDetail.route to Screen.Calendar.route,

    // reached from Settings
    Screen.Plan.route to Screen.Settings.route,
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
