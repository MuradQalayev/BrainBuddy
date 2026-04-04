package com.muradgalayev.brainbuddy.ui.pomodoro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.R
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.data.local.TimerState
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType
import com.muradgalayev.brainbuddy.ui.theme.AiButtonDark
import com.muradgalayev.brainbuddy.ui.theme.AiButtonDarkEnd
import com.muradgalayev.brainbuddy.ui.theme.AiButtonLight
import com.muradgalayev.brainbuddy.ui.theme.AiButtonLightEnd
import kotlinx.coroutines.delay
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.muradgalayev.brainbuddy.ui.pomodoro.components.AmbientMusicSection
import com.muradgalayev.brainbuddy.ui.pomodoro.components.SessionTypePills
import com.muradgalayev.brainbuddy.ui.pomodoro.components.BottomControls
import com.muradgalayev.brainbuddy.ui.pomodoro.components.HistorySummarySwitcher
import com.muradgalayev.brainbuddy.ui.pomodoro.dialogs.PomodoroHistoryDialog
import com.muradgalayev.brainbuddy.ui.pomodoro.dialogs.DurationPickerDialog


private val FocusPurpleLight = AiButtonLight       // #6366F1
private val FocusPurpleLightEnd = AiButtonLightEnd // #8B5CF6
private val FocusPurpleDark = AiButtonDark         // #818CF8
private val FocusPurpleDarkEnd = AiButtonDarkEnd   // #A78BFA

// Break accent
private val BreakGreen = Color(0xFF4CAF50)
private val BreakBlue = Color(0xFF42A5F5)

