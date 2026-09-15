package com.muradgalayev.brainbuddy.domain.scheduling

import java.util.Locale

// turns 'grab dinner with Leyla' into ActivityKind.Dinner. a lexicon, not a model: the whole
// vocabulary of a personal calendar is a few hundred words, and a lookup table gets them right
// instantly, offline and for free, where a small language model would cost hundreds of
// megabytes of APK, a cold-start delay on every keystroke, and worse accuracy on exactly the
// short fragmentary titles people actually type.
// matching is longest-phrase-wins, so 'coffee with mum' reads as a catch-up rather than a
// snack, and multi-word phrases beat the single words inside them
object ActivityClassifier {

    // what the classifier concluded, and how much the caller should trust it
    data class Result(
        val kind: ActivityKind,
        // 0f when nothing matched and we fell back to General
        val confidence: Float,
        // the phrase that decided it, for debugging and for the why text
        val matchedPhrase: String? = null,
    )

    fun classify(title: String, description: String = ""): Result {
        // the title carries the intent and the description is a weak tie-breaker, so it's only
        // consulted when the title says nothing at all
        classifyText(title)?.let { return it }
        classifyText(description)?.let { return it.copy(confidence = it.confidence * 0.6f) }
        return Result(ActivityKind.General, 0f)
    }

    private fun classifyText(text: String): Result? {
        val normalized = normalize(text)
        if (normalized.isBlank()) return null

        val best = LEXICON
            .filter { (phrase, _) -> normalized.containsWord(phrase) }
            .maxByOrNull { (phrase, _) -> phrase.length }
            ?: return null

        val (phrase, kind) = best
        // longer phrases are far less likely to be coincidence. a 3-letter hit like 'gym' is still
        // good, 'grocery shopping' is as good as it gets
        val confidence = when {
            phrase.contains(' ') -> 1.0f
            phrase.length >= 5 -> 0.9f
            else -> 0.75f
        }
        return Result(kind, confidence, phrase)
    }

    // lowercases, strips punctuation and collapses whitespace, then pads with spaces so
    // containsWord can match on word boundaries without a regex per lookup. this runs on every
    // keystroke
    private fun normalize(text: String): String {
        val cleaned = buildString(text.length + 2) {
            append(' ')
            for (ch in text.lowercase(Locale.ROOT)) {
                when {
                    ch.isLetterOrDigit() -> append(ch)
                    // keep intra-word marks from fusing two words together
                    else -> if (isNotEmpty() && last() != ' ') append(' ')
                }
            }
            append(' ')
        }
        return if (cleaned.isBlank()) "" else cleaned
    }

    // `this` is already space-padded and normalized, phrase is a lexicon entry
    private fun String.containsWord(phrase: String): Boolean = contains(" $phrase ")

