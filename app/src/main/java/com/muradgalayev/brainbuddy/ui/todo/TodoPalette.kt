package com.muradgalayev.brainbuddy.ui.todo

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents

@Immutable
data class TodoPalette(
    val bg: Color,
    val ink: Color,
    val muted: Color,
    val cardBg: Color,
    val lavender: Color,
    val lavenderSoft: Color,
    val pillBg: Color,
    val flagRed: Color,
    val sky: Color,
    val lilac: Color,
    val lime: Color,
    val periwinkle: Color,
    val progressTrack: Color,
    val avatarBg: Color,
    val avatarFace: Color,
    val avatarEyes: Color,
    val searchCursor: Color,
    val dialogBorder: Color,
    val taskRed: Color,
    val taskBlue: Color,
    val taskYellow: Color,
)

// derived from the active theme rather than hardcoded, so switching colour theme carries the
// todo list with it. lavender is the accent slot, a name that predates the accent becoming
// themeable and is threaded through every Todo composable.
// the three task colours stay fixed: they're choices the user made per task, so they have to
// keep meaning the same thing after a theme change
@Composable
fun rememberTodoPalette(): TodoPalette {
    val c = MaterialTheme.colorScheme
    val accents = MaterialTheme.myndoraAccents
    return TodoPalette(
        bg = c.background,
        ink = c.onSurface,
        muted = c.onSurfaceVariant,
        cardBg = c.surface,
        lavender = accents.accent,
        lavenderSoft = c.primaryContainer,
        pillBg = c.surfaceContainer,
        flagRed = c.error,
        sky = accents.support,
        lilac = accents.accentEnd,
        lime = accents.supportEnd,
        periwinkle = accents.accentEnd,
        progressTrack = c.surfaceContainerHighest,
        avatarBg = c.primaryContainer,
        avatarFace = accents.accent,
        avatarEyes = c.surface,
        searchCursor = accents.accent,
        dialogBorder = c.outlineVariant,
        taskRed = Color(0xFFEF4444),
        taskBlue = Color(0xFF0EA5E9),
        taskYellow = Color(0xFFF59E0B),
    )
}
