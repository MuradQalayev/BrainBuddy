package com.muradgalayev.brainbuddy.domain.ai.offline

import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.data.local.TextSpacing
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.domain.model.AiTone
import com.muradgalayev.brainbuddy.domain.model.ProductiveTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

// everything the assistant can do offline, and the order to offer it in. option lists are
// built from the app's own enums rather than typed out, so adding a font or a tone shows up
// here automatically instead of quietly going missing
@Singleton
class OfflineActionCatalog @Inject constructor() {

    val actions: List<OfflineAction> = listOf(
        // capture. the reason offline mode exists: a thought you can't write down is a thought you
        // lose, and 'no connection' is not an acceptable reason to lose it
        OfflineAction(
            id = "capture_todo",
            title = "Add a to-do",
            subtitle = "Saves on this device, syncs when you're back",
            group = OfflineActionGroup.Capture,
            icon = OfflineActionIcon.Todo,
            toolName = "create_todo",
            slots = listOf(
                OfflineSlot.FreeText("title", "What is it?", hint = "e.g. Call the pharmacy"),
                OfflineSlot.DayPick("date", "When?"),
                OfflineSlot.TimePick("start_time", "Time (optional)"),
                OfflineSlot.Choice(
                    key = "priority",
                    label = "Priority",
                    options = listOf(
                        OfflineOption("MEDIUM", "Normal"),
                        OfflineOption("HIGH", "Important"),
                        OfflineOption("LOW", "Whenever"),
                    ),
                    defaultValue = "MEDIUM",
                ),
            ),
        ),
        OfflineAction(
            id = "capture_event",
            title = "Add a calendar event",
            subtitle = "Goes straight into your day, syncs later",
            group = OfflineActionGroup.Capture,
            icon = OfflineActionIcon.Event,
            toolName = "create_calendar_event",
            slots = listOf(
                OfflineSlot.FreeText("title", "What is it?", hint = "e.g. Dentist"),
                OfflineSlot.DayPick("date", "When?"),
                // create_calendar_event requires a start time, so this one isn't optional
                OfflineSlot.TimePick("start_time", "Starts at", optional = false),
                OfflineSlot.Choice(
                    key = "color",
                    label = "Colour",
                    options = listOf(
                        OfflineOption("blue", "Blue"),
                        OfflineOption("red", "Red"),
                        OfflineOption("yellow", "Yellow"),
                    ),
                    defaultValue = "blue",
                ),
            ),
        ),

        // look and feel. purely local settings, which have no business needing a network and now
        // don't: the write lands locally and is queued for Supabase
        OfflineAction(
            id = "set_font",
            title = "Change the font",
            subtitle = "Including OpenDyslexic",
            group = OfflineActionGroup.Appearance,
            icon = OfflineActionIcon.Font,
            toolName = "update_appearance",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "font",
                    label = "Typeface",
                    options = FontMode.entries.map {
                        OfflineOption(it.name, if (it == FontMode.OpenDyslexic) "OpenDyslexic" else it.name)
                    },
                ),
            ),
        ),
        OfflineAction(
            id = "set_font_size",
            title = "Change text size",
            subtitle = "Smaller or bigger everywhere",
            group = OfflineActionGroup.Appearance,
            icon = OfflineActionIcon.TextSize,
            toolName = "update_appearance",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "font_size",
                    label = "Text size",
                    options = FontSize.entries.map { OfflineOption(it.name, it.name) },
                ),
            ),
        ),
        OfflineAction(
            id = "set_text_spacing",
            title = "Change text spacing",
            subtitle = "More room between lines and letters",
            group = OfflineActionGroup.Appearance,
            icon = OfflineActionIcon.TextSpacing,
            toolName = "update_appearance",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "text_spacing",
                    label = "Spacing",
                    options = TextSpacing.entries.map { OfflineOption(it.name, it.name) },
                ),
            ),
        ),
        OfflineAction(
            id = "set_theme",
            title = "Switch theme",
            subtitle = "Light, dark, or follow the system",
            group = OfflineActionGroup.Appearance,
            icon = OfflineActionIcon.Theme,
            toolName = "update_appearance",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "theme",
                    label = "Theme",
                    options = ThemeMode.entries.map { OfflineOption(it.name, it.name) },
                ),
            ),
        ),

        // profile
        OfflineAction(
            id = "profile_focus_length",
            title = "Set my focus length",
            subtitle = "How long a block feels doable right now",
            group = OfflineActionGroup.Profile,
            icon = OfflineActionIcon.Clock,
            toolName = "update_adhd_profile",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "focus_duration_minutes",
                    label = "Focus block",
                    options = listOf(10, 15, 25, 45, 60).map {
                        OfflineOption(it.toString(), "$it min")
                    },
                    defaultValue = "25",
                ),
            ),
        ),
        OfflineAction(
            id = "profile_productive_time",
            title = "Change my best time of day",
            subtitle = "When your focus is actually there",
            group = OfflineActionGroup.Profile,
            icon = OfflineActionIcon.Clock,
            toolName = "update_adhd_profile",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "productive_time",
                    label = "Peak time",
                    options = ProductiveTime.entries.map { OfflineOption(it.key, it.label) },
                ),
            ),
        ),
        OfflineAction(
            id = "profile_tone",
            title = "Change how I talk to you",
            subtitle = "Applies once you're back online too",
            group = OfflineActionGroup.Profile,
            icon = OfflineActionIcon.Tone,
            toolName = "update_adhd_profile",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "ai_tone",
                    label = "Tone",
                    options = AiTone.entries.map { OfflineOption(it.key, it.label) },
                ),
            ),
        ),
        OfflineAction(
            id = "profile_pain_point",
            title = "Note what's hard right now",
            subtitle = "I'll use it to personalise once we reconnect",
            group = OfflineActionGroup.Profile,
            icon = OfflineActionIcon.Note,
            toolName = "update_adhd_profile",
            slots = listOf(
                OfflineSlot.FreeText(
                    key = "pain_point",
                    label = "What's getting in the way?",
                    hint = "e.g. Can't start anything in the mornings",
                ),
            ),
        ),

        // focus
        OfflineAction(
            id = "start_focus",
            title = "Start a focus timer",
            subtitle = "Runs entirely on the device",
            group = OfflineActionGroup.Focus,
            icon = OfflineActionIcon.FocusTimer,
            toolName = "start_pomodoro",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "minutes",
                    label = "Length",
                    options = listOf(10, 15, 25, 45).map { OfflineOption(it.toString(), "$it min") },
                    defaultValue = "25",
                ),
            ),
            syncs = false,
        ),

        // care. read-only over the cached place list, and only offered when there is actually a cache
        // to read, see suggest()
        OfflineAction(
            id = "find_care",
            title = "Look up care nearby",
            subtitle = "From what's already saved on this device",
            group = OfflineActionGroup.Care,
            icon = OfflineActionIcon.Care,
            toolName = "find_care_nearby",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "category",
                    label = "What do you need?",
                    options = listOf(
                        OfflineOption("pharmacy", "Pharmacy"),
                        OfflineOption("hospital", "Hospital"),
                        OfflineOption("specialist", "Specialist / dentist"),
                        OfflineOption("support_group", "Support group"),
                    ),
                ),
            ),
            fixedArgs = mapOf("open_screen" to "true", "limit" to "5"),
            syncs = false,
        ),
    )

    fun byId(id: String): OfflineAction? = actions.firstOrNull { it.id == id }

    // the handful to put in front of the user first. deterministic, and deliberately shallow: time
    // of day, what they last used, and whether care data is even available offline. it reads like
    // a suggestion because the ordering is contextual, not because anything is being generated,
    // which is the honest version of 'AI suggests' with no model loaded
    fun suggest(
        now: LocalTime = LocalTime.now(),
        recentActionIds: List<String> = emptyList(),
        careAvailable: Boolean = false,
        limit: Int = 4,
    ): List<OfflineAction> {
        val available = actions.filter { careAvailable || it.group != OfflineActionGroup.Care }
        val score = { action: OfflineAction ->
            var s = 0
            // recently used first, offline sessions are short and repetitive
            val recentIndex = recentActionIds.indexOf(action.id)
            if (recentIndex >= 0) s += 100 - recentIndex * 10
            // capture is the point of being offline, so always near the top
            if (action.group == OfflineActionGroup.Capture) s += 40
            // planning reads as a morning thing, focus timers as a working-hours thing
            if (action.id == "capture_todo" && now.hour in 6..11) s += 15
            if (action.id == "start_focus" && now.hour in 9..18) s += 12
            if (action.id == "profile_pain_point" && now.hour >= 20) s += 10
            s
        }
        return available.sortedByDescending(score).take(limit)
    }
}
