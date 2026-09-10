package com.muradgalayev.brainbuddy.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import com.muradgalayev.brainbuddy.R
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.muradgalayev.brainbuddy.ui.pomodoro.FocusRoomScreen
import com.muradgalayev.brainbuddy.data.local.entity.TodoItemEntity
import com.muradgalayev.brainbuddy.ui.activity.WorkspaceScreen
import com.muradgalayev.brainbuddy.ui.calendar.CalendarScreen
import com.muradgalayev.brainbuddy.ui.ai.AiPromptCard
import com.muradgalayev.brainbuddy.ui.ai.offline.OfflineAssistantPanel
import com.muradgalayev.brainbuddy.ui.home.HomeScreen
import com.muradgalayev.brainbuddy.ui.settings.SettingsScreen
import com.muradgalayev.brainbuddy.ui.pomodoro.PomodoroScreen
import com.muradgalayev.brainbuddy.ui.auth.AuthScreen
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.splash.SplashScreen
import com.muradgalayev.brainbuddy.ui.todo.TaskDetailScreen
import com.muradgalayev.brainbuddy.ui.todo.TodoScreen

private const val MAX_VISIBLE_NAV_ITEMS = 4

// which assistant the nav-bar AI button opened
private enum class AiSheet { None, Online, Offline }

