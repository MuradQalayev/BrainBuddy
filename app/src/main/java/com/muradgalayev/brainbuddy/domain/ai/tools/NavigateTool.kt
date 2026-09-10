package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.domain.ai.AiNavigator
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

// moves the user to a screen in the app. for 'go to', 'open' or 'show me the calendar'
class NavigateTool @Inject constructor(
    private val navigator: AiNavigator,
) : AiTool {

    override val name: String = "navigate_to_screen"

    override val description: String =
        "Open a screen in the app for the user. Use for 'go to the calendar', " +
            "'open settings', 'show me care nearby', 'take me to pomodoro'. " +
            "Use add_event to open the calendar with the new-event form already " +
            "open — that is how you hand something over for the user to finish " +
            "themselves, such as adding an event to someone else's calendar. " +
            "destination must be one of: home, workspace, calendar, add_event, " +
            "todo, pomodoro, care_nearby, settings, myndora_ai, myndora_together, " +
            "linked_devices."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("destination") {
                put("type", "string")
                putJsonArray("enum") {
                    add("home"); add("workspace"); add("calendar"); add("add_event")
                    add("todo"); add("pomodoro"); add("care_nearby"); add("settings")
                    add("myndora_ai"); add("myndora_together"); add("linked_devices")
                }
                put("description", "Which screen to open")
            }
        }
        putJsonArray("required") { add("destination") }
    }

    override suspend fun execute(args: JsonObject): String {
        val raw = args["destination"]?.jsonPrimitive?.content?.trim()?.lowercase()
            ?: return "No destination provided. Valid screens: " +
                "home, workspace, calendar, todo, pomodoro, care_nearby, settings, " +
                "myndora_ai, myndora_together, linked_devices."

        val route = AiNavigator.ROUTE_ALIASES[raw]
            ?: return "I can't open '$raw'. I can go to: home, workspace, calendar, " +
                "todo, pomodoro, care nearby, settings, Myndora AI, Myndora Together, " +
                "or linked devices."

        // latch the request before navigating. the calendar's view-model doesn't exist until the screen
        // composes, so the flag has to be waiting for it
        if (raw == "add_event") navigator.requestCalendarAdd()

        navigator.navigateTo(route)
        return if (raw == "add_event") {
            "Opened the calendar with the new-event form ready. The user fills it " +
                "in themselves — do not claim you created anything."
        } else {
            "Opened the ${raw.replace('_', ' ')} screen."
        }
    }
}
