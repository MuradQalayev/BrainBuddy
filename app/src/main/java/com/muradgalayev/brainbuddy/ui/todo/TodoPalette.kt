package com.muradgalayev.brainbuddy.ui.todo

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color


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

val LightPalette = TodoPalette(
    taskRed = Color(0xFFE57373),
    taskBlue = Color(0xFF8B8CF8),
    taskYellow = Color(0xFFFFD54F),
    bg = Color(0xFFFCFCFF),
    ink = Color(0xFF1B1B21),
    muted = Color(0xFF5C5B68),
    cardBg = Color(0xFFFFFFFF),
    lavender = Color(0xFF6366F1),
    lavenderSoft = Color(0xFFE0DFFF),
    pillBg = Color(0xFFF1F0F7),
    flagRed = Color(0xFFE53E3E),
    sky = Color(0xFF82C8FF),
    lilac = Color(0xFF9A7CF3),
    lime = Color(0xFFD0DB56),
    periwinkle = Color(0xFFB9C5FF),
    progressTrack = Color(0xFFE8E7F0),
    avatarBg = Color(0xFFE0DFFF),
    avatarFace = Color(0xFF8B8CF8),
    avatarEyes = Color(0xFFE0DFFF),
    searchCursor = Color(0xFF6366F1),
    dialogBorder = Color(0xFFF1F0F7),
)

val DarkPalette = TodoPalette(
    bg = Color(0xFF0F0F15),
    ink = Color(0xFFE5E4EC),
    muted = Color(0xFF9C9BA8),
    cardBg = Color(0xFF1E1E28),
    lavender = Color(0xFFA5A4FB),
    lavenderSoft = Color(0xFF2A2650),
    pillBg = Color(0xFF282834),
    flagRed = Color(0xFFFC5555),
    sky = Color(0xFF5DADEB),
    lilac = Color(0xFFB49BFF),
    lime = Color(0xFFB8C244),
    periwinkle = Color(0xFF8E9DE0),
    progressTrack = Color(0xFF2A2A36),
    avatarBg = Color(0xFF2A2650),
    avatarFace = Color(0xFF6366F1),
    avatarEyes = Color(0xFFA5A4FB),
    searchCursor = Color(0xFFA5A4FB),
    dialogBorder = Color(0xFF33333F),
    taskRed = Color(0xFFEF5350),
    taskBlue = Color(0xFF818CF8),
    taskYellow = Color(0xFFFFCA28),
)
