package com.muradgalayev.brainbuddy.ui.settings

import android.graphics.Bitmap
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.ManageAccounts
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.muradgalayev.brainbuddy.ui.settings.components.ThemePicker
import com.muradgalayev.brainbuddy.data.notifications.ReminderCategory
import com.muradgalayev.brainbuddy.data.health.HealthConnectAvailability
import com.muradgalayev.brainbuddy.data.health.HealthConnectUiState
import androidx.health.connect.client.PermissionController
import com.muradgalayev.brainbuddy.data.sync.CalendarSyncFrequency
import com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability
import com.muradgalayev.brainbuddy.ui.settings.components.AppearanceRow
import com.muradgalayev.brainbuddy.ui.settings.components.NotificationsSection
import com.muradgalayev.brainbuddy.ui.settings.components.QuickAccessSection
import com.muradgalayev.brainbuddy.ui.settings.components.SettingsDetailHero
import com.muradgalayev.brainbuddy.ui.settings.components.SettingsSectionLabel
import com.muradgalayev.brainbuddy.ui.settings.components.SettingsSubScaffold
import com.muradgalayev.brainbuddy.ui.ai.MyndoraVoiceProfiles
import com.muradgalayev.brainbuddy.ui.ai.aiVoiceProfile
import com.muradgalayev.brainbuddy.ui.ai.curatedAiVoices
import com.muradgalayev.brainbuddy.ui.modes.modeAccentColor
import com.muradgalayev.brainbuddy.ui.modes.modeIcon
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CustomizationScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val activeMode by viewModel.activeMode.collectAsState()
    var entryMode by remember {
        mutableStateOf<com.muradgalayev.brainbuddy.domain.model.AppMode?>(null)
    }
    var modeGateResolved by remember { mutableStateOf(false) }
    var liveModeObserved by remember { mutableStateOf(false) }
    val themeMode by viewModel.themeMode.collectAsState()
    val fontMode by viewModel.fontMode.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val textSpacing by viewModel.textSpacing.collectAsState()
    val readAloudTaps by viewModel.readAloudTaps.collectAsState(initial = false)
    val reduceMotion by viewModel.reduceMotion.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val customThemeSpec by viewModel.customThemeSpec.collectAsState()
    val context = LocalContext.current

    // the StateFlow starts with a null seed while Room and DataStore warm up. resolve the real
    // value before rendering any writable control, so a restored route can't briefly bypass the
    // mode lock during cold start
    LaunchedEffect(Unit) {
        entryMode = viewModel.activeModeNow()
        modeGateResolved = true
    }
    LaunchedEffect(modeGateResolved, activeMode?.id, entryMode?.id) {
        if (modeGateResolved && (entryMode == null || activeMode != null)) {
            liveModeObserved = true
        }
    }

    if (!modeGateResolved) {
        SettingsSubScaffold(title = "Customization", onBack = onBack) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
        return
    }

    val lockedMode = if (liveModeObserved) activeMode else activeMode ?: entryMode

    // defence in depth for restored or deep navigation. the Settings row normally blocks the
    // route with a notice, but the destination also has to be unable to mutate base settings
    lockedMode?.let { mode ->
        SettingsSubScaffold(title = "Customization", onBack = onBack) {
            val accent = modeAccentColor(mode.accent)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, accent.copy(alpha = .28f)),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(accent.copy(alpha = .16f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(modeIcon(mode.icon), null, tint = accent, modifier = Modifier.size(30.dp))
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Lock, null, tint = accent, modifier = Modifier.size(14.dp))
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "${mode.name} mode is active",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Customization isn't available in this mode. Turn it off or edit " +
                            "the mode first so its look and layout stay predictable.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(22.dp))
                    Button(
                        onClick = onBack,
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                    ) {
                        Text("Back to settings", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        return
    }

    SettingsSubScaffold(title = "Customization", onBack = onBack) {
        SettingsDetailHero(
            icon = Icons.Rounded.Palette,
            title = "Make Myndora yours",
            description = "Shape the colors, type and shortcuts around how you think.",
            accent = MaterialTheme.colorScheme.primary,
        )
        AppearanceRow(
            themeMode = themeMode,
            fontMode = fontMode,
            fontSize = fontSize,
            textSpacing = textSpacing,
            expanded = false,
            embedded = true,
            onToggle = {},
            onThemeChange = { viewModel.setThemeMode(it) },
            onFontChange = { viewModel.setFontMode(it) },
            onFontSizeChange = { viewModel.setFontSize(it) },
            onTextSpacingChange = { viewModel.setTextSpacing(it) },
        )

        // right under light/dark: both answer 'how should this look', and keeping them adjacent means
        // the two get compared together rather than found separately
        ThemePicker(
            selected = appTheme,
            customSpec = customThemeSpec,
            themeMode = themeMode,
            onSelectBuiltIn = { viewModel.setAppTheme(it) },
            onCustomChange = { viewModel.setCustomTheme(it) },
        )

        // here rather than under Myndora AI: it has nothing to do with the assistant's voice, it
        // changes how the whole app behaves. same shelf as theme and typeface
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Speak what I tap",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (readAloudTaps)
                                "Buttons, tabs and cards say their name as you tap them"
                            else
                                "Tapping around Myndora stays silent",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = readAloudTaps,
                        onCheckedChange = viewModel::setReadAloudTaps,
                    )
                }
                // better to state the limit than let someone discover it by tapping something that stays
                // quiet: this only covers Myndora, and Android already ships the everywhere version
                if (readAloudTaps) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "This covers Myndora's own screens. To have every app read " +
                            "aloud, turn on Select to Speak in Android's accessibility settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    TextButton(
                        onClick = {
                            runCatching {
                                context.startActivity(
                                    android.content.Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    ) {
                        Text("Open accessibility settings")
                    }
                }
            }
        }

        // last on the page because it's the one setting here that isn't about taste. it's the one
        // you reach for on a bad day, so it should be findable by scrolling to the end
        MotionSetting(
            reduceMotion = reduceMotion,
            onChange = viewModel::setReduceMotion,
        )
    }
}

