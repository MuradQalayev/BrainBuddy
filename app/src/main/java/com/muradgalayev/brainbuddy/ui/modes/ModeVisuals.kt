package com.muradgalayev.brainbuddy.ui.modes

import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DirectionsRun
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents

// the icons and colours a mode can wear. stored as string keys on AppMode rather than as
// Compose types, so a mode survives the round trip through Room and Supabase, and so a key
// written by a newer build with an icon this version doesn't have falls back to the default
// instead of failing to load the mode at all
data class ModeIconOption(val key: String, @androidx.annotation.StringRes val labelRes: Int, val icon: ImageVector)

val modeIconOptions = listOf(
    ModeIconOption("mode", R.string.mode_icon_general, Icons.Rounded.Tune),
    ModeIconOption("work", R.string.mode_work, Icons.Rounded.Work),
    ModeIconOption("weekend", R.string.mode_weekend, Icons.Rounded.Weekend),
    ModeIconOption("focus", R.string.home_focus_widget, Icons.Rounded.Whatshot),
    ModeIconOption("quiet", R.string.mode_icon_quiet, Icons.Rounded.DoNotDisturbOn),
    ModeIconOption("rest", R.string.mode_icon_rest, Icons.Rounded.Bedtime),
    ModeIconOption("study", R.string.mode_icon_study, Icons.Rounded.MenuBook),
    ModeIconOption("exercise", R.string.mode_icon_exercise, Icons.Rounded.DirectionsRun),
    ModeIconOption("social", R.string.mode_icon_social, Icons.Rounded.Groups),
    ModeIconOption("calm", R.string.theme_calm, Icons.Rounded.SelfImprovement),
)

fun modeIcon(key: String?): ImageVector =
    modeIconOptions.firstOrNull { it.key == key }?.icon ?: Icons.Rounded.Tune

data class ModeAccentOption(val key: String, @androidx.annotation.StringRes val labelRes: Int)

// named roles rather than literal colours, so a mode stays legible in every theme. a mode that
// hard-coded orange would fight the theme the user actually picked
val modeAccentOptions = listOf(
    ModeAccentOption("theme", R.string.mode_accent_theme),
    ModeAccentOption("focus", R.string.home_focus_widget),
    ModeAccentOption("calm", R.string.theme_calm),
    ModeAccentOption("warm", R.string.mode_accent_warm),
    ModeAccentOption("neutral", R.string.mode_accent_neutral),
)

@Composable
fun modeAccentColor(key: String?): Color {
    val accents = MaterialTheme.myndoraAccents
    return when (key) {
        "focus" -> accents.accent
        "calm" -> accents.support
        "warm" -> accents.accentEnd
        "neutral" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
}

// 'Mon-Fri 09:00-17:00', or null when the mode is switched by hand only
@Composable
fun scheduleSummary(days: Set<Int>, startMinute: Int, endMinute: Int, enabled: Boolean): String? {
    if (!enabled || days.isEmpty()) return null
    return "${daysLabel(days)} · ${minutesLabel(startMinute)}–${minutesLabel(endMinute)}"
}

fun minutesLabel(minuteOfDay: Int): String {
    if (minuteOfDay == 24 * 60) return "24:00"
    val m = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    return "%02d:%02d".format(m / 60, m % 60)
}

// one letter per ISO day, Monday first, in the app's language: M T W T F S S, or L M M G V S D
fun dayInitials(): List<String> = java.time.DayOfWeek.entries.map {
    it.getDisplayName(java.time.format.TextStyle.NARROW, java.util.Locale.getDefault())
}

private fun shortDay(day: java.time.DayOfWeek): String =
    day.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
        .removeSuffix(".")
        .replaceFirstChar { it.titlecase(java.util.Locale.getDefault()) }

@Composable
fun daysLabel(days: Set<Int>): String = when {
    days.size == 7 -> stringResource(R.string.mode_every_day)
    days == setOf(1, 2, 3, 4, 5) -> "${shortDay(java.time.DayOfWeek.MONDAY)}–${shortDay(java.time.DayOfWeek.FRIDAY)}"
    days == setOf(6, 7) -> "${shortDay(java.time.DayOfWeek.SATURDAY)}–${shortDay(java.time.DayOfWeek.SUNDAY)}"
    else -> dayInitials().let { initials -> (1..7).filter { it in days }.joinToString("") { initials[it - 1] } }
}
