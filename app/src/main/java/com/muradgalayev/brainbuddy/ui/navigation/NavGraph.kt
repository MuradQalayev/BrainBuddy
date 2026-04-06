package com.muradgalayev.brainbuddy.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.muradgalayev.brainbuddy.ui.utils.SpeechRecognitionHelper
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.muradgalayev.brainbuddy.ui.theme.AiButtonDark
import com.muradgalayev.brainbuddy.ui.theme.AiButtonDarkEnd
import com.muradgalayev.brainbuddy.ui.theme.AiButtonLight
import com.muradgalayev.brainbuddy.ui.theme.AiButtonLightEnd
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.muradgalayev.brainbuddy.data.local.entity.TodoItemEntity
import com.muradgalayev.brainbuddy.ui.activity.WorkspaceScreen
import com.muradgalayev.brainbuddy.ui.calendar.CalendarScreen
import com.muradgalayev.brainbuddy.ui.home.HomeScreen
import com.muradgalayev.brainbuddy.ui.settings.SettingsScreen
import com.muradgalayev.brainbuddy.ui.pomodoro.PomodoroScreen
import com.muradgalayev.brainbuddy.ui.todo.TaskDetailScreen
import com.muradgalayev.brainbuddy.ui.todo.TodoScreen
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow

private const val MAX_VISIBLE_NAV_ITEMS = 4

