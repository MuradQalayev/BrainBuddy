package com.muradgalayev.brainbuddy.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Medication(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    // the amount only, '20' or '1.5'. the unit lives in doseUnit. older rows stored the two fused
    // into one string like '20mg', which is why the mapper splits them on read
    val dose: String = "",
    // MedicationUnit key. blank means the user hasn't said, or it's a legacy row
    val doseUnit: String = "",
    val slots: List<String> = emptyList(),
    // ISO weekday numbers (Monday = 1). empty keeps legacy medications on every day
    val daysOfWeek: List<Int> = emptyList(),
    // N4, the actual clock times this is taken, HH:mm each. slots only says 'morning', which is
    // too coarse for what this is for: stimulant onset and wear-off are among the strongest
    // predictors of real focus capacity through the day, and 'morning' covers four hours in which
    // a dose could peak anywhere. empty means the user kept the coarse slots, a valid answer
    val times: List<String> = emptyList(),
    // N4, as-needed rather than daily. an as-needed medication must not be reasoned about as a
    // predictable daily curve, and must not generate a reminder for a dose that may not be due
    val asNeeded: Boolean = false,
) {
    val timesPerDay: Int get() = if (times.isNotEmpty()) times.size else slots.size

    // amount and unit as one readable string, '20 mg' or '2 tablets'. every place that shows a
    // dose uses this rather than dose alone, so a legacy row with the unit baked into the amount
    // renders once rather than as '20mg mg'
    val doseLabel: String
        get() {
            val amount = dose.trim()
            if (amount.isEmpty()) return ""
            val unit = MedicationUnit.fromKey(doseUnit) ?: return amount
            return "$amount ${unit.label}"
        }

    fun isScheduledOn(day: java.time.DayOfWeek): Boolean =
        daysOfWeek.isEmpty() || day.value in daysOfWeek

    // clock times to reason about, falling back to the midpoint of each coarse slot when the user
    // hasn't given exact ones. a rough time beats no time: it still tells a morning dose from an
    // evening one
    val effectiveTimes: List<String>
        get() = times.ifEmpty {
            slots.mapNotNull { key -> MedicationSlot.fromKey(key)?.approximateTime }
        }
}

// units a dose can be written in. deliberately short: this is a picker on a form people fill
// in once, not a pharmacological reference, and every extra option is another decision to
// make. the long tail (puffs, units, sachets) is rare enough that the free amount field plus a
// sensible neighbour covers it. ordered by how often they come up for ADHD medication
enum class MedicationUnit(val key: String, val label: String) {
    Mg("mg", "mg"),
    Mcg("mcg", "mcg"),
    G("g", "g"),
    Tablets("tablets", "tablets"),
    Capsules("capsules", "capsules"),
    Pieces("pieces", "pieces"),
    Ml("ml", "ml"),
    Drops("drops", "drops");

    companion object {
        val DEFAULT = Mg

        fun fromKey(key: String): MedicationUnit? = entries.firstOrNull { it.key == key }

        // pulls a trailing unit off a legacy fused dose like '20mg' or '2 tablets', returning amount
        // and unit separately. null when nothing matches, so free-form text the user wrote themselves
        // is left exactly as typed
        fun splitLegacy(raw: String): Pair<String, MedicationUnit>? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            // longest label first, so 'mcg' isn't matched as 'g'
            val match = entries.sortedByDescending { it.label.length }.firstOrNull {
                trimmed.endsWith(it.label, ignoreCase = true)
            } ?: return null
            val amount = trimmed.dropLast(match.label.length).trim()
            // 'mg' on its own is a unit with no amount, not something to split
            return if (amount.isEmpty()) null else amount to match
        }
    }
}

enum class MedicationSlot(
    val key: String,
    val label: String,
    // middle of the window, used when no exact time was given
    val approximateTime: String,
) {
    Morning("morning", "Morning", "08:00"),
    Afternoon("afternoon", "Afternoon", "14:00"),
    Evening("evening", "Evening", "19:00"),
    Night("night", "Night", "22:00");

    companion object {
        fun fromKey(key: String): MedicationSlot? = entries.firstOrNull { it.key == key }
    }
}
