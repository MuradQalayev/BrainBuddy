package com.muradgalayev.brainbuddy.ui.calendar

import androidx.compose.ui.graphics.Color

data class EventColor(
    val key: String,
    val accent: Color,
    val displayName: String,
)

// 8 curated tones — designed to harmonize with the warm-orange brand
// without all leaning warm, so events stay distinguishable.
val EventColors: List<EventColor> = listOf(
    EventColor("coral", Color(0xFFC75A4A), "Coral"),
    EventColor("amber", Color(0xFFD9B05C), "Amber"),
    EventColor("sage", Color(0xFF8AAE7E), "Sage"),
    EventColor("sky", Color(0xFF7FA3C9), "Sky"),
    EventColor("lilac", Color(0xFFA88AB8), "Lilac"),
    EventColor("rose", Color(0xFFD68FA8), "Rose"),
    EventColor("slate", Color(0xFF7A8A95), "Slate"),
    EventColor("sand", Color(0xFFC9A87B), "Sand"),
)

// Map legacy stored values from the original red/blue/yellow scheme.
private val LegacyAliases: Map<String, String> = mapOf(
    "red" to "coral",
    "blue" to "sky",
    "yellow" to "amber",
)

const val DefaultEventColorKey: String = "sky"

fun resolveEventColor(key: String?): EventColor {
    val normalized = key?.lowercase()?.let { LegacyAliases[it] ?: it }
    return EventColors.firstOrNull { it.key == normalized }
        ?: EventColors.first { it.key == DefaultEventColorKey }
}
