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
    taskRed = Color(0xFFC75A4A),
    taskBlue = Color(0xFF7FA3C9),
    taskYellow = Color(0xFFD9B05C),
    bg = Color(0xFFFAF7F2),
    ink = Color(0xFF2A2A2A),
    muted = Color(0xFF6B6B6B),
    cardBg = Color(0xFFFFFBF6),
    lavender = Color(0xFFD97A3D),
    lavenderSoft = Color(0xFFF5E1CB),
    pillBg = Color(0xFFF0E8DC),
    flagRed = Color(0xFFC75A4A),
    sky = Color(0xFF7FA3C9),
    lilac = Color(0xFFE8A878),
    lime = Color(0xFFB6C68A),
    periwinkle = Color(0xFFE8C8A8),
    progressTrack = Color(0xFFEFE8DC),
    avatarBg = Color(0xFFF5E1CB),
    avatarFace = Color(0xFFD97A3D),
    avatarEyes = Color(0xFFFFFBF6),
    searchCursor = Color(0xFFD97A3D),
    dialogBorder = Color(0xFFE5DCCE),
)

val DarkPalette = TodoPalette(
    bg = Color(0xFF1F1F1F),
    ink = Color(0xFFEDE7DF),
    muted = Color(0xFFA8A8A8),
    cardBg = Color(0xFF2B2B2B),
    lavender = Color(0xFFE89866),
    lavenderSoft = Color(0xFF4A2E1A),
    pillBg = Color(0xFF353535),
    flagRed = Color(0xFFD96A5A),
    sky = Color(0xFF9CB9D9),
    lilac = Color(0xFFE8B888),
    lime = Color(0xFFB6C68A),
    periwinkle = Color(0xFFD9B894),
    progressTrack = Color(0xFF353535),
    avatarBg = Color(0xFF4A2E1A),
    avatarFace = Color(0xFFE89866),
    avatarEyes = Color(0xFFF0B988),
    searchCursor = Color(0xFFE89866),
    dialogBorder = Color(0xFF3F3F3F),
    taskRed = Color(0xFFD96A5A),
    taskBlue = Color(0xFF9CB9D9),
    taskYellow = Color(0xFFD9C570),
)
