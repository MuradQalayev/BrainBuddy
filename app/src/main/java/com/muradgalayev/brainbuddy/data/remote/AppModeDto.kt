package com.muradgalayev.brainbuddy.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

// a mode row in app_modes. schedule and overrides are JsonElement rather than strings because
// the columns are jsonb: sending a serialised string would store a quoted blob Postgres treats
// as a single scalar, and reading it back would need double decoding.
// only fields that may legitimately be absent carry defaults. everything the user can clear, an
// accent removed or a schedule deleted, is nullable so clearing writes an explicit null rather
// than being dropped from the payload by encodeDefaults = false
@Serializable
data class AppModeDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    val name: String,
    val icon: String,
    val accent: String?,
    @SerialName("is_built_in")
    val isBuiltIn: Boolean,
    @SerialName("sort_index")
    val sortIndex: Int,
    val schedule: JsonElement?,
    val overrides: JsonElement,
)
