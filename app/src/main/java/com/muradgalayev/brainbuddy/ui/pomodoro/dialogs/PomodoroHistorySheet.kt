package com.muradgalayev.brainbuddy.ui.pomodoro.dialogs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType
import com.muradgalayev.brainbuddy.domain.model.PomodoroSession
import com.muradgalayev.brainbuddy.ui.pomodoro.FocusHistory
import com.muradgalayev.brainbuddy.ui.pomodoro.formatFocusMinutes
import com.muradgalayev.brainbuddy.ui.pomodoro.utils.formatSessionTime

private val ChartHeight = 104.dp

// focus history. the previous version listed the last five sessions and nothing else, which
// answers 'what did I just do', a question the user already knows the answer to. the week chart
// leads instead, because the thing worth knowing from a history screen is whether the habit is
// holding up
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroHistorySheet(
    history: FocusHistory,
    sessions: List<PomodoroSession>,
    accentColor: Color,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sortedSessions = sessions.sortedByDescending { it.endTime }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
                .padding(top = 18.dp, bottom = 30.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                        RoundedCornerShape(999.dp),
                    )
            )

            Spacer(Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Focus history",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Last 7 days",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (history.streakDays > 1) {
                    StreakBadge(days = history.streakDays, accentColor = accentColor)
                }
            }

            Spacer(Modifier.height(22.dp))

            WeekChart(history = history, accentColor = accentColor)

            Spacer(Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HistoryStat(
                    value = history.weekTotalLabel,
                    label = "this week",
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                )
                HistoryStat(
                    value = history.bestDayLabel,
                    label = "best day",
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                )
                HistoryStat(
                    value = "${history.week.count { it.minutes > 0 }}",
                    label = "active days",
                    accentColor = accentColor,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(26.dp))

            Text(
                text = "Recent sessions",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(12.dp))

            if (sortedSessions.isEmpty()) {
                EmptyHistoryState()
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sortedSessions.forEach { session -> SessionRow(session) }
                }
            }
        }
    }
}

@Composable
private fun WeekChart(history: FocusHistory, accentColor: Color) {
    val peak = history.chartPeakMinutes

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ChartHeight + 26.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        history.week.forEach { day ->
            // empty days keep a sliver of height so the row still reads as seven days rather than a chart
            // with holes punched in it
            val fraction = if (day.minutes <= 0) 0f else (day.minutes.toFloat() / peak)
            val animatedFraction by animateFloatAsState(
                targetValue = fraction.coerceIn(0f, 1f),
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "barHeight",
            )

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(ChartHeight),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    // a track behind every bar gives the eye a consistent baseline
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.045f)
                            )
                    )

                    if (day.minutes > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(animatedFraction.coerceAtLeast(0.06f))
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            accentColor,
                                            accentColor.copy(alpha = 0.62f),
                                        )
                                    )
                                )
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text(
                    text = day.label,
                    fontSize = 11.sp,
                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Medium,
                    color = if (day.isToday) accentColor
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun StreakBadge(days: Int, accentColor: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(accentColor.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "🔥",
            fontSize = 13.sp,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = "$days day streak",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor,
        )
    }
}

@Composable
private fun HistoryStat(
    value: String,
    label: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 13.dp),
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = accentColor,
            maxLines = 1,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun SessionRow(session: PomodoroSession) {
    // rows written before the two break types merged still carry the old strings
    val type = PomodoroSessionType.fromStored(session.sessionType)
    val isFocus = type == PomodoroSessionType.FOCUS

    val dotColor = if (isFocus) MaterialTheme.colorScheme.primary
    else MaterialTheme.colorScheme.tertiary

    // actual, not planned: a session finished early on the ring is worth what it ran for
    val minutes = (session.actualDurationMs / 60000L).toInt()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )

        Spacer(Modifier.width(11.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = if (isFocus) "Focus" else "Break",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = formatSessionTime(session.endTime),
                fontSize = 11.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = formatFocusMinutes(minutes),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = dotColor,
        )
    }
}

@Composable
private fun EmptyHistoryState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_history),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(21.dp),
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "No sessions yet",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = "Finish a focus session and it will show up here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
