package com.muradgalayev.brainbuddy.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Medication(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val dose: String = "",
    val slots: List<String> = emptyList(),
) {
    val timesPerDay: Int get() = slots.size
}

enum class MedicationSlot(val key: String, val label: String) {
    Morning("morning", "Morning"),
    Afternoon("afternoon", "Afternoon"),
    Evening("evening", "Evening"),
    Night("night", "Night");

    companion object {
        fun fromKey(key: String): MedicationSlot? = entries.firstOrNull { it.key == key }
    }
}