// one switch to turn the app's movement down. phrased as what it gives you rather than what
// it removes: you look for this when the screen is already too busy, which is not the moment
// to be parsing what 'Animations: off' implies. it doesn't claim to remove all movement, a
// page still has to change when you tap Next. what goes is the movement you didn't ask for,
// and the explanation only appears once it's on
@Composable
private fun MotionSetting(
    reduceMotion: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Animation,
                    contentDescription = null,
                    tint = if (reduceMotion) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Reduce motion",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (reduceMotion)
                            "Things appear instead of moving"
                        else
                            "Things slide, grow and settle as they change",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = reduceMotion, onCheckedChange = onChange)
            }

            if (reduceMotion) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "Anything that drifts, pulses or breathes on its own stops. " +
                        "Screens still change when you tap — they just change straight away.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun NotificationsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val todoReminderKinds by viewModel.todoReminderKinds.collectAsState()
    val calendarReminderKinds by viewModel.calendarReminderKinds.collectAsState()
    val dailySummaryEnabled by viewModel.dailySummaryEnabled.collectAsState()
    val pomodoroNudgeFrequency by viewModel.pomodoroNudgeFrequency.collectAsState()
    val pomodoroNudgeTime by viewModel.pomodoroNudgeTime.collectAsState()
    val pomodoroBreakReminders by viewModel.pomodoroBreakReminders.collectAsState()

    SettingsSubScaffold(title = "Notifications", onBack = onBack) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = Color.Transparent,
            tonalElevation = 2.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.secondary.copy(alpha = .24f),
                                MaterialTheme.colorScheme.surfaceContainer,
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f),
                            ),
                        ),
                        RoundedCornerShape(26.dp),
                    )
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = .72f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.NotificationsActive,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(29.dp),
                    )
                }
                Spacer(Modifier.width(15.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Stay informed, not overwhelmed",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Choose only the moments when a gentle reminder genuinely helps.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        NotificationsSection(
            expanded = false,
            embedded = true,
            onToggleExpanded = {},
            todoKinds = todoReminderKinds,
            calendarKinds = calendarReminderKinds,
            dailySummaryEnabled = dailySummaryEnabled,
            nudgeFrequency = pomodoroNudgeFrequency,
            nudgeTime = pomodoroNudgeTime,
            breakReminders = pomodoroBreakReminders,
            onToggleTodoKind = { kind, enabled ->
                viewModel.toggleReminderKind(ReminderCategory.TODO, kind, enabled)
            },
            onToggleCalendarKind = { kind, enabled ->
                viewModel.toggleReminderKind(ReminderCategory.CALENDAR, kind, enabled)
            },
            onDailySummaryChange = { viewModel.setDailySummaryEnabled(it) },
            onNudgeFrequencyChange = { viewModel.setPomodoroNudgeFrequency(it) },
            onNudgeTimeChange = { viewModel.setPomodoroNudgeTime(it) },
            onBreakRemindersChange = { viewModel.setPomodoroBreakReminders(it) },
        )
    }
}

