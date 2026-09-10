package com.muradgalayev.brainbuddy.ui.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import java.util.Locale

// continuous speech recognition on top of Android's one-shot SpeechRecognizer. the platform
// recognizer only ever does a single turn: it ends at the first long silence and has to be
// started again for the next. everything here exists to make that look continuous, and to
// keep it alive, because the binding to the system service doesn't stay healthy forever
class SpeechRecognitionHelper(
    context: Context,
    private val onResult: (String) -> Unit,
    private val onPartialResult: (String) -> Unit = {},
    private val onError: (Int) -> Unit = {},
    private val onListeningStarted: () -> Unit = {},
    private val onListeningFinished: () -> Unit = {},
    private val onRms: (Float) -> Unit = {},
) {
    // application context: this outlives individual screens, and an Activity here would leak
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var listening = false
    private var pendingRestart: Runnable? = null

    // errors since we last actually heard something, the signal that the recognizer has gone bad.
    // a healthy one produces the odd NO_MATCH, a dead one produces nothing but errors forever
    private var consecutiveErrors = 0
    private var destroyed = false

    // built per session, not once and kept. holding one from construction is what broke this: by
    // the time the user taps the mic the binding to the system service has usually died, and a
    // dead proxy accepts startListening without forwarding it. the system logs 'Connection to
    // speech recognition service lost', and the app hears nothing at all, no results and no
    // error either, so it hangs with the bar open and no way to know why
    private var speechRecognizer: SpeechRecognizer? = null

    // fires if the recognizer never reports itself ready. a silent hang is the worst outcome, the
    // UI sits open forever with no error to show, so past this we treat it as broken and say so
    private var readyWatchdog: Runnable? = null

    private fun createRecognizer(): SpeechRecognizer? {
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) return null
        return SpeechRecognizer.createSpeechRecognizer(appContext).apply {
            setRecognitionListener(listener)
        }
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.i(TAG, "onReadyForSpeech")
            cancelWatchdog()
            listening = true
            mainHandler.post { onListeningStarted() }
        }

        override fun onBeginningOfSpeech() {
            // audio is reaching us, so whatever the recognizer is, it works
            consecutiveErrors = 0
        }

        override fun onRmsChanged(rmsdB: Float) {
            // live mic loudness in dB. drives the waveform so it only moves when the user is actually
            // speaking, rather than on a constant timer
            mainHandler.post { onRms(rmsdB) }
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            mainHandler.post { onListeningFinished() }
        }

        override fun onError(error: Int) {
            Log.w(TAG, "onError: $error")
            cancelWatchdog()
            listening = false
            consecutiveErrors++

            // a dead binding can't be revived by asking it again, it has to be thrown away and rebuilt.
            // without this the helper sits in a start -> ERROR_CLIENT -> start loop forever: the assistant
            // looks alive, the orb even pulses, and it hears nothing until the idle timer closes it.
            // that is the 'it stops working after a while'
            val fatal = error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                consecutiveErrors >= ERRORS_BEFORE_REBUILD
            if (fatal && !destroyed) {
                Log.w(TAG, "Rebuilding recognizer after $consecutiveErrors errors (last=$error)")
                rebuild()
            }

            mainHandler.post {
                onListeningFinished()
                onError(error)
            }
        }

        override fun onResults(results: Bundle?) {
            Log.i(TAG, "onResults: ${results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)}")
            listening = false
            consecutiveErrors = 0
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                mainHandler.post { onResult(text) }
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            Log.i(TAG, "onPartialResults")
            consecutiveErrors = 0
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            if (!matches.isNullOrEmpty()) {
                val text = matches[0]
                mainHandler.post { onPartialResult(text) }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    // throw the current recognizer away and build a fresh one
    private fun rebuild() {
        mainHandler.post {
            if (destroyed) return@post
            listening = false
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            }
            speechRecognizer = runCatching { createRecognizer() }.getOrNull()
            consecutiveErrors = 0
        }
    }

    fun startListening() {
        // any queued restart is redundant now, this call supersedes it. without it several callers
        // each scheduling their own restart would stack up and land on top of a session that had
        // already begun, which the platform answers with ERROR_RECOGNIZER_BUSY, and that error
        // scheduled another restart while the assistant sat there hearing nothing
        cancelPendingRestart()

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            // a language tag ('en-GB', 'az-AZ'). this used to pass the Locale object, which the recognizer
            // can't read, so it silently fell back to the system default: bad news for anyone whose
            // speech isn't in the phone's primary language
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                Locale.getDefault().toLanguageTag(),
            )
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // tolerate natural pauses. the old ~0.85s ended the turn during an ordinary mid-sentence
            // breath, so half-finished sentences went to the model and it answered the wrong question.
            // thinking out loud, 'add a... um... dentist appointment', needs room
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,
                MIN_UTTERANCE_MS.toInt(),
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                END_OF_UTTERANCE_SILENCE_MS.toInt(),
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                POSSIBLY_DONE_SILENCE_MS.toInt(),
            )
        }
        mainHandler.post {
            if (listening || destroyed) return@post

            // throw away whatever we had and bind again. cheap, and the only reliable way to know the
            // connection is alive
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            }
            val recognizer = createRecognizer()?.also { speechRecognizer = it } ?: run {
                Log.w(TAG, "No recognition service available")
                onError(SpeechRecognizer.ERROR_CLIENT)
                return@post
            }

            Log.i(TAG, "startListening -> recognizer=$recognizer")
            listening = true
            runCatching { recognizer.startListening(intent) }
                .onFailure {
                    listening = false
                    Log.w(TAG, "startListening threw", it)
                    onError(SpeechRecognizer.ERROR_CLIENT)
                    return@post
                }

            // nothing came back last time and nothing said so. if the service doesn't announce itself as
            // ready, stop pretending it might
            cancelWatchdog()
            val watchdog = Runnable {
                if (destroyed) return@Runnable
                Log.w(TAG, "No onReadyForSpeech within ${READY_TIMEOUT_MS}ms")
                listening = false
                runCatching { speechRecognizer?.cancel() }
                onError(SpeechRecognizer.ERROR_SERVER_DISCONNECTED)
            }
            readyWatchdog = watchdog
            mainHandler.postDelayed(watchdog, READY_TIMEOUT_MS)
        }
    }

    // schedule a (re)start, replacing any already queued. the single entry point for 'listen again
    // in a moment': callers used to post their own delayed starts from six different places, TTS
    // start, TTS done, TTS error, recognizer error, answer arrival and error recovery, and they
    // collided regularly
    fun restart(delayMs: Long) {
        cancelPendingRestart()
        if (destroyed) return
        val task = Runnable { startListening() }
        pendingRestart = task
        mainHandler.postDelayed(task, delayMs)
    }

    private fun cancelWatchdog() {
        readyWatchdog?.let { mainHandler.removeCallbacks(it) }
        readyWatchdog = null
    }

    private fun cancelPendingRestart() {
        pendingRestart?.let { mainHandler.removeCallbacks(it) }
        pendingRestart = null
    }

    fun stopListening() {
        cancelPendingRestart()
        mainHandler.post {
            listening = false
            speechRecognizer?.stopListening()
        }
    }

    // stop and stay stopped, used while the assistant is talking
    fun cancel() {
        cancelPendingRestart()
        mainHandler.post {
            listening = false
            runCatching { speechRecognizer?.cancel() }
        }
    }

    fun destroy() {
        cancelPendingRestart()
        cancelWatchdog()
        destroyed = true
        mainHandler.post {
            listening = false
            runCatching {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
            }
            speechRecognizer = null
        }
    }

    // asks the platform rather than reporting whether we hold an instance, we keep none between sessions
    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    companion object {
        private const val TAG = "SpeechRecognition"

        // errors in a row before we stop trusting the recognizer and rebuild it. three tolerates a
        // couple of ordinary no-match results without churning
        private const val ERRORS_BEFORE_REBUILD = 3

        // how long to wait for the service to say it's listening before giving up
        private const val READY_TIMEOUT_MS = 4_000L

        // don't call it a turn before this much audio, however quiet it was
        const val MIN_UTTERANCE_MS = 1_500L

        // silence that ends the turn. generous on purpose: a pause this long is a person who has
        // finished, not one drawing breath
        const val END_OF_UTTERANCE_SILENCE_MS = 2_500L

        // earliest point the recognizer may treat a pause as a likely ending
        const val POSSIBLY_DONE_SILENCE_MS = 1_800L
    }
}
