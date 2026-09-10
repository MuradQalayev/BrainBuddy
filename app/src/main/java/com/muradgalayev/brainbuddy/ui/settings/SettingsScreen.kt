package com.muradgalayev.brainbuddy.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.ui.accessibility.speaking
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.settings.components.AppearanceRow
import com.muradgalayev.brainbuddy.ui.settings.components.NotificationsSection
import com.muradgalayev.brainbuddy.ui.settings.components.QuickAccessSection
import com.muradgalayev.brainbuddy.data.notifications.ReminderCategory
import com.muradgalayev.brainbuddy.ui.modes.modeAccentColor
import com.muradgalayev.brainbuddy.ui.modes.modeIcon
import com.muradgalayev.brainbuddy.ui.modes.scheduleSummary
import kotlinx.coroutines.launch

private val CardCorner = 24.dp
private val IconCircleSize = 44.dp
private val ScreenHorizontalPadding = 20.dp

@Composable
fun SettingsScreen(
    onLogout: () -> Unit = {},
    onEditAdhdProfile: () -> Unit = {},
    onContinueQuestionnaire: (com.muradgalayev.brainbuddy.domain.model.SurveyVersion) -> Unit = {
        onEditAdhdProfile()
    },
    onEditProfile: () -> Unit = {},
    onOpenCustomization: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenAiSettings: () -> Unit = {},
    onOpenAiWellness: () -> Unit = {},
    onOpenLinkedAccounts: () -> Unit = {},
    onOpenLinkedDevices: () -> Unit = {},
    onOpenTogether: () -> Unit = {},
    onOpenModes: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val activeMode by viewModel.activeMode.collectAsState()
    val fontMode by viewModel.fontMode.collectAsState()
    val enabledNavItems by viewModel.enabledNavItems.collectAsState()
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    var quickAccessOpen by rememberSaveable { mutableStateOf(false) }
    var pomodoroOpen by rememberSaveable { mutableStateOf(false) }
    var notificationsOpen by rememberSaveable { mutableStateOf(false) }
    val todoReminderKinds by viewModel.todoReminderKinds.collectAsState()
    val calendarReminderKinds by viewModel.calendarReminderKinds.collectAsState()
    val dailySummaryEnabled by viewModel.dailySummaryEnabled.collectAsState()
    val pomodoroNudgeFrequency by viewModel.pomodoroNudgeFrequency.collectAsState()
    val pomodoroNudgeTime by viewModel.pomodoroNudgeTime.collectAsState()
    val pomodoroBreakReminders by viewModel.pomodoroBreakReminders.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()
    val exportingToCalendar by viewModel.exportingToCalendar.collectAsState()
    val calendarExportMessage by viewModel.calendarExportMessage.collectAsState()
    val profileExporting by viewModel.profileExportInProgress.collectAsState()
    val profileExportMessage by viewModel.profileExportMessage.collectAsState()
    val calendarAuthorizationRequest by viewModel.calendarAuthorizationRequest.collectAsState()
    val linkedGoogleEmail by viewModel.linkedGoogleEmail.collectAsState()
    val healthConnectState by viewModel.healthConnectState.collectAsState()
    val aiHealthPersonalization by viewModel.aiHealthPersonalization.collectAsState(initial = false)
    val profile by viewModel.profile.collectAsState()
    val surveyCompleted by viewModel.surveyCompleted.collectAsState()
    val questionnaireProgress by viewModel.questionnaireProgress.collectAsState()
    val profileSaving by viewModel.profileSaving.collectAsState()
    val profileSaveMessage by viewModel.profileSaveMessage.collectAsState()
    val passwordResetState by viewModel.passwordResetState.collectAsState()
    val pendingTogetherRequests by viewModel.pendingTogetherRequests.collectAsState()
    val togetherConnections by viewModel.togetherConnections.collectAsState()
    var editProfileOpen by rememberSaveable { mutableStateOf(false) }
    var passwordResetOpen by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val calendarAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onCalendarAuthorizationResult(result.data)
    }

    LaunchedEffect(loggedOut) {
        if (loggedOut) onLogout()
    }

    // re-check on every entry so the prompt clears the moment the survey is finished
    LaunchedEffect(Unit) { viewModel.refreshSurveyCompleted() }

    // pull the freshest photo and name from cache on resume, e.g. right after editing the profile
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.reseedProfileFromCache()
        viewModel.refreshHealthConnect()
    }

    LaunchedEffect(calendarAuthorizationRequest) {
        val pi = calendarAuthorizationRequest ?: return@LaunchedEffect
        viewModel.consumeCalendarAuthorizationRequest()
        calendarAuthLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
    }

    LaunchedEffect(profileExportMessage) {
        val msg = profileExportMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearProfileExportMessage()
        }
    }

    LaunchedEffect(calendarExportMessage) {
        val msg = calendarExportMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearCalendarExportMessage()
        }
    }

    LaunchedEffect(profileSaveMessage) {
        val msg = profileSaveMessage
        if (msg != null) {
            snackbarHostState.showSnackbar(msg)

            if (msg == "Profile updated") {
                editProfileOpen = false
            }

            viewModel.clearProfileSaveMessage()
        }
    }

    LaunchedEffect(passwordResetState.sentTo) {
        val email = passwordResetState.sentTo ?: return@LaunchedEffect
        passwordResetOpen = false
        snackbarHostState.showSnackbar("Password link sent to $email")
        viewModel.clearPasswordResetFeedback()
    }

    // collapsing header: once the profile card scrolls past, show a compact top bar
    val scrollState = rememberScrollState()
    val collapseThresholdPx = with(LocalDensity.current) { 150.dp.toPx() }
    val collapsed by remember {
        derivedStateOf { scrollState.value > collapseThresholdPx }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // scrim while the appearance panel is open
        if (appearanceOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.15f))
                    .zIndex(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { appearanceOpen = false }
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .zIndex(if (appearanceOpen) 2f else 0f)
        ) {
            // title header
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding),
            )

            Spacer(modifier = Modifier.height(20.dp))

            // profile card
            ProfileCard(
                name = profile.name,
                username = profile.username,
                email = profile.email,
                avatarUrl = profile.avatarUrl,
                onEditProfile = onEditProfile,
                downloadInProgress = profileExporting,
                onDownloadProfile = { viewModel.downloadMyProfile() },
                modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
            )

            if (!surveyCompleted) {
                Spacer(modifier = Modifier.height(12.dp))
                CompleteSurveyBanner(
                    progress = questionnaireProgress,
                    onClick = {
                        val version = questionnaireProgress?.version
                        if (version != null) onContinueQuestionnaire(version) else onEditAdhdProfile()
                    },
                    modifier = Modifier.padding(horizontal = ScreenHorizontalPadding),
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding)
            ) {
                // about you
                SectionHeader(title = "About You")

                Spacer(modifier = Modifier.height(10.dp))

                AdhdProfileRow(completed = surveyCompleted, onClick = onEditAdhdProfile)

                Spacer(modifier = Modifier.height(24.dp))

                SectionHeader(title = "Account")

                Spacer(modifier = Modifier.height(10.dp))

                SettingsNavRow(
                    title = "Password & sign-in",
                    subtitle = "Create or reset your password",
                    icon = Icons.Rounded.Lock,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onClick = {
                        viewModel.clearPasswordResetFeedback()
                        passwordResetOpen = true
                    },
                )

                Spacer(modifier = Modifier.height(28.dp))

                // general
                SectionHeader(title = "General")

                Spacer(modifier = Modifier.height(10.dp))

                // a normal nav row like the ones below it, only its icon, tint and subtitle follow whatever
                // mode is running, so what's on is readable without the row standing apart from its neighbours
                val runningMode = activeMode
                val activeSchedule = runningMode?.schedule?.let {
                    scheduleSummary(it.days, it.startMinute, it.endMinute, it.enabled)
                }
                SettingsNavRow(
                    title = "Modes",
                    subtitle = when {
                        runningMode == null -> "Create calm, focused setups that switch together"
                        activeSchedule != null -> "${runningMode.name} active • $activeSchedule"
                        else -> "${runningMode.name} active • switched on manually"
                    },
                    icon = runningMode?.let { modeIcon(it.icon) } ?: Icons.Rounded.AutoAwesome,
                    iconTint = modeAccentColor(runningMode?.accent),
                    onClick = onOpenModes,
                )

                Spacer(modifier = Modifier.height(10.dp))

                SettingsNavRow(
                    title = "Customization",
                    subtitle = activeMode?.let { "Locked while ${it.name} mode is active" }
                        ?: "Theme, font & display",
                    icon = Icons.Outlined.Tune,
                    trailingIcon = if (activeMode != null) Icons.Rounded.Lock else null,
                    onClick = {
                        coroutineScope.launch {
                            val mode = viewModel.activeModeNow()
                            if (mode == null) {
                                onOpenCustomization()
                            } else {
                                snackbarHostState.showSnackbar(
                                    "Customization isn't available while ${mode.name} mode is active. " +
                                        "Turn it off or edit that mode first."
                                )
                            }
                        }
                    },
                )

                Spacer(modifier = Modifier.height(10.dp))

                SettingsNavRow(
                    title = "Notifications",
                    subtitle = "Reminders, daily summary & nudges",
                    icon = Icons.Outlined.Notifications,
                    iconTint = MaterialTheme.colorScheme.secondary,
                    onClick = onOpenNotifications,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Myndora AI
                SectionHeader(title = "Myndora AI")

                Spacer(modifier = Modifier.height(10.dp))

                SettingsNavRow(
                    title = "Myndora AI",
                    subtitle = if (healthConnectState.connected && !aiHealthPersonalization)
                        "1 boost available • unlock wellness-aware support"
                    else "Voice, wellness personalization & preferences",
                    iconRes = R.drawable.ic_ai,
                    iconTint = MaterialTheme.colorScheme.primary,
                    attentionCount = if (healthConnectState.connected && !aiHealthPersonalization) 1 else 0,
                    onClick = if (healthConnectState.connected && !aiHealthPersonalization)
                        onOpenAiWellness else onOpenAiSettings,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // people. just a doorway, the hub itself lives in the Workspace where the things you do with
                // a connection are. Settings only has to get you there and flag anything waiting
                SectionHeader(title = "People")

                Spacer(modifier = Modifier.height(10.dp))

                SettingsNavRow(
                    title = "Myndora Together",
                    subtitle = when {
                        pendingTogetherRequests.isNotEmpty() -> {
                            val count = pendingTogetherRequests.size
                            "$count ${if (count == 1) "request" else "requests"} waiting for you"
                        }
                        togetherConnections.isNotEmpty() ->
                            togetherConnections.take(2).joinToString(" & ") { it.name } +
                                if (togetherConnections.size > 2) {
                                    " +${togetherConnections.size - 2}"
                                } else ""
                        else -> "Add someone you trust"
                    },
                    icon = Icons.Rounded.Diversity3,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    attentionCount = pendingTogetherRequests.size,
                    onClick = onOpenTogether,
                )

                Spacer(modifier = Modifier.height(24.dp))

                // linked accounts
                SectionHeader(title = "Linked accounts")

                Spacer(modifier = Modifier.height(10.dp))

                SettingsNavRow(
                    title = "Google Calendar",
                    subtitle = linkedGoogleEmail ?: "Not connected",
                    iconRes = R.drawable.ic_google_calendar,
                    onClick = onOpenLinkedAccounts,
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (healthConnectState.connected) {
                    SettingsNavRow(
                        title = "Health Connect",
                        subtitle = "Connected • steps and heart rate",
                        iconRes = R.drawable.health,
                        iconTint = MaterialTheme.colorScheme.tertiary,
                        onClick = onOpenLinkedDevices,
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (!healthConnectState.connected) {
                    SettingsNavRow(
                        title = "Connect Health Connect",
                        subtitle = "Share steps and heart rate securely",
                        iconRes = R.drawable.health,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = onOpenLinkedDevices,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // about
                SectionHeader(
                    title = "About",
                    accent = MaterialTheme.colorScheme.outline,
                )

                Spacer(modifier = Modifier.height(10.dp))

                AboutSection()

                Spacer(modifier = Modifier.height(28.dp))

                // sign out, separated and red
                SignOutRow(onSignOut = viewModel::signOut)

                Spacer(modifier = Modifier.height(24.dp))

                // footer
                Text(
                    text = "Myndora v1.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                // breathing room above the bottom nav bar
                Spacer(modifier = Modifier.height(96.dp))
            }
        }

        // compact top bar, slides in once the profile card scrolls away
        AnimatedVisibility(
            visible = collapsed,
            enter = fadeIn(tween(180)) + slideInVertically(animationSpec = tween(220)) { -it },
            exit = fadeOut(tween(150)) + slideOutVertically(animationSpec = tween(180)) { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .zIndex(4f),
        ) {
            val barInitials = (profile.name?.takeIf { it.isNotBlank() } ?: profile.username ?: profile.email)
                ?.split(" ", ".", "_", "-")
                ?.mapNotNull { it.firstOrNull()?.uppercase() }
                ?.take(2)?.joinToString("")?.takeIf { it.isNotBlank() } ?: "?"
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
                shadowElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                            .clickable(onClick = onEditProfile),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!profile.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(profile.avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profile",
                                modifier = Modifier.size(40.dp).clip(CircleShape),
                            )
                        } else {
                            Text(
                                text = barInitials,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        com.muradgalayev.brainbuddy.ui.sharedcomponents.MyndoraSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(16.dp)
                .zIndex(3f)
        )
    }

    if (passwordResetOpen) {
        PasswordResetDialog(
            email = profile.email,
            sending = passwordResetState.sending,
            error = passwordResetState.error,
            onSend = viewModel::sendPasswordReset,
            onDismiss = {
                passwordResetOpen = false
                viewModel.clearPasswordResetFeedback()
            },
        )
    }
}

@Composable
private fun PasswordResetDialog(
    email: String?,
    sending: Boolean,
    error: String?,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = "Create or reset password",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (email.isNullOrBlank()) {
                        "We couldn't find an email address for this account."
                    } else {
                        "We'll email $email. Open the secure link to choose a password. " +
                            "You can still continue with Google afterward."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                if (error != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSend,
                enabled = !sending && !email.isNullOrBlank(),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Send email", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !sending) {
                Text("Cancel")
            }
        },
    )
}

// Supportive progress card for a profile that can be continued in small chunks.
@Composable
private fun CompleteSurveyBanner(
    progress: com.muradgalayev.brainbuddy.data.notifications.QuestionnaireReminderProgress?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.44f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f)),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Your profile is taking shape",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = progress?.let {
                        "${it.answered} of ${it.total} steps complete · ${it.remaining} left"
                    } ?: "Continue to unlock more personal guidance.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (progress != null) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress.fraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(50)),
                        color = accent,
                        trackColor = accent.copy(alpha = 0.13f),
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

// profile card: avatar, name, email, chevron

@Composable
private fun ProfileCard(
    name: String?,
    username: String?,
    email: String?,
    avatarUrl: String?,
    onEditProfile: () -> Unit,
    downloadInProgress: Boolean,
    onDownloadProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val displayName = name?.takeIf { it.isNotBlank() }
        ?: email?.substringBefore("@")
        ?: "Welcome"
    val subtitle = email
        ?: username?.takeIf { it.isNotBlank() }?.let { "@$it" }
        ?: "Not signed in"
    val initials = (name?.takeIf { it.isNotBlank() } ?: username ?: email)
        ?.split(" ", ".", "_", "-")
        ?.mapNotNull { it.firstOrNull()?.uppercase() }
        ?.take(2)
        ?.joinToString("")
        ?.takeIf { it.isNotBlank() }
        ?: "?"

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f)),
    ) {
      Column {
        // the identity row has its own click target now that the card has a second action. tapping
        // anywhere used to mean edit profile, which swallowed taps meant for the download strip
        Row(
            modifier = Modifier
                .clickable(onClick = onEditProfile)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = colors.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    if (!avatarUrl.isNullOrBlank()) {
                        val context = LocalContext.current
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(avatarUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Profile picture",
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    } else {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = colors.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }

        // download strip, tucked into the lower edge of the profile card: it's about your data, so
        // it belongs with your identity rather than as a full-weight row further down the page
        HorizontalDivider(color = colors.outlineVariant.copy(alpha = 0.45f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !downloadInProgress, onClick = onDownloadProfile)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (downloadInProgress) {
                CircularProgressIndicator(
                    modifier = Modifier.size(17.dp),
                    strokeWidth = 2.dp,
                    color = colors.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(17.dp),
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (downloadInProgress) "Preparing your PDF…" else "Download my profile",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                )
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = "A PDF with your details and the last week of activity.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        }
      }
    }
}

@Composable
private fun ProfileHeroCard(
    name: String?,
    username: String?,
    email: String?,
    avatarUrl: String?,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    val displayName = name?.takeIf { it.isNotBlank() }
        ?: email?.substringBefore("@")
        ?: "Welcome"
    val handle = username?.takeIf { it.isNotBlank() }?.let { "@$it" }
        ?: email
        ?: "Not signed in"
    val initialsSource = name?.takeIf { it.isNotBlank() }
        ?: username?.takeIf { it.isNotBlank() }
        ?: email

    val initials = initialsSource
        ?.split(" ", ".", "_", "-")
        ?.mapNotNull { it.firstOrNull()?.uppercase() }
        ?.take(2)
        ?.joinToString("")
        ?.takeIf { it.isNotBlank() }
        ?: "?"

    // deep charcoal hero with a warm gradient orb in the top-right corner
    val heroBg = Color(0xFF1B1B23)
    val heroShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = 36.dp,
        bottomEnd = 36.dp
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(heroShape)
            .background(heroBg)
    ) {
        // colours read outside the Canvas, a DrawScope isn't a composable context
        val orbAccent = MaterialTheme.myndoraAccents.accent
        val orbAccentEnd = MaterialTheme.myndoraAccents.accentEnd
        Canvas(modifier = Modifier.matchParentSize()) {
            val w = size.width
            val orbRadius = w * 0.55f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        orbAccent.copy(alpha = 0.85f),
                        orbAccentEnd.copy(alpha = 0.55f),
                        heroBg.copy(alpha = 0f),
                    ),
                    center = Offset(w * 1.05f, -w * 0.05f),
                    radius = orbRadius,
                ),
                radius = orbRadius,
                center = Offset(w * 1.05f, -w * 0.05f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 44.dp, bottom = 28.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(
                    avatarUrl = avatarUrl,
                    initials = initials,
                    size = 60.dp
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = handle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            // edit profile, pill-shaped glass over the dark hero
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onEditProfile),
                shape = RoundedCornerShape(999.dp),
                color = Color.White.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Edit profile",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(
    avatarUrl: String?,
    initials: String,
    size: Dp
) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = 0.18f),
        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.55f)),
        modifier = Modifier.size(size)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                val context = LocalContext.current
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Profile picture",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun EditProfileDialog(
    initialName: String,
    initialUsername: String,
    initialPhone: String,
    email: String?,
    saving: Boolean,
    availability: com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability,
    onUsernameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (name: String, username: String, phone: String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var username by rememberSaveable { mutableStateOf(initialUsername) }
    var phone by rememberSaveable { mutableStateOf(initialPhone) }

    // fire an initial check for the pre-filled value so the user sees state immediately
    LaunchedEffect(Unit) { onUsernameChange(username) }

    val statusText: String? = when (availability) {
        com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability.Idle -> null
        com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability.Checking -> "Checking…"
        com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability.Available -> "Available"
        com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability.Taken -> "Already taken"
        com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability.Invalid ->
            "Use 3–20 letters, numbers or underscores"
    }
    val statusColor = when (availability) {
        com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability.Available ->
            Color(0xFF6B8F5D)
        com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability.Taken,
        com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability.Invalid ->
            MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val canSave = !saving && name.isNotBlank() && (
        availability == com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability.Available ||
            availability == com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability.Idle
    )

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Edit Profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Person, contentDescription = null)
                    },
                    singleLine = true,
                    enabled = !saving,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Column {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it
                            onUsernameChange(it)
                        },
                        label = { Text("Username") },
                        leadingIcon = {
                            Icon(Icons.Rounded.AlternateEmail, contentDescription = null)
                        },
                        trailingIcon = {
                            if (availability == com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability.Checking) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        singleLine = true,
                        enabled = !saving,
                        isError = availability ==
                            com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability.Taken ||
                            availability ==
                            com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability.Invalid,
                        shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (statusText != null) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = statusColor,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
                // phone, editable and saved to the account
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone (optional)") },
                    leadingIcon = { Icon(Icons.Rounded.Phone, contentDescription = null) },
                    placeholder = { Text("e.g. +1 555 123 4567") },
                    singleLine = true,
                    enabled = !saving,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                // email, read-only, the address used to sign in
                OutlinedTextField(
                    value = email.orEmpty(),
                    onValueChange = {},
                    label = { Text("Email") },
                    leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                    readOnly = true,
                    enabled = false,
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    supportingText = { Text("Used to sign in — can't be changed here") },
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, username, phone) },
                enabled = canSave,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ListCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(CardCorner)
    val baseModifier = modifier
        .fillMaxWidth()
        .border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            shape = shape,
        )

    if (onClick != null) {
        Surface(
            modifier = baseModifier,
            shape = shape,
            color = color,
            shadowElevation = 0.dp,
            onClick = onClick,
            content = { content() }
        )
    } else {
        Surface(
            modifier = baseModifier,
            shape = shape,
            color = color,
            shadowElevation = 0.dp,
            content = { content() }
        )
    }
}

