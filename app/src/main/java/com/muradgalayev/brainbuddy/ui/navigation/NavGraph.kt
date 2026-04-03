package com.muradgalayev.brainbuddy.ui.navigation

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Locale
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
import com.muradgalayev.brainbuddy.ui.activity.ActivityScreen
import com.muradgalayev.brainbuddy.ui.calendar.CalendarScreen
import com.muradgalayev.brainbuddy.ui.home.HomeScreen
import com.muradgalayev.brainbuddy.ui.settings.SettingsScreen
import com.muradgalayev.brainbuddy.ui.pomodoro.PomodoroScreen
import com.muradgalayev.brainbuddy.ui.todo.TaskDetailScreen
import com.muradgalayev.brainbuddy.ui.todo.TodoScreen

private const val MAX_VISIBLE_NAV_ITEMS = 4

@Composable
fun NavGraph(
    navController: NavHostController,
    enabledOptionalRoutes: Set<String>
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var showAiPrompt by remember { mutableStateOf(false) }
    // Build the visible nav items: fixed + enabled optional
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

    // Full-screen routes hide the bottom nav
    val fullScreenRoutes = setOf(Screen.Pomodoro.route, Screen.Calendar.route)
    val isFullScreen = currentRoute in fullScreenRoutes

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
                .then(
                    if (isFullScreen) Modifier
                    else Modifier
                        .statusBarsPadding()
                        .padding(bottom = 104.dp)
                ),
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Activity.route) {
                ActivityScreen(
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
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

            // Speech recognition launcher
            val speechRecognitionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val data = result.data
                    val results = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    if (!results.isNullOrEmpty()) {
                        promptText += if (promptText.isEmpty()) results[0] else " ${results[0]}"
                    }
                }
                isListening = false
            }

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 126.dp),
                shape = RoundedCornerShape(28.dp),
                tonalElevation = 6.dp,
                shadowElevation = 24.dp,
                color = MaterialTheme.colorScheme.surface
            ) {
                Column {
                    // Gradient header
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = aiGradient,
                                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
                            )
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Ask BrainBuddy",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f))
                                    .clickable { showAiPrompt = false },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Close",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Input area
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "What can I help you with?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Text field with send button
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            TextField(
                                value = promptText,
                                onValueChange = { promptText = it },
                                modifier = Modifier.weight(1f),
                                placeholder = {
                                    Text(
                                        "Ask anything...",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                },
                                shape = RoundedCornerShape(20.dp),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                ),
                                maxLines = 4
                            )

                            // Voice button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        color = if (isListening)
                                            MaterialTheme.colorScheme.errorContainer
                                        else
                                            MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .clickable {
                                        isListening = true
                                        val speechIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                            putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak now...")
                                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                                        }
                                        speechRecognitionLauncher.launch(speechIntent)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Mic,
                                    contentDescription = "Voice input",
                                    tint = if (isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Send button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
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

                        Spacer(modifier = Modifier.height(12.dp))

                        // Suggestion chips
                        Row(
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
                        ) {
                            AiSuggestionChip("Summarize my day")
                            AiSuggestionChip("Help me focus")
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
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
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
private fun AiSuggestionChip(text: String) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = { /* TODO: fill prompt with suggestion */ }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}