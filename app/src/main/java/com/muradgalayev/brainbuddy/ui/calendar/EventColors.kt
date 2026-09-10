package com.muradgalayev.brainbuddy.ui.calendar

import androidx.compose.ui.graphics.Color

data class EventColor(
    val key: String,
    val accent: Color,
    val displayName: String,
)

// 8 curated tones, spread around the wheel so two events are never mistaken for each other,
// at a saturation that holds up against the app's own accent. the previous set was desaturated
// enough that coral, rose and sand read as three shades of the same dusty pink on a phone
val EventColors: List<EventColor> = listOf(
    EventColor("coral", Color(0xFFEF4444), "Coral"),
    EventColor("amber", Color(0xFFF59E0B), "Amber"),
    EventColor("sage", Color(0xFF10B981), "Sage"),
    EventColor("sky", Color(0xFF0EA5E9), "Sky"),
    EventColor("lilac", Color(0xFFA855F7), "Lilac"),
    EventColor("rose", Color(0xFFEC4899), "Rose"),
    EventColor("slate", Color(0xFF64748B), "Slate"),
    EventColor("sand", Color(0xFFD97706), "Sand"),
)

// map legacy stored values from the original red/blue/yellow scheme
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
