package com.muradgalayev.brainbuddy.ui.components

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
import androidx.core.content.ContextCompat
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.ui.utils.SpeechRecognitionHelper
import androidx.compose.ui.res.stringResource

@Composable
fun AiPromptCard(
    onDismiss: (() -> Unit)? = null,
    embedded: Boolean = false
) {
    var promptText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var partialText by remember { mutableStateOf("") }
    var showVoiceExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val accents = MaterialTheme.myndoraAccents
    val aiGradient = Brush.horizontalGradient(listOf(accents.accent, accents.accentEnd))

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

    // permission launcher for RECORD_AUDIO
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
            .padding(horizontal = if (embedded) 0.dp else 12.dp)
            .then(if (embedded) Modifier else Modifier.padding(bottom = 120.dp)),
        shape = RoundedCornerShape(32.dp),
        tonalElevation = 2.dp,
        shadowElevation = if (embedded) 12.dp else 28.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column {
            // gradient header with sparkle icon, popup mode only
            if (!embedded) {
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
                                text = stringResource(R.string.ai_myndora_ai),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.ai_personal_assistant),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                        if (onDismiss != null) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.15f))
                                    .clickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = stringResource(R.string.common_close),
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // input area
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
            ) {

                if (embedded) {
                    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f)
                    TextField(
                        value = promptText,
                        onValueChange = { promptText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = {
                            Text(
                                stringResource(R.string.ai_what_help),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        shape = RoundedCornerShape(28.dp),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = containerColor,
                            unfocusedContainerColor = containerColor,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = MaterialTheme.colorScheme.primary
                        ),
                        trailingIcon = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                AnimatedVisibility(
                                    visible = promptText.isEmpty(),
                                    enter = fadeIn(tween(160)) + scaleIn(initialScale = 0.85f),
                                    exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.85f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isListening)
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                                                else
                                                    Color.Transparent
                                            )
                                            .clickable {
                                                if (isListening) {
                                                    speechHelper.stopListening()
                                                    isListening = false
                                                    partialText = ""
                                                } else {
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
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                            contentDescription = if (isListening) stringResource(R.string.ai_stop_listening) else stringResource(R.string.ai_voice_input),
                                            tint = if (isListening)
                                                MaterialTheme.colorScheme.primary
                                            else
                                                MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(brush = aiGradient)
                                        .clickable {
                                            // TODO: send prompt to AI backend
                                            promptText = ""
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_ai),
                                        contentDescription = stringResource(R.string.common_send),
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        },
                        maxLines = 4,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    return@Column
                }

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
                                    stringResource(R.string.ai_what_help),
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
                        // pulsing rings, only when listening and collapsed
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
                                            contentDescription = if (isListening) stringResource(R.string.ai_stop_listening) else stringResource(R.string.ai_voice_input),
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
                                            MaterialTheme.myndoraAccents.accent
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        },
                                        idleColor = MaterialTheme.myndoraAccents.accentEnd.copy(alpha = 0.15f)
                                    )

                                    Icon(
                                        imageVector = Icons.Rounded.ArrowUpward,
                                        contentDescription = stringResource(R.string.common_send),
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                    contentDescription = if (isListening) stringResource(R.string.ai_stop_listening) else stringResource(R.string.ai_voice_input),
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
                                contentDescription = stringResource(R.string.common_send),
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                if (!embedded) {
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
                        text = stringResource(R.string.ai_suggestions),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AiSuggestionChip(stringResource(R.string.ai_suggest_summarize), aiGradient)
                        AiSuggestionChip(stringResource(R.string.ai_suggest_focus), aiGradient)
                    }
                }
            }
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