@Composable
fun PomodoroScreen(
    onBackClick: () -> Unit,
    viewModel: PomodoroViewModel = hiltViewModel()
) {
    val timerState by viewModel.timerState.collectAsState()
    val recentSessions by viewModel.recentSessions.collectAsState()
    val uiExtra by viewModel.uiExtra.collectAsState()
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

    val focusGradient = if (isDark)
        Brush.linearGradient(listOf(FocusPurpleDark, FocusPurpleDarkEnd))
    else
        Brush.linearGradient(listOf(FocusPurpleLight, FocusPurpleLightEnd))

    val arcColor = when (timerState.sessionType) {
        PomodoroSessionType.FOCUS -> if (isDark) FocusPurpleDark else FocusPurpleLight
        PomodoroSessionType.SHORT_BREAK -> BreakGreen
        PomodoroSessionType.LONG_BREAK -> BreakBlue
    }
    val arcColorEnd = when (timerState.sessionType) {
        PomodoroSessionType.FOCUS -> if (isDark) FocusPurpleDarkEnd else FocusPurpleLightEnd
        PomodoroSessionType.SHORT_BREAK -> BreakGreen
        PomodoroSessionType.LONG_BREAK -> BreakBlue
    }

    val trackColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFF0F0F0)

    val animatedProgress by animateFloatAsState(
        targetValue = timerState.progress,
        animationSpec = tween(150),
        label = "progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // Top bar
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
                }
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    if (!uiExtra.focusModePermissionGranted) {
                        viewModel.requestFocusModePermission()
                    } else {
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
                    contentDescription = if (uiExtra.focusModeEnabled)
                        "Focus Mode Enabled"
                    else
                        "Focus Mode Disabled",
                    tint = if (uiExtra.focusModeEnabled)
                        arcColor
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                            text = if (uiExtra.focusModeEnabled) {
                                "Focus mode enabled"
                            } else {
                                "Focus mode disabled"
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Session type pills
            SessionTypePills(
                currentType = timerState.sessionType,
                onSelect = { type ->
                    if (timerState.timerState == TimerState.IDLE) {
                        viewModel.selectSessionType(type)
                        scope.launch {
                            pagerState.animateScrollToPage(sessionTypes.indexOf(type))
                        }
                    }
                },
                enabled = timerState.timerState == TimerState.IDLE,
                accentColor = arcColor
            )

            Spacer(modifier = Modifier.height(24.dp))

            HorizontalPager(
                state = pagerState,
                userScrollEnabled = timerState.timerState == TimerState.IDLE,
                modifier = Modifier.fillMaxWidth()
            ) { _ ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Focus Progress",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )

                            if (timerState.timerState == TimerState.IDLE) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = arcColor.copy(alpha = 0.12f),
                                    onClick = { viewModel.showDurationPicker() }
                                ) {
                                    Text(
                                        text = "${timerState.totalDurationMs / 60000} min",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = arcColor,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.7f)
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 12.dp.toPx()
                                val radius = (size.minDimension - strokeWidth) / 2f
                                val topLeft = Offset(
                                    (size.width - radius * 2) / 2f,
                                    (size.height - radius * 2) / 2f
                                )
                                val arcSize = Size(radius * 2, radius * 2)

                                drawArc(
                                    color = trackColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )

                                drawArc(
                                    brush = Brush.sweepGradient(
                                        colors = listOf(arcColor, arcColorEnd, arcColor)
                                    ),
                                    startAngle = -90f,
                                    sweepAngle = 360f * animatedProgress,
                                    useCenter = false,
                                    topLeft = topLeft,
                                    size = arcSize,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = if (timerState.timerState == TimerState.IDLE) {
                                    Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .clickable { viewModel.showDurationPicker() }
                                        .padding(12.dp)
                                } else {
                                    Modifier.padding(12.dp)
                                }
                            ) {
                                val minutes = (timerState.remainingMs / 1000) / 60
                                val seconds = (timerState.remainingMs / 1000) % 60

                                Text(
                                    text = "%02d.%02d".format(minutes, seconds),
                                    fontSize = 44.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = when (timerState.timerState) {
                                        TimerState.IDLE -> "Tap to edit"
                                        TimerState.RUNNING -> "Focus"
                                        TimerState.PAUSED -> "Paused"
                                        TimerState.COMPLETED -> "Done!"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (timerState.timerState == TimerState.IDLE) arcColor
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val sessionLabel = when (timerState.sessionType) {
                            PomodoroSessionType.FOCUS -> "Stay focus for ${timerState.totalDurationMs / 60000} min"
                            PomodoroSessionType.SHORT_BREAK -> "Short break for ${timerState.totalDurationMs / 60000} min"
                            PomodoroSessionType.LONG_BREAK -> "Long break for ${timerState.totalDurationMs / 60000} min"
                        }

                        Text(
                            text = sessionLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (timerState.completedSessions > 0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${timerState.completedSessions} session${if (timerState.completedSessions > 1) "s" else ""} completed",
                                style = MaterialTheme.typography.bodySmall,
                                color = arcColor,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Spacer(modifier = Modifier.height(20.dp))

            // Add / Subtract time
            AnimatedVisibility(
                visible = timerState.timerState == TimerState.RUNNING || timerState.timerState == TimerState.PAUSED,
                enter = fadeIn(tween(200)),
                exit = fadeOut(tween(200))
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SmallControlButton(
                        icon = Icons.Rounded.Remove,
                        label = "-5 min",
                        onClick = viewModel::subtractTime,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(32.dp))
                    SmallControlButton(
                        icon = Icons.Rounded.Add,
                        label = "+5 min",
                        onClick = viewModel::addTime,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ambient Music section
            AmbientMusicSection(
                selectedSound = timerState.selectedAmbientSound,
                onSoundSelect = viewModel::selectAmbientSound,
                accentColor = arcColor
            )

            Spacer(modifier = Modifier.weight(1f))

            // Bottom controls
            BottomControls(
                timerState = timerState.timerState,
                accentColor = arcColor,
                accentGradient = if (timerState.sessionType == PomodoroSessionType.FOCUS) focusGradient
                else Brush.linearGradient(listOf(arcColor, arcColor)),
                onStart = viewModel::start,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onReset = viewModel::reset,
                onStop = viewModel::stop,
                onSkip = viewModel::skipToNext
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Duration picker dialog
    if (uiExtra.showDurationPicker) {
        DurationPickerDialog(
            currentMinutes = (timerState.totalDurationMs / 60000).toInt(),
            onConfirm = { viewModel.setCustomDuration(it) },
            onDismiss = viewModel::dismissDurationPicker,
            accentColor = arcColor
        )
    }
    if (showHistoryDialog) {
        PomodoroHistoryDialog(
            sessions = recentSessions.take(5),
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
                    "To silence notifications during focus sessions, BrainBuddy needs " +
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
private fun SmallControlButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = tint)
    }
}

@Composable
fun GradientCircleButton(
    icon: ImageVector,
    label: String,
    gradient: Brush,
    size: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(gradient)
            .clickable(onClick = onClick),
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
