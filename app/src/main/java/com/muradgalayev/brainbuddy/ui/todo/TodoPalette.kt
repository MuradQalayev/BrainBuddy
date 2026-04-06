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
    taskBlue = Color(0xFF64B5F6),
    taskYellow = Color(0xFFFFD54F),
    bg = Color(0xFFFFFFFF),
    ink = Color(0xFF1A1C20),
    muted = Color(0xFF5A5E68),
    cardBg = Color(0xFFFFFFFF),
    lavender = Color(0xFF3D7BD9),
    lavenderSoft = Color(0xFFD6E4FF),
    pillBg = Color(0xFFF0F2F7),
    flagRed = Color(0xFFE53E3E),
    sky = Color(0xFF82C8FF),
    lilac = Color(0xFF8AB8F0),
    lime = Color(0xFFD0DB56),
    periwinkle = Color(0xFFB9C5FF),
    progressTrack = Color(0xFFE4E7EF),
    avatarBg = Color(0xFFD6E4FF),
    avatarFace = Color(0xFF8AB8F0),
    avatarEyes = Color(0xFFD6E4FF),
    searchCursor = Color(0xFF3D7BD9),
    dialogBorder = Color(0xFFF0F2F7),
)

val DarkPalette = TodoPalette(
    bg = Color(0xFF0F1115),
    ink = Color(0xFFE4E5EA),
    muted = Color(0xFF9A9DA6),
    cardBg = Color(0xFF1E2128),
    lavender = Color(0xFF8AB8F0),
    lavenderSoft = Color(0xFF1C3D6E),
    pillBg = Color(0xFF282B34),
    flagRed = Color(0xFFFC5555),
    sky = Color(0xFF5DADEB),
    lilac = Color(0xFF8AB8F0),
    lime = Color(0xFFB8C244),
    periwinkle = Color(0xFF8E9DE0),
    progressTrack = Color(0xFF2A2D36),
    avatarBg = Color(0xFF1C3D6E),
    avatarFace = Color(0xFF3D7BD9),
    avatarEyes = Color(0xFF8AB8F0),
    searchCursor = Color(0xFF8AB8F0),
    dialogBorder = Color(0xFF32353F),
    taskRed = Color(0xFFEF5350),
    taskBlue = Color(0xFF42A5F5),
    taskYellow = Color(0xFFFFCA28),
)
