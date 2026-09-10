package com.muradgalayev.brainbuddy.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// one consent decision, as stored in ai_consents. granted carries no default on purpose:
// supabase-kt serialises with encodeDefaults = false and postgrest-kt derives the written
// column list from the keys present in the body, so a field left at its default is dropped from
// the write entirely. a granted = false that silently failed to reach the server would be a
// revoke the user watched happen and that never took effect anywhere but their phone
@Serializable
data class AiConsentDto(
    @SerialName("user_id")
    val userId: String,
    val scope: String,
    val granted: Boolean,
    @SerialName("consent_version")
    val consentVersion: Int,
    @SerialName("decided_at")
    val decidedAt: String,
)
