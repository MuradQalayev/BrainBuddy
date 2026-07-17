package com.muradgalayev.brainbuddy.ui.pomodoro.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Coffee
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.data.local.PomodoroQueueState

/**
 * Compact "you are running this calendar plan" header that lives above the timer when a
 * queue is loaded. Shows the event title, the current step, and a station ribbon below.
 */
@Composable
fun CalendarPlanStrip(
    queue: PomodoroQueueState?,
    accentColor: Color,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = queue != null,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        if (queue == null) return@AnimatedVisibility
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val surface = if (isDark) Color(0xFF1F1F1F) else Color(0xFFFAF7F2)
        val ink = MaterialTheme.colorScheme.onSurface
        val muted = ink.copy(alpha = 0.55f)

        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(surface)
                .border(1.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarToday,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(15.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Calendar plan",
                        color = muted,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        text = queue.eventTitle,
                        color = ink,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ink.copy(alpha = 0.06f))
                        .clickable(onClick = onClear),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Stop following plan",
                        tint = muted,
                        modifier = Modifier.size(13.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Stations ribbon — proportional weights, current item highlighted
            val total = queue.items.sumOf { it.durationMs }.coerceAtLeast(1L)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                queue.items.forEachIndexed { index, item ->
                    val color = when {
                        index < queue.currentIndex -> accentColor
                        index == queue.currentIndex -> accentColor.copy(alpha = 0.9f)
                        item.isFocus -> accentColor.copy(alpha = 0.25f)
                        else -> ink.copy(alpha = 0.15f)
                    }
                    Box(
                        modifier = Modifier
                            .weight(item.durationMs.toFloat() / total.toFloat())
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(color)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            val current = queue.current
            val currentLabel = if (current != null) {
                val n = queue.currentIndex + 1
                val total = queue.items.size
                "Step $n of $total · ${current.title}"
            } else {
                "Plan complete"
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (current != null && !current.isFocus) {
                    Icon(
                        imageVector = Icons.Outlined.Coffee,
                        contentDescription = null,
                        tint = muted,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = currentLabel,
                    color = muted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
