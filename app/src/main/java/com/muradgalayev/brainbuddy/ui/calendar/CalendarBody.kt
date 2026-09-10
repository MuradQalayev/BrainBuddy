package com.muradgalayev.brainbuddy.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.unit.Dp
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

// calendar body (month or week)
@Composable
fun CalendarBody(
    mode: CalendarMode,
    monthState: com.kizitonwose.calendar.compose.CalendarState,
    weekState: com.kizitonwose.calendar.compose.weekcalendar.WeekCalendarState,
    selectedDate: LocalDate,
    datesWithTasks: Map<LocalDate, CalendarDayMarks>,
    onDateSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }

    BoxWithConstraints(modifier) {
        // a month needs up to six rows and the grid has no scroll of its own, so a fixed row height
        // silently cuts the last week off wherever the calendar is short, which is every landscape
        // phone. sizing to the worst case means no month is ever clipped, and in portrait there's more
        // than enough room so the cell keeps its natural size
        val dayHeight = (maxHeight / MonthGridRows).coerceIn(MinDayHeight, MaxDayHeight)

        CalendarPager(
            mode = mode,
            monthState = monthState,
            weekState = weekState,
            selectedDate = selectedDate,
            today = today,
            datesWithTasks = datesWithTasks,
            dayHeight = dayHeight,
            onDateSelect = onDateSelect,
        )
    }
}

// the tallest a month can be, and the smallest a cell can shrink to before it stops reading
private const val MonthGridRows = 6
private val MaxDayHeight = 52.dp
private val MinDayHeight = 34.dp

@Composable
private fun CalendarPager(
    mode: CalendarMode,
    monthState: com.kizitonwose.calendar.compose.CalendarState,
    weekState: com.kizitonwose.calendar.compose.weekcalendar.WeekCalendarState,
    selectedDate: LocalDate,
    today: LocalDate,
    datesWithTasks: Map<LocalDate, CalendarDayMarks>,
    dayHeight: Dp,
    onDateSelect: (LocalDate) -> Unit,
) {
    // cross-fade only, `using null` disables the SizeTransform deliberately. animating the
    // container height re-measured a full HorizontalCalendar every frame for the whole transition
    // (the month grid builds ~42 day cells, and both calendars are composed at once mid-swap),
    // which was the calendar's jank. snapping the size and fading the content keeps the swap
    // feeling deliberate at a fraction of the layout cost
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            fadeIn(tween(durationMillis = 160)) togetherWith
                fadeOut(tween(durationMillis = 90)) using null
        },
        label = "calendar_mode_swap",
    ) { animatedMode ->
        when (animatedMode) {
            CalendarMode.Monthly -> {
                HorizontalCalendar(
                    state = monthState,
                    dayContent = { day ->
                        MonthDay(
                            day = day,
                            isSelected = day.date == selectedDate,
                            isToday = day.date == today,
                            marks = datesWithTasks[day.date],
                            height = dayHeight,
                            onClick = { onDateSelect(day.date) }
                        )
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
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
                            marks = datesWithTasks[day.date],
                            onClick = { onDateSelect(day.date) }
                        )
                    },
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
fun MonthDay(
    day: CalendarDay,
    isSelected: Boolean,
    isToday: Boolean,
    marks: CalendarDayMarks?,
    onClick: () -> Unit,
    height: Dp = 52.dp,
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

    // the circle gives up room before the cell does, so a shorter row still leaves space for the
    // task dots underneath rather than pushing them out of the cell
    val circleSize = (height - 16.dp).coerceIn(24.dp, 36.dp)

    Column(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .padding(1.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
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

        if (marks != null && marks.total > 0 && inMonth) {
            Spacer(Modifier.height(2.dp))
            DayDots(marks = marks, onSelectedBackground = isSelected)
        }
    }
}

// week day cell
@Composable
fun WeekDayItem(
    day: WeekDay,
    isSelected: Boolean,
    isToday: Boolean,
    marks: CalendarDayMarks?,
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
        if (marks != null && marks.total > 0 && inWeek) {
            Spacer(Modifier.height(3.dp))
            DayDots(marks = marks, onSelectedBackground = isSelected)
        }
    }
}

// the day-cell markers, one rule applied everywhere: one dot per event in that event's own
// colour, in start-time order; filled while unfinished and hollow once done, so a cleared day
// reads as outlines at a glance without losing which events were there; at most three, then a
// smaller faded dot meaning and-more, because past three 4dp dots stop being countable.
// they previously derived both count and colour from the day-of-month number, which made them
// decoration dressed as data: the 7th always showed two dots whether it held one event or nine
@Composable
private fun DayDots(marks: CalendarDayMarks, onSelectedBackground: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        marks.dots.forEach { dot ->
            // on the selected filled cell event colours would clash and several would vanish into the
            // background, and white reads on all of them
            val color = if (onSelectedBackground) Color.White else dot.color
            Box(
                modifier = Modifier
                    .size(4.5.dp)
                    .clip(CircleShape)
                    .then(
                        if (dot.done) {
                            Modifier.border(1.dp, color.copy(alpha = 0.75f), CircleShape)
                        } else {
                            Modifier.background(
                                if (onSelectedBackground) color.copy(alpha = 0.9f) else color
                            )
                        }
                    ),
            )
        }
        if (marks.hasMore) {
            Box(
                modifier = Modifier
                    .size(3.dp)
                    .clip(CircleShape)
                    .background(
                        (if (onSelectedBackground) Color.White else MaterialTheme.colorScheme.onSurface)
                            .copy(alpha = 0.35f)
                    ),
            )
        }
    }
}
