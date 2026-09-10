package com.muradgalayev.brainbuddy.ui.ai

import android.speech.tts.Voice
import java.util.Locale

data class AiVoiceProfile(
    val name: String,
    val description: String,
    val pitch: Float,
    val rate: Float,
)

val MyndoraVoiceProfiles = listOf(
    AiVoiceProfile("Noah", "Calm, grounded voice", pitch = 1.00f, rate = .97f),
    AiVoiceProfile("Milo", "Warm, conversational voice", pitch = 1.00f, rate = 1.00f),
    AiVoiceProfile("Luna", "Gentle, reassuring voice", pitch = 1.00f, rate = .98f),
    AiVoiceProfile("Nova", "Bright, energetic voice", pitch = 1.00f, rate = 1.03f),
)

fun curatedAiVoices(voices: Set<Voice>?, locale: Locale = Locale.getDefault()): List<Voice> =
    voices.orEmpty()
        .filter { it.locale.language == locale.language }
        .distinctBy { it.name }
        // neural and network voices generally sound much more human, so quality is the primary signal.
        // prefer an installed voice at equal quality, so opening the assistant and starting speech
        // never waits for a network voice
        .sortedWith(
            compareByDescending<Voice> { it.quality }
                .thenBy { it.isNetworkConnectionRequired }
                .thenBy { it.latency }
                .thenBy { it.name },
        )
        .take(MyndoraVoiceProfiles.size)

fun aiVoiceProfile(selectedVoiceName: String?, voices: List<Voice>): AiVoiceProfile {
    val index = voices.indexOfFirst { it.name == selectedVoiceName }.coerceAtLeast(0)
    return MyndoraVoiceProfiles[index.coerceAtMost(MyndoraVoiceProfiles.lastIndex)]
}

fun String.toMyndoraSpeech(): String = this
    .replace(Regex("```[\\s\\S]*?```"), "")
    .replace(Regex("[*_#>`]"), "")
    .replace(Regex("\\[([^]]+)]\\([^)]+\\)"), "$1")
    .replace(Regex("\\s+"), " ")
    .trim()
    .withoutVisualSymbols()

// removes emoji and UI-only symbols so TTS never says 'smiling face' aloud
private fun String.withoutVisualSymbols(): String {
    val clean = StringBuilder(length)
    codePoints().forEach { codePoint ->
        val type = Character.getType(codePoint)
        val visualOnly = type == Character.OTHER_SYMBOL.toInt() ||
            codePoint == 0xFE0F || // emoji variation selector
            codePoint == 0x200D || // zero-width emoji joiner
            codePoint in 0x1F3FB..0x1F3FF // skin-tone modifiers
        if (!visualOnly) clean.appendCodePoint(codePoint)
    }
    return clean.toString().replace(Regex("\\s+"), " ").trim()
}
