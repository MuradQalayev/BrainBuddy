package com.muradgalayev.brainbuddy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MedicationDoseLogDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    @SerialName("log_key")
    val logKey: String,
    val taken: Boolean,
    // epoch millis. decides the winner when two devices disagree about one dose
    @SerialName("updated_at_millis")
    val updatedAtMillis: Long,
)