@Composable
fun AiSettingsScreen(
    onBack: () -> Unit,
    focusWellness: Boolean = false,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val spokenResponses by viewModel.aiSpokenResponses.collectAsState(initial = true)
    val chatReadAloud by viewModel.aiChatReadAloud.collectAsState(initial = false)
    val selectedVoice by viewModel.aiVoiceName.collectAsState(initial = null)
    val healthPersonalization by viewModel.aiHealthPersonalization.collectAsState(initial = false)
    val healthState by viewModel.healthConnectState.collectAsState()
    var showHealthConsent by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var voices by remember { mutableStateOf<List<android.speech.tts.Voice>>(emptyList()) }
    var voicesReady by remember { mutableStateOf(false) }
    val voiceEnabled = spokenResponses || chatReadAloud
    val wellnessRequester = remember { BringIntoViewRequester() }
    var wellnessHighlighted by remember(focusWellness) { mutableStateOf(focusWellness) }

    LaunchedEffect(focusWellness) {
        if (focusWellness) {
            delay(320)
            wellnessRequester.bringIntoView()
            delay(1_800)
            wellnessHighlighted = false
        }
    }

    LaunchedEffect(Unit) { viewModel.refreshHealthConnect() }

    DisposableEffect(context) {
        var localEngine: TextToSpeech? = null
        var disposed = false
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        val initializeVoices = Runnable {
            if (!disposed) localEngine = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS && !disposed) {
                    engine = localEngine
                    voices = curatedAiVoices(localEngine?.voices)
                }
                if (!disposed) voicesReady = true
            }
        }
        // render the settings immediately, then discover system voices
        handler.post(initializeVoices)
        onDispose {
            disposed = true
            handler.removeCallbacks(initializeVoices)
            localEngine?.stop()
            localEngine?.shutdown()
            engine = null
        }
    }

    LaunchedEffect(voices, selectedVoice) {
        if (voices.isNotEmpty() && voices.none { it.name == selectedVoice }) {
            viewModel.setAiVoiceName(voices.first().name)
        }
    }

    SettingsSubScaffold(title = "Myndora AI", onBack = onBack) {
        SettingsDetailHero(
            icon = Icons.Rounded.AutoAwesome,
            title = "Your assistant, your voice",
            description = "Choose when Myndora speaks and how its answers sound.",
            accent = MaterialTheme.colorScheme.primary,
        )
        AiSettingsSectionLabel(
            title = "Voice & sound",
            subtitle = "Choose when Myndora speaks and how it sounds.",
        )
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Spoken responses",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (spokenResponses) "Live assistant answers with text and voice"
                        else "Live assistant answers silently with text only",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = spokenResponses,
                    onCheckedChange = viewModel::setAiSpokenResponses,
                )
            }
        }

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Smart Search read aloud",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (chatReadAloud) "New Myndora chat answers play automatically"
                        else "Chat stays silent unless you tap its speaker button",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = chatReadAloud,
                    onCheckedChange = viewModel::setAiChatReadAloud,
                )
            }
        }

        Text(
            "Choose a voice",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = if (voiceEnabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = .45f),
        )

        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                MyndoraVoiceProfiles.forEachIndexed { index, profile ->
                    val voice = voices.getOrNull(index)
                    VoiceChoiceRow(
                        title = profile.name,
                        subtitle = profile.description,
                        selected = voice != null && selectedVoice == voice.name,
                        enabled = voiceEnabled && voice != null,
                        onClick = {
                            if (voice == null) return@VoiceChoiceRow
                            viewModel.setAiVoiceName(voice.name)
                            engine?.voice = voice
                            engine?.setPitch(profile.pitch)
                            engine?.setSpeechRate(profile.rate)
                            engine?.speak(
                                "Hi, I'm Myndora. How can I help?",
                                TextToSpeech.QUEUE_FLUSH,
                                null,
                                "voice_preview",
                            )
                        },
                    )
                }
                if (!voicesReady) {
                    Row(
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            "Preparing voice previews…",
                            modifier = Modifier.padding(start = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else if (voices.isEmpty()) {
                    Text(
                        "Your device’s system voice will be used.",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        AiSettingsSectionLabel(
            title = "Personalization & privacy",
            subtitle = "You decide whether wellness data can shape AI suggestions.",
        )

        Surface(
            modifier = Modifier.bringIntoViewRequester(wellnessRequester),
            shape = RoundedCornerShape(24.dp),
            color = if (wellnessHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)
                else MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(
                if (wellnessHighlighted) 2.dp else 0.dp,
                if (wellnessHighlighted) MaterialTheme.colorScheme.primary else Color.Transparent,
            ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(
                                com.muradgalayev.brainbuddy.R.drawable.health,
                            ),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(27.dp),
                        )
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Wellness personalization", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        when {
                            // disconnecting Health Connect doesn't withdraw consent, so say so rather than reading as
                            // off: the decision is an account-level record now and would otherwise follow the user to a
                            // new device after they believed they had revoked it
                            !healthState.connected && healthPersonalization ->
                                "Allowed • paused until Health Connect is reconnected"
                            !healthState.connected -> "Connect Health Connect to make this available"
                            healthPersonalization -> "On • recent wellness summaries can personalize AI"
                            else -> "Off • health data is not included in AI requests"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    // shows the decision, enabled shows whether it's in effect. folding connectedness into
                    // checked made the switch flip itself off on disconnect, which looks exactly like a revoke
                    // that never happened
                    checked = healthPersonalization,
                    enabled = healthState.connected,
                    onCheckedChange = { enabled ->
                        if (enabled) showHealthConsent = true
                        else viewModel.setAiHealthPersonalization(false)
                    },
                )
            }
        }

        AiSettingsSectionLabel(
            title = "Feature preview",
            subtitle = "A quick visual guide to Smart Search read aloud.",
        )

        SmartSearchFeaturePreview()

        Text(
            "These settings are stored only on this device. Your onboarding survey can update the spoken-response preference later.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showHealthConsent) {
        AlertDialog(
            onDismissRequest = { showHealthConsent = false },
            title = { Text("Use wellness data with AI?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Myndora will include recent summaries of your selected Health Connect data in AI requests to personalize focus, timing, workload, and break suggestions. Raw records are not sent. This is wellness guidance, not medical advice. You can turn it off anytime.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setAiHealthPersonalization(true)
                    showHealthConsent = false
                }) { Text("Allow personalization") }
            },
            dismissButton = {
                TextButton(onClick = { showHealthConsent = false }) { Text("Not now") }
            },
        )
    }
}

@Composable
private fun AiSettingsSectionLabel(title: String, subtitle: String) =
    SettingsSectionLabel(title = title, subtitle = subtitle)

// tuning values for the still-image focus animation
private const val SmartSearchPreviewFocusX = .667f
private const val SmartSearchPreviewFocusY = .415f
private const val SmartSearchPreviewFinalScale = 2.45f
private const val SmartSearchPreviewDelayMs = 650L
private const val SmartSearchPreviewDurationMs = 800
private const val SmartSearchPreviewHoldMs = 2_200L
private const val SmartSearchPreviewResetMs = 650
private const val SmartSearchPreviewLoopPauseMs = 500L

@Composable
private fun SmartSearchFeaturePreview() {
    val context = LocalContext.current
    val motionEnabled = remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
    val progress = remember { Animatable(0f) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    // recomposition keeps this Animatable, deliberately reopening the preview creates a fresh
    // one-shot animation
    LaunchedEffect(Unit) {
        if (motionEnabled) {
            while (true) {
                delay(SmartSearchPreviewDelayMs)
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = SmartSearchPreviewDurationMs,
                        easing = FastOutSlowInEasing,
                    ),
                )
                delay(SmartSearchPreviewHoldMs)
                progress.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(
                        durationMillis = SmartSearchPreviewResetMs,
                        easing = FastOutSlowInEasing,
                    ),
                )
                delay(SmartSearchPreviewLoopPauseMs)
            }
        } else {
            progress.snapTo(1f)
        }
    }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(
                            com.muradgalayev.brainbuddy.R.drawable.ic_ai,
                        ),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(23.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "See it in action",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Watch how to find and use read aloud",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.067f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Image(
                    painter = androidx.compose.ui.res.painterResource(
                        com.muradgalayev.brainbuddy.R.drawable.voice_assistant_reference,
                    ),
                    contentDescription = "Preview showing the read-answer-aloud button",
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .onSizeChanged { imageSize = it }
                        .graphicsLayer {
                            val animatedScale = 1f +
                                (SmartSearchPreviewFinalScale - 1f) * progress.value
                            scaleX = animatedScale
                            scaleY = animatedScale
                            transformOrigin = TransformOrigin(
                                SmartSearchPreviewFocusX,
                                SmartSearchPreviewFocusY,
                            )
                            translationX = imageSize.width *
                                (.5f - SmartSearchPreviewFocusX) * progress.value
                            translationY = imageSize.height *
                                (.5f - SmartSearchPreviewFocusY) * progress.value
                        },
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = (-74).dp)
                        .graphicsLayer {
                            alpha = ((progress.value - .62f) / .38f).coerceIn(0f, 1f)
                            scaleX = .88f + alpha * .12f
                            scaleY = .88f + alpha * .12f
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                        shadowElevation = 4.dp,
                    ) {
                        Text(
                            "READ ALOUD",
                            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ArrowDownward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else .42f),
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else .42f),
            )
        }
    }
}

