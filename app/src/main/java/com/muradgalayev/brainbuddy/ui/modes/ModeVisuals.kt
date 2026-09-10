package com.muradgalayev.brainbuddy.ui.modes

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
data class ModeIconOption(val key: String, val label: String, val icon: ImageVector)

val modeIconOptions = listOf(
    ModeIconOption("mode", "General", Icons.Rounded.Tune),
    ModeIconOption("work", "Work", Icons.Rounded.Work),
    ModeIconOption("weekend", "Weekend", Icons.Rounded.Weekend),
    ModeIconOption("focus", "Focus", Icons.Rounded.Whatshot),
    ModeIconOption("quiet", "Quiet", Icons.Rounded.DoNotDisturbOn),
    ModeIconOption("rest", "Rest", Icons.Rounded.Bedtime),
    ModeIconOption("study", "Study", Icons.Rounded.MenuBook),
    ModeIconOption("exercise", "Exercise", Icons.Rounded.DirectionsRun),
    ModeIconOption("social", "Social", Icons.Rounded.Groups),
    ModeIconOption("calm", "Calm", Icons.Rounded.SelfImprovement),
)

fun modeIcon(key: String?): ImageVector =
    modeIconOptions.firstOrNull { it.key == key }?.icon ?: Icons.Rounded.Tune

data class ModeAccentOption(val key: String, val label: String)

// named roles rather than literal colours, so a mode stays legible in every theme. a mode that
// hard-coded orange would fight the theme the user actually picked
val modeAccentOptions = listOf(
    ModeAccentOption("theme", "Theme colour"),
    ModeAccentOption("focus", "Focus"),
    ModeAccentOption("calm", "Calm"),
    ModeAccentOption("warm", "Warm"),
    ModeAccentOption("neutral", "Neutral"),
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
fun scheduleSummary(days: Set<Int>, startMinute: Int, endMinute: Int, enabled: Boolean): String? {
    if (!enabled || days.isEmpty()) return null
    return "${daysLabel(days)} · ${minutesLabel(startMinute)}–${minutesLabel(endMinute)}"
}

fun minutesLabel(minuteOfDay: Int): String {
    if (minuteOfDay == 24 * 60) return "24:00"
    val m = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    return "%02d:%02d".format(m / 60, m % 60)
}

private val dayInitials = listOf("M", "T", "W", "T", "F", "S", "S")

fun daysLabel(days: Set<Int>): String = when {
    days.size == 7 -> "Every day"
    days == setOf(1, 2, 3, 4, 5) -> "Mon–Fri"
    days == setOf(6, 7) -> "Sat–Sun"
    else -> (1..7).filter { it in days }.joinToString("") { dayInitials[it - 1] }
}