@Composable
fun NavGraph(
    navController: NavHostController,
    enabledOptionalRoutes: Set<String>,
    passwordRecoveryActive: Boolean = false,
    requestedQuestionnaireRoute: String? = null,
    onQuestionnaireRouteConsumed: () -> Unit = {},
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    // which assistant the AI button opened, decided at tap time and then held.
    // this used to be a boolean with the online/offline choice re-derived on every recomposition,
    // so a connectivity blip mid-conversation swapped the panel out from under the user and threw
    // away whatever they were typing. the calendar pill and workspace handle always decided at
    // tap time, the nav button was the one that didn't
    var aiSheet by remember { mutableStateOf(AiSheet.None) }
    var isNavDragging by remember { mutableStateOf(false) }

    // A reminder can open the activity from a cold start. Wait for Splash/Auth to resolve, then
    // take the signed-in user straight to the saved survey, which starts on its first incomplete page.
    LaunchedEffect(requestedQuestionnaireRoute, currentRoute) {
        val route = requestedQuestionnaireRoute ?: return@LaunchedEffect
        if (currentRoute == null || currentRoute == Screen.Splash.route || currentRoute == Screen.Auth.route) {
            return@LaunchedEffect
        }
        if (currentRoute != route) navController.navigate(route) { launchSingleTop = true }
        onQuestionnaireRouteConsumed()
    }

    // A recovery callback can arrive while any signed-in screen is open. Always bring the
    // existing set-password dialog into view; previously only a cold launch through Splash did.
    LaunchedEffect(passwordRecoveryActive, currentRoute) {
        if (
            passwordRecoveryActive &&
            currentRoute != null &&
            currentRoute != Screen.Auth.route
        ) {
            aiSheet = AiSheet.None
            navController.navigate(Screen.Auth.route) { launchSingleTop = true }
        }
    }
    // observe active-chat status at graph level so the badge on the AI nav icon stays live.
    // injecting the view-model here doesn't force it to load history, Hilt creates it lazily
    val aiViewModel: com.muradgalayev.brainbuddy.ui.ai.AiAssistantViewModel = hiltViewModel()
    val aiHasActiveChat by aiViewModel.hasActiveChat.collectAsState()
    // AI stays locked until the survey is finished. re-checked on route change so unlocking
    // right after completing it is instant
    val surveyCompleted by aiViewModel.surveyCompleted.collectAsState()
    LaunchedEffect(currentRoute) { aiViewModel.refreshSurveyCompleted() }
    LaunchedEffect(surveyCompleted) { if (!surveyCompleted) aiSheet = AiSheet.None }
    // losing the connection deliberately does nothing here any more. closing the card out from
    // under the user was the worst version: on a flaky network the assistant shut itself
    // mid-sentence and the button changed colour, so what they were reading and what they were
    // about to tap both moved at once. it stays open and grows a switch to offline instead
    val enabledOptional = remember(enabledOptionalRoutes) {
        optionalNavItems.filter { it.route in enabledOptionalRoutes }
    }

    // a tapped myndora://connect invite has to land on Together whichever screen the app opened
    // on, cold start from the link included
    val togetherViewModel: com.muradgalayev.brainbuddy.ui.together.TogetherViewModel = hiltViewModel()
    val pendingInviteToken by togetherViewModel.incomingInviteToken.collectAsState()
    LaunchedEffect(pendingInviteToken) {
        if (pendingInviteToken != null && currentRoute != Screen.Together.route) {
            navController.navigate(Screen.Together.route) { launchSingleTop = true }
        }
    }

    // AI-driven navigation: tools emit a route through AiNavigator, so drive the NavController
    // here and close the assistant card so the screen is visible
    LaunchedEffect(Unit) {
        aiViewModel.navigationCommands.collect { route ->
            aiSheet = AiSheet.None
            navController.navigate(route) {
                popUpTo(Screen.Home.route) { saveState = false }
                launchSingleTop = true
                restoreState = false
            }
        }
    }

    // insert optional items before Settings, which stays last
    val allEnabled = remember(enabledOptional) {
        val fixed = fixedNavItems.toMutableList()
        val settingsIndex = fixed.indexOfFirst { it is Screen.Settings }
        fixed.addAll(settingsIndex, enabledOptional)
        fixed.toList()
    }

    // past the cap, show the first MAX-1 plus a More button
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
        Screen.ShapeFlow.route,
        Screen.Calendar.route,
        Screen.OnboardingAppearance.route,
        Screen.OnboardingChoice.route,
        Screen.QuickSetup.route,
        Screen.DeepDive.route,
        Screen.CareNearby.route,
        Screen.Reservation.route,
        Screen.ReservationHistory.route,
        Screen.EditProfile.route,
        Screen.Customization.route,
        Screen.NotificationSettings.route,
        Screen.AiSettings.route,
        "${Screen.AiSettings.route}?focusWellness={focusWellness}",
        Screen.LinkedAccounts.route,
        Screen.LinkedDevices.route,
        Screen.Health.route,
        Screen.WellnessSummary.route,
        Screen.MedicationEdit.route,
        Screen.ActivityGoals.route,
        Screen.WellnessEdit.route,
        Screen.Modes.route,
        "${Screen.ModeEdit.route}?modeId={modeId}",
    )
    val isFullScreen = currentRoute in fullScreenRoutes
    val aiBlockedRoutes = setOf(
        Screen.Splash.route,
        Screen.Auth.route,
        Screen.OnboardingAppearance.route,
        Screen.OnboardingChoice.route,
        Screen.QuickSetup.route,
        Screen.DeepDive.route,
    )
    val globalAiAvailable = currentRoute != null && currentRoute !in aiBlockedRoutes && surveyCompleted
    val motionDuration = if (animationsOn()) 300 else 0
    val fadeDuration = if (animationsOn()) 220 else 0

    // push/pop slide for hierarchical sub-pages: in from the right, back out to the right
    val pageEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideIntoContainer(
            AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(motionDuration),
        ) + fadeIn(tween(fadeDuration))
    }
    val pagePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutOfContainer(
            AnimatedContentTransitionScope.SlideDirection.End,
            animationSpec = tween(motionDuration),
        ) + fadeOut(tween(fadeDuration))
    }
    // no animation on bottom padding, an extra 220ms layout pass on every tab change made
    // navigation feel sluggish on older devices for no visible benefit
    val showBottomNav = !isFullScreen || isNavDragging
    val bottomPadding by animateDpAsState(
        targetValue = if (showBottomNav) 104.dp else 0.dp,
        animationSpec = tween(if (animationsOn()) 260 else 0, easing = FastOutSlowInEasing),
        label = "navigationContentInset",
    )
    val tabRoutes = allEnabled.map { it.route }
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
                val from = tabRoutes.indexOf(initialState.destination.route)
                val to = tabRoutes.indexOf(targetState.destination.route)
                if (from >= 0 && to >= 0) {
                    slideIntoContainer(
                        towards = if (to > from) AnimatedContentTransitionScope.SlideDirection.Start
                        else AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(380, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(240, easing = FastOutSlowInEasing))
                } else {
                    fadeIn(tween(240, easing = FastOutSlowInEasing))
                }
            },
            exitTransition = {
                val from = tabRoutes.indexOf(initialState.destination.route)
                val to = tabRoutes.indexOf(targetState.destination.route)
                if (from >= 0 && to >= 0) {
                    slideOutOfContainer(
                        towards = if (to > from) AnimatedContentTransitionScope.SlideDirection.Start
                        else AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(380, easing = FastOutSlowInEasing),
                    ) + fadeOut(tween(210, easing = FastOutSlowInEasing))
                } else {
                    fadeOut(tween(180, easing = FastOutSlowInEasing))
                }
            },
            popEnterTransition = {
                val from = tabRoutes.indexOf(initialState.destination.route)
                val to = tabRoutes.indexOf(targetState.destination.route)
                if (from >= 0 && to >= 0) {
                    slideIntoContainer(
                        towards = if (to > from) AnimatedContentTransitionScope.SlideDirection.Start
                        else AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(380, easing = FastOutSlowInEasing),
                    ) + fadeIn(tween(240, easing = FastOutSlowInEasing))
                } else fadeIn(tween(240, easing = FastOutSlowInEasing))
            },
            popExitTransition = {
                val from = tabRoutes.indexOf(initialState.destination.route)
                val to = tabRoutes.indexOf(targetState.destination.route)
                if (from >= 0 && to >= 0) {
                    slideOutOfContainer(
                        towards = if (to > from) AnimatedContentTransitionScope.SlideDirection.Start
                        else AnimatedContentTransitionScope.SlideDirection.End,
                        animationSpec = tween(380, easing = FastOutSlowInEasing),
                    ) + fadeOut(tween(210, easing = FastOutSlowInEasing))
                } else fadeOut(tween(180, easing = FastOutSlowInEasing))
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
                        navController.navigate(Screen.OnboardingAppearance.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.Auth.route) {
                AuthScreen(
                    onAuthSuccess = {
                        // route through Splash so onboarding gating applies to new users
                        navController.navigate(Screen.Splash.route) {
                            popUpTo(Screen.Auth.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(Screen.OnboardingAppearance.route) {
                com.muradgalayev.brainbuddy.ui.onboarding.AppearanceSetupScreen(
                    onContinue = {
                        // popped on the way out, so finishing the questionnaire later doesn't unwind onto appearance
                        navController.navigate(Screen.OnboardingChoice.route) {
                            popUpTo(Screen.OnboardingAppearance.route) { inclusive = true }
                        }
                    },
                )
            }
            composable(Screen.OnboardingChoice.route) {
                val onboardingViewModel: com.muradgalayev.brainbuddy.ui.onboarding.OnboardingViewModel =
                    hiltViewModel()
                com.muradgalayev.brainbuddy.ui.onboarding.OnboardingChoiceScreen(
                    onPickQuick = { navController.navigate(Screen.QuickSetup.route) },
                    onPickDeep = { navController.navigate(Screen.DeepDive.route) },
                    onSkip = { skipOnboarding(navController, onboardingViewModel) },
                )
            }
            composable(Screen.QuickSetup.route) {
                val onboardingViewModel: com.muradgalayev.brainbuddy.ui.onboarding.OnboardingViewModel =
                    hiltViewModel()
                com.muradgalayev.brainbuddy.ui.onboarding.QuickSetupScreen(
                    onBack = { exitOnboarding(navController) },
                    onDone = { finishOnboarding(navController) },
                    onSkip = { skipOnboarding(navController, onboardingViewModel) },
                )
            }
            composable(Screen.DeepDive.route) {
                val onboardingViewModel: com.muradgalayev.brainbuddy.ui.onboarding.OnboardingViewModel =
                    hiltViewModel()
                com.muradgalayev.brainbuddy.ui.onboarding.DeepDiveScreen(
                    onBack = { exitOnboarding(navController) },
                    onDone = { finishOnboarding(navController) },
                    onSkip = { skipOnboarding(navController, onboardingViewModel) },
                )
            }
            composable(Screen.Home.route) {
                HomeScreen(
                    onContinueProfile = { navController.navigate(Screen.DeepDive.route) },
                    onOpenWellness = { navController.navigate(Screen.Health.route) },
                    onOpenPomodoro = {
                        navController.navigate(Screen.Pomodoro.route) { launchSingleTop = true }
                    },
                    onOpenProfile = {
                        navController.navigate(Screen.EditProfile.route) { launchSingleTop = true }
                    },
                    onManageModes = {
                        navController.navigate(Screen.Modes.route) { launchSingleTop = true }
                    },
                )
            }
            composable(Screen.Modes.route, enterTransition = pageEnter, popExitTransition = pagePopExit) {
                com.muradgalayev.brainbuddy.ui.modes.ModesScreen(
                    onBack = { navController.popBackStack() },
                    onEditMode = { modeId ->
                        // a blank id means new. encoded rather than left out so the route shape is the same either
                        // way and the arg is never missing
                        navController.navigate(
                            "${Screen.ModeEdit.route}?modeId=${android.net.Uri.encode(modeId.orEmpty())}"
                        )
                    },
                )
            }
            composable(
                "${Screen.ModeEdit.route}?modeId={modeId}",
                arguments = listOf(
                    navArgument("modeId") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                    }
                ),
                enterTransition = pageEnter,
                popExitTransition = pagePopExit,
            ) { entry ->
                com.muradgalayev.brainbuddy.ui.modes.ModeEditScreen(
                    modeId = entry.arguments?.getString("modeId")?.takeIf { it.isNotBlank() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.Activity.route) {
                WorkspaceScreen(
                    onNavigate = { route ->
                        navController.navigate(route)
                    }
                )
            }
            composable(
                Screen.ShapeFlow.route,
                enterTransition = pageEnter,
                popExitTransition = pagePopExit,
            ) {
                com.muradgalayev.brainbuddy.ui.games.ShapeFlowScreen(
                    onBack = { navController.popBackStack() },
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
                        // existing users edit the full questionnaire, quick/deep is only first-time onboarding
                        navController.navigate(Screen.DeepDive.route)
                    },
                    onContinueQuestionnaire = { version ->
                        val route = if (version == com.muradgalayev.brainbuddy.domain.model.SurveyVersion.Quick) {
                            Screen.QuickSetup.route
                        } else {
                            Screen.DeepDive.route
                        }
                        navController.navigate(route) { launchSingleTop = true }
                    },
                    onEditProfile = { navController.navigate(Screen.EditProfile.route) },
                    onOpenCustomization = { navController.navigate(Screen.Customization.route) },
                    onOpenNotifications = { navController.navigate(Screen.NotificationSettings.route) },
                    onOpenAiSettings = { navController.navigate(Screen.AiSettings.route) },
                    onOpenAiWellness = { navController.navigate("${Screen.AiSettings.route}?focusWellness=true") },
                    onOpenLinkedAccounts = { navController.navigate(Screen.LinkedAccounts.route) },
                    onOpenLinkedDevices = { navController.navigate(Screen.LinkedDevices.route) },
                    onOpenTogether = { navController.navigate(Screen.Together.route) },
                    onOpenModes = { navController.navigate(Screen.Modes.route) },
                )
            }
            composable(
                Screen.EditProfile.route,
                enterTransition = pageEnter,
                popExitTransition = pagePopExit,
            ) {
                com.muradgalayev.brainbuddy.ui.settings.EditProfileScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Screen.Customization.route,
                enterTransition = pageEnter,
                popExitTransition = pagePopExit,
            ) {
                com.muradgalayev.brainbuddy.ui.settings.CustomizationScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Screen.NotificationSettings.route,
                enterTransition = pageEnter,
                popExitTransition = pagePopExit,
            ) {
                com.muradgalayev.brainbuddy.ui.settings.NotificationsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                "${Screen.AiSettings.route}?focusWellness={focusWellness}",
                arguments = listOf(navArgument("focusWellness") {
                    type = NavType.BoolType
                    defaultValue = false
                }),
                enterTransition = pageEnter,
                popExitTransition = pagePopExit,
            ) { entry ->
                com.muradgalayev.brainbuddy.ui.settings.AiSettingsScreen(
                    onBack = { navController.popBackStack() },
                    focusWellness = entry.arguments?.getBoolean("focusWellness") == true,
                )
            }
            composable(
                Screen.LinkedAccounts.route,
                enterTransition = pageEnter,
                popExitTransition = pagePopExit,
            ) {
                com.muradgalayev.brainbuddy.ui.settings.LinkedAccountsScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Screen.LinkedDevices.route,
                enterTransition = pageEnter,
                popExitTransition = pagePopExit,
            ) {
                com.muradgalayev.brainbuddy.ui.settings.LinkedDevicesScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                Screen.Together.route,
                enterTransition = pageEnter,
                popExitTransition = pagePopExit,
            ) {
                com.muradgalayev.brainbuddy.ui.together.TogetherScreen(
                    onBack = { navController.popBackStack() },
                    onAddSomeone = { navController.navigate(Screen.AddConnection.route) },
                    onOpenConnection = { userId ->
                        navController.navigate(
                            "${Screen.ConnectionProfile.route}?userId=${android.net.Uri.encode(userId)}"
                        )
                    },
                )
            }
            composable(
                Screen.AddConnection.route,
                enterTransition = pageEnter,
                popExitTransition = pagePopExit,
            ) {
                com.muradgalayev.brainbuddy.ui.together.AddConnectionScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(
                "${Screen.ConnectionProfile.route}?userId={userId}",
                arguments = listOf(
                    navArgument("userId") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
                enterTransition = pageEnter,
                popExitTransition = pagePopExit,
            ) {
                com.muradgalayev.brainbuddy.ui.together.ConnectionProfileScreen(
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Screen.Calendar.route) {
                CalendarScreen(
                    onBackClick = { navController.popBackStack() },
                    onNavigateToPomodoro = {
                        navController.navigate(Screen.Pomodoro.route) {
                            launchSingleTop = true
                        }
                    },
                    onOpenBreakdown = { eventId ->
                        navController.navigate(
                            "${Screen.TaskBreakdown.route}?eventId=${android.net.Uri.encode(eventId)}"
                        )
                    },
                    onConnectPeople = { navController.navigate(Screen.AddConnection.route) },
                )
            }
            composable(
                "${Screen.TaskBreakdown.route}?eventId={eventId}",
                arguments = listOf(
                    navArgument("eventId") {
                        type = androidx.navigation.NavType.StringType
                        defaultValue = ""
                    }
                ),
                enterTransition = pageEnter,
                popExitTransition = pagePopExit,
            ) {
                com.muradgalayev.brainbuddy.ui.calendar.TaskBreakdownScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToPomodoro = {
                        navController.navigate(Screen.Pomodoro.route) {
                            launchSingleTop = true
                        }
                    },
                )
            }
            composable(Screen.Todo.route) {
                TodoScreen(
                    onConnectPeople = { navController.navigate(Screen.AddConnection.route) },
                )
            }
            composable(Screen.Health.route, enterTransition = pageEnter, popExitTransition = pagePopExit) {
                com.muradgalayev.brainbuddy.ui.activity.HealthScreen(
                    onBack = { navController.popBackStack() },
                    onMedications = { navController.navigate(Screen.Medications.route) },
                    onHealthConnect = { navController.navigate(Screen.WellnessSummary.route) },
                )
            }
            composable(
                Screen.WellnessSummary.route,
                enterTransition = pageEnter,
                popExitTransition = pagePopExit,
            ) {
                com.muradgalayev.brainbuddy.ui.activity.WellnessSummaryScreen(
                    onBack = { navController.popBackStack() },
                    onConnectHealth = { navController.navigate(Screen.LinkedDevices.route) },
                    onActivityGoals = { navController.navigate(Screen.ActivityGoals.route) },
                    onEdit = { navController.navigate(Screen.WellnessEdit.route) },
                    onMedications = { navController.navigate(Screen.Medications.route) },
                )
            }
            composable(Screen.WellnessEdit.route, enterTransition = pageEnter, popExitTransition = pagePopExit) {
                com.muradgalayev.brainbuddy.ui.activity.WellnessEditScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.ActivityGoals.route, enterTransition = pageEnter, popExitTransition = pagePopExit) {
                com.muradgalayev.brainbuddy.ui.activity.ActivityGoalsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.Medications.route, enterTransition = pageEnter, popExitTransition = pagePopExit) {
                com.muradgalayev.brainbuddy.ui.activity.MedicationsScreen(
                    onBack = { navController.popBackStack() },
                    // a null id means new. the editor tells the two apart by whether it finds a matching
                    // medication, so there's no separate add route
                    onEditMedication = { id ->
                        navController.navigate(
                            if (id == null) Screen.MedicationEdit.route
                            else "${Screen.MedicationEdit.route}?id=$id"
                        )
                    },
                )
            }
            composable(
                route = "${Screen.MedicationEdit.route}?id={id}",
                arguments = listOf(navArgument("id") { nullable = true; defaultValue = null; type = NavType.StringType }),
                enterTransition = pageEnter,
                popExitTransition = pagePopExit,
            ) { entry ->
                com.muradgalayev.brainbuddy.ui.activity.MedicationEditScreen(
                    medicationId = entry.arguments?.getString("id"),
                    onBack = { navController.popBackStack() },
                )
            }
            // reached from the focus-together card. its own screen because waiting is a whole job on
            // its own, not a strip on the timer
            composable(
                route = "focus_room/{sessionId}",
                arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
            ) { entry ->
                FocusRoomScreen(
                    sessionId = entry.arguments?.getString("sessionId").orEmpty(),
                    onClose = { navController.popBackStack() },
                )
            }
            composable(Screen.Pomodoro.route) {
                PomodoroScreen(
                    onBackClick = { navController.popBackStack() },
                    onOpenFocusRoom = { sessionId ->
                        navController.navigate("focus_room/$sessionId") {
                            launchSingleTop = true
                        }
                    },
                    onOpenBreakdown = { eventId ->
                        navController.navigate(
                            "${Screen.TaskBreakdown.route}?eventId=${android.net.Uri.encode(eventId)}"
                        ) { launchSingleTop = true }
                    },
                )
            }
            composable(Screen.CareNearby.route, enterTransition = pageEnter, popExitTransition = pagePopExit) {
                com.muradgalayev.brainbuddy.ui.care.CareNearbyScreen(
                    onBack = { navController.popBackStack() },
                    onReserve = { placeId ->
                        val route = placeId?.let {
                            "${Screen.Reservation.route}?placeId=${android.net.Uri.encode(it)}"
                        } ?: Screen.Reservation.route
                        navController.navigate(route)
                    },
                )
            }
            composable(
                "${Screen.Reservation.route}?placeId={placeId}",
                arguments = listOf(
                    navArgument("placeId") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
                enterTransition = pageEnter,
                popExitTransition = pagePopExit,
            ) {
                com.muradgalayev.brainbuddy.ui.reservation.ReservationScreen(
                    onBack = { navController.popBackStack() },
                    onHistory = { navController.navigate(Screen.ReservationHistory.route) },
                )
            }
            composable(
                Screen.ReservationHistory.route,
                enterTransition = pageEnter,
                popExitTransition = pagePopExit,
            ) {
                com.muradgalayev.brainbuddy.ui.reservation.ReservationHistoryScreen(
                    onBack = { navController.popBackStack() },
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

        // scrim
        AnimatedVisibility(
            visible = aiSheet != AiSheet.None && globalAiAvailable,
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
                        onClick = {
                            aiSheet = AiSheet.None
                        }
                    )
            )
        }

        // offline, the same button opens the on-device assistant instead. the online card could only
        // show a network error, and 'here are eight things I can still do' is a better answer
        AnimatedVisibility(
            visible = aiSheet == AiSheet.Offline && globalAiAvailable,
            enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.92f, animationSpec = tween(230)),
            exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.95f, animationSpec = tween(170)),
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 120.dp),
        ) {
            OfflineAssistantPanel(onDismiss = { aiSheet = AiSheet.None })
        }

        // AI prompt card
        AnimatedVisibility(
            visible = aiSheet == AiSheet.Online && globalAiAvailable,
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
            AiPromptCard(
                onDismiss = {
                    aiSheet = AiSheet.None
                },
                viewModel = aiViewModel,
                onSwitchToOffline = { aiSheet = AiSheet.Offline },
            )
        }

        AnimatedVisibility(
            visible = showBottomNav,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            ) + fadeIn(tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(220),
            ) + fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            BottomNavBar(
                items = visibleItems,
                overflowItems = overflowItems,
                // resolved to the owning tab, so a detail screen keeps its parent lit instead of falling back
                // to Home. only the highlight uses this, the full-screen and AI checks read the real route
                currentRoute = tabRouteFor(currentRoute),
                onItemClick = { screen ->
                    aiSheet = AiSheet.None
                    navController.navigate(screen.route) {
                        popUpTo(Screen.Home.route) {
                            saveState = false
                        }
                        launchSingleTop = true
                        restoreState = false
                    }
                },
                onAiClick = {
                    // one button, one destination, connected or not. which panel a tap produced used to depend
                    // on the network at that instant, which made the same gesture unpredictable
                    if (surveyCompleted) {
                        aiSheet = if (aiSheet != AiSheet.None) AiSheet.None else AiSheet.Online
                    }
                },
                isAiOpen = aiSheet != AiSheet.None,
                hasActiveAiChat = aiHasActiveChat,
                aiEnabled = surveyCompleted,
                onDragStateChanged = { isNavDragging = it },
            )
        }
    }
}

// navigation after finishing or editing the ADHD survey. pops the survey and the choice
// screen. if that empties the back stack (a new user who came from Splash) go Home, if it
// lands back on the previous screen (editing from Settings) stay there

private fun finishOnboarding(navController: NavHostController) {

    // editing from Settings should always land back on Settings after the success panel
    if (navController.popBackStack(Screen.Settings.route, inclusive = false)) return

    val popped = navController.popBackStack(Screen.OnboardingChoice.route, inclusive = true)

    if (!popped || navController.currentDestination == null) {

        navController.navigate(Screen.Home.route) {

            popUpTo(0) { inclusive = true }

        }

    }

}

// 'I'll do this later', from the chooser or from either survey. records the skip in DataStore
// with no network, so Splash stops routing back here, then clears the onboarding stack.
// independent of every save path on purpose: this is the exit that works when the server doesn't
private fun skipOnboarding(
    navController: NavHostController,
    onboardingViewModel: com.muradgalayev.brainbuddy.ui.onboarding.OnboardingViewModel,
) {
    onboardingViewModel.skipOnboarding()
    navController.navigate(Screen.Home.route) {
        popUpTo(0) { inclusive = true }
    }
}

private fun exitOnboarding(navController: NavHostController) {
    // launched from ADHD Profile in Settings, both Back and Save & exit should skip the chooser
    // and return to Settings
    if (navController.popBackStack(Screen.Settings.route, inclusive = false)) return
    navController.popBackStack()
}