@Composable
fun LinkedAccountsScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val exportingToCalendar by viewModel.exportingToCalendar.collectAsState()
    val linkedGoogleEmail by viewModel.linkedGoogleEmail.collectAsState()
    val syncFrequency by viewModel.calendarSyncFrequency.collectAsState()
    val calendarExportMessage by viewModel.calendarExportMessage.collectAsState()
    val calendarAuthorizationRequest by viewModel.calendarAuthorizationRequest.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val authLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result -> viewModel.onCalendarAuthorizationResult(result.data) }
    LaunchedEffect(calendarAuthorizationRequest) {
        val pi = calendarAuthorizationRequest ?: return@LaunchedEffect
        viewModel.consumeCalendarAuthorizationRequest()
        authLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
    }
    LaunchedEffect(calendarExportMessage) {
        val msg = calendarExportMessage
        if (msg != null) {
            snackbar.showSnackbar(msg)
            viewModel.clearCalendarExportMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsSubScaffold(title = "Linked accounts", onBack = onBack) {
            ConnectedServicesHero(
                connected = linkedGoogleEmail != null,
            )
            GoogleCalendarExportRow(
                isExporting = exportingToCalendar,
                linkedEmail = linkedGoogleEmail,
                avatarUrl = profile.avatarUrl,
                onExport = { viewModel.exportToGoogleCalendar() },
                onDisconnect = { viewModel.disconnectGoogleCalendar() },
            )

            if (linkedGoogleEmail != null) {
                val lastSyncedAt by viewModel.calendarLastSyncedAt.collectAsState()
                val lastSyncResult by viewModel.calendarLastSyncResult.collectAsState()
                SyncFrequencyCard(
                    selected = syncFrequency,
                    onSelect = { viewModel.setCalendarSyncFrequency(it) },
                    lastSyncedAt = lastSyncedAt,
                    lastSyncResult = lastSyncResult,
                )
            }
        }
        com.muradgalayev.brainbuddy.ui.sharedcomponents.MyndoraSnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
        )
    }
}

