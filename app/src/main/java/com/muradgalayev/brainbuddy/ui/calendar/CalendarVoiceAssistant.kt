package com.muradgalayev.brainbuddy.ui.calendar

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.SpeechRecognizer
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.animation.animateContentSize
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.domain.ai.ChatRole
import com.muradgalayev.brainbuddy.ui.ai.AiAssistantViewModel
import com.muradgalayev.brainbuddy.ui.ai.aiVoiceProfile
import com.muradgalayev.brainbuddy.ui.ai.curatedAiVoices
import com.muradgalayev.brainbuddy.ui.ai.toMyndoraSpeech
import com.muradgalayev.brainbuddy.ui.components.VoiceWaveform
import com.muradgalayev.brainbuddy.ui.utils.SpeechRecognitionHelper
import java.util.Locale

// hands-free safety cut-off. the assistant is a continuous conversation: listen, answer, back
// to listening, indefinitely. it doesn't close because you paused to think, which used to
// happen after 9 seconds. this is only the backstop for a session left running in a pocket
private const val ABANDONED_SESSION_MS = 3 * 60 * 1000L

@Composable
fun CalendarVoiceAssistant(
    viewModel: AiAssistantViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val spokenResponsesEnabled by viewModel.spokenResponsesEnabled.collectAsState()
    val selectedVoiceName by viewModel.selectedVoiceName.collectAsState()
    var listening by remember { mutableStateOf(false) }
    var partial by remember { mutableStateOf("") }
    var spokenRequest by remember { mutableStateOf("") }
    var level by remember { mutableFloatStateOf(0f) }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var spokenAnswerId by remember { mutableStateOf<String?>(null) }
    var permissionDenied by remember { mutableStateOf(false) }
    var typedAnswer by remember { mutableStateOf("") }
    var appeared by remember { mutableStateOf(false) }
    var activitySignal by remember { mutableIntStateOf(0) }
    var speaking by remember { mutableStateOf(false) }
    var spokenAnswerText by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme
    // the glass rim and the orb were built with hard-coded white highlights. against a dark
    // background white reads as a lit edge, against the light theme's warm off-white it is the
    // background, so both of them dissolved. in light mode the sheen has to run the other way,
    // darker than the surface
    val isDarkTheme = colors.background.luminance() < 0.5f
    fun sheen(alpha: Float): Color =
        if (isDarkTheme) Color.White.copy(alpha = alpha)
        else colors.onSurface.copy(alpha = alpha * 0.45f)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val speechRef = remember { arrayOfNulls<SpeechRecognitionHelper>(1) }

    val speech = remember {
        SpeechRecognitionHelper(
            context = context,
            onResult = { text ->
                // nothing arrives here while Myndora is talking any more, the mic is closed for the duration,
                // so a result is always genuinely the user and no echo guessing is needed
                partial = ""
                typedAnswer = ""
                spokenRequest = text
                listening = false
                activitySignal++
                viewModel.send(text)
            },
            onPartialResult = {
                partial = it
                if (it.isNotBlank()) {
                    typedAnswer = ""
                    activitySignal++
                }
            },
            onListeningStarted = { listening = true },
            // Android calls this before delivering onResults, so keep the session visually active until
            // a result or an error actually arrives
            onListeningFinished = { level = 0f },
            onError = { error ->
                listening = false
                level = 0f
                val restartDelay = when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT -> 850L
                    // heard nothing or timed out, the harmless case. come back so nobody talks into a dead mic
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 250L
                    else -> 450L
                }
                // don't reopen the mic underneath our own voice
                if (!speaking) speechRef[0]?.restart(restartDelay)
            },
            onRms = {
                level = ((it + 2f) / 12f).coerceIn(0f, 1f)
                if (it > 2.5f) activitySignal++
            },
        )
    }
    speechRef[0] = speech
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permissionDenied = !it
        if (it) speech.startListening()
    }
    fun listen() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            speech.startListening()
        } else permission.launch(Manifest.permission.RECORD_AUDIO)
    }

    DisposableEffect(Unit) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val localeResult = engine?.setLanguage(Locale.getDefault())
                if (localeResult == TextToSpeech.LANG_MISSING_DATA ||
                    localeResult == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    engine?.setLanguage(Locale.US)
                }
                val curated = curatedAiVoices(engine?.voices)
                val profile = aiVoiceProfile(selectedVoiceName, curated)
                engine?.setSpeechRate(profile.rate)
                engine?.setPitch(profile.pitch)
                val preferredVoice = curated.firstOrNull { it.name == selectedVoiceName }
                    ?: curated.firstOrNull()
                preferredVoice?.let { engine?.voice = it }
                engine?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    // half-duplex, deliberately. the mic used to stay open while Myndora talked, on the theory
                    // that you could barge in. with no echo cancellation the recognizer mostly heard Myndora,
                    // transcribed it badly, failed the 'is this my own voice' comparison and concluded the user
                    // had interrupted, so it cut its own sentence off and sometimes fed its own words back to
                    // the model as a question. that's the stammering. barging in is a tap on the glass now
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post { speech.cancel() }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            speaking = false
                            activitySignal++
                        }
                        // short pause so the speaker has actually gone quiet before the mic opens, otherwise the
                        // tail of the last word lands in the next transcript
                        speech.restart(350L)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            speaking = false
                            activitySignal++
                        }
                        speech.restart(350L)
                    }
                })
                tts = engine
            }
        }
        onDispose { speech.destroy(); engine?.stop(); engine?.shutdown() }
    }
    LaunchedEffect(Unit) { listen() }
    LaunchedEffect(Unit) { appeared = true }
    val lastUserIndex = state.messages.indexOfLast { it.role == ChatRole.USER }
    val answer = state.messages.drop(lastUserIndex + 1)
        .lastOrNull { it.role == ChatRole.ASSISTANT && it.text.isNotBlank() }
    LaunchedEffect(answer?.id, state.isThinking, tts) {
        if (spokenResponsesEnabled && !state.isThinking && spokenRequest.isNotBlank() && answer != null && answer.id != spokenAnswerId) {
            val engine = tts
            if (engine != null) {
                spokenAnswerId = answer.id
                speaking = true
                spokenAnswerText = answer.text.toMyndoraSpeech()
                engine.speak(spokenAnswerText, TextToSpeech.QUEUE_FLUSH, null, answer.id)
            }
        }
    }
    // watchdog over the speaking flag. the mic stays shut for as long as it's true, so a TTS
    // engine that never calls onDone (some vendor engines drop it after an audio-focus change or
    // a Bluetooth handover) would leave the assistant permanently deaf, looking alive but hearing
    // nothing. nothing else clears the flag, so this does
    LaunchedEffect(spokenAnswerId, speaking) {
        if (!speaking) return@LaunchedEffect
        val expected = 2_000L + spokenAnswerText.length * 110L
        delay(expected.coerceAtMost(60_000L))
        if (speaking) {
            speaking = false
            activitySignal++
            speech.restart(200L)
        }
    }
    LaunchedEffect(selectedVoiceName, tts) {
        val engine = tts ?: return@LaunchedEffect
        val curated = curatedAiVoices(engine.voices)
        val profile = aiVoiceProfile(selectedVoiceName, curated)
        engine.setSpeechRate(profile.rate)
        engine.setPitch(profile.pitch)
        val preferredVoice = curated.firstOrNull { it.name == selectedVoiceName }
            ?: curated.firstOrNull()
        preferredVoice?.let { engine.voice = it }
    }
    LaunchedEffect(answer?.id, state.isThinking, spokenResponsesEnabled) {
        if (!spokenResponsesEnabled && !state.isThinking && spokenRequest.isNotBlank() && answer != null) {
            spokenAnswerId = answer.id
            activitySignal++
            speech.restart(180L)
        }
    }
    LaunchedEffect(answer?.id, state.isThinking) {
        if (!state.isThinking && spokenRequest.isNotBlank() && answer != null) {
            typedAnswer = ""
            answer.text.forEach { character ->
                typedAnswer += character
                delay(18)
            }
        }
    }
    // errors have to be seen and heard. voice has no status bar and no second channel, so a
    // failure the UI swallows is indistinguishable from the assistant being broken, which is
    // exactly how it felt: you speak, and nothing whatsoever happens
    LaunchedEffect(state.error, state.isThinking) {
        val message = state.error
        if (state.isThinking || message == null || spokenRequest.isBlank()) return@LaunchedEffect
        typedAnswer = message
        activitySignal++
        val engine = tts
        if (spokenResponsesEnabled && engine != null) {
            speaking = true
            spokenAnswerText = message.toMyndoraSpeech()
            // the utterance listener reopens the mic when it finishes
            engine.speak(spokenAnswerText, TextToSpeech.QUEUE_FLUSH, null, "error-$message".take(64))
        } else {
            speech.restart(350L)
        }
    }
    // hands-free: the mic reopens after every turn and stays open through any pause. only the
    // close button, a tap outside and the abandoned-session backstop end a session
    LaunchedEffect(activitySignal, state.isThinking, speaking) {
        if (state.isThinking || speaking) return@LaunchedEffect
        delay(ABANDONED_SESSION_MS)
        speech.stopListening()
        onDismiss()
    }

    // keeps the loop closed. every path back to listening runs through a callback (recognizer
    // error, TTS finished, answer delivered) and missing any single one leaves the assistant
    // quietly deaf. this notices the mic is shut with no reason to be, and reopens it
    LaunchedEffect(listening, speaking, state.isThinking, permissionDenied) {
        if (listening || speaking || state.isThinking || permissionDenied) return@LaunchedEffect
        delay(1_200L)
        speech.startListening()
    }

    val islandMotion = rememberInfiniteTransition(label = "voiceIsland")
    val pulse by islandMotion.animateFloat(
        initialValue = 1f,
        targetValue = if (listening) 1.12f else 1.035f,
        animationSpec = infiniteRepeatable(tween(if (listening) 620 else 1400), RepeatMode.Reverse),
        label = "voiceOrbPulse",
    )
    val wavePhase by islandMotion.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Restart),
        label = "voiceWavePhase",
    )
    val liquidFlow by islandMotion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1150), RepeatMode.Reverse),
        label = "liquidGlassFlow",
    )
    val voiceEnergy by animateFloatAsState(
        targetValue = when {
            listening -> level.coerceAtLeast(.12f)
            speaking -> .58f
            state.isThinking -> .32f
            else -> .08f
        },
        animationSpec = tween(110),
        label = "voiceEnergy",
    )
    val popupScale by animateFloatAsState(
        if (appeared) 1f else .55f,
        spring(dampingRatio = .82f, stiffness = Spring.StiffnessMediumLow),
        label = "voicePopupArrival",
    )
    val responseVisible = typedAnswer.isNotEmpty()

    Box(Modifier.fillMaxSize().clickable(onClick = onDismiss)) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp, start = 16.dp, end = 16.dp)
                .scale(popupScale)
                .animateContentSize(
                    animationSpec = spring(dampingRatio = .88f, stiffness = Spring.StiffnessLow),
                )
                .border(
                    width = 1.2.dp,
                    brush = Brush.linearGradient(
                        listOf(
                            sheen(.72f),
                            colors.primary.copy(alpha = .82f),
                            colors.secondary.copy(alpha = .62f),
                            colors.tertiary.copy(alpha = .66f),
                            sheen(.55f),
                        ),
                    ),
                    shape = RoundedCornerShape(30.dp),
                )
                .clickable {
                    // natural barge-in: tapping the glass while Myndora is talking stops speech and hands the
                    // mic back for a correction or a follow-up
                    if (speaking) {
                        tts?.stop()
                        speaking = false
                        activitySignal++
                        speech.startListening()
                    } else if (!listening && !state.isThinking) {
                        activitySignal++
                        speech.startListening()
                    }
                },
            shape = RoundedCornerShape(30.dp),
            color = Color.Transparent,
            // dark mode separates by tone, #2B2B2B on #1F1F1F. light mode has no such gap, so the panel
            // needs a real shadow or it reads as a faint smudge rather than a floating card
            shadowElevation = if (isDarkTheme) 0.dp else 14.dp,
            tonalElevation = 0.dp,
        ) {
            Box(
                Modifier.background(
                    brush = Brush.linearGradient(
                        if (isDarkTheme) listOf(
                            colors.surface.copy(alpha = .91f),
                            colors.primaryContainer.copy(alpha = .55f),
                            colors.surface.copy(alpha = .86f),
                            colors.secondary.copy(alpha = .16f),
                        ) else listOf(
                            // near-opaque, and leaning on the containers rather than surface: the tinted stops are what
                            // make the glass readable when the page behind it is already off-white
                            colors.surface,
                            colors.primaryContainer.copy(alpha = .72f),
                            colors.surface.copy(alpha = .97f),
                            colors.secondary.copy(alpha = .26f),
                        ),
                    ),
                    shape = RoundedCornerShape(30.dp),
                ),
            ) {
                if (!responseVisible) {
                    Box(
                        Modifier.padding(horizontal = 12.dp, vertical = 9.dp).size(132.dp, 88.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Canvas(Modifier.fillMaxSize()) {
                            val energy = voiceEnergy
                            // a translucent wash reads far weaker over off-white than over near-black, so light mode
                            // gets about half again the opacity to land at the same visual strength
                            val tint = if (isDarkTheme) 1f else 1.55f
                            drawCircle(
                                color = colors.primary.copy(alpha = ((.16f + energy * .16f) * tint).coerceAtMost(1f)),
                                radius = size.minDimension * (.38f + energy * .12f),
                                center = androidx.compose.ui.geometry.Offset(
                                    size.width * (.38f + liquidFlow * .08f),
                                    size.height * .48f,
                                ),
                            )
                            drawCircle(
                                color = colors.secondary.copy(alpha = ((.13f + energy * .12f) * tint).coerceAtMost(1f)),
                                radius = size.minDimension * (.31f + energy * .09f),
                                center = androidx.compose.ui.geometry.Offset(
                                    size.width * (.66f - liquidFlow * .06f),
                                    size.height * (.46f + liquidFlow * .08f),
                                ),
                            )
                            drawCircle(
                                color = colors.tertiary.copy(alpha = ((.11f + energy * .1f) * tint).coerceAtMost(1f)),
                                radius = size.minDimension * .24f,
                                center = androidx.compose.ui.geometry.Offset(size.width * .55f, size.height * .7f),
                            )

                            val palette = listOf(colors.primary, colors.secondary, colors.tertiary)
                            palette.forEachIndexed { index, color ->
                                val path = Path()
                                val center = size.height * (.38f + index * .12f)
                                val amplitude = size.height * (.035f + energy * .085f + index * .008f)
                                val points = 30
                                path.moveTo(0f, center)
                                for (step in 1..points) {
                                    val x = size.width * step / points
                                    val y = center + kotlin.math.sin(wavePhase + step * .38f + index * 1.5f) * amplitude
                                    path.lineTo(x, y)
                                }
                                drawPath(
                                    path = path,
                                    color = color.copy(alpha = .34f + energy * .34f),
                                    style = Stroke(width = (2.2f + energy * 2.4f).dp.toPx(), cap = StrokeCap.Round),
                                )
                            }
                        }
                        Box(
                            Modifier
                                .size(58.dp + (voiceEnergy * 11f).dp)
                                .scale(pulse + voiceEnergy * .05f)
                                .rotate(kotlin.math.sin(wavePhase) * (2f + voiceEnergy * 5f))
                                .clip(RoundedCornerShape((18f + voiceEnergy * 12f).dp))
                                .background(
                                    Brush.linearGradient(
                                        if (isDarkTheme) listOf(
                                            Color.White.copy(alpha = .84f),
                                            colors.primaryContainer.copy(alpha = .76f),
                                            colors.surface.copy(alpha = .72f),
                                        ) else listOf(
                                            // light mode keeps the same shape but carries its own colour, so the orb is an object on
                                            // the surface rather than a pale patch of it
                                            colors.primaryContainer,
                                            colors.primary.copy(alpha = .70f),
                                            colors.tertiaryContainer.copy(alpha = .80f),
                                        ),
                                    ),
                                )
                                .border(
                                    1.dp,
                                    sheen(.68f),
                                    RoundedCornerShape((18f + voiceEnergy * 12f).dp),
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painterResource(R.drawable.ic_ai),
                                null,
                                Modifier.size(39.dp + (voiceEnergy * 7f).dp),
                            )
                        }
                    }
                } else {
                    Text(
                        text = typedAnswer,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 18.dp),
                        color = colors.onSurface,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Start,
                    )
                }
            }
        }
    }
}
