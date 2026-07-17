package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.data.local.PomodoroTimerManager
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import javax.inject.Inject

/**
 * Kicks off a Pomodoro session in the background. The user doesn't have to be on
 * the Pomodoro screen — the shared PomodoroTimerManager keeps state app-wide and
 * PomodoroTimerService picks it up if it isn't already running.
 */
class StartPomodoroTool @Inject constructor(
    private val timerManager: PomodoroTimerManager,
    private val preferencesManager: PreferencesManager,
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
                    "Length of the session in minutes. Defaults to 25 for focus, 5 " +
                        "for short_break, 15 for long_break if not provided."
                )
            }
            putJsonObject("session_type") {
                put("type", "string")
                put(
                    "description",
                    "One of: focus, short_break, long_break. Defaults to focus."
                )
            }
        }
    }

    override suspend fun execute(args: JsonObject): String {
        if (timerManager.isRunningOrPaused) {
            return "A pomodoro session is already active — ask the user if they want " +
                "to stop it first before starting a new one."
        }

        val typeRaw = args["session_type"]?.jsonPrimitive?.content
            ?.trim()?.lowercase()?.replace('-', '_') ?: "focus"
        val sessionType = when (typeRaw) {
            "short_break", "short", "break" -> PomodoroSessionType.SHORT_BREAK
            "long_break", "long" -> PomodoroSessionType.LONG_BREAK
            else -> PomodoroSessionType.FOCUS
        }
        timerManager.selectSessionType(sessionType)

        val requestedMinutes = args["minutes"]?.jsonPrimitive?.content?.toIntOrNull()
        val minutes = requestedMinutes?.coerceIn(1, 180) ?: defaultMinutesFor(sessionType)
        timerManager.setCustomDuration(minutes)

        val focusModeEnabled = preferencesManager.focusModeEnabled.first()
        timerManager.start(focusModeEnabled)

        return "Started a ${minutes}-minute ${sessionType.name.lowercase().replace('_', ' ')} " +
            "session."
    }

    private fun defaultMinutesFor(type: PomodoroSessionType): Int = when (type) {
        PomodoroSessionType.FOCUS -> 25
        PomodoroSessionType.SHORT_BREAK -> 5
        PomodoroSessionType.LONG_BREAK -> 15
    }
}