@Composable
fun LinkedDevicesScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.healthConnectState.collectAsState()
    val healthPersonalization by viewModel.aiHealthPersonalization.collectAsState(initial = false)
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showAiWellnessInvite by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        viewModel.refreshHealthConnect()
        scope.launch { snackbar.showSnackbar("Health access updated") }
        if (granted.isNotEmpty() && !healthPersonalization) {
            showAiWellnessInvite = true
        }
    }

    // ON_RESUME alone. LifecycleRegistry replays events to a newly added observer, so this
    // already fires once on entry, and the extra LaunchedEffect(Unit) that used to sit here just
    // ran the whole permission check and metric read a second time
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.refreshHealthConnect()
    }

    val connect: () -> Unit = {
        when (state.availability) {
            HealthConnectAvailability.AVAILABLE -> {
                viewModel.prepareHealthConnect()
                permissionLauncher.launch(viewModel.healthConnectPermissions)
            }
            HealthConnectAvailability.INSTALL_OR_UPDATE -> installHealthConnect(context)
            HealthConnectAvailability.NOT_SUPPORTED -> {
                scope.launch {
                    snackbar.showSnackbar("Health Connect is not supported on this device")
                }
            }
        }
    }

    SettingsSubScaffold(title = "Linked devices", onBack = onBack) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = Color.Transparent,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .72f),
                            ),
                        ),
                    )
                    .padding(20.dp),
            ) {
                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ServiceLogoTile(com.muradgalayev.brainbuddy.R.drawable.ic_ai, MaterialTheme.colorScheme.primary)
                    Box(
                        modifier = Modifier.width(88.dp).height(44.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.fillMaxWidth().height(2.dp).background(
                                if (state.connected) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape,
                            ),
                        )
                        Surface(
                            shape = CircleShape,
                            color = if (state.connected) MaterialTheme.colorScheme.tertiaryContainer
                            else MaterialTheme.colorScheme.surface,
                        ) {
                            Icon(
                                Icons.Rounded.Sync,
                                contentDescription = null,
                                tint = if (state.connected) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp).size(18.dp),
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.size(58.dp),
                        shape = RoundedCornerShape(19.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .82f),
                        tonalElevation = 2.dp,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(
                                    com.muradgalayev.brainbuddy.R.drawable.health,
                                ),
                                contentDescription = "Health Connect",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(31.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(15.dp))
                Text(
                    if (state.connected) "Health data connected" else "Connect Health Connect",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (state.connected) "Your wellness insights are ready for Myndora."
                    else "Share selected wellness data privately.",
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        HealthConnectCard(
            state = state,
            permissionLabels = viewModel.healthPermissionLabels,
            onConnect = connect,
            onDisconnect = viewModel::disconnectHealthConnect,
            onInstall = { installHealthConnect(context) },
        )

        if (state.connected) {
            HealthAiPersonalizationCard(
                enabled = healthPersonalization,
                onChange = { enabled ->
                    if (enabled) showAiWellnessInvite = true
                    else viewModel.setAiHealthPersonalization(false)
                },
            )
        }
    }

    com.muradgalayev.brainbuddy.ui.sharedcomponents.MyndoraSnackbarHost(
        hostState = snackbar,
        modifier = Modifier.padding(16.dp),
    )

    if (showAiWellnessInvite) {
        AlertDialog(
            onDismissRequest = { showAiWellnessInvite = false },
            icon = {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(
                        com.muradgalayev.brainbuddy.R.drawable.ic_ai,
                    ),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
            },
            title = { Text("Let Myndora personalize gently?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "Myndora can use compact wellness summaries to adjust focus, workload, timing, and break suggestions. Raw Health Connect records are not sent, and this is never used for diagnosis. You can change this anytime in Myndora AI settings.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.setAiHealthPersonalization(true)
                    showAiWellnessInvite = false
                }) { Text("Allow personalization") }
            },
            dismissButton = {
                TextButton(onClick = { showAiWellnessInvite = false }) { Text("Not now") }
            },
        )
    }
}

private fun installHealthConnect(context: android.content.Context) {
    val market = Intent(
        Intent.ACTION_VIEW,
        Uri.parse("market://details?id=com.google.android.apps.healthdata"),
    )
    runCatching { context.startActivity(market) }.onFailure {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata"),
            ),
        )
    }
}

@Composable
private fun HealthConnectCard(
    state: HealthConnectUiState,
    permissionLabels: List<com.muradgalayev.brainbuddy.data.health.HealthPermissionInfo>,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onInstall: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = Color.Transparent,
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.surface,
                            colors.primaryContainer.copy(alpha = .34f),
                            colors.tertiaryContainer.copy(alpha = .28f),
                        ),
                    ),
                )
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    colors.tertiary.copy(alpha = .28f),
                                    colors.secondary.copy(alpha = .22f),
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Favorite,
                        contentDescription = null,
                        tint = colors.tertiary,
                        modifier = Modifier.size(25.dp),
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Health Connect",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when {
                            state.connected -> "Read-only wellness insights are connected"
                            state.availability == HealthConnectAvailability.INSTALL_OR_UPDATE -> "Install or update to connect"
                            state.availability == HealthConnectAvailability.NOT_SUPPORTED -> "Not supported on this device"
                            else -> "Optional steps and heart-rate context"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                if (state.connected) {
                    Surface(shape = CircleShape, color = colors.tertiary.copy(alpha = .16f)) {
                        Text(
                            "Live",
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = colors.tertiary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            if (state.connected) {
                HealthPermissionBreakdown(
                    permissionLabels = permissionLabels,
                    granted = state.grantedPermissions,
                )
                if (!state.allPermissionsGranted) {
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                        Text("Allow additional health insights")
                    }
                }
                if (state.error != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.error,
                    )
                }
                TextButton(
                    onClick = onDisconnect,
                    enabled = !state.disconnecting,
                    modifier = Modifier.align(Alignment.End),
                ) {
                    if (state.disconnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(7.dp))
                    }
                    Text(if (state.disconnecting) "Disconnecting…" else "Disconnect")
                }
            } else if (state.availability == HealthConnectAvailability.AVAILABLE) {
                Text(
                    "You choose each permission. Myndora requests read access only and does not upload these records.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(7.dp))
                    Text("Connect Health Connect")
                }
            } else if (state.availability == HealthConnectAvailability.INSTALL_OR_UPDATE) {
                Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
                    Text("Install or update Health Connect")
                }
            }
        }
    }
}

