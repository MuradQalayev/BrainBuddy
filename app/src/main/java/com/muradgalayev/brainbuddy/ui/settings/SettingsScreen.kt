package com.muradgalayev.brainbuddy.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.muradgalayev.brainbuddy.ui.settings.components.AppearanceRow
import com.muradgalayev.brainbuddy.ui.settings.components.FocusModeSection
import com.muradgalayev.brainbuddy.ui.settings.components.QuickAccessSection
import com.muradgalayev.brainbuddy.ui.theme.AiButtonDark
import com.muradgalayev.brainbuddy.ui.theme.AiButtonDarkEnd
import com.muradgalayev.brainbuddy.ui.theme.AiButtonLight
import com.muradgalayev.brainbuddy.ui.theme.AiButtonLightEnd

private val CardCorner = 24.dp
private val IconCircleSize = 44.dp
private val ScreenHorizontalPadding = 20.dp

@Composable
fun SettingsScreen(
    onLogout: () -> Unit = {},
    onEditAdhdProfile: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val fontMode by viewModel.fontMode.collectAsState()
    val enabledNavItems by viewModel.enabledNavItems.collectAsState()
    val focusModeEnabled by viewModel.focusModeEnabled.collectAsState()
    val simplifiedWorkspace by viewModel.simplifiedWorkspace.collectAsState()
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    var quickAccessOpen by rememberSaveable { mutableStateOf(false) }
    var pomodoroOpen by rememberSaveable { mutableStateOf(false) }
    val fontSize by viewModel.fontSize.collectAsState()
    val loggedOut by viewModel.loggedOut.collectAsState()
    val exportingToCalendar by viewModel.exportingToCalendar.collectAsState()
    val calendarExportMessage by viewModel.calendarExportMessage.collectAsState()
    val calendarAuthorizationRequest by viewModel.calendarAuthorizationRequest.collectAsState()
    val linkedGoogleEmail by viewModel.linkedGoogleEmail.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val profileSaving by viewModel.profileSaving.collectAsState()
    val profileSaveMessage by viewModel.profileSaveMessage.collectAsState()
    var editProfileOpen by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    val calendarAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        viewModel.onCalendarAuthorizationResult(result.data)
    }

    LaunchedEffect(loggedOut) {
        if (loggedOut) onLogout()
    }

    LaunchedEffect(calendarAuthorizationRequest) {
        val pi = calendarAuthorizationRequest ?: return@LaunchedEffect
        viewModel.consumeCalendarAuthorizationRequest()
        calendarAuthLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Scrim when appearance panel is open
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
                .verticalScroll(rememberScrollState())
                .zIndex(if (appearanceOpen) 2f else 0f)
        ) {
            // ── Hero Profile Card (edge-to-edge banner) ──
            ProfileHeroCard(
                name = profile.name,
                username = profile.username,
                email = profile.email,
                avatarUrl = profile.avatarUrl,
                onEditProfile = { editProfileOpen = true }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ScreenHorizontalPadding)
            ) {
                // ── Customization ──
                SectionHeader(title = "Customization")

                Spacer(modifier = Modifier.height(10.dp))

                AppearanceRow(
                    themeMode = themeMode,
                    fontMode = fontMode,
                    fontSize = fontSize,
                    expanded = appearanceOpen,
                    onToggle = { appearanceOpen = !appearanceOpen },
                    onThemeChange = { viewModel.setThemeMode(it) },
                    onFontChange = { viewModel.setFontMode(it) },
                    onFontSizeChange = { viewModel.setFontSize(it) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                QuickAccessSection(
                    enabledRoutes = enabledNavItems,
                    expanded = quickAccessOpen,
                    onToggleExpanded = { quickAccessOpen = !quickAccessOpen },
                    onToggle = { route, enabled -> viewModel.toggleNavItem(route, enabled) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── About You ──
                SectionHeader(title = "About You")

                Spacer(modifier = Modifier.height(10.dp))

                AdhdProfileRow(onClick = onEditAdhdProfile)

                Spacer(modifier = Modifier.height(24.dp))

                // ── Focus ──
                SectionHeader(title = "Focus")

                Spacer(modifier = Modifier.height(10.dp))

                FocusModeSection(
                    enabled = focusModeEnabled,
                    expanded = pomodoroOpen,
                    onToggleExpanded = { pomodoroOpen = !pomodoroOpen },
                    onToggle = { viewModel.toggleFocusMode(it) }
                )

                Spacer(modifier = Modifier.height(10.dp))

                SimplifiedWorkspaceRow(
                    enabled = simplifiedWorkspace,
                    onToggle = { viewModel.toggleSimplifiedWorkspace(it) }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── Integrations ──
                SectionHeader(title = "Integrations")

                Spacer(modifier = Modifier.height(10.dp))

                GoogleCalendarExportRow(
                    isExporting = exportingToCalendar,
                    linkedEmail = linkedGoogleEmail,
                    onExport = { viewModel.exportToGoogleCalendar() },
                    onDisconnect = { viewModel.disconnectGoogleCalendar() }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // ── About ──
                SectionHeader(title = "About")

                Spacer(modifier = Modifier.height(10.dp))

                AboutSection()

                Spacer(modifier = Modifier.height(28.dp))

                // ── Sign Out (separated, red) ──
                SignOutRow(onSignOut = viewModel::signOut)

                Spacer(modifier = Modifier.height(24.dp))

                // ── Footer ──
                Text(
                    text = "BrainBuddy v1.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                // Breathing room above bottom nav bar
                Spacer(modifier = Modifier.height(96.dp))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .zIndex(3f)
        ) { data ->
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface
            )
        }
    }

    if (editProfileOpen) {
        EditProfileDialog(
            initialName = profile.name.orEmpty(),
            initialUsername = profile.username.orEmpty(),
            saving = profileSaving,
            onDismiss = { editProfileOpen = false },
            onSave = { name, username ->
                viewModel.updateProfile(name, username)
            }
        )
    }
}

// ── Hero Profile Card (gradient header inspired by screenshot) ──

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
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(AiButtonLight, AiButtonLightEnd)
                )
            )
            .padding(horizontal = 24.dp)
            .padding(top = 36.dp, bottom = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatar(
                    avatarUrl = avatarUrl,
                    initials = initials,
                    size = 68.dp
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = handle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Edit Profile action button
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable(onClick = onEditProfile),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.20f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.32f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Edit Profile",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
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
    saving: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, username: String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var username by rememberSaveable { mutableStateOf(initialUsername) }

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
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    leadingIcon = {
                        Icon(Icons.Rounded.AlternateEmail, contentDescription = null)
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
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name, username) },
                enabled = !saving && name.isNotBlank(),
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
    color: Color = MaterialTheme.colorScheme.surfaceContainer,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val baseModifier = modifier
        .fillMaxWidth()
        .border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(CardCorner)
        )

    if (onClick != null) {
        Surface(
            modifier = baseModifier,
            shape = RoundedCornerShape(CardCorner),
            color = color,
            shadowElevation = 1.dp,
            onClick = onClick,
            content = { content() }
        )
    } else {
        Surface(
            modifier = baseModifier,
            shape = RoundedCornerShape(CardCorner),
            color = color,
            shadowElevation = 1.dp,
            content = { content() }
        )
    }
}

@Composable
private fun IconBubble(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    background: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    size: Dp = IconCircleSize
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = background,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── Section Header ──

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
        letterSpacing = 1.6.sp,
        modifier = Modifier.padding(start = 6.dp)
    )
}

