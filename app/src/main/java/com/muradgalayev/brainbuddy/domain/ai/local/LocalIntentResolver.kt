package com.muradgalayev.brainbuddy.domain.ai.local

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// a command the app understood by itself, with the reply to show for it. reply is written here
// rather than generated because the whole point is that no model ran. it has to sound like the
// rest of the assistant, short and warm, or the shortcut announces itself as a lesser path
data class LocalIntent(
    val toolName: String,
    val args: JsonObject,
    val reply: String,
)

// answers the handful of commands that never needed a model. 'make the font opendyslexic' is
// a lookup, not a language problem, and sending it to Mistral costs ~8,700 tokens of profile,
// rules and tool schemas to move one enum. worse, it ships the user's medication list to a
// third party to change a typeface. this resolves those locally: no tokens, no network, no
// quota, no data leaving the device, and an answer that lands instantly.
// the design constraint is false positives, not coverage. a missed match costs a few
// hundredths of a cent and the user still gets the right answer. a wrong match silently does
// something they didn't ask for, which is the exact failure that makes people stop trusting
// the assistant. so every rule here is narrow, and anything ambiguous goes to the model:
// - questions are never matched. 'how do i change the font' is a request for help, and
//   answering it by silently changing the font is the worst possible reading.
// - long sentences are never matched. multi-clause input means more is being asked than one
//   tool can do.
// - nothing carrying free text is matched. todos and events have titles, dates and ambiguity,
//   and those are what the model is for
@Singleton
class LocalIntentResolver @Inject constructor() {

    fun resolve(rawText: String): LocalIntent? {
        val text = normalise(rawText)
        if (text.isBlank()) return null

        // a question wants an answer, not an action
        if (rawText.contains('?')) return null
        if (QUESTION_OPENERS.any { text == it || text.startsWith("$it ") }) return null

        // more than a short phrase is more than one instruction
        if (text.split(' ').size > MAX_WORDS) return null

        return resolveFont(text)
            ?: resolveTheme(text)
            ?: resolveFontSize(text)
            ?: resolveSpacing(text)
    }

    // font family. the typeface names are distinctive enough to stand on their own, nobody writes
    // 'opendyslexic' meaning anything else. 'arial' is the weakest of the three, so it needs a
    // verb alongside it
    private fun resolveFont(text: String): LocalIntent? {
        val font = when {
            text.containsAny("opendyslexic", "open dyslexic", "dyslexic font", "dyslexia font") ->
                "OpenDyslexic"
            text.containsAny("atkinson", "hyperlegible") -> "Atkinson"
            text.containsAny("arial", "arimo") && text.hasChangeVerb() -> "Arial"
            else -> return null
        }
        return LocalIntent(
            toolName = "update_appearance",
            args = buildJsonObject { put("font", font) },
            reply = when (font) {
                "OpenDyslexic" -> "Switched to OpenDyslexic 👍"
                "Atkinson" -> "Switched to Atkinson Hyperlegible 👍"
                else -> "Switched to Arial 👍"
            },
        )
    }

    // theme. 'mode' and 'theme' are required alongside the colour word: without them, 'dark'
    // alone matches 'the room is dark' and every other passing use
    private fun resolveTheme(text: String): LocalIntent? {
        if (!text.containsAny("mode", "theme")) return null
        val theme = when {
            text.contains("dark") -> "Dark"
            text.contains("light") -> "Light"
            text.containsAny("system", "automatic", "auto") -> "System"
            else -> return null
        }
        return LocalIntent(
            toolName = "update_appearance",
            args = buildJsonObject { put("theme", theme) },
            reply = when (theme) {
                "Dark" -> "Dark mode on 🌙"
                "Light" -> "Light mode on ☀️"
                else -> "Following your system theme now 👍"
            },
        )
    }

    // text size
    private fun resolveFontSize(text: String): LocalIntent? {
        if (!text.containsAny("text", "font", "letters", "writing")) return null
        val size = when {
            text.containsAny("bigger", "larger", "big", "large", "increase") -> "Large"
            text.containsAny("smaller", "small", "decrease", "tiny") -> "Small"
            text.containsAny("normal size", "medium", "default size") -> "Medium"
            else -> return null
        }
        // 'bigger spacing' is about air, not size, so let the spacing rule take it
        if (text.containsAny("spacing", "space", "gap")) return null

        return LocalIntent(
            toolName = "update_appearance",
            args = buildJsonObject { put("font_size", size) },
            reply = when (size) {
                "Large" -> "Text is bigger now 👍"
                "Small" -> "Text is smaller now 👍"
                else -> "Back to the normal text size 👍"
            },
        )
    }

    // spacing
    private fun resolveSpacing(text: String): LocalIntent? {
        if (!text.containsAny("spacing", "space between", "line height")) return null
        val spacing = when {
            text.containsAny("loose", "loosest", "most", "maximum") -> "Loose"
            text.containsAny("more", "bigger", "wider", "increase", "relaxed") -> "Relaxed"
            text.containsAny("less", "normal", "tighter", "default", "reset") -> "Normal"
            else -> return null
        }
        return LocalIntent(
            toolName = "update_appearance",
            args = buildJsonObject { put("text_spacing", spacing) },
            reply = when (spacing) {
                "Loose" -> "Spacing is at its loosest now 👍"
                "Relaxed" -> "Gave the text more room 👍"
                else -> "Back to normal spacing 👍"
            },
        )
    }

    private fun normalise(raw: String): String = raw
        .lowercase(Locale.ROOT)
        .map { if (it.isLetterOrDigit() || it == ' ') it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ")

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { this.contains(it) }

    private fun String.hasChangeVerb(): Boolean =
        containsAny("change", "set", "use", "switch", "make", "turn")

    private companion object {
        // six words covers 'change the font to opendyslexic' with room to spare, and stops well short
        // of anything with a second clause in it
        const val MAX_WORDS = 7

        val QUESTION_OPENERS = setOf(
            "how", "what", "why", "when", "where", "which", "who",
            "can", "could", "should", "is", "are", "do", "does", "did",
        )
    }
}
