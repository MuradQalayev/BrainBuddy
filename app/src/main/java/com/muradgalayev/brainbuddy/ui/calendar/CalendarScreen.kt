package com.muradgalayev.brainbuddy.ui.calendar

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Close
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
import com.muradgalayev.brainbuddy.ui.ai.AiAssistantViewModel
import com.muradgalayev.brainbuddy.ui.ai.AiPromptCard
import com.muradgalayev.brainbuddy.ui.ai.offline.OfflineAssistantPanel
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.compose.weekcalendar.rememberWeekCalendarState
import com.kizitonwose.calendar.core.firstDayOfWeekFromLocale
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt
import com.muradgalayev.brainbuddy.R

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

// derived from the active theme rather than hardcoded, so switching theme carries the
// calendar with it. lavender is the accent slot, historically purple and now whatever the
// theme's accent is. the name stays because it's threaded through every Calendar composable
@Composable
fun rememberCalendarPalette(): CalendarPalette {
    val c = MaterialTheme.colorScheme
    val accents = MaterialTheme.myndoraAccents
    return CalendarPalette(
        bg = c.background,
        ink = c.onSurface,
        muted = c.onSurfaceVariant,
        cardBg = c.surface,
        lavender = accents.accent,
        lavenderSoft = c.primaryContainer,
        pillBg = c.surfaceContainer,
        flagRed = c.error,
        sky = accents.support,
        lilac = accents.accentEnd,
        lime = accents.supportEnd,
        periwinkle = accents.accentEnd,
        dialogBorder = c.outlineVariant,
        sheetBg = c.surfaceContainer,
        dateHeaderBg = c.onSurface,
        dateHeaderText = c.background,
    )
}

