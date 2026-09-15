package com.muradgalayev.brainbuddy.ui.pomodoro.utils

import androidx.compose.runtime.Composable
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

@Composable
fun formatSessionTime(endTime: Long): String {
    if (endTime <= 0L) return stringResource(R.string.focus_unknown_time)

    val formatter = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return formatter.format(java.util.Date(endTime))
}