package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.data.repository.ExportResult
import com.muradgalayev.brainbuddy.data.repository.GoogleCalendarRepository
import com.muradgalayev.brainbuddy.domain.ai.AiNavigator
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject

// pushes the user's calendar events to their linked Google Calendar. works headless when an
// account is already connected, and when it isn't we can't run the OAuth consent flow from a
// background tool, so the user is sent to Settings to connect
class ExportCalendarToGoogleTool @Inject constructor(
    private val googleCalendarRepository: GoogleCalendarRepository,
    private val navigator: AiNavigator,
) : AiTool {

    override val name: String = "export_calendar_to_google"

    override val description: String =
        "Push all of the user's Myndora calendar events to their Google Calendar. " +
            "Use for 'sync my calendar to Google', 'export my events to Google Calendar'. " +
            "If no Google account is connected yet, this opens Settings so they can link one."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject { })
    }

    override suspend fun execute(args: JsonObject): String =
        when (val result = googleCalendarRepository.exportAllEvents()) {
            is ExportResult.Success -> {
                val parts = mutableListOf<String>()
                if (result.pushed > 0) parts += "${result.pushed} added"
                if (result.alreadyExisted > 0) parts += "${result.alreadyExisted} already there"
                if (result.failed > 0) parts += "${result.failed} failed"
                if (parts.isEmpty()) "There were no events to export."
                else "Synced to Google Calendar: ${parts.joinToString(", ")}."
            }
            ExportResult.NeedsGoogleSignIn -> {
                navigator.navigateTo("settings")
                "No Google account is connected yet. I opened Settings — connect Google " +
                    "Calendar there, then ask me to sync again."
            }
        }
}
