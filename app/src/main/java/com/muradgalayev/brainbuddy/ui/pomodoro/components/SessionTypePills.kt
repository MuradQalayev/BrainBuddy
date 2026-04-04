package com.muradgalayev.brainbuddy.ui.pomodoro.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType


@Composable
fun SessionTypePills(
    currentType: PomodoroSessionType,
    onSelect: (PomodoroSessionType) -> Unit,
    enabled: Boolean,
    accentColor: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        PomodoroSessionType.entries.forEach { type ->
            val selected = type == currentType
            val label = when (type) {
                PomodoroSessionType.FOCUS -> "Focus"
                PomodoroSessionType.SHORT_BREAK -> "Short Break"
                PomodoroSessionType.LONG_BREAK -> "Long Break"
            }
            val bgColor by animateColorAsState(
                targetValue = if (selected) accentColor.copy(alpha = 0.15f)
                else Color.Transparent,
                animationSpec = tween(250),
                label = "pillBg"
            )
            val textColor by animateColorAsState(
                targetValue = if (selected) accentColor
                else MaterialTheme.colorScheme.onSurfaceVariant,
                animationSpec = tween(250),
                label = "pillText"
            )

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                color = bgColor,
                onClick = { if (enabled) onSelect(type) }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
