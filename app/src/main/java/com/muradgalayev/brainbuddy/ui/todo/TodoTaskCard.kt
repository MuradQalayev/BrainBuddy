package com.muradgalayev.brainbuddy.ui.todo

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.outlined.OutlinedFlag
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TaskCard(
    palette: TodoPalette,
    task: TaskUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFlagClick: () -> Unit
) {
    val flagColor by animateColorAsState(
        targetValue = if (task.flagged) palette.flagRed else palette.muted.copy(alpha = 0.45f),
        animationSpec = tween(250),
        label = "flagColor"
    )

    val baseHeight = 70
    val extraPerHour = 20

    val hours = (task.durationMinutes / 60f)

    val cardHeight = (baseHeight + (hours * extraPerHour))
        .coerceIn(80f, 180f)
        .dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = palette.cardBg),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(cardHeight),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(8.dp)
                    .height(cardHeight)
                    .clip(RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp))
                    .background(task.accent)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TaskStatusCircle(palette = palette, completed = task.completed)
                Spacer(Modifier.width(14.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = task.title,
                        color = if (task.completed) palette.muted else palette.ink,
                        fontSize = 16.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // attribution, when a connection put this on your list. directly under the title, since 'who
                    // added this' is the first question about a task you don't remember writing
                    task.addedByName?.let { author ->
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(task.accent.copy(alpha = .16f))
                                    .padding(horizontal = 7.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Diversity3,
                                    contentDescription = null,
                                    tint = task.accent,
                                    modifier = Modifier.size(11.dp),
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = "Added by $author",
                                    color = task.accent,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    if (task.flagged && !task.completed) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "HIGH PRIORITY",
                            color = palette.flagRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(palette.flagRed.copy(alpha = .12f))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }

                    if (!task.subtitle.isNullOrEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = task.subtitle,
                            color = palette.muted,
                            fontSize = 13.sp,
                            lineHeight = 16.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (!task.timeRange.isNullOrEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = task.timeRange,
                            color = palette.ink.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onFlagClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (task.flagged) Icons.Outlined.Flag else Icons.Outlined.OutlinedFlag,
                            contentDescription = if (task.flagged) "Remove flag" else "Flag as important",
                            tint = flagColor,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    if (!task.trailingDate.isNullOrEmpty()) {
                        Text(
                            text = task.trailingDate,
                            color = palette.muted.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