    // phrase to kind. order is irrelevant since longest match wins, so entries are grouped by
    // kind for readability. everything here is lowercase, unpunctuated and single-spaced, to
    // match what normalize() produces
    private val LEXICON: List<Pair<String, ActivityKind>> = buildList {
        fun add(kind: ActivityKind, vararg phrases: String) {
            phrases.forEach { add(it to kind) }
        }

        add(ActivityKind.Breakfast, "breakfast", "brekkie", "morning coffee", "brunch")
        add(ActivityKind.Lunch, "lunch", "luncheon", "lunch break")
        add(
            ActivityKind.Dinner,
            "dinner", "supper", "evening meal", "cook dinner", "make dinner",
        )
        add(
            ActivityKind.Snack,
            "snack", "coffee", "tea", "coffee break", "smoothie", "afternoon tea",
        )

        add(
            ActivityKind.Workout,
            "gym", "workout", "work out", "training", "lift", "weights", "crossfit",
            "run", "running", "jog", "swim", "swimming", "cycling", "spin class",
            "yoga", "pilates", "hiit", "cardio", "football", "basketball", "tennis",
            "boxing", "climbing", "leg day", "push day", "pull day",
        )
        add(ActivityKind.Walk, "walk", "walking", "stroll", "dog walk", "hike", "fresh air")

        add(
            ActivityKind.DeepWork,
            "deep work", "focus", "focus block", "coding", "code", "write", "writing",
            "design", "research", "thesis", "dissertation", "essay", "project work",
            "build", "debug", "review code",
        )
        add(
            ActivityKind.Study,
            "study", "studying", "revision", "revise", "homework", "assignment",
            "lecture", "class", "seminar", "exam prep", "reading", "flashcards",
            "practice", "course", "tutorial",
        )
        add(
            ActivityKind.Meeting,
            "meeting", "standup", "stand up", "sync", "call", "1 1", "one on one",
            "interview", "demo", "retro", "retrospective", "presentation", "zoom",
            "catch up call", "team meeting", "client call", "check in",
        )
        add(
            ActivityKind.Errand,
            "errand", "errands", "shopping", "groceries", "grocery", "grocery run",
            "grocery shopping", "bank", "post office", "pharmacy", "pick up",
            "drop off", "haircut", "barber", "car service", "mot",
        )
        add(
            ActivityKind.Chore,
            "laundry", "dishes", "clean", "cleaning", "tidy", "tidy up", "vacuum",
            "hoover", "chores", "bins", "washing", "ironing", "meal prep",
            "wash up", "declutter",
        )

        add(
            ActivityKind.Appointment,
            "appointment", "doctor", "dentist", "gp", "clinic", "therapy",
            "therapist", "counselling", "counseling", "psychiatrist", "checkup",
            "check up", "physio", "physiotherapy", "vet", "optician", "blood test",
            "scan", "consultation",
        )
        add(
            ActivityKind.Medication,
            "medication", "meds", "pill", "pills", "tablet", "dose", "vitamins",
            "supplement", "supplements", "take meds", "prescription",
        )

        add(
            ActivityKind.Social,
            "party", "birthday", "drinks", "pub", "bar", "hang out", "hangout",
            "catch up", "meet up", "meetup", "date", "date night", "visit",
            "family dinner", "wedding", "friends",
        )
        add(
            ActivityKind.Leisure,
            "movie", "film", "cinema", "tv", "netflix", "game", "gaming", "series",
            "podcast", "music", "concert", "gig", "book", "hobby", "relax", "chill",
            "rest", "break",
        )
        add(
            ActivityKind.Commute,
            "commute", "drive", "driving", "travel", "train", "bus", "flight",
            "airport", "journey", "school run", "pick up kids",
        )
        add(
            ActivityKind.SelfCare,
            "shower", "bath", "skincare", "meditate", "meditation", "breathing",
            "journal", "journaling", "stretch", "stretching", "self care",
            "grooming", "nap",
        )
        add(
            ActivityKind.WindDown,
            "wind down", "winddown", "bedtime", "bed", "sleep", "night routine",
            "lights out", "read before bed",
        )

        // Italian, for people who write their calendar in it. same rules: lowercase, single-spaced,
        // and both spellings where a phone keyboard commonly drops the accent
        add(ActivityKind.Breakfast, "colazione", "prima colazione", "caffè del mattino", "caffe del mattino")
        add(ActivityKind.Lunch, "pranzo", "pausa pranzo")
        add(ActivityKind.Dinner, "cena", "cenare", "preparare la cena", "cucinare")
        add(ActivityKind.Snack, "merenda", "spuntino", "caffè", "caffe", "pausa caffè", "pausa caffe", "aperitivo")
        add(
            ActivityKind.Workout,
            "palestra", "allenamento", "allenarsi", "corsa", "correre", "nuoto", "piscina", "calcetto",
            "calcio", "tennis", "pilates", "spinning", "bici", "ciclismo",
        )
        add(ActivityKind.Walk, "passeggiata", "camminata", "portare fuori il cane", "giro col cane", "escursione")
        add(
            ActivityKind.DeepWork,
            "lavoro concentrato", "concentrazione", "scrivere", "progetto", "tesi", "programmare",
            "relazione", "presentazione",
        )
        add(
            ActivityKind.Study,
            "studio", "studiare", "lezione", "università", "universita", "esame", "ripasso", "compiti",
            "corso",
        )
        add(
            ActivityKind.Meeting,
            "riunione", "incontro di lavoro", "call", "chiamata", "videochiamata", "colloquio",
            "meeting con il cliente",
        )
        add(
            ActivityKind.Errand,
            "commissioni", "commissione", "spesa", "fare la spesa", "supermercato", "banca", "posta",
            "ufficio postale", "farmacia", "ritirare", "parrucchiere", "barbiere",
        )
        add(
            ActivityKind.Chore,
            "pulizie", "pulire", "bucato", "lavatrice", "piatti", "lavare i piatti", "stirare", "riordinare",
            "faccende", "spazzatura",
        )
        add(
            ActivityKind.Appointment,
            "appuntamento", "visita", "visita medica", "dottore", "medico", "dentista", "analisi",
            "esami del sangue", "psicologo", "psicologa", "terapia", "fisioterapia", "veterinario",
        )
        add(ActivityKind.Medication, "farmaco", "farmaci", "medicina", "medicine", "pillola", "prendere le medicine")
        add(
            ActivityKind.Social,
            "festa", "compleanno", "uscita", "uscire", "amici", "cena con amici", "pranzo con amici",
            "vedere", "appuntamento romantico", "matrimonio", "cena di famiglia",
        )
        add(ActivityKind.Leisure, "relax", "film", "serie", "videogiochi", "leggere", "lettura", "hobby", "musica")
        add(ActivityKind.Commute, "viaggio", "treno", "aeroporto", "tragitto", "portare i bambini a scuola")
        add(ActivityKind.SelfCare, "meditazione", "meditare", "yoga", "diario", "stretching", "cura di sé", "cura di se")
        add(ActivityKind.WindDown, "andare a letto", "letto", "dormire", "routine serale", "nanna")
    }
}