@Composable
fun NavGraph(
    navController: NavHostController,
    enabledOptionalRoutes: Set<String>
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var showVoiceExpanded by remember { mutableStateOf(false) }
    var showAiPrompt by remember { mutableStateOf(false) }
    val enabledOptional = remember(enabledOptionalRoutes) {
        optionalNavItems.filter { it.route in enabledOptionalRoutes }
    }

    // Insert optional items before Settings (Settings stays last)
    val allEnabled = remember(enabledOptional) {
        val fixed = fixedNavItems.toMutableList()
        val settingsIndex = fixed.indexOfFirst { it is Screen.Settings }
        fixed.addAll(settingsIndex, enabledOptional)
        fixed.toList()
    }

    // If more than MAX, show first (MAX-1) + More button
    val needsMore = allEnabled.size > MAX_VISIBLE_NAV_ITEMS
    val visibleItems = remember(allEnabled, needsMore) {
        if (needsMore) {
            allEnabled.take(MAX_VISIBLE_NAV_ITEMS - 1) + Screen.More
        } else {
            allEnabled
        }
    }
    val overflowItems = remember(allEnabled, needsMore) {
        if (needsMore) allEnabled.drop(MAX_VISIBLE_NAV_ITEMS - 1) else emptyList()
    }

    val fullScreenRoutes = setOf(Screen.Pomodoro.route, Screen.Calendar.route)
    val isFullScreen = currentRoute in fullScreenRoutes
    val bottomPadding by animateDpAsState(
        targetValue = if (isFullScreen) 0.dp else 104.dp,
        animationSpec = tween(220),
        label = "bottomPadding"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        NavHost(
            navController = navController,
            startDestination = Screen.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(bottom = bottomPadding),
            enterTransition = {
                slideInHorizontally(
                    initialOffsetX = { it / 2 },
                    animationSpec = tween(350)
                ) + fadeIn(animationSpec = tween(350))
            },
            exitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { -it / 4 },
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            },
            popEnterTransition = {
                slideInHorizontally(
                    initialOffsetX = { -it / 4 },
                    animationSpec = tween(300)
                ) + fadeIn(animationSpec = tween(300))
            },
            popExitTransition = {
                slideOutHorizontally(
                    targetOffsetX = { it / 2 },
                    animationSpec = tween(300)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Activity.route) {
                WorkspaceScreen(
                    onNavigate = { route ->
                        navController.navigate(route)
                    }
                )
            }
            composable(Screen.Settings.route) { SettingsScreen() }
            composable(Screen.Calendar.route) {
                CalendarScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(Screen.Todo.route) {
                TodoScreen()
            }
            composable(Screen.Pomodoro.route) {
                PomodoroScreen(
                    onBackClick = { navController.popBackStack() }
                )
            }
            composable(
                "${Screen.TaskDetail.route}?taskId={taskId}",
                arguments = listOf(
                    navArgument("taskId") {
                        type = NavType.StringType
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val taskId = backStackEntry.arguments?.getString("taskId") ?: ""
                // For now, pass a demo task - later this will come from a ViewModel
                val demoTask = TodoItemEntity(
                    id = taskId,
                    title = "Sample Task",
                    description = "Sample description",
                    startTime = "10:00",
                    endTime = "11:00"
                )

                TaskDetailScreen(
                    task = demoTask,
                    onBackClick = { navController.popBackStack() },
                    onDeleteClick = { navController.popBackStack() },
                    onUpdateClick = { navController.popBackStack() }
                )
            }
        }
        // Scrim
        AnimatedVisibility(
            visible = showAiPrompt && !isFullScreen,
            enter = fadeIn(tween(250)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.matchParentSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { showAiPrompt = false }
                    )
            )
        }

        // AI prompt card
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val aiGradient = if (isDark) {
            Brush.horizontalGradient(listOf(AiButtonDark, AiButtonDarkEnd))
        } else {
            Brush.horizontalGradient(listOf(AiButtonLight, AiButtonLightEnd))
        }

        AnimatedVisibility(
            visible = showAiPrompt && !isFullScreen,
            enter = slideInVertically(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                ),
                initialOffsetY = { it / 2 }
            ) + fadeIn(tween(200)) + scaleIn(
                initialScale = 0.92f,
                animationSpec = tween(250)
            ),
            exit = slideOutVertically(
                animationSpec = tween(200),
                targetOffsetY = { it / 3 }
            ) + fadeOut(tween(150)) + scaleOut(
                targetScale = 0.95f,
                animationSpec = tween(180)
            ),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            var promptText by remember { mutableStateOf("") }
            var isListening by remember { mutableStateOf(false) }
            var partialText by remember { mutableStateOf("") }
            val context = LocalContext.current

            // Background speech recognizer
            val speechHelper = remember {
                SpeechRecognitionHelper(
                    context = context,
                    onResult = { text ->
                        promptText += if (promptText.isEmpty()) text else " $text"
                        partialText = ""
                        isListening = false
                    },
                    onPartialResult = { text ->
                        partialText = text
                    },
                    onError = {
                        partialText = ""
                        isListening = false
                    },
                    onListeningStarted = {
                        isListening = true
                    },
                    onListeningFinished = {}
                )
            }

            DisposableEffect(Unit) {
                onDispose { speechHelper.destroy() }
            }

            // Permission launcher for RECORD_AUDIO
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { granted ->
                if (granted) {
                    speechHelper.startListening()
                }
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 120.dp),
                shape = RoundedCornerShape(32.dp),
                tonalElevation = 2.dp,
                shadowElevation = 28.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    // Modern gradient header with sparkle icon
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = aiGradient,
                                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)
                            )
                            .padding(horizontal = 20.dp, vertical = 18.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // AI sparkle icon
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.size(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "BrainBuddy AI",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Your personal assistant",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable { showAiPrompt = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Input area
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {

                        // Text field with send button
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            AnimatedVisibility(
                                visible = !showVoiceExpanded,
                                enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.98f),
                                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.98f),
                                modifier = Modifier.weight(1f)
                            ) {
                                TextField(
                                    value = promptText,
                                    onValueChange = { promptText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = {
                                        Text(
                                            "What can I help you with?",
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    },
                                    shape = RoundedCornerShape(22.dp),
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.5f),
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        cursorColor = MaterialTheme.colorScheme.primary
                                    ),
                                    maxLines = 4,
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                )
                            }

                            // --- Animated Mic Button ---
                            val micScale by animateFloatAsState(
                                targetValue = if (isListening) 1.08f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                ),
                                label = "mic_scale"
                            )

                            val micBgColor by animateColorAsState(
                                targetValue = if (isListening)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                animationSpec = tween(300),
                                label = "mic_bg"
                            )

                            val micIconTint by animateColorAsState(
                                targetValue = if (isListening)
                                    MaterialTheme.colorScheme.onPrimary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = tween(300),
                                label = "mic_icon_tint"
                            )

                            // Pulsing ring animation
                            val pulseTransition = rememberInfiniteTransition(label = "pulse")
                            val pulseScale by pulseTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.6f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "pulse_scale"
                            )
                            val pulseAlpha by pulseTransition.animateFloat(
                                initialValue = 0.5f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "pulse_alpha"
                            )
                            val pulse2Scale by pulseTransition.animateFloat(
                                initialValue = 1f,
                                targetValue = 1.9f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, delayMillis = 400),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "pulse2_scale"
                            )
                            val pulse2Alpha by pulseTransition.animateFloat(
                                initialValue = 0.35f,
                                targetValue = 0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1200, delayMillis = 400),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "pulse2_alpha"
                            )

                            // Glow border animation
                            val glowAlpha by pulseTransition.animateFloat(
                                initialValue = 0.7f,
                                targetValue = 0.25f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(800),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "glow_alpha"
                            )

                            Box(
                                modifier = Modifier
                                    .then(
                                        if (showVoiceExpanded) Modifier.weight(1f) else Modifier.size(52.dp)
                                    )
                                    .height(if (showVoiceExpanded) 52.dp else 52.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Pulsing rings (only when listening & collapsed)
                                if (isListening && !showVoiceExpanded) {
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .scale(pulseScale)
                                            .border(
                                                width = 2.dp,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                                                shape = CircleShape
                                            )
                                    )
                                    Box(
                                        modifier = Modifier
                                            .size(52.dp)
                                            .scale(pulse2Scale)
                                            .border(
                                                width = 1.5.dp,
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = pulse2Alpha),
                                                shape = CircleShape
                                            )
                                    )
                                }

                                // Main button
                                Box(
                                    modifier = Modifier
                                        .then(
                                            if (showVoiceExpanded) Modifier.fillMaxSize()
                                            else Modifier.size(52.dp)
                                        )
                                        .scale(if (!showVoiceExpanded) micScale else 1f)
                                        .then(
                                            if (isListening && !showVoiceExpanded) {
                                                Modifier
                                                    .shadow(
                                                        elevation = 12.dp,
                                                        shape = CircleShape,
                                                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                                    )
                                                    .border(
                                                        width = 2.dp,
                                                        color = MaterialTheme.colorScheme.primary.copy(alpha = glowAlpha),
                                                        shape = CircleShape
                                                    )
                                            } else if (isListening) {
                                                Modifier.border(
                                                    width = 1.5.dp,
                                                    brush = aiGradient,
                                                    shape = RoundedCornerShape(26.dp)
                                                )
                                            } else Modifier
                                        )
                                        .clip(if (showVoiceExpanded) RoundedCornerShape(26.dp) else CircleShape)
                                        .background(
                                            color = if (showVoiceExpanded && isListening)
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                            else if (showVoiceExpanded)
                                                MaterialTheme.colorScheme.surfaceContainerHigh
                                            else micBgColor
                                        )
                                        .clickable {
                                            if (isListening) {
                                                speechHelper.stopListening()
                                                isListening = false
                                                partialText = ""
                                                showVoiceExpanded = false
                                            } else {
                                                showVoiceExpanded = true

                                                val hasPermission = ContextCompat.checkSelfPermission(
                                                    context,
                                                    Manifest.permission.RECORD_AUDIO
                                                ) == PackageManager.PERMISSION_GRANTED

                                                if (hasPermission) {
                                                    speechHelper.startListening()
                                                } else {
                                                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                                }
                                            }
                                        }
                                        .padding(horizontal = if (showVoiceExpanded) 16.dp else 0.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (showVoiceExpanded) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
                                        ) {
                                            // Mic icon with its own mini pulse when listening
                                            Box(contentAlignment = Alignment.Center) {
                                                if (isListening) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(32.dp)
                                                            .scale(pulseScale.coerceAtMost(1.3f))
                                                            .background(
                                                                color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha * 0.5f),
                                                                shape = CircleShape
                                                            )
                                                    )
                                                }
                                                Icon(
                                                    imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                                    contentDescription = if (isListening) "Stop listening" else "Voice input",
                                                    tint = if (isListening)
                                                        MaterialTheme.colorScheme.primary
                                                    else
                                                        MaterialTheme.colorScheme.onSurfaceVariant,
                                                    modifier = Modifier.size(22.dp)
                                                )
                                            }

                                            VoiceWaveform(
                                                isListening = isListening,
                                                modifier = Modifier.weight(1f),
                                                activeColor = if (isListening) {
                                                    if (isDark) AiButtonDark else AiButtonLight
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                                },
                                                idleColor = if (isDark) AiButtonDarkEnd.copy(alpha = 0.15f)
                                                    else AiButtonLightEnd.copy(alpha = 0.15f)
                                            )

                                            Icon(
                                                imageVector = Icons.Rounded.ArrowUpward,
                                                contentDescription = "Send",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    } else {
                                        Icon(
                                            imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                            contentDescription = if (isListening) "Stop listening" else "Voice input",
                                            tint = micIconTint,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }
                            }

                            AnimatedVisibility(
                                visible = !showVoiceExpanded,
                                enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.98f),
                                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.98f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(brush = aiGradient)
                                        .clickable {
                                            // TODO: send prompt to AI backend
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowUpward,
                                        contentDescription = "Send",
                                        tint = Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Divider
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(
                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                                )
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Suggestion chips
                        Text(
                            text = "Suggestions",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            AiSuggestionChip("Summarize my day", aiGradient)
                            AiSuggestionChip("Help me focus", aiGradient)
                        }
                    }
                }
            }
        }

        if (!isFullScreen) {
            BottomNavBar(
                items = visibleItems,
                overflowItems = overflowItems,
                currentRoute = currentRoute,
                onItemClick = { screen ->
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = false
                        }
                        launchSingleTop = true
                        restoreState = false
                    }
                },
                onAiClick = { showAiPrompt = !showAiPrompt },
                isAiOpen = showAiPrompt,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

