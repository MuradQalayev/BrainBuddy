package com.muradgalayev.brainbuddy.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.animateDpAsState
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.muradgalayev.brainbuddy.data.local.entity.TodoItemEntity
import com.muradgalayev.brainbuddy.ui.activity.WorkspaceScreen
import com.muradgalayev.brainbuddy.ui.calendar.CalendarScreen
import com.muradgalayev.brainbuddy.ui.components.AiPromptCard
import com.muradgalayev.brainbuddy.ui.home.HomeScreen
import com.muradgalayev.brainbuddy.ui.settings.SettingsScreen
import com.muradgalayev.brainbuddy.ui.pomodoro.PomodoroScreen
import com.muradgalayev.brainbuddy.ui.auth.AuthScreen
import com.muradgalayev.brainbuddy.ui.splash.SplashScreen
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

    val fullScreenRoutes = setOf(
        Screen.Splash.route,
        Screen.Auth.route,
        Screen.Pomodoro.route,
        Screen.Calendar.route,
        Screen.OnboardingChoice.route,
        Screen.QuickSetup.route,
        Screen.DeepDive.route,
        Screen.CareNearby.route,
    )
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
            startDestination = Screen.Splash.route,
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
            composable(Screen.Splash.route) {
                SplashScreen(
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToAuth = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToOnboarding = {
                        navController.navigate(Screen.OnboardingChoice.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Auth.route) {
                AuthScreen(
                    onAuthSuccess = {
                        // Route through Splash so onboarding gating applies for new users.
                        navController.navigate(Screen.Splash.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.OnboardingChoice.route) {
                com.muradgalayev.brainbuddy.ui.onboarding.OnboardingChoiceScreen(
                    onPickQuick = { navController.navigate(Screen.QuickSetup.route) },
                    onPickDeep = { navController.navigate(Screen.DeepDive.route) },
                )
            }
            composable(Screen.QuickSetup.route) {
                com.muradgalayev.brainbuddy.ui.onboarding.QuickSetupScreen(
                    onBack = { navController.popBackStack() },
                    onDone = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.OnboardingChoice.route) { inclusive = true }
                        }
                    },
                )
            }
            composable(Screen.DeepDive.route) {
                com.muradgalayev.brainbuddy.ui.onboarding.DeepDiveScreen(
                    onBack = { navController.popBackStack() },
                    onDone = {
                        // If we came from Onboarding, pop the whole onboarding stack;
                        // if we came from Settings, just pop back to Settings.
                        val poppedOnboarding = navController.popBackStack(
                            route = Screen.OnboardingChoice.route,
                            inclusive = true,
                        )
                        if (poppedOnboarding) {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(0) { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                )
            }
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.Activity.route) {
                WorkspaceScreen(
                    onNavigate = { route ->
                        navController.navigate(route)
                    }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onLogout = {
                        navController.navigate(Screen.Auth.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onEditAdhdProfile = {
                        navController.navigate(Screen.DeepDive.route)
                    }
                )
            }
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
            composable(Screen.CareNearby.route) {
                com.muradgalayev.brainbuddy.ui.care.CareNearbyScreen(
                    onBack = { navController.popBackStack() }
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
                
                // Demo task updated with required userId to fix compilation
                val demoTask = TodoItemEntity(
                    id = taskId,
                    userId = "demo_user",
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
            AiPromptCard(onDismiss = { showAiPrompt = false })
        }

        if (!isFullScreen) {
            BottomNavBar(
                items = visibleItems,
                overflowItems = overflowItems,
                currentRoute = currentRoute,
                onItemClick = { screen ->
                    navController.navigate(screen.route) {
                        popUpTo(Screen.Home.route) {
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
