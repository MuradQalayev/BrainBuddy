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
import com.muradgalayev.brainbuddy.data.local.AppLocale
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.domain.ai.ChatRole
import com.muradgalayev.brainbuddy.ui.ai.AiAssistantViewModel
import com.muradgalayev.brainbuddy.ui.ai.aiVoiceProfile
import com.muradgalayev.brainbuddy.ui.ai.curatedAiVoices
import com.muradgalayev.brainbuddy.ui.ai.parseOptions
import com.muradgalayev.brainbuddy.ui.ai.speechErrorMessage
import com.muradgalayev.brainbuddy.ui.ai.toMyndoraSpeech
import com.muradgalayev.brainbuddy.ui.components.VoiceWaveform
import com.muradgalayev.brainbuddy.ui.utils.SpeechRecognitionHelper
import java.util.Locale

// hands-free safety cut-off. the assistant is a continuous conversation: listen, answer, back
// to listening, indefinitely. it doesn't close because you paused to think, which used to
// happen after 9 seconds. this is only the backstop for a session left running in a pocket
private const val ABANDONED_SESSION_MS = 3 * 60 * 1000L

// how many real recognition failures in a row before the panel says so out loud. two, because one
// is often just a stumble over the first word, and three is long enough to feel ignored
private const val MIC_FAILURES_BEFORE_SPEAKING_UP = 3

// consecutive turns that hear nothing before the recognizer is rebuilt
private const val MIC_FAILURES_BEFORE_RESET = 3

// silent turns before the microphone stops reopening and waits for a tap instead
private const val SILENT_TURNS_BEFORE_WAITING = 2

// dim behind the panel. dark in both themes: a light scrim over a light app dims nothing, and what
// the text needs is the contrast, not the tint. deep enough to read against, not so deep that the
// app looks switched off
private val SCRIM = Color.Black
private const val SCRIM_ALPHA = 0.46f