@Composable
private fun AiSuggestionChip(text: String, gradient: Brush) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = gradient,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable { /* TODO: fill prompt with suggestion */ }
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun VoiceWaveform(
    isListening: Boolean,
    modifier: Modifier = Modifier,
    barCount: Int = 32,
    activeColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    idleColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "voice_wave")

    // Multiple phase offsets for a richer, organic wave
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase1"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase2"
    )
    val phase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_phase3"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
    ) {
        val barWidth = 3.dp.toPx()
        val corner = barWidth / 2f
        val totalBars = barCount.coerceAtMost(
            ((size.width + 4.dp.toPx()) / (barWidth + 4.dp.toPx())).toInt()
        )
        if (totalBars <= 0) return@Canvas

        val totalWidth = totalBars * barWidth + (totalBars - 1) * 4.dp.toPx()
        val startX = (size.width - totalWidth) / 2f

        val centerY = size.height / 2f
        val minBarHeight = 4.dp.toPx()
        val maxBarHeight = size.height * 0.9f

        for (i in 0 until totalBars) {
            val x = startX + i * (barWidth + 4.dp.toPx())
            val ratio = i.toFloat() / (totalBars - 1).coerceAtLeast(1)

            val animatedAmp = if (isListening) {
                // Three overlapping sine waves for organic movement
                val w1 = kotlin.math.sin((ratio * Math.PI * 3) + phase1).toFloat()
                val w2 = kotlin.math.sin((ratio * Math.PI * 5) + phase2).toFloat() * 0.5f
                val w3 = kotlin.math.sin((ratio * Math.PI * 7) + phase3).toFloat() * 0.3f
                val combined = (w1 + w2 + w3) / 1.8f  // normalize
                // Gentle center-weighted envelope so edges are slightly shorter
                val envelope = 0.6f + 0.4f * kotlin.math.sin((ratio * Math.PI).toFloat())
                ((combined + 1f) / 2f * envelope).coerceIn(0.08f, 1f)
            } else {
                // Idle: small static bars
                val idle = 0.08f + 0.06f * kotlin.math.sin((ratio * Math.PI * 4).toFloat())
                idle
            }

            val height = minBarHeight + ((maxBarHeight - minBarHeight) * animatedAmp)
            val top = centerY - height / 2f

            drawRoundRect(
                color = if (isListening) activeColor else idleColor,
                topLeft = androidx.compose.ui.geometry.Offset(x, top),
                size = androidx.compose.ui.geometry.Size(barWidth, height),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(corner, corner)
            )
        }
    }
}