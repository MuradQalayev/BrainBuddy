package com.muradgalayev.brainbuddy.ui.calendar

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth


enum class CalendarMode { Monthly, Weekly }

@Immutable
 data class CalendarPalette(
    val bg: Color,
    val ink: Color,
    val muted: Color,
    val cardBg: Color,
    val lavender: Color,
    val lavenderSoft: Color,
    val pillBg: Color,
    val flagRed: Color,
    val sky: Color,
    val lilac: Color,
    val lime: Color,
    val periwinkle: Color,
    val dialogBorder: Color,
)

val LightCalendarPalette = CalendarPalette(
    bg = Color(0xFFF6F4F8),
    ink = Color(0xFF2C295B),
    muted = Color(0xFF8F8CA1),
    cardBg = Color(0xFFFFFFFF),
    lavender = Color(0xFF9A7CF3),
    lavenderSoft = Color(0xFFE7DFFF),
    pillBg = Color(0xFFF0EEF5),
    flagRed = Color(0xFFE53E3E),
    sky = Color(0xFF82C8FF),
    lilac = Color(0xFFD8A4FF),
    lime = Color(0xFFD0DB56),
    periwinkle = Color(0xFFB9C5FF),
    dialogBorder = Color(0xFFF0EEF5),
)

val DarkCalendarPalette = CalendarPalette(
    bg = Color(0xFF0F1115),
    ink = Color(0xFFE4E5EA),
    muted = Color(0xFF9A9DA6),
    cardBg = Color(0xFF1E2128),
    lavender = Color(0xFFB49BFF),
    lavenderSoft = Color(0xFF2D2547),
    pillBg = Color(0xFF282B34),
    flagRed = Color(0xFFFC5555),
    sky = Color(0xFF5DADEB),
    lilac = Color(0xFFC48FEE),
    lime = Color(0xFFB8C244),
    periwinkle = Color(0xFF8E9DE0),
    dialogBorder = Color(0xFF32353F),
)

@Composable
private fun rememberCalendarPalette(): CalendarPalette {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) DarkCalendarPalette else LightCalendarPalette
}

