package com.muradgalayev.brainbuddy.data.local

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ReadAloud"

// speaks the label of whatever the user just tapped, when 'Speak what I tap' is on. one engine
// for the whole app, created lazily the first time the setting is turned on and torn down when
// it's turned off. the screens that already do text-to-speech each build their own short-lived
// engine for a single utterance; this one has to outlive every screen, because taps happen
// everywhere and engine startup is far too slow to do per tap
@Singleton
class ReadAloudSpeaker @Inject constructor(
    @ApplicationContext private val context: Context,
    preferencesManager: PreferencesManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var engine: TextToSpeech? = null
    private val engineReady = AtomicBoolean(false)

    @Volatile
    var enabled: Boolean = false
        private set

    // ignore a repeat of the same label in quick succession. Compose can deliver a click to a row
    // and to something it contains, and a double tap on a toggle is normal use, so neither should
    // stutter the same words twice
    private var lastSpoken: String? = null
    private var lastSpokenAt = 0L

    init {
        scope.launch {
            preferencesManager.readAloudTaps.distinctUntilChanged().collect { on ->
                enabled = on
                if (on) ensureEngine() else shutdown()
            }
        }
    }

    // say the text. no-op unless the setting is on, so callers can wire this in unconditionally
    // without checking anything first
    fun speak(text: String?) {
        if (!enabled) return
        val trimmed = text?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val now = System.currentTimeMillis()
        if (trimmed == lastSpoken && now - lastSpokenAt < REPEAT_WINDOW_MS) return
        lastSpoken = trimmed
        lastSpokenAt = now

        val active = engine
        if (active == null || !engineReady.get()) {
            // the setting was just switched on and the engine is still starting. dropping this one
            // utterance beats queueing it to arrive seconds later, attached to a screen already left
            ensureEngine()
            return
        }
        // QUEUE_FLUSH: the newest tap is the only one worth hearing, and queueing would make the voice
        // run further and further behind a user moving quickly
        active.speak(trimmed, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    // stop mid-word, e.g. when a screen is being left behind
    fun stop() {
        runCatching { engine?.stop() }
    }

    private fun ensureEngine() {
        if (engine != null) return
        engineReady.set(false)
        engine = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = runCatching { engine?.setLanguage(Locale.getDefault()) }.getOrNull()
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                    result == TextToSpeech.LANG_NOT_SUPPORTED
                ) {
                    // fall back rather than fail: a device without the local voice can still read English aloud
                    runCatching { engine?.setLanguage(Locale.US) }
                }
                engineReady.set(true)
            } else {
                Log.w(TAG, "Text-to-speech unavailable (status=$status)")
                shutdown()
            }
        }
    }

    private fun shutdown() {
        engineReady.set(false)
        val current = engine ?: return
        engine = null
        runCatching {
            current.stop()
            current.shutdown()
        }
    }

    private companion object {
        const val UTTERANCE_ID = "read-aloud-tap"
        const val REPEAT_WINDOW_MS = 700L
    }
}
