package com.muradgalayev.brainbuddy.ui.todo

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color


@Immutable
data class CategoryUi(
    val id: String,
    val title: String,
    val selected: Boolean = false,
)

@Immutable
data class TaskUi(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val trailingDate: String,
    val accent: Color,
    val completed: Boolean = false,
    val flagged: Boolean = false,
)