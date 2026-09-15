package com.muradgalayev.brainbuddy.ui.todo

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class CategoryUi(
    val id: String,
    val title: com.muradgalayev.brainbuddy.ui.utils.UiText,
    val selected: Boolean = false,
)

@Immutable
data class TaskUi(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val timeRange: String? = null,
    val durationMinutes: Int = 60,
    val trailingDate: String,
    val accent: Color,
    val completed: Boolean = false,
    val flagged: Boolean = false,
    // name of the connection who added this task to your list, or null when you created it. drives
    // the 'Added by' badge, since a task you never wrote appearing in your list needs to say where
    // it came from
    val addedByName: String? = null,
)