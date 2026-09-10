package com.muradgalayev.brainbuddy.domain.ai.tools

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

// opens the dialler, an email draft, or a website when the user asks to contact a place. uses
// ACTION_DIAL rather than ACTION_CALL so no calling permission is needed and the user still
// taps the green button themselves. the value normally comes from a preceding
// find_care_nearby result
class ContactTool @Inject constructor(
    @ApplicationContext private val context: Context,
) : AiTool {

    override val name: String = "contact"

    override val description: String =
        "Reach out to a place for the user. action=call opens the dialer with the " +
            "number pre-filled; action=email opens a new email draft (optional " +
            "subject/body); action=website opens the site in the browser. 'value' is " +
            "the phone number, email address, or URL — usually taken from a " +
            "find_care_nearby result. Confirm with the user before contacting when " +
            "it's ambiguous which place they mean."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("action") {
                put("type", "string")
                putJsonArray("enum") { add("call"); add("email"); add("website") }
                put("description", "What to open: call, email, or website.")
            }
            putJsonObject("value") {
                put("type", "string")
                put("description", "Phone number, email address, or URL to act on.")
            }
            putJsonObject("email_subject") {
                put("type", "string")
                put("description", "Optional subject line when action=email.")
            }
            putJsonObject("email_body") {
                put("type", "string")
                put("description", "Optional pre-filled body when action=email.")
            }
        }
        putJsonArray("required") { add("action"); add("value") }
    }

    override suspend fun execute(args: JsonObject): String {
        val action = args["action"]?.jsonPrimitive?.content?.trim()?.lowercase()
            ?: return "No action given. Use call, email, or website."
        val value = args["value"]?.jsonPrimitive?.content?.trim()
        if (value.isNullOrBlank()) return "No number, address, or link to open."

        val intent = when (action) {
            "call" -> Intent(Intent.ACTION_DIAL, "tel:${value.filterNot { it == ' ' }}".toUri())
            "email" -> Intent(Intent.ACTION_SENDTO, "mailto:$value".toUri()).apply {
                args["email_subject"]?.jsonPrimitive?.content?.let {
                    putExtra(Intent.EXTRA_SUBJECT, it)
                }
                args["email_body"]?.jsonPrimitive?.content?.let {
                    putExtra(Intent.EXTRA_TEXT, it)
                }
            }
            "website" -> {
                val url = if (value.startsWith("http", ignoreCase = true)) value else "https://$value"
                Intent(Intent.ACTION_VIEW, url.toUri())
            }
            else -> return "I can only call, email, or open a website — not '$action'."
        }.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }

        return runCatching {
            context.startActivity(intent)
            when (action) {
                "call" -> "Opened the dialer for $value."
                "email" -> "Started an email to $value."
                else -> "Opened $value."
            }
        }.getOrElse {
            "Couldn't open that — there may be no app to handle it on this device."
        }
    }
}
