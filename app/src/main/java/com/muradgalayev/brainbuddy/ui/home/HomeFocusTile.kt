package com.muradgalayev.brainbuddy.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents

// three durations, one tap each. the Pomodoro screen already does this properly, but it asks
// you to go there, pick a length and press play, and picking a length is exactly the decision
// that stalls a start. five minutes is the important one: short enough to agree to when the
// task itself feels impossible, and starting is nearly always the hard part
@Composable
fun HomeFocusTile(
    running: Boolean,
    onStart: (Int) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = MaterialTheme.myndoraAccents
    val haptics = LocalHapticFeedback.current

    HomeCard(modifier = modifier, accent = accents.support, onClick = if (running) onOpen else null) {
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(38.dp)
                        .clip(HomeInnerShape)
                        .background(accents.support.copy(alpha = .14f))
                        .border(1.dp, accents.support.copy(alpha = .18f), HomeInnerShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Timer, null, tint = accents.support, modifier = Modifier.size(19.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    HomeSectionLabel("FOCUS", accents.support)
                    Text(
                        if (running) "Session running" else "Pick a block",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }

            if (running) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = accents.support, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Open timer",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = accents.support,
                    )
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    listOf(5, 15, 25).forEach { minutes ->
                        MinuteButton(
                            minutes = minutes,
                            accent = accents.support,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onStart(minutes)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MinuteButton(minutes: Int, accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .93f else 1f,
        animationSpec = spring(dampingRatio = .55f),
        label = "focus_minute_press",
    )
    Box(
        modifier
            .scale(scale)
            .height(44.dp)
            .clip(HomeInnerShape)
            .background(accent.copy(alpha = .13f))
            .border(1.dp, accent.copy(alpha = .2f), HomeInnerShape)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "$minutes",
            style = MaterialTheme.typography.titleMedium.tabular(),
            fontWeight = FontWeight.Bold,
            color = accent,
            maxLines = 1,
        )
    }
}
