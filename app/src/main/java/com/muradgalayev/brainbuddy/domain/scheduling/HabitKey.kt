package com.muradgalayev.brainbuddy.domain.scheduling

import java.util.Locale

// builds the keys the learned-timing table is indexed by. two levels, and both matter:
// - title:dinner is the specific habit, and what makes 'last time I had dinner it was 21:00'
//   actually change the next suggestion.
// - kind:dinner is the family. it carries evidence across titles the user phrases differently
//   ('dinner', 'supper', 'eat with mum'), so the pattern still forms when no single wording
//   repeats often enough.
// titles are reduced hard before becoming a key: case, punctuation, filler verbs and everything
// after 'with' or 'at' are dropped. without that, 'dinner', 'Dinner' and 'dinner with Leyla'
// would be three unrelated habits that each learn nothing
object HabitKey {

    // every event the user has ever scheduled, pooled into one row. not a habit, a baseline: it
    // answers 'when does this person put things on their calendar at all', which is the only thing
    // we can say about a title we don't recognise. a hardcoded fallback can't know that one user's
    // day runs 06:00-15:00 and another's 11:00-23:00, this can. used deliberately weakly
    const val ALL_EVENTS: String = "all:events"

    fun forTitle(title: String): String? {
        val core = normalizeTitle(title) ?: return null
        return "title:$core"
    }

    fun forKind(kind: ActivityKind): String = "kind:${kind.key}"

    // the comparable core of a title, or null when nothing meaningful survives: an event called
    // '...' teaches us nothing and must not create a row
    fun normalizeTitle(title: String): String? {
        val tokens = title
            .lowercase(Locale.ROOT)
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotBlank() }

        val meaningful = mutableListOf<String>()
        for (token in tokens) {
            // everything past a preposition is a person, place or detail, and the habit is the same
            // whoever you're having dinner with
            if (token in TRAILING_STOPWORDS) break
            if (token in FILLER) continue
            if (token.length < 2) continue
            meaningful += token
        }
        if (meaningful.isEmpty()) return null
        // two tokens is enough to separate 'morning run' from 'errand run' without splintering into
        // one key per phrasing
        return meaningful.take(2).joinToString(" ")
    }

    // words that end the meaningful part of a title
    private val TRAILING_STOPWORDS = setOf(
        "with", "at", "in", "on", "for", "to", "from", "about", "re", "via", "and",
    )

    // words that carry no timing information wherever they appear
    private val FILLER = setOf(
        "the", "a", "an", "my", "our", "some", "quick", "little", "bit", "of",
        "go", "going", "grab", "get", "getting", "do", "doing", "have", "having",
        "take", "taking", "make", "making", "attend", "session", "time", "day",
        "today", "tomorrow", "tonight", "again", "new", "please", "asap",
    )
}