@Composable
private fun IconBubble(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    background: Color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
    size: Dp = IconCircleSize
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = background,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

// section header

@Composable
private fun SectionHeader(title: String, accent: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 4.dp),
    )
}

// navigation row: icon, title, subtitle, chevron, opens a sub-page
@Composable
private fun SettingsNavRow(
    title: String,
    subtitle: String?,
    icon: ImageVector? = null,
    iconRes: Int? = null,
    onClick: () -> Unit,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    attentionCount: Int = 0,
    trailingIcon: ImageVector? = null,
) {
    // title only. the subtitle is usually current-state detail like '2 reminders on', which is
    // worth reading on the destination screen rather than on the way there
    ListCard(onClick = speaking(title, onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .heightIn(min = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (iconRes != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            } else if (icon != null) {
                IconBubble(
                    icon = icon,
                    tint = iconTint,
                    background = iconTint.copy(alpha = 0.14f),
                    size = 44.dp,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (subtitle != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
            if (attentionCount > 0) {
                Surface(
                    modifier = Modifier.size(22.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            attentionCount.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = trailingIcon ?: Icons.Rounded.ChevronRight,
                contentDescription = if (trailingIcon != null) "Unavailable while a mode is active" else null,
                tint = if (trailingIcon != null) iconTint
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
    }
}

// collapsible group: a header row that expands to reveal its child settings
@Composable
private fun SettingsGroup(
    title: String,
    icon: ImageVector,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "group_chevron",
    )
    Column {
        ListCard(onClick = { expanded = !expanded }) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 18.dp)
                    .heightIn(min = 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBubble(
                    icon = icon,
                    tint = iconTint,
                    background = iconTint.copy(alpha = 0.14f),
                    size = 44.dp,
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.rotate(rotation),
                )
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column(
                modifier = Modifier.padding(top = 10.dp, start = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content,
            )
        }
    }
}

// google calendar export row

@Composable
fun GoogleCalendarExportRow(
    isExporting: Boolean,
    linkedEmail: String?,
    avatarUrl: String?,
    onExport: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isConnected = linkedEmail != null
    val rowClickable = !isConnected && !isExporting

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = rowClickable, onClick = onExport)
                    .heightIn(min = 68.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(62.dp)) {
                    Surface(
                        modifier = Modifier.size(56.dp).align(Alignment.TopStart),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surface),
                        shadowElevation = 3.dp,
                    ) {
                        if (isConnected && !avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current).data(avatarUrl).crossfade(true).build(),
                                contentDescription = "Google account photo",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            )
                        } else {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    linkedEmail?.firstOrNull()?.uppercaseChar()?.toString() ?: "G",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    Surface(
                        modifier = Modifier.size(25.dp).align(Alignment.BottomEnd),
                        shape = CircleShape,
                        color = Color.White,
                        shadowElevation = 2.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_google_calendar),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isConnected) "Google Calendar" else "Connect Google Calendar",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isConnected) linkedEmail.orEmpty()
                        else "Connect to push your events to Google Calendar",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                if (isConnected) {
                    StatusPill()
                } else {
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (linkedEmail != null) {
                Spacer(modifier = Modifier.height(12.dp))
                ExportActionButton(
                    isExporting = isExporting,
                    onClick = onExport
                )
                TextButton(
                    onClick = onDisconnect,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Disconnect Google account", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun StatusPill() {
    val accent = Color(0xFF0D9488)
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(accent)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Linked",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.4.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ConnectedAccountChip(
    email: String,
    onDisconnect: () -> Unit
) {
    val initial = email.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primary,
                                MaterialTheme.colorScheme.tertiary
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Connected account",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.4.sp
                )
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
            TextButton(
                onClick = onDisconnect,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Disconnect",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun ExportActionButton(
    isExporting: Boolean,
    onClick: () -> Unit
) {
    val accents = MaterialTheme.myndoraAccents
    val gradient = Brush.horizontalGradient(listOf(accents.accent, accents.accentEnd))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .height(50.dp),
        shape = RoundedCornerShape(14.dp),
        color = Color.Transparent,
        onClick = onClick,
        enabled = !isExporting,
        shadowElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isExporting) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Exporting…",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Sync,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Sync todos to Calendar",
                        color = Color.White,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.2.sp
                    )
                }
            }
        }
    }
}

// about section

@Composable
private fun AdhdProfileRow(completed: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val accent = colors.secondary
    val green = Color(0xFF3E9E5E)
    val red = Color(0xFFD84A4A)
    val statusColor = if (completed) green else red

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = colors.surface,
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f)),
    ) {
        Column {
            // header band, survey icon on a soft accent gradient
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            listOf(accent.copy(alpha = 0.18f), accent.copy(alpha = 0.05f))
                        )
                    )
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_survey),
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "ADHD Profile",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Personalizes how your assistant helps you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }

            // footer: status pill and action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = statusColor.copy(alpha = 0.12f),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(statusColor),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (completed) "Complete" else "Not finished",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = statusColor,
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = if (completed) "Edit" else "Finish now",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = accent,
                )
            }
        }
    }
}

@Composable
private fun AboutSection() {
    ListCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp)
                .heightIn(min = 60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBubble(
                icon = Icons.Outlined.Info,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                background = MaterialTheme.colorScheme.surfaceContainer,
                size = 48.dp,
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Version",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            // pill badge
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                ),
            ) {
                Text(
                    text = "1.0",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// shared settings components

@Composable
fun SectionLabel(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SignOutRow(onSignOut: () -> Unit) {
    val errorColor = MaterialTheme.colorScheme.error
    val shape = RoundedCornerShape(999.dp)

    // compact centred pill, not a full-width banner
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            modifier = Modifier
                .clip(shape)
                .clickable(onClick = onSignOut)
                .border(
                    width = 1.dp,
                    color = errorColor.copy(alpha = 0.30f),
                    shape = shape,
                ),
            shape = shape,
            color = errorColor.copy(alpha = 0.05f),
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Logout,
                    contentDescription = null,
                    tint = errorColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sign out",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = errorColor,
                )
            }
        }
    }
}

@Composable
fun PillOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val bg = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHighest

    val fg = if (selected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = speaking(label, onClick))
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = fg
            )
        }
    }
}
