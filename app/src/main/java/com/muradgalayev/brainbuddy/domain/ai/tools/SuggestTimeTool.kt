package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.domain.ai.AiTool
import com.muradgalayev.brainbuddy.domain.scheduling.SuggestTimeUseCase
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalDate
import javax.inject.Inject

// asks the app's own timing engine when something should go. the model must not answer this
// from the profile text in its prompt: it can see 'most productive time: morning' and will
// happily invent 09:00, but the engine knows the user's last eight gym sessions were at 18:30,
// that Thursday evening is already full, that they slept five hours, and that dinner is not a
// thing that happens at 09:00. none of that is in the prompt and none of it can be.
// keeping this as a tool rather than more context is deliberate: the answer is a computation
// over the user's history, not a fact to be recalled, and it comes back with a stated reason
// the user can be shown and can argue with
class SuggestTimeTool @Inject constructor(
    private val suggestTime: SuggestTimeUseCase,
) : AiTool {

    override val name: String = "suggest_time"

    override val description: String =
        "Ask the app when to schedule something. Use this WHENEVER the user asks " +
            "to add an event or to-do without saying a time, or asks when they " +
            "should do something. Never guess a time yourself — this knows their " +
            "actual history, what's already on the day, their sleep window and how " +
            "tired they are. Returns a few options with reasons; offer the first " +
            "one and use its date and start_time when you then create the event."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("title") {
                put("type", "string")
                put(
                    "description",
                    "What the thing is, in the user's own words — 'gym', 'dinner " +
                        "with mum', 'finish the report'. The wording matters: it is " +
                        "how the activity is classified and matched to past habits.",
                )
            }
            putJsonObject("date") {
                put("type", "string")
                put(
                    "description",
                    "yyyy-MM-dd, the day they want it on. Defaults to today. The " +
                        "engine may still propose a later day if this one is full.",
                )
            }
        }
        putJsonArray("required") { add(JsonPrimitive("title")) }
    }

    override suspend fun execute(args: JsonObject): String {
        val title = args["title"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (title.isBlank()) return "Failed: title is required."

        val date = args["date"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotBlank() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: LocalDate.now()

        val suggestions = runCatching { suggestTime(title = title, date = date) }
            .getOrElse { return "Failed: couldn't work out a time (${it.message})." }

        if (suggestions.isEmpty()) {
            // distinct from a failure, and the model has to say something useful rather than inventing a
            // slot the engine deliberately refused to offer
            return "No free slot for '$title' on $date or the next few days. " +
                "Tell the user their days are full and ask what to move."
        }

        // the date is on every line, not only the ones that move. the model reads this back as ground
        // truth, and an omitted date is the kind of gap it fills by assuming 'the day I was asked about'
        return buildString {
            append("Suggested times for '$title', best first:\n")
            suggestions.take(3).forEach { s ->
                append("  • ${s.date} ${s.startLabel}–${s.endLabel} — ${s.reasonText}\n")
            }
            append(
                "Offer the first one in one short sentence with its reason, and " +
                    "chips to accept or ask for another. Only create the event once " +
                    "they agree."
            )
        }
    }
}
