package com.muradgalayev.brainbuddy.ui.pomodoro.components

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.R
import kotlinx.coroutines.delay

// today's focus total, doubling as the entry point to history. when animated is false it stops
// cycling and just sits there: a chip that keeps flipping in the corner is movement in the
// user's peripheral vision, precisely what a focus timer shouldn't produce mid-session
@Composable
fun HistorySummarySwitcher(
    todayFocusMinutes: Int,
    accentColor: Color,
    onClick: () -> Unit,
    animated: Boolean = true
) {
    var showText by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(animated) {
        if (!animated) {
            showText = true
            return@LaunchedEffect
        }
        while (true) {
            showText = true
            delay(3000)
            showText = false
            delay(1500)
        }
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = accentColor.copy(alpha = 0.12f),
        onClick = onClick
    ) {
        Box(
            modifier = Modifier
                .height(36.dp)
                .width(108.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!animated) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_history),
                        contentDescription = "History",
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "${todayFocusMinutes}m today",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        maxLines = 1
                    )
                }
                return@Box
            }

            androidx.compose.animation.AnimatedContent(
                targetState = showText,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith
                            fadeOut(animationSpec = tween(180))
                },
                label = "history_summary_switch"
            ) { isTextVisible ->
                if (isTextVisible) {
                    Text(
                        text = if (todayFocusMinutes > 0) {
                            "${todayFocusMinutes}m today"
                        } else {
                            "0m today"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accentColor,
                        maxLines = 1
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_history),
                        contentDescription = "History",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}