package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import com.muradgalayev.brainbuddy.domain.model.AGE_RANGES
import com.muradgalayev.brainbuddy.domain.model.AiTone
import com.muradgalayev.brainbuddy.domain.model.ProductiveTime
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.add
import javax.inject.Inject

/**
 * Updates the user's ADHD profile fields — the ones filled in during the deep-dive
 * survey. This is separate from `update_profile` (which handles display name and
 * username on the profiles table).
 *
 * Only overwrites the specific fields the caller provides. Everything else on the
 * profile stays intact, so the model can nudge one setting at a time without wiping
 * the rest of what the user filled in.
 */
class UpdateAdhdProfileTool @Inject constructor(
    private val adhdProfileRepository: AdhdProfileRepository,
) : AiTool {

    override val name: String = "update_adhd_profile"

    override val description: String =
        "Change fields on the user's ADHD profile — age range, peak productive time, " +
            "comfortable focus length, sleep window, AI tone preference, or their " +
            "pain-point note. Only fields you pass are changed; everything else is " +
            "preserved. Use this when the user says things like 'change my age to " +
            "25', 'I'm more productive in the evening now', 'set my focus block to " +
            "45 minutes', 'I go to bed at midnight'. " +
            "Note: this doesn't touch display name or username — use update_profile " +
            "for those."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("age_range") {
                put("type", "string")
                put(
                    "description",
                    "One of: ${AGE_RANGES.joinToString(", ")}. Map ages like " +
                        "'25' → '25–34'."
                )
            }
            putJsonObject("productive_time") {
                put("type", "string")
                put(
                    "description",
                    "One of: morning, afternoon, evening, night, varies."
                )
            }
            putJsonObject("focus_duration_minutes") {
                put("type", "integer")
                put("description", "Preferred focus block length, 5–120 minutes.")
            }
            putJsonObject("sleep_bedtime") {
                put("type", "string")
                put("description", "HH:mm 24h — usual time going to bed.")
            }
            putJsonObject("sleep_wake_time") {
                put("type", "string")
                put("description", "HH:mm 24h — usual wake-up time.")
            }
            putJsonObject("ai_tone") {
                put("type", "string")
                put("description", "One of: gentle, direct, playful, professional.")
            }
            putJsonObject("pain_point") {
                put("type", "string")
                put(
                    "description",
                    "A short free-text note about what's hardest right now."
                )
            }
        }
    }

    override suspend fun execute(args: JsonObject): String {
        val current = runCatching { adhdProfileRepository.getProfile() }.getOrNull()
            ?: return "Failed: no ADHD profile yet — user should complete the survey first."

        val newAge = args["age_range"]?.jsonPrimitive?.content?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { normalizeAgeRange(it) ?: return "Failed: '$it' isn't a valid age range." }
        val newProductive = args["productive_time"]?.jsonPrimitive?.content?.trim()
            ?.lowercase()?.takeIf { it.isNotEmpty() }
            ?.let {
                ProductiveTime.fromKey(it)
                    ?: return "Failed: '$it' isn't a valid productive time."
            }
        val newFocus = args["focus_duration_minutes"]?.jsonPrimitive?.content?.toIntOrNull()
            ?.let {
                if (it !in 5..120) return "Failed: focus duration must be 5–120 minutes."
                else it
            }
        val newBedtime = args["sleep_bedtime"]?.jsonPrimitive?.content?.trim()
        val newWake = args["sleep_wake_time"]?.jsonPrimitive?.content?.trim()
        val newTone = args["ai_tone"]?.jsonPrimitive?.content?.trim()?.lowercase()
            ?.takeIf { it.isNotEmpty() }
            ?.let {
                AiTone.fromKey(it) ?: return "Failed: '$it' isn't a valid tone."
            }
        val newPain = args["pain_point"]?.jsonPrimitive?.content

        val updated = current.copy(
            ageRange = newAge ?: current.ageRange,
            productiveTime = newProductive ?: current.productiveTime,
            focusDurationMinutes = newFocus ?: current.focusDurationMinutes,
            sleepBedtime = newBedtime ?: current.sleepBedtime,
            sleepWakeTime = newWake ?: current.sleepWakeTime,
            aiTonePreference = newTone ?: current.aiTonePreference,
            painPoint = newPain ?: current.painPoint,
        )
        adhdProfileRepository.saveProfile(updated)

        val changed = buildList {
            if (newAge != null) add("age → $newAge")
            if (newProductive != null) add("productive time → ${newProductive.label.lowercase()}")
            if (newFocus != null) add("focus block → ${newFocus}min")
            if (newBedtime != null) add("bedtime → $newBedtime")
            if (newWake != null) add("wake → $newWake")
            if (newTone != null) add("tone → ${newTone.label.lowercase()}")
            if (newPain != null) add("pain-point updated")
        }
        return if (changed.isEmpty()) "Nothing changed — pass at least one field."
        else "Updated ADHD profile: ${changed.joinToString(", ")}."
    }

    /**
     * Users might say "25" or "twenty-five" — map to the closest bucket in AGE_RANGES.
     * Exact bucket labels are also accepted.
     */
    private fun normalizeAgeRange(input: String): String? {
        val cleaned = input.trim()
        // Exact match first.
        AGE_RANGES.firstOrNull { it.equals(cleaned, ignoreCase = true) }?.let { return it }
        // Numeric age → bucket.
        val n = cleaned.filter { it.isDigit() }.toIntOrNull() ?: return null
        return when {
            n < 18 -> "Under 18"
            n in 18..24 -> "18–24"
            n in 25..34 -> "25–34"
            n in 35..44 -> "35–44"
            n in 45..54 -> "45–54"
            else -> "55+"
        }
    }
}
