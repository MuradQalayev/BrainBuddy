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
)

val LightPalette = TodoPalette(
    bg = Color(0xFFF6F4F8),
    ink = Color(0xFF2C295B),
    muted = Color(0xFF8F8CA1),
    cardBg = Color(0xFFFFFFFF),
    lavender = Color(0xFF9A7CF3),
    lavenderSoft = Color(0xFFE7DFFF),
    pillBg = Color(0xFFF0EEF5),
    flagRed = Color(0xFFE53E3E),
    sky = Color(0xFF82C8FF),
    lilac = Color(0xFFD8A4FF),
    lime = Color(0xFFD0DB56),
    periwinkle = Color(0xFFB9C5FF),
    progressTrack = Color(0xFFEAE7F0),
    avatarBg = Color(0xFFC2B1FF),
    avatarFace = Color(0xFFD6B9FF),
    avatarEyes = Color(0xFFF4DDFF),
    searchCursor = Color(0xFF9A7CF3),
    dialogBorder = Color(0xFFF0EEF5),
)

val DarkPalette = TodoPalette(
    bg = Color(0xFF0F1115),
    ink = Color(0xFFE4E5EA),
    muted = Color(0xFF9A9DA6),
    cardBg = Color(0xFF1E2128),
    lavender = Color(0xFFB49BFF),
    lavenderSoft = Color(0xFF2D2547),
    pillBg = Color(0xFF282B34),
    flagRed = Color(0xFFFC5555),
    sky = Color(0xFF5DADEB),
    lilac = Color(0xFFC48FEE),
    lime = Color(0xFFB8C244),
    periwinkle = Color(0xFF8E9DE0),
    progressTrack = Color(0xFF2A2D36),
    avatarBg = Color(0xFF3D3460),
    avatarFace = Color(0xFF5A4B8A),
    avatarEyes = Color(0xFF7B6BAA),
    searchCursor = Color(0xFFB49BFF),
    dialogBorder = Color(0xFF32353F),
)