package com.muradgalayev.brainbuddy.ui.calendar

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.WeekDay
import com.kizitonwose.calendar.core.WeekDayPosition
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

// Dot colors for multi-event indicators (like the reference shows)
private val dotColors = listOf(
    Color(0xFFD97A3D), // accent orange
    Color(0xFF7FA3C9), // accent blue
    Color(0xFFC75A4A), // muted red
    Color(0xFFB6C68A), // sage lime
    Color(0xFFE8A878), // soft peach
)

/* ── Calendar body (month or week) ── */
@Composable
fun CalendarBody(
    mode: CalendarMode,
    monthState: com.kizitonwose.calendar.compose.CalendarState,
    weekState: com.kizitonwose.calendar.compose.weekcalendar.WeekCalendarState,
    selectedDate: LocalDate,
    datesWithTasks: Set<LocalDate>,
    onDateSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }

    when (mode) {
        CalendarMode.Monthly -> {
            HorizontalCalendar(
                state = monthState,
                dayContent = { day ->
                    MonthDay(
                        day = day,
                        isSelected = day.date == selectedDate,
                        isToday = day.date == today,
                        hasEvents = day.date in datesWithTasks,
                        onClick = { onDateSelect(day.date) }
                    )
                },
                modifier = modifier.padding(horizontal = 4.dp)
            )
        }

        CalendarMode.Weekly -> {
            WeekCalendar(
                state = weekState,
                dayContent = { day ->
                    WeekDayItem(
                        day = day,
                        isSelected = day.date == selectedDate,
                        isToday = day.date == today,
                        hasEvents = day.date in datesWithTasks,
                        onClick = { onDateSelect(day.date) }
                    )
                },
                modifier = modifier.padding(horizontal = 4.dp)
            )
        }
    }
}


@Composable
fun MonthDay(
    day: CalendarDay,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit
) {
    val inMonth = day.position == DayPosition.MonthDate

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color(0xFFFF6A1A) // brand orange accent
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "dayBg"
    )

    val textColor = when {
        isSelected -> Color.White
        isToday -> Color(0xFFFF6A1A)
        inMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    }

    Column(
        modifier = Modifier
            .height(52.dp)
            .fillMaxWidth()
            .padding(1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(bgColor)
                .clickable(enabled = inMonth) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                fontSize = 14.sp,
                fontWeight = when {
                    isSelected -> FontWeight.Bold
                    isToday -> FontWeight.Bold
                    else -> FontWeight.Normal
                },
                color = textColor
            )
        }

        // Colored event dots (like the reference image)
        if (hasEvents && inMonth) {
            Spacer(Modifier.height(2.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Show 1-3 colored dots based on day hash for visual variety
                val dotCount = ((day.date.dayOfMonth % 3) + 1).coerceIn(1, 3)
                repeat(dotCount) { i ->
                    val colorIndex = (day.date.dayOfMonth + i) % dotColors.size
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color.White.copy(alpha = 0.8f)
                                else dotColors[colorIndex]
                            )
                    )
                }
            }
        }
    }
}

/* ── Week day cell ── */
@Composable
fun WeekDayItem(
    day: WeekDay,
    isSelected: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
    onClick: () -> Unit
) {
    val inWeek = day.position == WeekDayPosition.RangeDate

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> Color(0xFFFF6A1A)
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "weekDayBg"
    )

    val textColor = when {
        isSelected -> Color.White
        isToday -> Color(0xFFFF6A1A)
        inWeek -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .width(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                .uppercase()
                .take(2),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(bgColor)
                .clickable(enabled = inWeek) { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.date.dayOfMonth.toString(),
                fontSize = 15.sp,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor
            )
        }
        if (hasEvents && inWeek) {
            Spacer(Modifier.height(3.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dotCount = ((day.date.dayOfMonth % 3) + 1).coerceIn(1, 3)
                repeat(dotCount) { i ->
                    val colorIndex = (day.date.dayOfMonth + i) % dotColors.size
                    Box(
                        modifier = Modifier
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color.White.copy(alpha = 0.8f)
                                else dotColors[colorIndex]
                            )
                    )
                }
            }
        }
    }
}