// per-type grant status. Health Connect grants each data type separately, and after a couple
// of dismissals it stops showing the consent sheet at all, so steps allowed with heart rate
// silently denied is a real state. one connected flag can't express that. types a phone
// can't record on its own are marked, since a granted permission with no wearable behind it
// produces no data, which otherwise looks like a bug
@Composable
private fun HealthPermissionBreakdown(
    permissionLabels: List<com.muradgalayev.brainbuddy.data.health.HealthPermissionInfo>,
    granted: Set<String>,
) {
    val colors = MaterialTheme.colorScheme
    val grantedCount = permissionLabels.count { it.permission in granted }
    // collapsed by default, six rows is a lot of card for something you read once. the 'n of m'
    // summary stays visible so a partial grant is still obvious without opening it, and the
    // allow-additional button sits outside this section so acting on it never needs an expand
    var expanded by rememberSaveable { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(200),
        label = "healthPermissionsChevron",
    )
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "What you've allowed",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = if (grantedCount == permissionLabels.size) {
                    colors.tertiary.copy(alpha = .16f)
                } else {
                    colors.secondaryContainer.copy(alpha = .55f)
                },
            ) {
                Text(
                    "$grantedCount of ${permissionLabels.size}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (grantedCount == permissionLabels.size) colors.tertiary
                    else colors.onSecondaryContainer,
                )
            }
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) "Hide the data types"
                else "Show the data types",
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(chevronRotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(tween(150)) + expandVertically(tween(200)),
            exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
        ) {
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                permissionLabels.forEach { info ->
                    val isGranted = info.permission in granted
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (isGranted) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel,
                            contentDescription = null,
                            tint = if (isGranted) colors.tertiary else colors.onSurfaceVariant.copy(alpha = .45f),
                            modifier = Modifier.size(15.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            info.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isGranted) colors.onSurface else colors.onSurfaceVariant,
                        )
                        if (isGranted && info.needsDevice) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "· needs a watch",
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant.copy(alpha = .7f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthStepsFeature(today: Long?, last7Days: Long?) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = colors.primary,
        shadowElevation = 5.dp,
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(54.dp),
                shape = RoundedCornerShape(18.dp),
                color = colors.onPrimary.copy(alpha = .14f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.DirectionsWalk, null, tint = colors.onPrimary, modifier = Modifier.size(29.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 15.dp)) {
                Text("TODAY'S STEPS", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = colors.onPrimary.copy(alpha = .72f))
                Text(
                    today?.let { "%,d".format(it) } ?: "—",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = colors.onPrimary,
                )
            }
            if (last7Days != null) {
                Surface(shape = RoundedCornerShape(14.dp), color = colors.onPrimary.copy(alpha = .12f)) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), horizontalAlignment = Alignment.End) {
                        Text("7 DAYS", style = MaterialTheme.typography.labelSmall, color = colors.onPrimary.copy(alpha = .7f))
                        Text("%,d".format(last7Days), fontWeight = FontWeight.Bold, color = colors.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
private fun HealthMetric(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Surface(
        modifier = modifier.height(142.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, accent.copy(alpha = .14f)),
    ) {
        Column(
            Modifier
                .background(
                    Brush.linearGradient(
                        listOf(
                            accent.copy(alpha = .18f),
                            accent.copy(alpha = .04f),
                        ),
                    ),
                )
                .padding(13.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = .16f),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(7.dp).size(17.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            if (detail != null) Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = accent,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HealthAiPersonalizationCard(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = if (enabled) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .42f)
        else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier.padding(17.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = .8f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        painter = androidx.compose.ui.res.painterResource(
                            com.muradgalayev.brainbuddy.R.drawable.ic_ai,
                        ),
                        contentDescription = null,
                        tint = if (enabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(25.dp),
                    )
                }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text("Personalize Myndora AI", fontWeight = FontWeight.Bold)
                Text(
                    if (enabled) "On • wellness summaries can shape suggestions"
                    else "Optional • use summaries for focus and break suggestions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = enabled, onCheckedChange = onChange)
        }
    }
}

@Composable
private fun ConnectedServicesHero(connected: Boolean) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = Color.Transparent,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.secondary.copy(alpha = .20f),
                            colors.surfaceContainer,
                            colors.tertiary.copy(alpha = .18f),
                        ),
                    ),
                    RoundedCornerShape(28.dp),
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceLogoTile(
                    painter = com.muradgalayev.brainbuddy.R.drawable.ic_ai,
                    tint = colors.primary,
                )
                Box(
                    modifier = Modifier.width(88.dp).height(44.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(2.dp)
                            .background(
                                if (connected) colors.tertiary else colors.outlineVariant,
                                CircleShape,
                            ),
                    )
                    Surface(
                        shape = CircleShape,
                        color = if (connected) colors.tertiaryContainer else colors.surface,
                    ) {
                        Icon(
                            Icons.Rounded.Sync,
                            contentDescription = null,
                            tint = if (connected) colors.tertiary else colors.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp).size(18.dp),
                        )
                    }
                }
                ServiceLogoTile(
                    painter = com.muradgalayev.brainbuddy.R.drawable.ic_google_calendar,
                    tint = Color.Unspecified,
                )
            }
            Spacer(Modifier.height(15.dp))
            Text(
                if (connected) "Calendars connected" else "Bring your plans together",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (connected) "Myndora and Google Calendar are ready to sync."
                else "Link Google Calendar to carry Myndora plans wherever you go.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ServiceLogoTile(painter: Int, tint: Color) {
    Surface(
        modifier = Modifier.size(58.dp),
        shape = RoundedCornerShape(19.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .82f),
        tonalElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                painter = androidx.compose.ui.res.painterResource(painter),
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(31.dp),
            )
        }
    }
}

