package com.muradgalayev.brainbuddy.ui.ai

import android.Manifest
import android.util.Log
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import androidx.core.content.ContextCompat
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.res.painterResource
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.domain.ai.ChatMessage
import com.muradgalayev.brainbuddy.domain.ai.ChatRole
import com.muradgalayev.brainbuddy.ui.components.VoiceWaveform
import com.muradgalayev.brainbuddy.ui.utils.SpeechRecognitionHelper
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AiPromptCard(
    onDismiss: () -> Unit,
    viewModel: AiAssistantViewModel = hiltViewModel(),
    showSuggestions: Boolean = true,
    showHistory: Boolean = true,
    onLiveVoiceClick: (() -> Unit)? = null,
    // losing the connection puts a switch inside this card rather than changing what the AI
    // button looks like from the outside, see OfflineSwitchBar
    onSwitchToOffline: (() -> Unit)? = null,
) {
    val isOnline by viewModel.isOnline.collectAsState()
    var promptText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var voiceLevel by remember { mutableStateOf(0f) }
    var partialText by remember { mutableStateOf("") }
    // why the mic didn't work, in words. every failure path here used to end at isListening =
    // false and nothing else: the bar opened, the orb sat still, no explanation appeared. that is
    // indistinguishable from a broken button, and it hid the commonest cause entirely, a device
    // with no recognition service at all
    var voiceError by remember { mutableStateOf<String?>(null) }
    var showVoiceExpanded by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var newChatTransitioning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val selectedVoiceName by viewModel.selectedVoiceName.collectAsState()
    val chatReadAloudEnabled by viewModel.chatReadAloudEnabled.collectAsState()
    val cardOpenedAt = remember { System.currentTimeMillis() }
    var autoSpokenMessageId by remember { mutableStateOf<String?>(null) }
    var speechEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    var speakingMessageId by remember { mutableStateOf<String?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        var disposed = false
        // let the assistant render first. some device TTS engines do expensive discovery work in
        // their constructor, which used to delay opening
        mainHandler.post {
            if (disposed) return@post
            engine = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS && !disposed) {
                    engine?.setLanguage(Locale.getDefault())
                    engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post { speakingMessageId = utteranceId }
                    }
                    override fun onDone(utteranceId: String?) {
                        mainHandler.post { if (speakingMessageId == utteranceId) speakingMessageId = null }
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post { speakingMessageId = null }
                    }
                    })
                    speechEngine = engine
                }
            }
        }
        onDispose {
            disposed = true
            engine?.stop()
            engine?.shutdown()
            speechEngine = null
        }
    }

    LaunchedEffect(selectedVoiceName, speechEngine) {
        val engine = speechEngine ?: return@LaunchedEffect
        val curated = curatedAiVoices(engine.voices)
        val profile = aiVoiceProfile(selectedVoiceName, curated)
        engine.setPitch(profile.pitch)
        engine.setSpeechRate(profile.rate)
        val preferredVoice = curated.firstOrNull { it.name == selectedVoiceName }
            ?: curated.firstOrNull()
        preferredVoice?.let { engine.voice = it }
    }

    // the centre-navbar assistant only reads answers created after this card opened, so existing
    // chat history never starts speaking unexpectedly
    LaunchedEffect(
        uiState.messages,
        uiState.isThinking,
        chatReadAloudEnabled,
        speechEngine,
    ) {
        val engine = speechEngine ?: return@LaunchedEffect
        if (!chatReadAloudEnabled) {
            if (speakingMessageId == autoSpokenMessageId) {
                engine.stop()
                speakingMessageId = null
            }
            return@LaunchedEffect
        }
        if (uiState.isThinking) return@LaunchedEffect
        val latestAnswer = uiState.messages.lastOrNull {
            it.role == ChatRole.ASSISTANT && it.text.isNotBlank() && it.createdAt >= cardOpenedAt
        } ?: return@LaunchedEffect
        if (latestAnswer.id == autoSpokenMessageId) return@LaunchedEffect
        autoSpokenMessageId = latestAnswer.id
        engine.speak(
            parseOptions(latestAnswer.text).first.toMyndoraSpeech(),
            TextToSpeech.QUEUE_FLUSH,
            null,
            latestAnswer.id,
        )
    }

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val aiGradient = Brush.horizontalGradient(
        listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    )
    val headerMotion = rememberInfiniteTransition(label = "aiHeader")
    val logoBreath by headerMotion.animateFloat(
        initialValue = 1f,
        targetValue = 1.055f,
        animationSpec = infiniteRepeatable(tween(1800), RepeatMode.Reverse),
        label = "aiLogoBreath",
    )
    val newChatMotion by animateFloatAsState(
        targetValue = if (newChatTransitioning) 0f else 1f,
        animationSpec = tween(if (newChatTransitioning) 170 else 280),
        label = "new_chat_transition",
    )

    // background speech recognizer
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
            onError = { code ->
                partialText = ""
                isListening = false
                showVoiceExpanded = false
                voiceError = speechErrorMessage(code)
                Log.w("AiPromptCard", "Speech recognition error $code")
            },
            onListeningStarted = {
                isListening = true
            },
            onListeningFinished = {
                voiceLevel = 0f
            },
            onRms = { rms ->
                // map mic loudness (roughly -2..10 dB) onto 0..1
                voiceLevel = ((rms + 2f) / 12f).coerceIn(0f, 1f)
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose { speechHelper.destroy() }
    }

    // permission launcher for RECORD_AUDIO
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            speechHelper.startListening()
        } else {
            // denied is an answer, not a dead end. leaving the bar open with no word looked like a crash
            showVoiceExpanded = false
            voiceError = "Myndora needs microphone access to hear you."
        }
    }

    // the card is anchored above the nav bar and grows upward as the conversation fills it.
    // unbounded, in landscape it grows past the top of the screen and the composer, last in the
    // column, is what gets cut off, so the assistant opens with nowhere to type. capping the
    // height bounds the column, which lets the message list flex and keeps the composer's space
    val configuration = LocalConfiguration.current
    val maxCardHeight = (configuration.screenHeightDp.dp - 132.dp).coerceAtLeast(200.dp)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            // full width is right on a phone held upright and absurd across a landscape tablet, where a
            // chat bubble would run the whole way across
            .widthIn(max = 620.dp)
            .heightIn(max = maxCardHeight)
            .padding(horizontal = 12.dp)
            .padding(bottom = 120.dp)
            .graphicsLayer {
                alpha = .42f + (.58f * newChatMotion)
                scaleX = .985f + (.015f * newChatMotion)
                scaleY = .985f + (.015f * newChatMotion)
                translationY = (1f - newChatMotion) * 10.dp.toPx()
            },
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 2.dp,
        shadowElevation = 7.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // gradient header with the sparkle icon
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
                    // Myndora identity mark, shared by Calendar and the global AI
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(logoBreath)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .8f))
                            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = .2f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_ai),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    Spacer(modifier = Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Myndora",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(Modifier.size(7.dp))
                            Box(
                                Modifier.clip(RoundedCornerShape(7.dp)).background(MaterialTheme.colorScheme.primary.copy(alpha = .12f)).padding(horizontal = 7.dp, vertical = 3.dp),
                            ) {
                                Text("AI", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                    if (showHistory) {
                        HeaderIconButton(
                            icon = Icons.Rounded.History,
                            contentDescription = "Chat history",
                            onClick = {
                                viewModel.refreshConversations()
                                showHistorySheet = true
                            },
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    if (onLiveVoiceClick != null) {
                        HeaderIconButton(
                            icon = Icons.Rounded.Mic,
                            contentDescription = "Start live voice conversation",
                            onClick = onLiveVoiceClick,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                    }
                    HeaderIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "New chat",
                        onClick = {
                            if (!newChatTransitioning) {
                                scope.launch {
                                    newChatTransitioning = true
                                    delay(180)
                                    promptText = ""
                                    partialText = ""
                                    showVoiceExpanded = false
                                    if (isListening) speechHelper.stopListening()
                                    viewModel.startNewChat()
                                    delay(45)
                                    newChatTransitioning = false
                                }
                            }
                        },
                    )
                    // no close button: tapping the scrim already dismisses it, a second way out crowds the header
                }
            }

            // the mic's own failures, said out loud rather than swallowed. sits above the composer where
            // the mic is, not in the chat log: it's a fact about the button, not something the AI said
            voiceError?.let { message ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Dismiss",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.clickable { voiceError = null },
                    )
                }
            }

            // chat history, only visible after the first message
            if (uiState.messages.isNotEmpty() || uiState.error != null) {
                ConversationView(
                    // takes what's left after the header and composer have theirs, rather than its own fixed
                    // slice: the composer must never be the thing that doesn't fit
                    modifier = Modifier.weight(1f, fill = false),
                    messages = uiState.messages,
                    error = uiState.error,
                    onChipTap = { viewModel.send(it) },
                    speakingMessageId = speakingMessageId,
                    onSpeak = { messageId, text ->
                        speechEngine?.let { engine ->
                            if (speakingMessageId == messageId) {
                                engine.stop()
                                speakingMessageId = null
                            } else {
                                engine.speak(
                                    text.toMyndoraSpeech(),
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    messageId,
                                )
                            }
                        }
                    },
                )
            }

            // losing the connection adds one control here. it doesn't grey the card out and it doesn't
            // change the AI button on the screen behind, both of those read as the assistant breaking
            // when in fact there's still plenty it can do without a network.
            // running out of the daily allowance lands here too: different cause, same situation
            AnimatedVisibility(
                visible = (!isOnline || uiState.quotaExhausted) && onSwitchToOffline != null,
                enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                exit = fadeOut(tween(140)) + shrinkVertically(tween(160)),
            ) {
                OfflineSwitchBar(
                    outOfMessages = isOnline && uiState.quotaExhausted,
                    onSwitch = { onSwitchToOffline?.invoke() },
                )
            }

            // input area
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {

                // text field with send button
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
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

                    // animated mic button
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

                    // pulsing ring animation
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

                    // glow border animation
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
                        // pulsing rings, only while listening and collapsed
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

                        // main button
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
                                    } else if (!speechHelper.isAvailable()) {
                                        // no recognition service on the device, common on emulators and on phones without the Google
                                        // app. nothing to start, so say so instead of pretending
                                        voiceError =
                                            "Voice input isn't available on this device."
                                    } else {
                                        showVoiceExpanded = true
                                        voiceError = null

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
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    // mic icon with its own mini pulse when listening
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
                                        level = voiceLevel,
                                        modifier = Modifier.weight(1f),
                                        activeColor = if (isListening) {
                                            MaterialTheme.myndoraAccents.accent
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        },
                                        idleColor = MaterialTheme.myndoraAccents.accentEnd.copy(alpha = 0.15f)
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
                        val canSend = promptText.isNotBlank() && !uiState.isThinking
                        val sendBackground by animateColorAsState(
                            targetValue = if (canSend) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                            animationSpec = tween(240),
                            label = "send_button_background",
                        )
                        val sendContent by animateColorAsState(
                            targetValue = if (canSend) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .45f),
                            animationSpec = tween(240),
                            label = "send_button_content",
                        )
                        val sendScale by animateFloatAsState(
                            targetValue = if (canSend) 1f else .94f,
                            animationSpec = spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow),
                            label = "send_button_scale",
                        )
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .scale(sendScale)
                                .clip(CircleShape)
                                .background(sendBackground)
                                .clickable(enabled = canSend) {
                                    viewModel.send(promptText)
                                    promptText = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isThinking) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = "Send",
                                    tint = sendContent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                if (showSuggestions) {
                    Spacer(modifier = Modifier.height(14.dp))

                    // divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                            )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // suggestion chips
                    Text(
                        text = "Suggestions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiSuggestionChip("Summarize my day", aiGradient) {
                            viewModel.send("Summarize my day")
                        }
                        AiSuggestionChip("Help me focus", aiGradient) {
                            viewModel.send("Help me focus")
                        }
                    }
                }
            }
        }
    }

    if (showHistorySheet) {
        ChatHistorySheet(
            conversations = conversations,
            onDismiss = { showHistorySheet = false },
            onSelect = { id ->
                viewModel.switchToConversation(id)
                showHistorySheet = false
            },
            onDelete = { id -> viewModel.deleteConversation(id) },
        )
    }
}

