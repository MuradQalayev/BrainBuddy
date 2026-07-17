package com.muradgalayev.brainbuddy.ui.ai

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.domain.ai.ChatMessage
import com.muradgalayev.brainbuddy.domain.ai.ChatRole
import com.muradgalayev.brainbuddy.ui.components.VoiceWaveform
import com.muradgalayev.brainbuddy.ui.theme.AiButtonDark
import com.muradgalayev.brainbuddy.ui.theme.AiButtonDarkEnd
import com.muradgalayev.brainbuddy.ui.theme.AiButtonLight
import com.muradgalayev.brainbuddy.ui.theme.AiButtonLightEnd
import com.muradgalayev.brainbuddy.ui.utils.SpeechRecognitionHelper

@Composable
fun AiPromptCard(
    onDismiss: () -> Unit,
    viewModel: AiAssistantViewModel = hiltViewModel()
) {
    var promptText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    var showVoiceExpanded by remember { mutableStateOf(false) }
    var showHistorySheet by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val conversations by viewModel.conversations.collectAsState()

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val aiGradient = if (isDark) {
        Brush.horizontalGradient(listOf(AiButtonDark, AiButtonDarkEnd))
    } else {
        Brush.horizontalGradient(listOf(AiButtonLight, AiButtonLightEnd))
    }

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
                    HeaderIconButton(
                        icon = Icons.Rounded.History,
                        contentDescription = "Chat history",
                        onClick = {
                            viewModel.refreshConversations()
                            showHistorySheet = true
                        },
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    HeaderIconButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = "New chat",
                        onClick = { viewModel.startNewChat() },
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    HeaderIconButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Close",
                        onClick = onDismiss,
                    )
                }
            }

            // Chat history (only visible after first message)
            if (uiState.messages.isNotEmpty() || uiState.error != null) {
                ConversationView(
                    messages = uiState.messages,
                    error = uiState.error,
                    onChipTap = { viewModel.send(it) },
                )
            }

            // Input area
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {

                // Text field with send button
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
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
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
                                .clickable(enabled = !uiState.isThinking && promptText.isNotBlank()) {
                                    viewModel.send(promptText)
                                    promptText = ""
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (uiState.isThinking) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.ArrowUpward,
                                    contentDescription = "Send",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
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
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    AiSuggestionChip("Summarize my day", aiGradient) {
                        promptText = "Summarize my day"
                    }
                    AiSuggestionChip("Help me focus", aiGradient) {
                        promptText = "Help me focus"
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

@Composable
private fun HeaderIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
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
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = "Chat history",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            if (conversations.isEmpty()) {
                Text(
                    text = "No past chats yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
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
private fun HistoryRow(
    summary: com.muradgalayev.brainbuddy.domain.ai.ConversationSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (summary.isActive) colors.primaryContainer.copy(alpha = 0.55f)
        else colors.surfaceContainer,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            colors.outlineVariant.copy(alpha = 0.55f),
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (summary.isActive) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(colors.primary),
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                    }
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
//                Text(
//                    text = "${summary.messageCount} message" +
//                        (if (summary.messageCount == 1) "" else "s"),
//                    style = MaterialTheme.typography.labelSmall,
//                    color = colors.onSurfaceVariant,
//                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(colors.errorContainer.copy(alpha = 0.4f))
                    .clickable(onClick = onDelete),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Delete chat",
                    tint = colors.error,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
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
) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, error) {
        val target = messages.size + (if (error != null) 1 else 0) - 1
        if (target >= 0) listState.animateScrollToItem(target)
    }
    val visible = messages.filter { it.role != ChatRole.TOOL && it.text.isNotBlank() }
    // Only the very last assistant message shows tappable chips — older ones stay
    // visually intact but non-interactive so the user doesn't accidentally
    // re-trigger something from three turns ago.
    val lastAssistantId = visible.lastOrNull { it.role == ChatRole.ASSISTANT }?.id
    LazyColumn(
        state = listState,
        modifier = Modifier
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

/**
 * Extracts the trailing `[options: A | B | C]` block (case-insensitive) from an
 * assistant message. Returns the visible text and up to 3 chip labels.
 * The whole line is stripped from what the user sees.
 */
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
) {
    val isUser = msg.role == ChatRole.USER
    val (bodyText, chips) = if (isUser) msg.text to emptyList()
    else parseOptions(msg.text)

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(
                    if (isUser) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceContainerHigh
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurface
            )
        }
        if (!isUser && chips.isNotEmpty() && showChips) {
            Spacer(modifier = Modifier.height(6.dp))
            QuickReplyChips(labels = chips, onTap = onChipTap)
        }
    }
}

@Composable
private fun QuickReplyChips(
    labels: List<String>,
    onTap: (String) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
