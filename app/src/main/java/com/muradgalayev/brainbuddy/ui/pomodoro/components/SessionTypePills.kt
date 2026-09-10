package com.muradgalayev.brainbuddy.ui.pomodoro.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
    accentColor: Color,
    collapseToSelected: Boolean = false
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        PomodoroSessionType.entries.forEach { type ->
            val selected = type == currentType
            val visible = !collapseToSelected || selected

            val label = when (type) {
                PomodoroSessionType.FOCUS -> "Focus"
                PomodoroSessionType.BREAK -> "Break"
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

            AnimatedVisibility(
                visible = visible,
                enter = fadeIn(animationSpec = tween(220)) +
                        expandHorizontally(animationSpec = tween(250)),
                exit = fadeOut(animationSpec = tween(180)) +
                        shrinkHorizontally(animationSpec = tween(220))
            ) {
                Surface(
                    modifier = if (collapseToSelected) {
                        Modifier.widthIn(min = 96.dp)
                    } else {
                        Modifier.weight(1f)
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = bgColor,
                    onClick = { if (enabled) onSelect(type) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(vertical = 10.dp, horizontal = 12.dp)
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
}