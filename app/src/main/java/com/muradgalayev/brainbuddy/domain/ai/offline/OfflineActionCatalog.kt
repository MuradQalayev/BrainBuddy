package com.muradgalayev.brainbuddy.domain.ai.offline

import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.data.local.TextSpacing
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.data.local.labelRes
import com.muradgalayev.brainbuddy.domain.model.AiTone
import com.muradgalayev.brainbuddy.domain.model.ProductiveTime
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton
import com.muradgalayev.brainbuddy.R

// everything the assistant can do offline, and the order to offer it in. option lists are
// built from the app's own enums rather than typed out, so adding a font or a tone shows up
// here automatically instead of quietly going missing
@Singleton
class OfflineActionCatalog @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
) {

    // a getter, not a stored list: the copy is read from resources, and a list built once would stay
    // in whatever language the app had when this singleton was made
    val actions: List<OfflineAction>
        get() = listOf(
        // capture. the reason offline mode exists: a thought you can't write down is a thought you
        // lose, and 'no connection' is not an acceptable reason to lose it
        OfflineAction(
            id = "capture_todo",
            confirmation = context.getString(R.string.offline_done_todo),
            title = context.getString(R.string.offline_add_todo),
            subtitle = context.getString(R.string.offline_add_todo_sub),
            group = OfflineActionGroup.Capture,
            icon = OfflineActionIcon.Todo,
            toolName = "create_todo",
            slots = listOf(
                OfflineSlot.FreeText("title", context.getString(R.string.together_what_is_it), hint = context.getString(R.string.offline_hint_pharmacy)),
                OfflineSlot.DayPick("date", context.getString(R.string.med_when)),
                OfflineSlot.TimePick("start_time", context.getString(R.string.offline_time_optional)),
                OfflineSlot.Choice(
                    key = "priority",
                    label = context.getString(R.string.common_priority),
                    options = listOf(
                        OfflineOption("MEDIUM", context.getString(R.string.priority_normal)),
                        OfflineOption("HIGH", context.getString(R.string.priority_important)),
                        OfflineOption("LOW", context.getString(R.string.priority_whenever)),
                    ),
                    defaultValue = "MEDIUM",
                ),
            ),
        ),
        OfflineAction(
            id = "capture_event",
            confirmation = context.getString(R.string.offline_done_event),
            title = context.getString(R.string.offline_add_event),
            subtitle = context.getString(R.string.offline_add_event_sub),
            group = OfflineActionGroup.Capture,
            icon = OfflineActionIcon.Event,
            toolName = "create_calendar_event",
            slots = listOf(
                OfflineSlot.FreeText("title", context.getString(R.string.together_what_is_it), hint = context.getString(R.string.offline_hint_dentist)),
                OfflineSlot.DayPick("date", context.getString(R.string.med_when)),
                // create_calendar_event requires a start time, so this one isn't optional
                OfflineSlot.TimePick("start_time", context.getString(R.string.offline_starts_at), optional = false),
                OfflineSlot.Choice(
                    key = "color",
                    label = context.getString(R.string.common_colour),
                    options = listOf(
                        OfflineOption("blue", context.getString(R.string.color_blue)),
                        OfflineOption("red", context.getString(R.string.color_red)),
                        OfflineOption("yellow", context.getString(R.string.color_yellow)),
                    ),
                    defaultValue = "blue",
                ),
            ),
        ),

        // look and feel. purely local settings, which have no business needing a network and now
        // don't: the write lands locally and is queued for Supabase
        OfflineAction(
            id = "set_font",
            confirmation = context.getString(R.string.offline_done_font),
            title = context.getString(R.string.offline_change_font),
            subtitle = context.getString(R.string.offline_change_font_sub),
            group = OfflineActionGroup.Appearance,
            icon = OfflineActionIcon.Font,
            toolName = "update_appearance",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "font",
                    label = context.getString(R.string.setup_typeface),
                    options = FontMode.entries.map {
                        OfflineOption(it.name, if (it == FontMode.OpenDyslexic) "OpenDyslexic" else it.name)
                    },
                ),
            ),
        ),
        OfflineAction(
            id = "set_font_size",
            confirmation = context.getString(R.string.offline_done_size),
            title = context.getString(R.string.offline_change_size),
            subtitle = context.getString(R.string.offline_change_size_sub),
            group = OfflineActionGroup.Appearance,
            icon = OfflineActionIcon.TextSize,
            toolName = "update_appearance",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "font_size",
                    label = context.getString(R.string.setup_text_size),
                    options = FontSize.entries.map { OfflineOption(it.name, context.getString(it.labelRes)) },
                ),
            ),
        ),
        OfflineAction(
            id = "set_text_spacing",
            confirmation = context.getString(R.string.offline_done_spacing),
            title = context.getString(R.string.offline_change_spacing),
            subtitle = context.getString(R.string.offline_change_spacing_sub),
            group = OfflineActionGroup.Appearance,
            icon = OfflineActionIcon.TextSpacing,
            toolName = "update_appearance",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "text_spacing",
                    label = context.getString(R.string.common_spacing),
                    options = TextSpacing.entries.map { OfflineOption(it.name, context.getString(it.labelRes)) },
                ),
            ),
        ),
        OfflineAction(
            id = "set_theme",
            confirmation = context.getString(R.string.offline_done_theme),
            title = context.getString(R.string.offline_switch_theme),
            subtitle = context.getString(R.string.offline_switch_theme_sub),
            group = OfflineActionGroup.Appearance,
            icon = OfflineActionIcon.Theme,
            toolName = "update_appearance",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "theme",
                    label = context.getString(R.string.common_theme),
                    options = ThemeMode.entries.map { OfflineOption(it.name, context.getString(it.labelRes)) },
                ),
            ),
        ),

        // profile
        OfflineAction(
            id = "profile_focus_length",
            confirmation = context.getString(R.string.offline_done_focus_length),
            title = context.getString(R.string.offline_focus_length),
            subtitle = context.getString(R.string.offline_focus_length_sub),
            group = OfflineActionGroup.Profile,
            icon = OfflineActionIcon.Clock,
            toolName = "update_adhd_profile",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "focus_duration_minutes",
                    label = context.getString(R.string.offline_focus_block),
                    options = listOf(10, 15, 25, 45, 60).map {
                        OfflineOption(it.toString(), context.getString(R.string.common_minutes_short, it))
                    },
                    defaultValue = "25",
                ),
            ),
        ),
        OfflineAction(
            id = "profile_productive_time",
            confirmation = context.getString(R.string.offline_done_best_time),
            title = context.getString(R.string.offline_best_time),
            subtitle = context.getString(R.string.offline_best_time_sub),
            group = OfflineActionGroup.Profile,
            icon = OfflineActionIcon.Clock,
            toolName = "update_adhd_profile",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "productive_time",
                    label = context.getString(R.string.offline_peak_time),
                    options = ProductiveTime.entries.map { OfflineOption(it.key, context.getString(it.labelRes)) },
                ),
            ),
        ),
        OfflineAction(
            id = "profile_tone",
            confirmation = context.getString(R.string.offline_done_tone),
            title = context.getString(R.string.offline_tone),
            subtitle = context.getString(R.string.offline_tone_sub),
            group = OfflineActionGroup.Profile,
            icon = OfflineActionIcon.Tone,
            toolName = "update_adhd_profile",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "ai_tone",
                    label = context.getString(R.string.common_tone),
                    options = AiTone.entries.map { OfflineOption(it.key, context.getString(it.labelRes)) },
                ),
            ),
        ),
        OfflineAction(
            id = "profile_pain_point",
            confirmation = context.getString(R.string.offline_done_note),
            title = context.getString(R.string.offline_note_hard),
            subtitle = context.getString(R.string.offline_note_hard_sub),
            group = OfflineActionGroup.Profile,
            icon = OfflineActionIcon.Note,
            toolName = "update_adhd_profile",
            slots = listOf(
                OfflineSlot.FreeText(
                    key = "pain_point",
                    label = context.getString(R.string.offline_in_the_way),
                    hint = context.getString(R.string.offline_hint_mornings),
                ),
            ),
        ),

        // focus
        OfflineAction(
            id = "start_focus",
            confirmation = context.getString(R.string.offline_done_focus),
            title = context.getString(R.string.offline_start_focus),
            subtitle = context.getString(R.string.offline_start_focus_sub),
            group = OfflineActionGroup.Focus,
            icon = OfflineActionIcon.FocusTimer,
            toolName = "start_pomodoro",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "minutes",
                    label = context.getString(R.string.common_length),
                    options = listOf(10, 15, 25, 45).map { OfflineOption(it.toString(), context.getString(R.string.common_minutes_short, it)) },
                    defaultValue = "25",
                ),
            ),
            syncs = false,
        ),

        // care. read-only over the cached place list, and only offered when there is actually a cache
        // to read, see suggest()
        OfflineAction(
            id = "find_care",
            title = context.getString(R.string.offline_care),
            subtitle = context.getString(R.string.offline_care_sub),
            group = OfflineActionGroup.Care,
            icon = OfflineActionIcon.Care,
            toolName = "find_care_nearby",
            slots = listOf(
                OfflineSlot.Choice(
                    key = "category",
                    label = context.getString(R.string.offline_what_need),
                    options = listOf(
                        OfflineOption("pharmacy", context.getString(R.string.care_pharmacy)),
                        OfflineOption("hospital", context.getString(R.string.care_hospital)),
                        OfflineOption("specialist", context.getString(R.string.care_specialist)),
                        OfflineOption("support_group", context.getString(R.string.care_support_group)),
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