@Composable
private fun SyncFrequencyCard(
    selected: CalendarSyncFrequency,
    onSelect: (CalendarSyncFrequency) -> Unit,
    lastSyncedAt: Long?,
    lastSyncResult: String?,
) {
    val colors = MaterialTheme.colorScheme
    var expanded by remember { mutableStateOf(false) }
    var visualSelection by remember(selected) { mutableStateOf(selected) }
    val scope = rememberCoroutineScope()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(30.dp),
        color = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(30.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            colors.primaryContainer.copy(alpha = .88f),
                            colors.surfaceContainer,
                            colors.tertiaryContainer.copy(alpha = .58f),
                        ),
                    ),
                )
                .padding(18.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).clickable { expanded = !expanded }.padding(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(58.dp)) {
                    Surface(
                        modifier = Modifier.size(52.dp).align(Alignment.TopStart),
                        shape = RoundedCornerShape(17.dp),
                        color = colors.surface.copy(alpha = .9f),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                painter = androidx.compose.ui.res.painterResource(com.muradgalayev.brainbuddy.R.drawable.ic_google_calendar),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                    }
                    Surface(
                        modifier = Modifier.size(25.dp).align(Alignment.BottomEnd),
                        shape = CircleShape,
                        color = colors.primary,
                        border = BorderStroke(2.dp, colors.surface),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Sync, null, tint = colors.onPrimary, modifier = Modifier.size(14.dp))
                        }
                    }
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("Your sync rhythm", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    Text(
                        syncFrequencyDescription(selected),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
                // 'Every 6 hours' is wide enough to reach the title without this, and the text column gives
                // up width before the badge does
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Surface(shape = RoundedCornerShape(12.dp), color = colors.primary) {
                        Text(
                            selected.label,
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.onPrimary,
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Icon(
                        Icons.Rounded.KeyboardArrowDown,
                        contentDescription = if (expanded) "Collapse sync options" else "Expand sync options",
                        modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
                        tint = colors.onSurfaceVariant,
                    )
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(Modifier.padding(top = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "How closely should your calendars move together?",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceVariant,
                    )
                    CalendarSyncFrequency.entries.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { frequency ->
                                SyncRhythmTile(
                                    frequency = frequency,
                                    selected = frequency == visualSelection,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        visualSelection = frequency
                                        onSelect(frequency)
                                        scope.launch {
                                            delay(480)
                                            expanded = false
                                        }
                                    },
                                )
                            }
                            if (pair.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }

                    // proof the background job is actually running. Android defers periodic work during Doze and
                    // some OEMs kill it outright, so 'did it sync overnight?' is otherwise unanswerable from the UI
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = buildString {
                            append("Last sync: ")
                            append(
                                if (lastSyncedAt == null) "not yet — background sync hasn't run"
                                else formatRelativeSync(lastSyncedAt)
                            )
                            lastSyncResult?.let { append(" · $it") }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// '12 minutes ago' / '6 hours ago' / '3 days ago', enough to judge the cadence
private fun formatRelativeSync(atMillis: Long): String {
    val deltaMs = (System.currentTimeMillis() - atMillis).coerceAtLeast(0L)
    val minutes = deltaMs / 60_000L
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes minute${if (minutes == 1L) "" else "s"} ago"
        minutes < 60 * 24 -> {
            val hours = minutes / 60
            "$hours hour${if (hours == 1L) "" else "s"} ago"
        }
        else -> {
            val days = minutes / (60 * 24)
            "$days day${if (days == 1L) "" else "s"} ago"
        }
    }
}

@Composable
private fun SyncRhythmTile(
    frequency: CalendarSyncFrequency,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val tileColor by animateColorAsState(
        if (selected) colors.primary else colors.surface.copy(alpha = .94f),
        animationSpec = tween(260),
        label = "sync_tile_color",
    )
    val borderColor by animateColorAsState(
        if (selected) colors.primary else colors.outlineVariant.copy(alpha = .32f),
        animationSpec = tween(260),
        label = "sync_tile_border",
    )
    val elevation by animateDpAsState(if (selected) 6.dp else 0.dp, tween(260), label = "sync_tile_elevation")
    val scale by animateFloatAsState(if (selected) 1.025f else 1f, tween(220), label = "sync_tile_scale")
    Surface(
        onClick = onClick,
        modifier = modifier.height(112.dp).graphicsLayer { scaleX = scale; scaleY = scale },
        shape = RoundedCornerShape(20.dp),
        color = tileColor,
        border = BorderStroke(
            1.dp,
            borderColor,
        ),
        shadowElevation = elevation,
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    syncFrequencyMark(frequency),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black,
                    color = if (selected) colors.onPrimary else colors.primary,
                )
                if (selected) Icon(Icons.Rounded.Check, "Selected", tint = colors.onPrimary, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(frequency.label, fontWeight = FontWeight.Bold, color = if (selected) colors.onPrimary else colors.onSurface)
                Text(
                    syncFrequencyDescription(frequency),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    color = if (selected) colors.onPrimary.copy(alpha = .78f) else colors.onSurfaceVariant,
                )
            }
        }
    }
}

private fun syncFrequencyMark(frequency: CalendarSyncFrequency): String = when (frequency) {
    CalendarSyncFrequency.MANUAL -> "TAP"
    CalendarSyncFrequency.WEEKLY -> "7D"
    CalendarSyncFrequency.DAILY -> "1D"
    CalendarSyncFrequency.TWICE_DAILY -> "2×"
    CalendarSyncFrequency.EVERY_6H -> "6H"
}

private fun syncFrequencyDescription(frequency: CalendarSyncFrequency): String = when (frequency) {
    CalendarSyncFrequency.MANUAL -> "Only when you choose Sync now"
    CalendarSyncFrequency.WEEKLY -> "A quiet refresh once each week"
    CalendarSyncFrequency.DAILY -> "Keep tomorrow’s plans up to date"
    CalendarSyncFrequency.TWICE_DAILY -> "Refresh around morning and evening"
    CalendarSyncFrequency.EVERY_6H -> "Keep changes closely aligned"
}

@Composable
fun EditProfileScreen(onBack: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val colors = MaterialTheme.colorScheme
    val profile by viewModel.profile.collectAsState()
    val saving by viewModel.profileSaving.collectAsState()
    val availability by viewModel.usernameAvailability.collectAsState()
    val saveMessage by viewModel.profileSaveMessage.collectAsState()
    val location by viewModel.profileLocation.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var name by remember { mutableStateOf(profile.name.orEmpty()) }
    var username by remember { mutableStateOf(profile.username.orEmpty()) }
    var phone by remember { mutableStateOf(profile.phone.orEmpty()) }

    val context = LocalContext.current
    val avatarUploading by viewModel.avatarUploading.collectAsState()

    // crop then upload: the picked image goes to the cropper, the cropped result gets uploaded
    val cropLauncher = rememberLauncherForActivityResult(CropImageContract()) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { uri ->
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
                if (bytes != null) viewModel.uploadAvatar(bytes, "jpg")
            }
        }
    }
    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            cropLauncher.launch(
                CropImageContractOptions(
                    uri = uri,
                    cropImageOptions = CropImageOptions(
                        cropShape = CropImageView.CropShape.OVAL,
                        aspectRatioX = 1,
                        aspectRatioY = 1,
                        fixAspectRatio = true,
                        outputCompressFormat = Bitmap.CompressFormat.JPEG,
                        outputCompressQuality = 90,
                    ),
                )
            )
        }
    }

    // their own username is trivially available, so the old check-on-open printed a green
    // 'Available' under a field they hadn't touched. the check still runs on every keystroke,
    // debounced, and Save depends on it, but the status only shows once the field differs
    val usernameChanged = !username.trim().equals(profile.username.orEmpty().trim(), ignoreCase = true)
    LaunchedEffect(username) { viewModel.onEditingUsername(username) }
    LaunchedEffect(saveMessage) {
        val msg = saveMessage ?: return@LaunchedEffect
        if (msg == "Profile updated") {
            viewModel.clearProfileSaveMessage()
            onBack()
        } else {
            snackbar.showSnackbar(msg)
            viewModel.clearProfileSaveMessage()
        }
    }

    val statusText: String? = when {
        // nothing to report about a username they haven't changed
        !usernameChanged -> null
        else -> when (availability) {
        UsernameAvailability.Idle -> null
        UsernameAvailability.Checking -> "Checking…"
        UsernameAvailability.Available -> "Available"
        UsernameAvailability.Taken -> "Already taken"
        UsernameAvailability.Invalid -> "Use 3–20 letters, numbers or underscores"
        }
    }
    val statusColor = when (availability) {
        UsernameAvailability.Available -> androidx.compose.ui.graphics.Color(0xFF6B8F5D)
        UsernameAvailability.Taken, UsernameAvailability.Invalid -> colors.error
        else -> colors.onSurfaceVariant
    }
    val canSave = !saving && name.isNotBlank() &&
        (availability == UsernameAvailability.Available || availability == UsernameAvailability.Idle)

    Box(modifier = Modifier.fillMaxSize()) {
        SettingsSubScaffold(title = "Edit profile", onBack = onBack) {
            SettingsDetailHero(
                icon = Icons.Rounded.ManageAccounts,
                title = "Your Myndora identity",
                description = "Keep your profile details current and recognizably yours.",
                accent = MaterialTheme.colorScheme.primary,
            )
            // avatar with change-photo button
            val initials = (profile.name?.takeIf { it.isNotBlank() } ?: profile.username ?: profile.email)
                ?.split(" ", ".", "_", "-")
                ?.mapNotNull { it.firstOrNull()?.uppercase() }
                ?.take(2)?.joinToString("")?.takeIf { it.isNotBlank() } ?: "?"
            val launchPicker = {
                photoPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.14f))
                            .clickable(enabled = !avatarUploading) { launchPicker() },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!profile.avatarUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(profile.avatarUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Profile picture",
                                modifier = Modifier.size(96.dp).clip(CircleShape),
                            )
                        } else {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.primary,
                            )
                        }
                        if (avatarUploading) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
                            }
                        }
                    }
                    // camera badge
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(colors.primary)
                            .clickable(enabled = !avatarUploading) { launchPicker() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.PhotoCamera,
                            contentDescription = "Change photo",
                            tint = colors.onPrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Change photo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                    modifier = Modifier.clickable(enabled = !avatarUploading) { launchPicker() },
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Name") },
                leadingIcon = { Icon(Icons.Rounded.Person, contentDescription = null) },
                singleLine = true,
                enabled = !saving,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.primary,
                    focusedLabelColor = colors.primary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Column {
                OutlinedTextField(
                    value = username,
                    onValueChange = {
                        username = it
                        viewModel.onEditingUsername(it)
                    },
                    label = { Text("Username") },
                    leadingIcon = { Icon(Icons.Rounded.AlternateEmail, contentDescription = null) },
                    singleLine = true,
                    enabled = !saving,
                    isError = availability == UsernameAvailability.Taken ||
                        availability == UsernameAvailability.Invalid,
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.primary,
                        focusedLabelColor = colors.primary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (statusText != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
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
                    focusedBorderColor = colors.primary,
                    focusedLabelColor = colors.primary,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = profile.email.orEmpty(),
                onValueChange = {},
                label = { Text("Email") },
                leadingIcon = { Icon(Icons.Rounded.Email, contentDescription = null) },
                readOnly = true,
                enabled = false,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                supportingText = { Text("Used to sign in — can't be changed here") },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = colors.outlineVariant,
                    disabledTextColor = colors.onSurfaceVariant,
                    disabledLeadingIconColor = colors.onSurfaceVariant,
                    disabledLabelColor = colors.onSurfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = location ?: "Not set",
                onValueChange = {},
                label = { Text("Location") },
                leadingIcon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
                readOnly = true,
                enabled = false,
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                supportingText = { Text("You can change this from ADHD Profile") },
                colors = OutlinedTextFieldDefaults.colors(
                    disabledBorderColor = colors.outlineVariant,
                    disabledTextColor = colors.onSurfaceVariant,
                    disabledLeadingIconColor = colors.onSurfaceVariant,
                    disabledLabelColor = colors.onSurfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { viewModel.updateProfile(name, username, phone) },
                enabled = canSave,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        color = colors.onPrimary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Text("Save changes", color = colors.onPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
        com.muradgalayev.brainbuddy.ui.sharedcomponents.MyndoraSnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.TopCenter).padding(16.dp),
        )
    }
}
