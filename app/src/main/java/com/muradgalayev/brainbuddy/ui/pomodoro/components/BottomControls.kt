package com.muradgalayev.brainbuddy.ui.pomodoro.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.data.local.TimerState
import com.muradgalayev.brainbuddy.ui.pomodoro.ControlCircle
import com.muradgalayev.brainbuddy.ui.pomodoro.GradientCircleButton


@Composable
fun BottomControls(
    timerState: TimerState,
    accentColor: Color,
    accentGradient: Brush,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onStop: () -> Unit,
    onSkip: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (timerState) {
            TimerState.IDLE -> {
                GradientCircleButton(
                    icon = Icons.Rounded.PlayArrow,
                    label = "Start",
                    gradient = accentGradient,
                    size = 72,
                    onClick = onStart
                )
            }
            TimerState.RUNNING -> {
                ControlCircle(Icons.Rounded.Refresh, "Reset", onReset,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.surfaceContainerHigh)
                Spacer(modifier = Modifier.width(20.dp))
                GradientCircleButton(Icons.Rounded.Pause, "Pause", accentGradient, 72, onPause)
                Spacer(modifier = Modifier.width(20.dp))
                ControlCircle(Icons.Rounded.SkipNext, "Skip", onSkip,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.surfaceContainerHigh)
            }
            TimerState.PAUSED -> {
                ControlCircle(Icons.Rounded.Stop, "Stop", onStop,
                    MaterialTheme.colorScheme.error,
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.width(20.dp))
                GradientCircleButton(Icons.Rounded.PlayArrow, "Resume", accentGradient, 72, onResume)
                Spacer(modifier = Modifier.width(20.dp))
                ControlCircle(Icons.Rounded.Refresh, "Reset", onReset,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.surfaceContainerHigh)
            }
            TimerState.COMPLETED -> {
                ControlCircle(Icons.Rounded.Refresh, "Restart", onReset,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    MaterialTheme.colorScheme.surfaceContainerHigh)
                Spacer(modifier = Modifier.width(20.dp))
                GradientCircleButton(Icons.Rounded.SkipNext, "Next", accentGradient, 72, onSkip)
            }
        }
    }
}