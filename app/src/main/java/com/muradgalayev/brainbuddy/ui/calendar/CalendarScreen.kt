package com.muradgalayev.brainbuddy.ui.calendar

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
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
    val sheetBg: Color,
    val dateHeaderBg: Color,
    val dateHeaderText: Color,
)

val LightCalendarPalette = CalendarPalette(
    bg = Color(0xFFFCFCFF),
    ink = Color(0xFF1B1B21),
    muted = Color(0xFF5C5B68),
    cardBg = Color(0xFFFFFFFF),
    lavender = Color(0xFF6366F1),
    lavenderSoft = Color(0xFFE0DFFF),
    pillBg = Color(0xFFF1F0F7),
    flagRed = Color(0xFFE53E3E),
    sky = Color(0xFF82C8FF),
    lilac = Color(0xFF9A7CF3),
    lime = Color(0xFFD0DB56),
    periwinkle = Color(0xFFB9C5FF),
    dialogBorder = Color(0xFFF1F0F7),
    sheetBg = Color(0xFFF9F8FC),
    dateHeaderBg = Color(0xFF1B1B2E),
    dateHeaderText = Color(0xFFFFFFFF),
)

val DarkCalendarPalette = CalendarPalette(
    bg = Color(0xFF0F0F15),
    ink = Color(0xFFE5E4EC),
    muted = Color(0xFF9C9BA8),
    cardBg = Color(0xFF1E1E28),
    lavender = Color(0xFFA5A4FB),
    lavenderSoft = Color(0xFF2A2650),
    pillBg = Color(0xFF282834),
    flagRed = Color(0xFFFC5555),
    sky = Color(0xFF5DADEB),
    lilac = Color(0xFFB49BFF),
    lime = Color(0xFFB8C244),
    periwinkle = Color(0xFF8E9DE0),
    dialogBorder = Color(0xFF33333F),
    sheetBg = Color(0xFF16161D),
    dateHeaderBg = Color(0xFF1A1A24),
    dateHeaderText = Color(0xFFE5E4EC),
)

@Composable
fun rememberCalendarPalette(): CalendarPalette {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return if (isDark) DarkCalendarPalette else LightCalendarPalette
}

/* ── Main Screen ── */
@OptIn(ExperimentalMaterial3Api::class)
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

    // Sync visible month to ViewModel
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

    if (isLandscape) {
        // ── Landscape: side-by-side (no bottom sheet) ──
        Box(modifier = Modifier.fillMaxSize().background(p.bg)) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    CalendarHeader(mode = mode, onModeChange = { mode = it }, onBackClick = onBackClick)
                    Spacer(modifier = Modifier.height(12.dp))
                    MonthNavigator(
                        palette = p,
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
    } else {
        // ── Portrait: Bottom Sheet layout ──
        val screenHeight = configuration.screenHeightDp.dp

        // Sheet peek = date header + ~2 task cards visible
        val sheetPeekHeight = remember(screenHeight) {
            (screenHeight * 0.38f).coerceIn(260.dp, 380.dp)
        }

        val bottomSheetState = rememberStandardBottomSheetState(
            initialValue = SheetValue.PartiallyExpanded,
            skipHiddenState = true
        )
        val scaffoldState = rememberBottomSheetScaffoldState(
            bottomSheetState = bottomSheetState
        )

        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = sheetPeekHeight,
            sheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            sheetContainerColor = p.sheetBg,
            sheetShadowElevation = 16.dp,
            sheetDragHandle = null, // We use our own custom date header as handle
            containerColor = p.bg,
            sheetContent = {
                // ── Bottom Sheet: date header + events ──
                EventsBottomSheet(
                    palette = p,
                    selectedDate = uiState.selectedDate,
                    tasks = uiState.tasksForSelectedDate,
                    onTaskClick = { viewModel.toggleTaskCompletion(it) },
                    onTaskDelete = { viewModel.deleteTask(it) },
                    onAddTask = { viewModel.showAddTaskDialog() }
                )
            }
        ) { innerPadding ->
            // ── Calendar area above the sheet ──
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
            ) {
                CalendarHeader(
                    mode = mode,
                    onModeChange = { mode = it },
                    onBackClick = onBackClick
                )

                Spacer(modifier = Modifier.height(16.dp))

                MonthNavigator(
                    palette = p,
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

                Spacer(modifier = Modifier.height(12.dp))

                DayOfWeekHeader(firstDayOfWeek)

                Spacer(modifier = Modifier.height(6.dp))

                CalendarBody(
                    mode = mode,
                    monthState = monthState,
                    weekState = weekState,
                    selectedDate = uiState.selectedDate,
                    datesWithTasks = uiState.datesWithTasks,
                    onDateSelect = { viewModel.selectDate(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                )
            }
        }
    }
}