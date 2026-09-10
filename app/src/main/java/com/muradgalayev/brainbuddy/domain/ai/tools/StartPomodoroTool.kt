package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.data.local.PomodoroTimerManager
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType
import com.muradgalayev.brainbuddy.domain.ai.AiNavigator
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

// kicks off a Pomodoro session in the background. the user doesn't have to be on the Pomodoro
// screen: the shared PomodoroTimerManager keeps state app-wide and PomodoroTimerService picks
// it up if it isn't already running
class StartPomodoroTool @Inject constructor(
    private val timerManager: PomodoroTimerManager,
    private val aiNavigator: AiNavigator,
) : AiTool {

    override val name: String = "start_pomodoro"

    override val description: String =
        "Start a focus (or break) timer. Use for asks like 'start a 25-minute focus " +
            "session', 'give me a quick 10-min block', 'take a 5-minute break'. " +
            "Won't start if a session is already running — tell the user in that case."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("minutes") {
                put("type", "integer")
                put(
                    "description",
                    "Length of the session in minutes. Defaults to 25 for focus and 5 " +
                        "for break if not provided."
                )
            }
            putJsonObject("session_type") {
                put("type", "string")
                put(
                    "description",
                    "One of: focus, break. Defaults to focus."
                )
            }
        }
    }

    override suspend fun execute(args: JsonObject): String {
        if (timerManager.isRunningOrPaused) {
            return "A pomodoro session is already active — ask the user if they want " +
                "to stop it first before starting a new one."
        }
        if (!timerManager.prepareForStandaloneStart()) {
            return "A calendar Pomodoro plan is already loaded. Ask the user to finish or " +
                "clear that plan before starting a separate session."
        }

        val typeRaw = args["session_type"]?.jsonPrimitive?.content
            ?.trim()?.lowercase()?.replace('-', '_') ?: "focus"
        // still accepts the retired short/long wording, so older phrasings keep working
        val sessionType = when (typeRaw) {
            "break", "short_break", "short", "long_break", "long" -> PomodoroSessionType.BREAK
            else -> PomodoroSessionType.FOCUS
        }
        val effective = timerManager.refreshEffectiveSettingsNow()
        timerManager.selectSessionType(sessionType)

        val requestedMinutes = args["minutes"]?.jsonPrimitive?.content?.toIntOrNull()
        val minutes = requestedMinutes?.coerceIn(1, 180) ?: effective.minutesFor(sessionType)
        if (requestedMinutes != null) timerManager.setCustomDuration(minutes)

        timerManager.start(effective.autoDndOnFocusSession)

        // take the user to the Pomodoro screen so they see the running timer
        aiNavigator.navigateTo("pomodoro")

        return "Started a ${minutes}-minute ${sessionType.name.lowercase().replace('_', ' ')} " +
            "session."
    }
}