// ── Google Calendar Export Row ──

@Composable
private fun GoogleCalendarExportRow(
    isExporting: Boolean,
    linkedEmail: String?,
    onExport: () -> Unit,
    onDisconnect: () -> Unit
) {
    val isConnected = linkedEmail != null
    val rowClickable = !isConnected && !isExporting

    ListCard {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = rowClickable, onClick = onExport)
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .heightIn(min = 60.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.size(IconCircleSize)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google_calendar),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Calendar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isConnected) "Sync your calendar events to Google Calendar"
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
                ConnectedAccountChip(
                    email = linkedEmail,
                    onDisconnect = onDisconnect
                )
                Spacer(modifier = Modifier.height(12.dp))
                ExportActionButton(
                    isExporting = isExporting,
                    onClick = onExport
                )
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun StatusPill() {
    val accent = Color(0xFF8AAE7E)
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
    val gradient = if (isSystemInDarkTheme()) {
        Brush.horizontalGradient(listOf(AiButtonDark, AiButtonDarkEnd))
    } else {
        Brush.horizontalGradient(listOf(AiButtonLight, AiButtonLightEnd))
    }
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

// ── About Section ──

@Composable
private fun AdhdProfileRow(onClick: () -> Unit) {
    ListCard(onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .heightIn(min = 60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBubble(icon = Icons.Outlined.Person)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ADHD Profile",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Edit your survey responses",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AboutSection() {
    ListCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBubble(icon = Icons.Outlined.Info)
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = "Version",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            // Pill badge — same visual slot as the "Free" pill in the reference
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = "1.0",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

// ── Shared settings components ──

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

// ── Simplified Workspace Toggle ──

@Composable
private fun SimplifiedWorkspaceRow(
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val cardColor = if (enabled)
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
    else
        MaterialTheme.colorScheme.surfaceContainer

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (enabled)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                else
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(CardCorner)
            ),
        shape = RoundedCornerShape(CardCorner),
        color = cardColor,
        shadowElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .heightIn(min = 60.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBubble(
                icon = Icons.Rounded.VisibilityOff,
                tint = if (enabled)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                background = if (enabled)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                else
                    MaterialTheme.colorScheme.surfaceContainerHighest
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Simplified Workspace",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (enabled)
                        "Showing only actionable items"
                    else
                        "Hide distractions in workspace",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    checkedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
}

@Composable
private fun SignOutRow(onSignOut: () -> Unit) {
    val errorColor = MaterialTheme.colorScheme.error

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CardCorner))
            .clickable(onClick = onSignOut)
            .border(
                width = 1.5.dp,
                color = errorColor.copy(alpha = 0.45f),
                shape = RoundedCornerShape(CardCorner)
            ),
        shape = RoundedCornerShape(CardCorner),
        color = errorColor.copy(alpha = 0.06f),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 18.dp, vertical = 16.dp)
                .heightIn(min = 60.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = errorColor.copy(alpha = 0.16f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Logout,
                        contentDescription = null,
                        tint = errorColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Sign Out",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = errorColor,
                letterSpacing = 0.4.sp
            )
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
            .clickable(onClick = onClick)
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
