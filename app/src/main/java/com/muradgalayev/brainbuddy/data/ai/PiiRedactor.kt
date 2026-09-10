package com.muradgalayev.brainbuddy.data.ai

import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

// strips the user's direct identifiers out of anything on its way to a third-party model.
// paired with the backend-issued pseudonym, this is what makes the AI calls GDPR-pseudonymous:
// the provider gets the user's ADHD context, but nothing naming the person it belongs to.
// two passes, deliberately different in aggressiveness:
// 1. known identifiers, the signed-in user's own name, username, email and phone, matched
//    literally. zero false positives, so this runs over everything: message text, tool
//    arguments and tool results alike.
// 2. generic email and phone patterns, for identifiers we can't know in advance, like a
//    friend's number typed into chat. runs over the user's own typed text only. it must not
//    touch tool results, because find_care_nearby returns clinic phone numbers and contact
//    needs those verbatim to open the dialler. clinic details are business data, not personal.
// redaction applies only to the copy sent upstream. local and Supabase history keep the real
// text, so the chat UI still shows the user what they actually wrote
@Singleton
class PiiRedactor @Inject constructor(
    private val authRepository: AuthRepository,
) {

    // replaces the user's own identifiers. safe on any string
    fun redactKnownIdentifiers(text: String): String {
        if (text.isBlank()) return text
        var out = text
        for ((pattern, replacement) in knownIdentifierPatterns()) {
            out = pattern.replace(out, replacement)
        }
        return out
    }

    // redactKnownIdentifiers plus generic contact-detail patterns. user text only
    fun redactUserText(text: String): String {
        if (text.isBlank()) return text
        var out = redactKnownIdentifiers(text)
        out = EMAIL_PATTERN.replace(out, EMAIL_TOKEN)
        out = PHONE_PATTERN.replace(out, PHONE_TOKEN)
        return out
    }

    // walks a tool-argument object and redacts every string leaf
    fun redactJson(element: JsonObject): JsonObject =
        JsonObject(element.mapValues { (_, value) -> redactElement(value) })

    private fun redactElement(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> redactJson(element)
        is JsonArray -> JsonArray(element.map { redactElement(it) })
        is JsonPrimitive ->
            if (element.isString) JsonPrimitive(redactKnownIdentifiers(element.content))
            else element
    }

    // built fresh per call: all sources are in-memory reads, and the user can rename themselves
    // mid-conversation through the update_profile tool, at which point the new name has to start
    // being redacted immediately
    private fun knownIdentifierPatterns(): List<Pair<Regex, String>> {
        val profile = authRepository.peekProfile()
        val patterns = mutableListOf<Pair<Regex, String>>()

        // email first: it contains the local part, which is often the name or the username, and
        // redacting it whole beats leaking it through a later pass
        val emails = listOfNotNull(profile?.email, authRepository.getCurrentUserEmail())
        emails.distinct().forEach { email ->
            literalPattern(email)?.let { patterns += it to EMAIL_TOKEN }
        }

        val phone = authRepository.getCurrentUserPhone()
        phone?.let { raw ->
            digitFlexiblePattern(raw)?.let { patterns += it to PHONE_TOKEN }
        }

        val usernames = listOfNotNull(profile?.username, authRepository.getCurrentUserUsername())
        usernames.distinct().forEach { username ->
            literalPattern(username)?.let { patterns += it to USERNAME_TOKEN }
        }

        // full names before their parts, so 'Ada Lovelace' collapses to one token instead of two
        val fullNames = listOfNotNull(profile?.displayName, authRepository.getCurrentUserFullName())
            .distinct()
        fullNames.sortedByDescending { it.length }.forEach { name ->
            literalPattern(name)?.let { patterns += it to NAME_TOKEN }
        }
        fullNames.flatMap { it.split(WHITESPACE) }.distinct().forEach { part ->
            literalPattern(part)?.let { patterns += it to NAME_TOKEN }
        }

        return patterns
    }

    // case-insensitive whole-word match. the boundaries are unicode letter/number lookarounds
    // rather than a word-boundary escape, which is ASCII-only in Java regex and would mis-handle
    // non-English names. terms shorter than MIN_TERM_LENGTH are skipped: a two-letter name or
    // handle would shred ordinary words out of the conversation for no privacy gain
    private fun literalPattern(term: String): Regex? {
        val trimmed = term.trim()
        if (trimmed.length < MIN_TERM_LENGTH) return null
        return Regex(
            "(?<![\\p{L}\\p{N}])${Regex.escape(trimmed)}(?![\\p{L}\\p{N}])",
            RegexOption.IGNORE_CASE,
        )
    }

    // matches a stored phone number however it was typed: +994551234567 stored, +994 55 123 45 67
    // typed. built from the digits alone, allowing any separator between them
    private fun digitFlexiblePattern(raw: String): Regex? {
        val digits = raw.filter { it.isDigit() }
        if (digits.length < MIN_PHONE_DIGITS) return null
        // digits need no escaping, and the boundaries stop a stored 7-digit number from matching the
        // middle of some longer unrelated figure
        val body = digits.toCharArray().joinToString(SEPARATOR)
        return Regex("(?<![\\p{N}])\\+?$SEPARATOR$body(?![\\p{N}])")
    }

    // internal rather than private so PiiRedactorTest asserts the real patterns
    internal companion object {
        const val MIN_TERM_LENGTH = 3
        const val MIN_PHONE_DIGITS = 7

        const val NAME_TOKEN = "[user's name withheld]"
        const val USERNAME_TOKEN = "[user's username withheld]"
        const val EMAIL_TOKEN = "[email withheld]"
        const val PHONE_TOKEN = "[phone withheld]"

        // optional punctuation or whitespace allowed between phone digits
        const val SEPARATOR = "[\\s\\-().]*"

        val WHITESPACE = Regex("\\s+")

        val EMAIL_PATTERN = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")

        // deliberately conservative. a permissive phone pattern eats 2026-08-07, and dates are
        // load-bearing for every calendar tool call, so each branch requires either an international
        // prefix, space or dot separators, or a run of digits too long to be a date. dash-separated
        // 4-2-2, an ISO date, matches none of them
        val PHONE_PATTERN = Regex(
            "(?<![\\p{N}])(?:" +
                "(?:\\+|00)\\p{N}[\\p{N}\\s().\\-]{6,}\\p{N}" +
                "|\\p{N}{3}[\\s.]\\p{N}{3}[\\s.]\\p{N}{3,4}" +
                "|\\p{N}{9,}" +
                ")(?![\\p{N}])"
        )
    }
}
