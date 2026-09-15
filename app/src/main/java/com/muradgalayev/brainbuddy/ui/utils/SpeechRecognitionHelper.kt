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
import com.muradgalayev.brainbuddy.data.local.AppLocale

// speech input, deliberately plain.
//
// this was rewritten twice into something clever — a process-wide arbiter, session tokens, ready
// and result watchdogs, a rebuild-on-error policy, a ladder of language tags — and each layer
// added a new way to end up listening to nothing. the version that actually worked on a real
// phone was this small: build one recognizer, keep it, start it when asked. so that is what this
// is again, and every addition since has been left out unless it fixed a fault seen on a device.
//
// two rules for anything added here later:
//   • never keep a flag that can refuse a start. if it latches, the microphone is dead for the
//     rest of the app's life and the screen has no way to say so
//   • never cancel a session on a timer. the recognizer is allowed to take its time, and a
//     cancel racing a result loses the result
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

    private var pendingRestart: Runnable? = null
    private var destroyed = false

    // the most recent partial transcript of the turn in progress. some engines — Google's SODA in
    // its continuous mode is one — stream the words through onPartialResults and then hand back a
    // final bundle with no RESULTS_RECOGNITION in it at all. the words were heard correctly; there
    // is simply nothing in the envelope. without this the turn is lost and the assistant looks like
    // it ignored you, which is exactly how it looked
    private var lastPartial: String = ""

    // set once the recognizer says it has no model for the language we named. one retry without a
    // language, then we live with whatever it picks — better a wrong model than no microphone
    private var languageRejected = false

    // built once and kept, which is what the working version did. rebuilding per session is what
    // introduced the bind/unbind race that made the second attempt fail
    private var recognizer: SpeechRecognizer? = null

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.i(TAG, "onReadyForSpeech")
            onListeningStarted()
        }

        override fun onBeginningOfSpeech() {
            Log.i(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) {
            onRms(rmsdB)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.i(TAG, "onEndOfSpeech")
            onListeningFinished()
        }

        override fun onError(error: Int) {
            Log.w(TAG, "onError: $error")
            if (destroyed) return
            lastPartial = ""
            if (!languageRejected &&
                (error == ERROR_LANGUAGE_NOT_SUPPORTED || error == ERROR_LANGUAGE_UNAVAILABLE)
            ) {
                Log.w(TAG, "No model for ${languageTag()}, retrying with the phone's own")
                languageRejected = true
                restart(120L)
                return
            }
            onListeningFinished()
            this@SpeechRecognitionHelper.onError(error)
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            Log.i(TAG, "onResults: $matches (keys=${results?.keySet()})")
            if (destroyed) return

            val heard = matches?.firstOrNull()?.takeIf { it.isNotBlank() }
                // an empty envelope doesn't mean nothing was said. the partials are the same
                // transcript, just delivered as it was being recognised
                ?: lastPartial.takeIf { it.isNotBlank() }
                    ?.also { Log.i(TAG, "empty final, using the last partial instead") }

            lastPartial = ""
            if (heard != null) {
                onResult(heard)
            } else {
                // genuinely nothing. tell the caller so it can restart rather than sit there
                Log.w(TAG, "no transcript in the final result and no partial to fall back on")
                onListeningFinished()
                this@SpeechRecognitionHelper.onError(SpeechRecognizer.ERROR_NO_MATCH)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (destroyed) return
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return
            Log.i(TAG, "onPartialResults: $text")
            lastPartial = text
            onPartialResult(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    fun startListening() {
        // any queued restart is redundant now, this call supersedes it
        cancelPendingRestart()
        if (destroyed) return
        mainHandler.post {
            if (destroyed) return@post

            // only one recognition session may run in a process. whoever starts last wins, and the
            // previous holder is stopped rather than left bidding for the same microphone
            HOLDER?.takeIf { it !== this }?.let { previous ->
                runCatching { previous.recognizer?.cancel() }
            }
            HOLDER = this

            val engine = recognizer
                ?: runCatching { SpeechRecognizer.createSpeechRecognizer(appContext) }
                    .onFailure { Log.w(TAG, "createSpeechRecognizer threw", it) }
                    .getOrNull()
                    ?.also {
                        it.setRecognitionListener(listener)
                        recognizer = it
                    }
                ?: run {
                    onError(SpeechRecognizer.ERROR_CLIENT)
                    return@post
                }

            // cancel first: harmless when idle, and the one thing that reliably clears a previous
            // turn of this same recognizer
            runCatching { engine.cancel() }

            lastPartial = ""
            Log.i(TAG, "startListening")
            runCatching { engine.startListening(intent()) }
                .onFailure {
                    Log.w(TAG, "startListening threw", it)
                    onError(SpeechRecognizer.ERROR_CLIENT)
                }
        }
    }

    // schedule a (re)start, replacing any already queued. the single entry point for 'listen again
    // in a moment': callers used to post their own delayed starts from six different places, TTS
    // start, TTS done, TTS error, recognizer error, answer arrival and error recovery, and they
    // collided regularly
    // `gate` is checked when the restart FIRES, not when it is scheduled. that distinction is the
    // whole point: a turn ending schedules the next listen, and the assistant starting to speak
    // happens moments later, so a decision made at scheduling time is made before the fact that
    // matters exists. reopening the microphone into the assistant's own speech puts the recognizer
    // behind an exclusive audio focus where it hears nothing, for every turn after, forever
    fun restart(delayMs: Long, gate: () -> Boolean = { true }) {
        cancelPendingRestart()
        if (destroyed) return
        val task = Runnable {
            if (gate()) startListening() else Log.i(TAG, "restart skipped, the gate is closed")
        }
        pendingRestart = task
        mainHandler.postDelayed(task, delayMs)
    }

    // throws the recognizer away so the next start builds a fresh one. only for the case the logs
    // actually showed: a session that opens the microphone and then hears nothing, turn after turn,
    // because its audio session was wedged by something else holding focus. rebuilding per start is
    // what caused the bind race, rebuilding after a wedge is how you get out of one
    fun resetEngine() {
        mainHandler.post {
            Log.w(TAG, "resetting the recognizer")
            runCatching {
                recognizer?.cancel()
                recognizer?.destroy()
            }
            recognizer = null
            lastPartial = ""
        }
    }

    // end the turn and take whatever was heard
    fun stopListening() {
        cancelPendingRestart()
        mainHandler.post { runCatching { recognizer?.stopListening() } }
    }

    // stop and stay stopped, used while the assistant is talking
    fun cancel() {
        cancelPendingRestart()
        mainHandler.post { runCatching { recognizer?.cancel() } }
    }

    fun destroy() {
        cancelPendingRestart()
        destroyed = true
        mainHandler.post {
            if (HOLDER === this) HOLDER = null
            runCatching {
                recognizer?.cancel()
                recognizer?.destroy()
            }
            recognizer = null
        }
    }

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(appContext)

    private fun cancelPendingRestart() {
        pendingRestart?.let { mainHandler.removeCallbacks(it) }
        pendingRestart = null
    }

    // the app's language, region-qualified, because the recognizer wants 'en-US' and not 'en'.
    //
    // this has to be named. leaving it out hands the choice to the phone's voice-input default,
    // which is not the language the app is in and not necessarily one the user even speaks: on the
    // device this was debugged on it was ru-RU, so English speech went into a Russian model and
    // came back as NO_SPEECH_DETECTED every single turn. the assistant then looked like it was
    // ignoring the user, because a turn that never produces a result never reaches the model
    private fun languageTag(): String = AppLocale.voiceLocale(appContext).toLanguageTag()

    private fun intent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            if (!languageRejected) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag())
            }
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            // tolerate natural pauses. the platform default ends the turn during an ordinary
            // mid-sentence breath, so half-finished sentences went to the model
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                END_OF_UTTERANCE_SILENCE_MS.toInt(),
            )
        }

    companion object {
        private const val TAG = "SpeechRecognition"

        // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / _UNAVAILABLE, added in API 33. spelled
        // out so this compiles and behaves the same on the API 26 floor
        private const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        private const val ERROR_LANGUAGE_UNAVAILABLE = 13

        // whoever is listening right now, if anyone. plain and process-wide, because the platform
        // limit it enforces is plain and process-wide
        private var HOLDER: SpeechRecognitionHelper? = null

        // silence that ends the turn, and the single biggest source of lag in a spoken exchange:
        // it is dead time between the last word and the answer. 2500 was chosen when an ended turn
        // meant a lost one, because the engine here returns an empty final bundle and everything
        // depended on the timing being right. now that the last partial is kept, ending a touch
        // early costs nothing — whatever was said by then is what gets sent. 850 was measurably
        // too short, it cut people off mid-breath; this is the middle
        const val END_OF_UTTERANCE_SILENCE_MS = 1_400L
    }
}
