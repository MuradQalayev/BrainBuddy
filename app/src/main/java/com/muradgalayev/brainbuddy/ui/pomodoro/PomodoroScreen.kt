package com.muradgalayev.brainbuddy.ui.pomodoro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.data.local.TimerState
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
import com.muradgalayev.brainbuddy.ui.pomodoro.components.AmbientSoundPill
import com.muradgalayev.brainbuddy.ui.pomodoro.components.BottomControls
import com.muradgalayev.brainbuddy.ui.pomodoro.components.CalendarPlanStrip
import com.muradgalayev.brainbuddy.ui.pomodoro.components.DailyFocusCard
import com.muradgalayev.brainbuddy.ui.pomodoro.components.FocusRing
import com.muradgalayev.brainbuddy.ui.pomodoro.components.HistorySummarySwitcher
import com.muradgalayev.brainbuddy.ui.pomodoro.components.SessionTypePills
import com.muradgalayev.brainbuddy.ui.pomodoro.dialogs.AmbientSoundSheet
import com.muradgalayev.brainbuddy.ui.pomodoro.dialogs.DurationPickerSheet
import com.muradgalayev.brainbuddy.ui.pomodoro.dialogs.PomodoroHistorySheet
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PomodoroScreen(
    onBackClick: () -> Unit,
    onOpenBreakdown: (String) -> Unit = {},
    onOpenFocusRoom: (String) -> Unit = {},
    viewModel: PomodoroViewModel = hiltViewModel()
) {
    val timerState by viewModel.timerState.collectAsState()
    // same view-model instance the card below uses, both live in this screen's scope, so there is
    // one answer to 'is a room running' rather than two
    val focusTogetherViewModel: FocusTogetherViewModel = hiltViewModel()
    val focusTogetherState by focusTogetherViewModel.uiState.collectAsState()
    val roomOwnsTheClock = focusTogetherState.activeSession != null
    val spotifyPlaylistLink by viewModel.spotifyPlaylistLink.collectAsState()
    val pomodoroQueue by viewModel.pomodoroQueue.collectAsState()
    val planCompletedIds by viewModel.planCompletedIds.collectAsState()
    var planExpanded by rememberSaveable { mutableStateOf(false) }
    val recentSessions by viewModel.recentSessions.collectAsState()
    val focusHistory by viewModel.focusHistory.collectAsState()
    val uiExtra by viewModel.uiExtra.collectAsState()
    var showSoundSheet by rememberSaveable { mutableStateOf(false) }
    var showFocusStatus by rememberSaveable { mutableStateOf(false) }
    var showHistoryDialog by rememberSaveable { mutableStateOf(false) }
    val sessionTypes = PomodoroSessionType.entries
    val pagerState = rememberPagerState(
        initialPage = sessionTypes.indexOf(timerState.sessionType).coerceAtLeast(0),
        pageCount = { sessionTypes.size }
    )
    val scope = rememberCoroutineScope()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshFocusModePermission()
        viewModel.dismissPermissionDialog()
    }

    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* granted or not, service will still work but notification may not show */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    var hasSeenInitialFocusState by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiExtra.focusModeEnabled) {
        if (!hasSeenInitialFocusState) {
            hasSeenInitialFocusState = true
            return@LaunchedEffect
        }

        showFocusStatus = true
        delay(1500)
        showFocusStatus = false
    }

    LaunchedEffect(timerState.sessionType) {
        val targetPage = sessionTypes.indexOf(timerState.sessionType)
        if (targetPage != pagerState.currentPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val selectedType = sessionTypes[pagerState.currentPage]
        if (
            timerState.timerState == TimerState.IDLE &&
            selectedType != timerState.sessionType
        ) {
            viewModel.selectSessionType(selectedType)
        }
    }
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f

    // focus wears the theme's accent and break its supporting colour, so the two states are told
    // apart by hue in every theme without either needing its own palette
    val accents = MaterialTheme.myndoraAccents

    val focusGradient = Brush.linearGradient(listOf(accents.accent, accents.accentEnd))
    val breakGradient = Brush.linearGradient(listOf(accents.support, accents.supportEnd))

    val targetArcColor = when (timerState.sessionType) {
        PomodoroSessionType.FOCUS -> accents.accent
        PomodoroSessionType.BREAK -> accents.support
    }

    val targetArcColorEnd = when (timerState.sessionType) {
        PomodoroSessionType.FOCUS -> accents.accentEnd
        PomodoroSessionType.BREAK -> accents.supportEnd
    }

    val infiniteTransition = rememberInfiniteTransition(label = "timer_infinite")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (timerState.timerState == TimerState.RUNNING) 1.03f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.10f,
        targetValue = if (timerState.timerState == TimerState.RUNNING) 0.22f else 0.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    val timerScale by animateFloatAsState(
        targetValue = when (timerState.timerState) {
            TimerState.RUNNING -> pulseScale
            TimerState.PAUSED -> 0.98f
            else -> 1f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "timerScale"
    )
    val arcColor by animateColorAsState(
        targetValue = targetArcColor,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "arcColor"
    )

    val arcColorEnd by animateColorAsState(
        targetValue = targetArcColorEnd,
        animationSpec = tween(500, easing = FastOutSlowInEasing),
        label = "arcColorEnd"
    )

    val trackColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F0)

    val animatedProgress by animateFloatAsState(
        targetValue = timerState.progress,
        animationSpec = tween(150),
        label = "progress"
    )
    val cardScale by animateFloatAsState(
        targetValue = if (timerState.timerState == TimerState.IDLE) 1f else 0.98f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "cardScale"
    )

    val isIdle = timerState.timerState == TimerState.IDLE
    val canScrub = timerState.timerState == TimerState.RUNNING ||
        timerState.timerState == TimerState.PAUSED

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Focus Timer",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            HistorySummarySwitcher(
                todayFocusMinutes = uiExtra.todayFocusMinutes,
                accentColor = arcColor,
                onClick = {
                    showHistoryDialog = true
                },
                // holds still once a session is under way, nothing should move in the corner of the eye
                animated = isIdle
            )

            Spacer(modifier = Modifier.width(8.dp))

            AnimatedVisibility(
                visible = isIdle,
                enter = fadeIn(tween(200)) + expandHorizontally(
                    animationSpec = tween(250)
                ),
                exit = fadeOut(tween(150)) + shrinkHorizontally(
                    animationSpec = tween(200)
                )
            ) {
                IconButton(
                    enabled = !uiExtra.focusModeControlledByMode ||
                        !uiExtra.focusModePermissionGranted,
                    onClick = {
                        if (!uiExtra.focusModePermissionGranted) {
                            viewModel.requestFocusModePermission()
                        } else if (!uiExtra.focusModeControlledByMode) {
                            viewModel.toggleFocusMode(!uiExtra.focusModeEnabled)
                        }
                    }
                ) {
                    Icon(
                        modifier = Modifier.size(30.dp),
                        painter = painterResource(
                            id = if (uiExtra.focusModeEnabled)
                                R.drawable.ic_dnd_on
                            else
                                R.drawable.ic_dnd_off
                        ),
                        contentDescription = when {
                            uiExtra.focusModeControlledByMode ->
                                "Focus silence managed by ${uiExtra.controllingModeName} mode"
                            uiExtra.focusModeEnabled -> "Focus Mode Enabled"
                            else -> "Focus Mode Disabled"
                        },
                        tint = if (uiExtra.focusModeEnabled)
                            arcColor
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // a shared session has exactly one home, and it is the room. backing out of it used to leave
        // the same countdown running on this screen with its own Start, Pause and Reset: two places
        // to control one agreed session, and Reset here would silently desync a block the other
        // person is still sitting in. while a room owns the clock there is nothing to show here
        // except the way back to it
        if (roomOwnsTheClock) {
            focusTogetherState.activeSession?.let { shared ->
                SharedSessionPanel(
                    session = shared,
                    onOpenRoom = { onOpenFocusRoom(shared.id) },
                )
            }
            return@Column
        }

        AnimatedVisibility(
            visible = showFocusStatus,
            enter = slideInVertically(
                initialOffsetY = { -it / 2 },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(
                animationSpec = tween(250)
            ) + expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = tween(250)
            ),
            exit = slideOutVertically(
                targetOffsetY = { -it / 3 },
                animationSpec = tween(220)
            ) + fadeOut(
                animationSpec = tween(180)
            ) + shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = tween(220)
            ),
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Text(
                    text = when {
                        uiExtra.focusModeControlledByMode ->
                            "${uiExtra.controllingModeName} mode manages focus silence"
                        uiExtra.focusModeEnabled -> "Focus mode enabled"
                        else -> "Focus mode disabled"
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    color = if (uiExtra.focusModeEnabled) {
                        arcColor
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // everything above the controls scrolls, the controls themselves are pinned below so
        // Start/Pause is reachable without ever scrolling for it
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            CalendarPlanStrip(
                queue = pomodoroQueue,
                accentColor = arcColor,
                expanded = planExpanded,
                onToggleExpanded = { planExpanded = !planExpanded },
                canSelectStep = isIdle,
                onStartStep = { index -> viewModel.startQueueItem(index) },
                onOpenBreakdown = {
                    pomodoroQueue?.eventId?.let(onOpenBreakdown)
                },
                completedSubtaskIds = planCompletedIds,
                onClear = { viewModel.clearQueue() },
            )

            if (pomodoroQueue != null) {
                Spacer(modifier = Modifier.height(12.dp))
            }

            AnimatedVisibility(
                visible = isIdle && pomodoroQueue == null,
                enter = fadeIn(tween(200)) + expandVertically(animationSpec = tween(220)),
                exit = fadeOut(tween(150)) + shrinkVertically(animationSpec = tween(180))
            ) {
                Column {
                    DailyFocusCard(
                        hasFocusedToday = uiExtra.hasFocusedToday,
                        todayLabel = uiExtra.todayFocusLabel,
                        comparisonMessage = uiExtra.comparisonMessage,
                        emptyStateTitle = uiExtra.emptyStateTitle,
                        emptyStateMessage = uiExtra.emptyStateMessage,
                        accentColor = arcColor
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            SessionTypePills(
                currentType = timerState.sessionType,
                onSelect = { type ->
                    if (isIdle) {
                        viewModel.selectSessionType(type)
                        scope.launch {
                            pagerState.animateScrollToPage(sessionTypes.indexOf(type))
                        }
                    }
                },
                enabled = isIdle,
                accentColor = arcColor,
                collapseToSelected = !isIdle
            )

            Spacer(modifier = Modifier.height(20.dp))

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = isIdle,
                modifier = Modifier.fillMaxWidth()
            ) { _ ->
                val cardBackground = if (isDark) {
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            MaterialTheme.colorScheme.surfaceContainer
                        )
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .scale(cardScale)
                        .clip(RoundedCornerShape(32.dp))
                        .background(cardBackground)
                        .padding(1.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(31.dp))
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        arcColor.copy(alpha = if (isDark) 0.14f else 0.10f),
                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                        MaterialTheme.colorScheme.surfaceContainer
                                    )
                                )
                            )
                            .padding(horizontal = 24.dp, vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = arcColor.copy(alpha = 0.14f)
                            ) {
                                Text(
                                    text = when (timerState.sessionType) {
                                        PomodoroSessionType.FOCUS -> "FOCUS SESSION"
                                        PomodoroSessionType.BREAK -> "BREAK"
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    color = arcColor,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(modifier = Modifier.height(22.dp))

                            FocusRing(
                                progress = animatedProgress,
                                arcColor = arcColor,
                                arcColorEnd = arcColorEnd,
                                trackColor = trackColor,
                                glowAlpha = glowAlpha,
                                scrubEnabled = canScrub,
                                onScrubStart = viewModel::beginScrub,
                                onScrub = viewModel::scrubToProgress,
                                onScrubReleased = viewModel::endScrub,
                                modifier = Modifier
                                    .fillMaxWidth(0.78f)
                                    .scale(timerScale)
                            ) { scrubbing, atEnd ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = if (isIdle) {
                                        Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .clickable { viewModel.showDurationPicker() }
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    } else {
                                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                                    }
                                ) {
                                    val minutes = (timerState.remainingMs / 1000) / 60
                                    val seconds = (timerState.remainingMs / 1000) % 60
                                    val minuteText = "%02d".format(minutes)
                                    val secondText = "%02d".format(seconds)

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        AnimatedDigit(minuteText[0], "min1")
                                        AnimatedDigit(minuteText[1], "min2")

                                        Text(
                                            text = ":",
                                            fontSize = 48.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )

                                        AnimatedDigit(secondText[0], "sec1")
                                        AnimatedDigit(secondText[1], "sec2")
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    val statusText = when {
                                        scrubbing && atEnd -> "Release to finish"
                                        scrubbing -> "Drag to adjust"
                                        else -> when (timerState.timerState) {
                                            TimerState.IDLE -> "Tap to set duration"
                                            TimerState.RUNNING -> "Drag the ring to adjust"
                                            TimerState.PAUSED -> "Paused"
                                            TimerState.COMPLETED -> "Session completed"
                                        }
                                    }

                                    AnimatedContent(
                                        targetState = statusText,
                                        transitionSpec = {
                                            fadeIn(tween(180)) togetherWith fadeOut(tween(120))
                                        },
                                        label = "statusText"
                                    ) { text ->
                                        Text(
                                            text = text,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (scrubbing && atEnd) FontWeight.SemiBold
                                            else FontWeight.Normal,
                                            color = if (isIdle || scrubbing) arcColor
                                            else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (timerState.completedSessions > 0) {
                                Spacer(modifier = Modifier.height(14.dp))

                                Text(
                                    text = "${timerState.completedSessions} session${if (timerState.completedSessions > 1) "s" else ""} completed",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = arcColor,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Spacer(modifier = Modifier.height(18.dp))

                            AmbientSoundPill(
                                selectedSound = timerState.selectedAmbientSound,
                                accentColor = arcColor,
                                onClick = { showSoundSheet = true }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // directly under the ring and always present, invite or not. below the controls it sat past
        // the fold and was found by nobody, and hidden when no one had granted permission it was
        // invisible to exactly the person who needed to learn the feature existed
        FocusTogetherCard(
            focusMinutes = (timerState.totalDurationMs / 60_000).toInt().coerceIn(5, 180),
            onOpenRoom = onOpenFocusRoom,
            viewModel = focusTogetherViewModel,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))

        BottomControls(
            timerState = timerState.timerState,
            accentColor = arcColor,
            accentGradient = if (timerState.sessionType == PomodoroSessionType.FOCUS) focusGradient
            else breakGradient,
            onStart = viewModel::start,
            onPause = viewModel::pause,
            onResume = viewModel::resume,
            onReset = viewModel::reset,
            onStop = viewModel::stop,
            onSkip = viewModel::skipToNext,
            startEnabled = !roomOwnsTheClock,
        )

        Spacer(modifier = Modifier.height(20.dp))
    }

    if (uiExtra.showDurationPicker) {
        DurationPickerSheet(
            currentMinutes = (timerState.totalDurationMs / 60000).toInt(),
            accentColor = arcColor,
            onConfirm = { viewModel.setCustomDuration(it) },
            onDismiss = viewModel::dismissDurationPicker,
        )
    }

    if (showSoundSheet) {
        AmbientSoundSheet(
            selectedSound = timerState.selectedAmbientSound,
            onSoundSelect = viewModel::selectAmbientSound,
            spotifyPlaylistLink = spotifyPlaylistLink,
            onSpotifyPlaylistLinkChange = viewModel::setSpotifyPlaylistLink,
            onOpenSpotifyPlaylist = viewModel::openSpotifyPlaylist,
            accentColor = arcColor,
            onDismiss = { showSoundSheet = false },
        )
    }

    if (showHistoryDialog) {
        PomodoroHistorySheet(
            history = focusHistory,
            sessions = recentSessions.take(5),
            accentColor = arcColor,
            onDismiss = { showHistoryDialog = false }
        )
    }

    // DND permission dialog
    if (uiExtra.showPermissionDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPermissionDialog,
            title = { Text("Focus Mode Permission") },
            text = {
                Text(
                    "To silence notifications during focus sessions, Myndora needs " +
                    "Do Not Disturb access. This lets the app temporarily mute notifications " +
                    "while your timer is running and restore them when it ends."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    permissionLauncher.launch(viewModel.getFocusModePermissionIntent())
                }) { Text("Open Settings") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPermissionDialog) { Text("Not Now") }
            }
        )
    }
}

@Composable
fun GradientCircleButton(
    icon: ImageVector,
    label: String,
    gradient: Brush,
    size: Int,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(gradient)
            // dimmed as well as inert. an unresponsive button that still looks pressable reads as the app
            // being broken, a faded one reads as 'not now', which is what it means
            .alpha(if (enabled) 1f else 0.35f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size((size / 2).dp)
        )
    }
}

@Composable
fun ControlCircle(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color,
    bgColor: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(52.dp),
            shape = CircleShape,
            color = bgColor,
            onClick = onClick
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AnimatedDigit(
    digit: Char,
    label: String
) {
    AnimatedContent(
        targetState = digit,
        transitionSpec = {
            (slideInVertically { it / 3 } + fadeIn()).togetherWith(
                slideOutVertically { -it / 3 } + fadeOut()
            )
        },
        label = label
    ) { value ->
        Text(
            text = value.toString(),
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
