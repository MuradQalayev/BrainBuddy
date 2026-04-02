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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.WeekCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.WeekDay
import com.kizitonwose.calendar.core.WeekDayPosition
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

enum class CalendarMode { Monthly, Weekly }

@Composable
fun CalendarScreen() {
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var mode by rememberSaveable { mutableStateOf(CalendarMode.Monthly) }
    val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }
    val scope = rememberCoroutineScope()

    val startMonth = remember { YearMonth.now().minusMonths(12) }
    val endMonth = remember { YearMonth.now().plusMonths(12) }
    val startWeek = remember { LocalDate.now().minusWeeks(52) }
    val endWeek = remember { LocalDate.now().plusWeeks(52) }

    val monthState = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = firstDayOfWeek
    )

    val weekState = rememberWeekCalendarState(
        startDate = startWeek,
        endDate = endWeek,
        firstVisibleWeekDate = selectedDate,
        firstDayOfWeek = firstDayOfWeek
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 20.dp)
    ) {
        // Header: title + mode toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Calendar",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            ModePill(
                currentMode = mode,
                onModeChange = { mode = it }
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Month navigation
        MonthNavigator(
            currentMonth = if (mode == CalendarMode.Monthly)
                monthState.firstVisibleMonth.yearMonth
            else
                YearMonth.from(selectedDate),
            onPrevious = {
                scope.launch {
                    if (mode == CalendarMode.Monthly) {
                        monthState.animateScrollToMonth(
                            monthState.firstVisibleMonth.yearMonth.minusMonths(1)
                        )
                    } else {
                        val newDate = selectedDate.minusWeeks(1)
                        selectedDate = newDate
                        weekState.animateScrollToWeek(newDate)
                    }
                }
            },
            onNext = {
                scope.launch {
                    if (mode == CalendarMode.Monthly) {
                        monthState.animateScrollToMonth(
                            monthState.firstVisibleMonth.yearMonth.plusMonths(1)
                        )
                    } else {
                        val newDate = selectedDate.plusWeeks(1)
                        selectedDate = newDate
                        weekState.animateScrollToWeek(newDate)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Day of week headers
        DayOfWeekHeader(firstDayOfWeek)

        Spacer(modifier = Modifier.height(8.dp))

        // Calendar body with constrained height
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier
                .fillMaxWidth()
                .height(if (mode == CalendarMode.Monthly) 380.dp else 160.dp)
        ) {
            when (mode) {
                CalendarMode.Monthly -> {
                    HorizontalCalendar(
                        state = monthState,
                        dayContent = { day ->
                            MonthDay(
                                day = day,
                                isSelected = day.date == selectedDate,
                                isToday = day.date == LocalDate.now(),
                                onClick = { selectedDate = day.date }
                            )
                        },
                        modifier = Modifier.padding(8.dp)
                    )
                }
                CalendarMode.Weekly -> {
                    WeekCalendar(
                        state = weekState,
                        dayContent = { day ->
                            WeekDayItem(
                                day = day,
                                isSelected = day.date == selectedDate,
                                isToday = day.date == LocalDate.now(),
                                onClick = { selectedDate = day.date }
                            )
                        },
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Events section with scrollable content
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Events",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Scrollable events list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                item {
                    // Selected date header
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = selectedDate.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = "${selectedDate.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${selectedDate.dayOfMonth}, ${selectedDate.year}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No events yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                // Add more event items here as they become available
                // Example: items(events) { event -> EventItem(event) }
            }
        }
    }
}

// ─── Mode toggle pill ───────────────────────────────────────────────

@Composable
private fun ModePill(
    currentMode: CalendarMode,
    onModeChange: (CalendarMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest
    ) {
        Row(modifier = Modifier.padding(3.dp)) {
            CalendarMode.entries.forEach { mode ->
                val selected = mode == currentMode
                val bg by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer
                    else Color.Transparent,
                    animationSpec = tween(200),
                    label = "modeBg"
                )
                val fg by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    animationSpec = tween(200),
                    label = "modeFg"
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(bg)
                        .clickable { onModeChange(mode) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = mode.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = fg
                    )
                }
            }
        }
    }
}

// ─── Month navigator ────────────────────────────────────────────────

@Composable
private fun MonthNavigator(
    currentMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = "Previous",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.width(12.dp))
        IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = "Next",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Day of week header ─────────────────────────────────────────────

@Composable
private fun DayOfWeekHeader(firstDayOfWeek: DayOfWeek) {
    val daysOfWeek = remember(firstDayOfWeek) {
        val days = DayOfWeek.entries.toMutableList()
        val index = days.indexOf(firstDayOfWeek)
        days.subList(index, days.size) + days.subList(0, index)
    }
    Row(modifier = Modifier.fillMaxWidth()) {
        daysOfWeek.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Month day cell ─────────────────────────────────────────────────

@Composable
private fun MonthDay(
    day: CalendarDay,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val inMonth = day.position == DayPosition.MonthDate

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isToday -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "dayBg"
    )

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        inMonth -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    }

    Box(
        modifier = Modifier
            .height(44.dp)
            .fillMaxWidth()
            .padding(2.dp)
            .clip(CircleShape)
            .background(bgColor)
            .clickable(enabled = inMonth) { onClick() },
        contentAlignment = Alignment.Center
    ){
        Text(
            text = day.date.dayOfMonth.toString(),
            fontSize = 14.sp,
            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
            color = textColor
        )
    }
}

// ─── Week day cell ──────────────────────────────────────────────────

@Composable
private fun WeekDayItem(
    day: WeekDay,
    isSelected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val inWeek = day.position == WeekDayPosition.RangeDate

    val bgColor by animateColorAsState(
        targetValue = when {
            isSelected -> MaterialTheme.colorScheme.primary
            isToday -> MaterialTheme.colorScheme.primaryContainer
            else -> Color.Transparent
        },
        animationSpec = tween(200),
        label = "weekDayBg"
    )

    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        inWeek -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    }

    Column(
        modifier = Modifier
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .width(44.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
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
    }
}