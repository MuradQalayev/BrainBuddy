package com.muradgalayev.brainbuddy.ui.pomodoro.utils

fun formatSessionTime(endTime: Long): String {
    if (endTime <= 0L) return "Unknown time"

    val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(endTime))
}