/* ── Main Screen ── */
@Composable
fun CalendarScreen(
    onBackClick: () -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val p = rememberCalendarPalette()

    var mode by rememberSaveable { mutableStateOf(CalendarMode.Monthly) }
    val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val startMonth = remember { YearMonth.now().minusMonths(12) }
    val endMonth = remember { YearMonth.now().plusMonths(12) }
    val startWeek = remember { LocalDate.now().minusWeeks(52) }
    val endWeek = remember { LocalDate.now().plusWeeks(52) }

    val monthState = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = uiState.visibleMonth,
        firstDayOfWeek = firstDayOfWeek
    )

    val weekState = rememberWeekCalendarState(
        startDate = startWeek,
        endDate = endWeek,
        firstVisibleWeekDate = uiState.selectedDate,
        firstDayOfWeek = firstDayOfWeek
    )

    // Sync visible month to ViewModel for dot indicators
    LaunchedEffect(monthState.firstVisibleMonth.yearMonth) {
        viewModel.updateVisibleMonth(monthState.firstVisibleMonth.yearMonth)
    }

    // Add task dialog
    if (uiState.showAddTaskDialog) {
        AddTaskDialog(
            palette = p,
            selectedDate = uiState.selectedDate,
            onDismiss = { viewModel.dismissAddTaskDialog() },
            onConfirm = { title, description, startTime, endTime, category, color ->
                viewModel.addTask(
                    title = title,
                    description = description,
                    startTime = startTime,
                    endTime = endTime,
                    category = category,
                    color = color
                )
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isLandscape) {
            // Landscape: side-by-side
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Left: calendar
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    CalendarHeader(mode = mode, onModeChange = { mode = it }, onBackClick = onBackClick)
                    Spacer(modifier = Modifier.height(12.dp))
                    MonthNavigator(
                        currentMonth = if (mode == CalendarMode.Monthly)
                            monthState.firstVisibleMonth.yearMonth
                        else YearMonth.from(uiState.selectedDate),
                        onPrevious = {
                            scope.launch {
                                if (mode == CalendarMode.Monthly)
                                    monthState.animateScrollToMonth(
                                        monthState.firstVisibleMonth.yearMonth.minusMonths(1)
                                    )
                                else {
                                    val newDate = uiState.selectedDate.minusWeeks(1)
                                    viewModel.selectDate(newDate)
                                    weekState.animateScrollToWeek(newDate)
                                }
                            }
                        },
                        onNext = {
                            scope.launch {
                                if (mode == CalendarMode.Monthly)
                                    monthState.animateScrollToMonth(
                                        monthState.firstVisibleMonth.yearMonth.plusMonths(1)
                                    )
                                else {
                                    val newDate = uiState.selectedDate.plusWeeks(1)
                                    viewModel.selectDate(newDate)
                                    weekState.animateScrollToWeek(newDate)
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    DayOfWeekHeader(firstDayOfWeek)
                    Spacer(modifier = Modifier.height(4.dp))
                    CalendarBody(
                        mode = mode,
                        monthState = monthState,
                        weekState = weekState,
                        selectedDate = uiState.selectedDate,
                        datesWithTasks = uiState.datesWithTasks,
                        onDateSelect = { viewModel.selectDate(it) },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Right: events
                EventsSection(
                    palette = p,
                    selectedDate = uiState.selectedDate,
                    tasks = uiState.tasksForSelectedDate,
                    onTaskClick = { viewModel.toggleTaskCompletion(it) },
                    onTaskDelete = { viewModel.deleteTask(it) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        } else {
            // Portrait: stacked
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                CalendarHeader(mode = mode, onModeChange = { mode = it }, onBackClick = onBackClick)
                Spacer(modifier = Modifier.height(20.dp))
                MonthNavigator(
                    currentMonth = if (mode == CalendarMode.Monthly)
                        monthState.firstVisibleMonth.yearMonth
                    else YearMonth.from(uiState.selectedDate),
                    onPrevious = {
                        scope.launch {
                            if (mode == CalendarMode.Monthly)
                                monthState.animateScrollToMonth(
                                    monthState.firstVisibleMonth.yearMonth.minusMonths(1)
                                )
                            else {
                                val newDate = uiState.selectedDate.minusWeeks(1)
                                viewModel.selectDate(newDate)
                                weekState.animateScrollToWeek(newDate)
                            }
                        }
                    },
                    onNext = {
                        scope.launch {
                            if (mode == CalendarMode.Monthly)
                                monthState.animateScrollToMonth(
                                    monthState.firstVisibleMonth.yearMonth.plusMonths(1)
                                )
                            else {
                                val newDate = uiState.selectedDate.plusWeeks(1)
                                viewModel.selectDate(newDate)
                                weekState.animateScrollToWeek(newDate)
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.height(16.dp))
                DayOfWeekHeader(firstDayOfWeek)
                Spacer(modifier = Modifier.height(8.dp))

                CalendarBody(
                    mode = mode,
                    monthState = monthState,
                    weekState = weekState,
                    selectedDate = uiState.selectedDate,
                    datesWithTasks = uiState.datesWithTasks,
                    onDateSelect = { viewModel.selectDate(it) },
                    modifier = Modifier.height(if (mode == CalendarMode.Monthly) 340.dp else 130.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                EventsSection(
                    palette = p,
                    selectedDate = uiState.selectedDate,
                    tasks = uiState.tasksForSelectedDate,
                    onTaskClick = { viewModel.toggleTaskCompletion(it) },
                    onTaskDelete = { viewModel.deleteTask(it) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // FAB
        FloatingActionButton(
            onClick = { viewModel.showAddTaskDialog() },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            shape = CircleShape,
            containerColor = p.lavender,
            contentColor = Color.White
        ) {
            Icon(Icons.Outlined.Add, "Add task", Modifier.size(28.dp))
        }
    }
}
