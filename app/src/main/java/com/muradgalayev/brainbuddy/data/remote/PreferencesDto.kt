package com.muradgalayev.brainbuddy.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PreferencesDto(
    @SerialName("user_id")
    val userId: String,
    @SerialName("theme_mode")
    val themeMode: String? = null,
    @SerialName("font_mode")
    val fontMode: String? = null,
    @SerialName("font_size")
    val fontSize: String? = null,
    @SerialName("focus_mode_enabled")
    val focusModeEnabled: Boolean? = null,
    @SerialName("simplified_workspace")
    val simplifiedWorkspace: Boolean? = null,
    @SerialName("enabled_nav_items")
    val enabledNavItems: String? = null
)