// the one thing that changes in the assistant when the connection drops. everything else
// stays exactly as it was: full colour, same button behind, chat still open and scrollable.
// draining the colour out of a working feature says 'broken', and on a flaky connection it
// made the assistant look like it was flickering in and out of existence. the wording leads
// with what the user can do, because 'no connection' on its own is a dead end
@Composable
private fun OfflineSwitchBar(
    outOfMessages: Boolean = false,
    onSwitch: () -> Unit,
) {
    Surface(
        onClick = onSwitch,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .55f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Icon(
                imageVector = if (outOfMessages) Icons.Rounded.Schedule else Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(19.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (outOfMessages) "That's today's AI messages" else "You're offline",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Text(
                    text = if (outOfMessages) {
                        "More tomorrow — the offline assistant still works"
                    } else {
                        "Switch to the offline assistant to keep going"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = .78f),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(colors.onPrimaryContainer.copy(alpha = 0.08f))
            .border(1.dp, colors.onPrimaryContainer.copy(alpha = .08f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = colors.onPrimaryContainer,
            modifier = Modifier.size(16.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatHistorySheet(
    conversations: List<com.muradgalayev.brainbuddy.domain.ai.ConversationSummary>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            // header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = "Chat history",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Spacer(modifier = Modifier.weight(1f))
                if (conversations.isNotEmpty()) {
                    Text(
                        text = "${conversations.size}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.surfaceContainerHighest)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
            }

            if (conversations.isEmpty()) {
                EmptyHistory()
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(items = conversations, key = { it.id }) { conv ->
                        HistoryRow(
                            summary = conv,
                            onClick = { onSelect(conv.id) },
                            onDelete = { onDelete(conv.id) },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EmptyHistory() {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "No chats yet",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Your conversations with Myndora will show up here.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HistoryRow(
    summary: com.muradgalayev.brainbuddy.domain.ai.ConversationSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val accents = MaterialTheme.myndoraAccents
    val gradient = Brush.linearGradient(listOf(accents.accent, accents.accentEnd))
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (summary.isActive) colors.primaryContainer.copy(alpha = 0.45f)
        else colors.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(
            width = if (summary.isActive) 1.5.dp else 1.dp,
            color = if (summary.isActive) colors.primary.copy(alpha = 0.5f)
            else colors.outlineVariant.copy(alpha = 0.5f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // leading gradient avatar
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(gradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.size(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = summary.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (summary.isActive) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.primary),
                        )
                        Spacer(modifier = Modifier.size(5.dp))
                        Text(
                            text = "Active",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.primary,
                        )
                        DotSeparator()
                    }
                    val relative = relativeTime(summary.lastActivityAt)
                    if (relative.isNotBlank()) {
                        Text(
                            text = relative,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                        )
                        DotSeparator()
                    }
                    Text(
                        text = "${summary.messageCount} message" +
                            (if (summary.messageCount == 1) "" else "s"),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Delete chat",
                    tint = colors.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun DotSeparator() {
    Text(
        text = " · ",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
    )
}

// relative time: 'Just now', '5m ago', '3h ago', 'Yesterday', 'Mar 4'
private fun relativeTime(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val diffMin = (System.currentTimeMillis() - epochMillis) / 60_000
    return when {
        diffMin < 1 -> "Just now"
        diffMin < 60 -> "${diffMin}m ago"
        diffMin < 60 * 24 -> "${diffMin / 60}h ago"
        diffMin < 60 * 24 * 2 -> "Yesterday"
        diffMin < 60 * 24 * 7 -> "${diffMin / (60 * 24)}d ago"
        else -> Instant.ofEpochMilli(epochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(DateTimeFormatter.ofPattern("MMM d"))
    }
}

@Composable
private fun AiSuggestionChip(
    text: String,
    gradient: Brush,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = gradient,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
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
private fun ConversationView(
    messages: List<ChatMessage>,
    error: String?,
    onChipTap: (String) -> Unit,
    speakingMessageId: String?,
    onSpeak: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, error) {
        val target = messages.size + (if (error != null) 1 else 0) - 1
        if (target >= 0) listState.animateScrollToItem(target)
    }
    val visible = messages.filter { it.role != ChatRole.TOOL && it.text.isNotBlank() }
    // only the last assistant message has tappable chips. older ones stay visually intact but
    // inert, so nothing gets re-triggered from three turns ago
    val lastAssistantId = visible.lastOrNull { it.role == ChatRole.ASSISTANT }?.id
    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            items = visible,
            key = { it.id }
        ) { msg ->
            MessageBubble(
                msg = msg,
                showChips = msg.id == lastAssistantId,
                onChipTap = onChipTap,
                isSpeaking = speakingMessageId == msg.id,
                onSpeak = { onSpeak(msg.id, parseOptions(msg.text).first) },
            )
        }
        if (error != null) {
            item("error") {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

// pulls the trailing [options: A | B | C] block off an assistant message, case-insensitive.
// returns the visible text and up to 3 chip labels, with the whole line stripped from view
private val OPTIONS_REGEX = Regex(
    pattern = """\[\s*options\s*:\s*([^\]]+)\]\s*$""",
    option = RegexOption.IGNORE_CASE,
)

private fun parseOptions(raw: String): Pair<String, List<String>> {
    val match = OPTIONS_REGEX.find(raw.trimEnd()) ?: return raw to emptyList()
    val labels = match.groupValues[1].split('|')
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .take(3)
    val cleaned = raw.substring(0, match.range.first).trimEnd()
    return cleaned to labels
}

@Composable
private fun MessageBubble(
    msg: ChatMessage,
    showChips: Boolean,
    onChipTap: (String) -> Unit,
    isSpeaking: Boolean,
    onSpeak: () -> Unit,
) {
    val entrance = remember(msg.id) { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(msg.id) {
        entrance.animateTo(
            1f,
            animationSpec = spring(dampingRatio = .86f, stiffness = Spring.StiffnessLow),
        )
    }
    val isUser = msg.role == ChatRole.USER
    val (bodyText, chips) = if (isUser) msg.text to emptyList()
    else parseOptions(msg.text)

    Column(
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            alpha = entrance.value
            translationY = (1f - entrance.value) * 18.dp.toPx()
            scaleX = .985f + entrance.value * .015f
            scaleY = .985f + entrance.value * .015f
        },
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(
                modifier = Modifier
                    .then(
                        if (!isUser) Modifier.weight(1f, fill = false)
                        else Modifier,
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (isUser) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .82f)
                        else MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = .62f)
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = renderInlineMarkdown(bodyText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
            if (!isUser) {
                Spacer(Modifier.size(6.dp))
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .8f))
                        .clickable(onClick = onSpeak),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isSpeaking) Icons.Rounded.Stop else Icons.Rounded.VolumeUp,
                        contentDescription = if (isSpeaking) "Stop reading answer" else "Read answer aloud",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        if (!isUser && chips.isNotEmpty() && showChips) {
            Spacer(modifier = Modifier.height(6.dp))
            QuickReplyChips(labels = chips, onTap = onChipTap)
        }
    }
}

// renders the tiny slice of markdown the model actually emits, bold and italic, instead of
// showing raw asterisks. unmatched markers are left as literal characters
private fun renderInlineMarkdown(text: String): AnnotatedString = buildAnnotatedString {
    var i = 0
    while (i < text.length) {
        when {
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end != -1) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i]); i++
                }
            }
            text[i] == '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1 && end > i + 1) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i]); i++
                }
            }
            else -> {
                append(text[i]); i++
            }
        }
    }
}

@Composable
private fun QuickReplyChips(
    labels: List<String>,
    onTap: (String) -> Unit,
) {
    // FlowRow so each chip sizes to its own text and wraps onto the next line. a Row squeezes
    // three chips into slivers and wraps long labels letter by letter
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEach { label ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                ),
                onClick = { onTap(label) },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

// platform error codes as something a person can act on. NO_MATCH and SPEECH_TIMEOUT aren't
// faults, the recognizer just heard nothing useful, so they get a nudge not an apology
private fun speechErrorMessage(code: Int): String = when (code) {
    android.speech.SpeechRecognizer.ERROR_NO_MATCH,
    android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Didn't catch that \u2014 try again."
    android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
        "Myndora needs microphone access to hear you."
    android.speech.SpeechRecognizer.ERROR_NETWORK,
    android.speech.SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
        "Voice input needs a connection right now."
    android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Still finishing the last one."
    android.speech.SpeechRecognizer.ERROR_CLIENT,
    android.speech.SpeechRecognizer.ERROR_SERVER,
    android.speech.SpeechRecognizer.ERROR_SERVER_DISCONNECTED ->
        "Voice input isn't working on this device."
    else -> "Voice input didn't work \u2014 try again."
}