// how long after the assistant stops speaking before the microphone opens. the speaker is still
// draining when the engine reports it is done. the gate is what actually stops the two overlapping
// now, so this only has to outlast the tail rather than be safe on its own
private const val TTS_DRAIN_MS = 450L

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
    // recognition failures in a row. a voice screen has no status bar, so a recognizer that never
    // succeeds produced total silence: every panel below is gated on spokenRequest, which is only
    // set by a successful transcription, so 'I can't hear you' could never reach the screen. that
    // is indistinguishable from the assistant being broken, and it is what 'it never answers' was
    var micFailures by remember { mutableIntStateOf(0) }
    // the microphone is idle and waiting to be tapped, rather than reopening on its own. hands-free
    // used to mean the mic reopened every couple of seconds for as long as the panel was up, which
    // is a recording indicator that never goes out, a flat battery, and a phone that feels like it
    // is listening to the room. one turn, then it waits
    var awaitingTap by remember { mutableStateOf(false) }
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
    // the microphone may only open when the assistant is neither speaking nor about to. read at the
    // moment a restart fires, through refs rather than captured values, because the whole failure
    // was a decision made a beat too early
    val micGate = remember { booleanArrayOf(true, true, true) }
    fun canOpenMic(): Boolean = micGate[0] && micGate[1] && micGate[2]

    val speech = remember {
        SpeechRecognitionHelper(
            context = context,
            onResult = { text ->
                // nothing arrives here while Myndora is talking any more, the mic is closed for the duration,
                // so a result is always genuinely the user and no echo guessing is needed
                partial = ""
                typedAnswer = ""
                spokenRequest = text
                micFailures = 0
                awaitingTap = false
                listening = false
                activitySignal++
                viewModel.send(text)
            },
            onPartialResult = {
                partial = it
                micFailures = 0
                awaitingTap = false
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
                // a pause with nothing said is not a failure, it's a pause. anything else is
                // NO_MATCH once is a pause. NO_MATCH over and over, with the microphone open each
                // time, is the recognizer hearing nothing at all — which is what a wedged audio
                // session looks like, so it counts
                micFailures += 1
                // say something once it is clearly not going to fix itself. permission and 'no
                // speech model' errors never recover on their own, and retrying in silence forever
                // is how this looked broken
                if (micFailures == MIC_FAILURES_BEFORE_SPEAKING_UP) {
                    typedAnswer = context.getString(speechErrorMessage(error))
                    activitySignal++
                }
                val restartDelay = when (error) {
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                    SpeechRecognizer.ERROR_CLIENT -> 850L
                    // heard nothing or timed out, the harmless case. come back so nobody talks into a dead mic
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> 250L
                    else -> 450L
                }
                // a run of turns that open the microphone and hear nothing means the audio session is
                // wedged, not that the room is quiet. rebuild before trying again
                if (micFailures > 0 && micFailures % MIC_FAILURES_BEFORE_RESET == 0) {
                    speechRef[0]?.resetEngine()
                }
                // heard nothing: one more go, then stop and wait to be tapped. retrying forever is
                // what kept the microphone running the whole time the panel was open
                if (micFailures >= SILENT_TURNS_BEFORE_WAITING) {
                    awaitingTap = true
                    return@SpeechRecognitionHelper
                }
                // don't reopen the mic underneath our own voice, checked when this actually fires
                speechRef[0]?.restart(restartDelay) { canOpenMic() }
            },
            onRms = {
                level = ((it + 2f) / 12f).coerceIn(0f, 1f)
                if (it > 2.5f) activitySignal++
            },
        )
    }
    speechRef[0] = speech
    micGate[0] = !speaking
    micGate[1] = !state.isThinking
    micGate[2] = !awaitingTap
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
                val localeResult = engine?.setLanguage(AppLocale.voiceLocale(context))
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
                        // onDone fires when synthesis finishes, not when the speaker has finished
                        // playing it. 350ms was inside the tail, which opened the microphone into
                        // the assistant's own voice and left it deaf for every turn afterwards
                        speech.restart(TTS_DRAIN_MS) { canOpenMic() }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            speaking = false
                            activitySignal++
                        }
                        speech.restart(TTS_DRAIN_MS) { canOpenMic() }
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
                // the chips are a tap-to-reply feature of the card, and there is nothing to tap here.
                // read aloud, the raw marker came out as the assistant literally saying
                // 'open square bracket options colon' at the end of every answer
                spokenAnswerText = parseOptions(answer.text).first.toMyndoraSpeech()
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
            speech.restart(TTS_DRAIN_MS) { canOpenMic() }
        }
    }
    // the gate closes while the assistant thinks and speaks, and any restart that fired in that
    // window was skipped rather than queued. this is what reopens the microphone once the coast is
    // clear, so a skipped restart costs a moment instead of the rest of the conversation
    LaunchedEffect(speaking, state.isThinking, listening) {
        if (speaking || state.isThinking || listening) return@LaunchedEffect
        // only once a conversation is under way: before the first turn, listen() has it
        if (spokenRequest.isBlank()) return@LaunchedEffect
        delay(TTS_DRAIN_MS)
        if (canOpenMic() && !listening) speech.startListening()
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
            speech.restart(180L) { canOpenMic() }
        }
    }
    LaunchedEffect(answer?.id, state.isThinking) {
        if (!state.isThinking && spokenRequest.isNotBlank() && answer != null) {
            // a fresh answer is a fresh turn: the mic is owed one open, whatever happened before
            micFailures = 0
            awaitingTap = false
            typedAnswer = ""
            // same strip as the spoken copy: the options line belongs to the nav bar's card, where
            // it becomes tappable chips. on the voice panel it is just markup with nowhere to go
            parseOptions(answer.text).first.forEach { character ->
                typedAnswer += character
                delay(12)
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
            speech.restart(TTS_DRAIN_MS) { canOpenMic() }
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

    // the panel used to float over live, moving, full-contrast app content, so its text competed
    // with whatever happened to be underneath it. the scrim pushes the app back a step: the answer
    // becomes the only lit thing on screen, and it makes 'tap anywhere to close' look like an
    // instruction rather than a guess. it fades with the panel rather than snapping on
    val scrim by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(if (animationsOn()) 260 else 0),
        label = "voiceScrim",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(SCRIM.copy(alpha = SCRIM_ALPHA * scrim))
            .clickable(onClick = onDismiss),
    ) {
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
                        micFailures = 0
                        awaitingTap = false
                        speech.startListening()
                    }
                },
            shape = RoundedCornerShape(30.dp),
            color = Color.Transparent,
            // flat in both themes. the gradient border already separates the glass from the page, and a
            // drop shadow under it read as a heavy box rather than something floating
            shadowElevation = 0.dp,
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
                        // the microphone has stopped on its own, so say how to start it again.
                        // an orb that looks identical whether it is listening or waiting is what
                        // made the old always-on loop feel necessary
                        androidx.compose.animation.AnimatedVisibility(
                            visible = awaitingTap,
                            enter = androidx.compose.animation.fadeIn(),
                            exit = androidx.compose.animation.fadeOut(),
                            modifier = Modifier.align(Alignment.BottomCenter),
                        ) {
                            Text(
                                text = androidx.compose.ui.res.stringResource(R.string.ai_voice_tap_to_talk),
                                style = MaterialTheme.typography.labelSmall,
                                color = colors.onSurfaceVariant,
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