// main screen
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onBackClick: () -> Unit = {},
    onNavigateToPomodoro: () -> Unit = {},
    onOpenBreakdown: (eventId: String) -> Unit = {},
    onConnectPeople: () -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isSyncingToGoogle by viewModel.isSyncingToGoogle.collectAsState()
    val googleSyncMessage by viewModel.googleSyncMessage.collectAsState()
    val togetherShareMessage by viewModel.togetherShareMessage.collectAsState()
    val calendarShareTargets by viewModel.calendarShareTargets.collectAsState()
    val suggestionAvailabilityNote by viewModel.suggestionAvailabilityNote.collectAsState()
    val timeSuggestions by viewModel.timeSuggestions.collectAsState()
    val googleAuthRequest by viewModel.googleAuthRequest.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val p = rememberCalendarPalette()

    var mode by rememberSaveable { mutableStateOf(CalendarMode.Monthly) }

    // calendar AI: pops the assistant, animated out of the AI button, to add an event
    val calendarAiVm: AiAssistantViewModel = hiltViewModel()
    // AI stays locked until the survey is done. the assistant's whole value is the profile it
    // personalises from, and without it every reply is generic. same gate the nav button uses
    val surveyCompleted by calendarAiVm.surveyCompleted.collectAsState()
    LaunchedEffect(Unit) { calendarAiVm.refreshSurveyCompleted() }
    var showCalendarAi by remember { mutableStateOf(false) }
    var showCalendarVoice by remember { mutableStateOf(false) }
    var showOfflineAssistant by remember { mutableStateOf(false) }
    // only the survey lock closes the assistant. losing the connection used to as well, which
    // shut the card mid-conversation every time the signal dipped
    LaunchedEffect(surveyCompleted) {
        if (!surveyCompleted) {
            showCalendarAi = false
            showCalendarVoice = false
        }
    }
    // live voice needs the network to hear anything, so that entry point still routes to the
    // on-device assistant when there's nothing to talk to
    val voiceUsable = isOnline && surveyCompleted
    // one-time intro: fly a round shape into the AI button on the first visit
    val showAiIntro by viewModel.showAiIntro.collectAsState()
    var aiButtonBounds by remember { mutableStateOf<Rect?>(null) }
    // root offset of this screen (the NavHost applies statusBarsPadding), for translating the AI
    // button's root-space bounds into the overlay's local space
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

    val firstDayOfWeek = remember { firstDayOfWeekFromLocale() }
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val snackbarHostState = remember { SnackbarHostState() }

    // always the chat, connected or not. the card offers the switch to the on-device assistant
    // itself, so one button keeps one meaning instead of quietly becoming a different button
    val openCalendarAi = {
        if (surveyCompleted) {
            calendarAiVm.primeCalendarEventPrompt()
            showCalendarAi = true
        }
        Unit
    }

    val googleAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onGoogleAuthResult(result.data)
    }

    LaunchedEffect(googleAuthRequest) {
        val pi = googleAuthRequest ?: return@LaunchedEffect
        viewModel.consumeGoogleAuthRequest()
        googleAuthLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
    }

    LaunchedEffect(googleSyncMessage) {
        val msg = googleSyncMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearGoogleSyncMessage()
        }
    }

    LaunchedEffect(togetherShareMessage) {
        val msg = togetherShareMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearTogetherShareMessage()
        }
    }

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

    // sync the visible month back to the ViewModel
    LaunchedEffect(monthState.firstVisibleMonth.yearMonth) {
        viewModel.updateVisibleMonth(monthState.firstVisibleMonth.yearMonth)
    }

    // add / edit event dialog
    if (uiState.showAddTaskDialog) {
        AddTaskDialog(
            palette = p,
            selectedDate = uiState.selectedDate,
            existingEvent = uiState.editingEvent,
            shareTargets = calendarShareTargets,
            onConnectPeople = onConnectPeople,
            suggestions = timeSuggestions,
            suggestionNote = suggestionAvailabilityNote,
            onSuggestionInputChanged = viewModel::onSuggestionInputChanged,
            onShareTargetsChanged = viewModel::onShareTargetsChanged,
            onSuggestionDateChange = viewModel::jumpToDate,
            onDismiss = { viewModel.dismissAddTaskDialog() },
            onConfirm = { title, description, startTime, endTime, location, color, link,
                          alsoAddFor, addToMyCalendar ->
                viewModel.saveTask(
                    title = title,
                    description = description,
                    startTime = startTime,
                    endTime = endTime,
                    location = location,
                    color = color,
                    link = link,
                    alsoAddFor = alsoAddFor,
                    addToMyCalendar = addToMyCalendar,
                )
            }
        )
    }

    // the subtask editor sheet used to open here. building and tracking a plan lives on its own
    // screen now: a sheet stacked over the calendar could only ever be an editor, never
    // somewhere to work from

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() }
    ) {
    if (isLandscape) {
        // landscape: side by side, no bottom sheet
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
                    CalendarHeader(
                        mode = mode,
                        onModeChange = { mode = it },
                        onBackClick = onBackClick,
                        onAiClick = openCalendarAi,
                        // visible and tappable whenever the survey is done
                        aiEnabled = surveyCompleted,
                        onAiButtonPositioned = { aiButtonBounds = it }
                    )
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
                    // month grid only, WeekDayItem draws its own MO/TU label so week mode duplicated it
                    if (mode == CalendarMode.Monthly) {
                        Spacer(modifier = Modifier.height(8.dp))
                        DayOfWeekHeader(firstDayOfWeek)
                    }
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
                    expandedEventId = uiState.expandedEventId,
                    onTaskClick = { viewModel.editEvent(it) },
                    onTaskDelete = { viewModel.deleteTask(it) },
                    onToggleCompleted = { viewModel.toggleTaskCompleted(it) },
                    onToggleExpanded = { viewModel.toggleExpanded(it) },
                    onToggleSubtask = { id, completed ->
                        viewModel.toggleSubtaskCompleted(id, completed)
                    },
                    onManageSubtasks = { onOpenBreakdown(it) },
                    onRunInPomodoro = { id ->
                        if (viewModel.launchInPomodoro(id)) onNavigateToPomodoro()
                    },
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
        // portrait: bottom sheet layout
        val screenHeight = configuration.screenHeightDp.dp

        // sheet peek is mode-aware, the two modes leave very different amounts of room above it. a
        // month grid needs about six rows of dates, a week strip needs one, and sizing for the month
        // left a dead gap above the first task, so in week mode the sheet rises to take it back.
        // snapped, not animated: animateDpAsState made BottomSheetScaffold recompute its anchors and
        // re-lay-out the whole sheet, calendar and task list, on every frame of the transition. that
        // was the worst offender for the slowness, and the sheet's own settle covers the change
        val sheetPeekHeight = remember(screenHeight, mode) {
            when (mode) {
                CalendarMode.Monthly -> (screenHeight * 0.38f).coerceIn(260.dp, 380.dp)
                CalendarMode.Weekly -> (screenHeight * 0.58f).coerceIn(380.dp, 560.dp)
            }
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
                // bottom sheet: date header and events
                EventsBottomSheet(
                    palette = p,
                    selectedDate = uiState.selectedDate,
                    tasks = uiState.tasksForSelectedDate,
                    expandedEventId = uiState.expandedEventId,
                    onTaskClick = { viewModel.editEvent(it) },
                    onTaskDelete = { viewModel.deleteTask(it) },
                    onToggleCompleted = { viewModel.toggleTaskCompleted(it) },
                    onAddTask = { viewModel.showAddTaskDialog() },
                    onToggleExpanded = { viewModel.toggleExpanded(it) },
                    onToggleSubtask = { id, completed ->
                        viewModel.toggleSubtaskCompleted(id, completed)
                    },
                    onManageSubtasks = { onOpenBreakdown(it) },
                    onRunInPomodoro = { id ->
                        if (viewModel.launchInPomodoro(id)) onNavigateToPomodoro()
                    },
                )
            }
        ) { innerPadding ->
            // calendar area above the sheet
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
                    onBackClick = onBackClick,
                    onAiClick = openCalendarAi,
                    // see the landscape branch: offline restyles the pill, it doesn't remove it
                    aiEnabled = surveyCompleted,
                    onAiButtonPositioned = { aiButtonBounds = it }
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

                // month grid only, see the landscape branch above
                if (mode == CalendarMode.Monthly) {
                    Spacer(modifier = Modifier.height(12.dp))
                    DayOfWeekHeader(firstDayOfWeek)
                }

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

        // add button, pinned to the screen rather than to the sheet's content, so it stays reachable
        // however long the day's list gets or however far the sheet is dragged. portrait only,
        // landscape has its own inside the Row
        if (!isLandscape) {
            FloatingActionButton(
                onClick = { viewModel.showAddTaskDialog() },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(end = 20.dp, bottom = 24.dp),
                shape = CircleShape,
                containerColor = p.lavender,
                contentColor = Color.White,
            ) {
                Icon(Icons.Outlined.Add, "Add task", Modifier.size(28.dp))
            }
        }

        com.muradgalayev.brainbuddy.ui.sharedcomponents.MyndoraSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
        )

        // the original calendar AI card. its header carries the calendar-only live voice entry
        AnimatedVisibility(
            visible = showCalendarAi,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f)).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { showCalendarAi = false },
                ),
            )
        }
        AnimatedVisibility(
            visible = showCalendarAi,
            enter = fadeIn(tween(160)) + scaleIn(
                initialScale = 0.6f,
                transformOrigin = TransformOrigin(1f, 0f),
                animationSpec = tween(240),
            ),
            exit = fadeOut(tween(140)) + scaleOut(
                targetScale = 0.6f,
                transformOrigin = TransformOrigin(1f, 0f),
                animationSpec = tween(160),
            ),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        ) {
            AiPromptCard(
                onDismiss = { showCalendarAi = false },
                viewModel = calendarAiVm,
                showSuggestions = false,
                showHistory = false,
                onLiveVoiceClick = {
                    showCalendarAi = false
                    // live voice can't work without a connection, so offline it hands over to the on-device
                    // assistant rather than opening a microphone with nowhere to send what it hears
                    if (voiceUsable) showCalendarVoice = true else showOfflineAssistant = true
                },
                onSwitchToOffline = {
                    showCalendarAi = false
                    showOfflineAssistant = true
                },
            )
        }
        AnimatedVisibility(
            visible = showCalendarVoice,
            enter = fadeIn(tween(260)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize(),
        ) {
            CalendarVoiceAssistant(
                viewModel = calendarAiVm,
                onDismiss = { showCalendarVoice = false },
            )
        }

        // offline assistant, opened by the same pill when there's no connection. not auto-dismissed
        // on reconnect: closing it mid-sentence would throw away whatever the user was typing, and
        // the header tells them the full assistant is available instead
        AnimatedVisibility(
            visible = showOfflineAssistant,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(120)),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showOfflineAssistant = false },
                    ),
            )
        }
        AnimatedVisibility(
            visible = showOfflineAssistant,
            enter = fadeIn(tween(160)) + scaleIn(
                initialScale = 0.7f,
                transformOrigin = TransformOrigin(1f, 0f),
                animationSpec = tween(240),
            ),
            exit = fadeOut(tween(140)) + scaleOut(
                targetScale = 0.7f,
                transformOrigin = TransformOrigin(1f, 0f),
                animationSpec = tween(160),
            ),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 8.dp),
        ) {
            OfflineAssistantPanel(onDismiss = { showOfflineAssistant = false })
        }

        // first-visit intro: a round shape flies into the AI logo once the button has been measured
        val introTarget = aiButtonBounds
        if (surveyCompleted && showAiIntro && introTarget != null) {
            CalendarAiIntroOverlay(
                targetCenter = introTarget.center - overlayOrigin,
                targetRadius = minOf(introTarget.width, introTarget.height) / 2f,
                color = p.lavender,
                onFinished = { viewModel.markAiIntroShown() },
            )
        }
    }
}
