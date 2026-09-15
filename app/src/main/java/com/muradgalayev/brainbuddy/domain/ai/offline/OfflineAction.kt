package com.muradgalayev.brainbuddy.domain.ai.offline

import com.muradgalayev.brainbuddy.R

// a thing the assistant can do with no network at all.
// the model here is deliberately not a chat. offline, the assistant offers a short list of
// concrete actions; the user picks one, fills its slots from pre-built options (typing only
// where free text is unavoidable), and confirms. every action is a thin front end over an
// existing AiTool, the same one the online model calls, so offline and online can't drift into
// doing different things, and the offline path inherits local-first writes and
// sync-on-reconnect for free.
// this is also the seam for a real on-device model later: something small could re-rank
// OfflineActionCatalog.suggest() or pre-fill slots from free text, with no UI or tool changes
data class OfflineAction(
    // stable id, used as the UI key and for the recently-used ordering
    val id: String,
    val title: String,
    // one line under the title, what confirming will actually do
    val subtitle: String,
    // what the result screen says once it worked. null shows the tool's own answer instead, for
    // the one action whose answer is the point (care nearby lists what it found)
    val confirmation: String? = null,
    val group: OfflineActionGroup,
    val icon: OfflineActionIcon,
    // name of the AiTool that performs it. must exist in the tool multibinding
    val toolName: String,
    val slots: List<OfflineSlot> = emptyList(),
    // arguments the user never sees or chooses
    val fixedArgs: Map<String, String> = emptyMap(),
    // whether confirming leaves something for Supabase to catch up on. drives the 'will sync when
    // you're back' note: a focus timer or a cached lookup changes nothing on the server, and
    // claiming otherwise would be noise
    val syncs: Boolean = true,
)

// which glyph fronts an action. named by meaning rather than by drawing, so the catalog stays
// free of Compose types and the UI owns the actual vector
enum class OfflineActionIcon {
    Todo, Event, Font, TextSize, TextSpacing, Theme, FocusTimer, Clock, Tone, Note, Care,
}

enum class OfflineActionGroup(@androidx.annotation.StringRes val labelRes: Int) {
    Capture(R.string.offline_group_capture),
    Appearance(R.string.offline_group_appearance),
    Profile(R.string.offline_group_profile),
    Focus(R.string.offline_group_focus),
    Care(R.string.offline_group_care),
}

// one question the user answers before an action can run
sealed interface OfflineSlot {
    val key: String
    val label: String
    val optional: Boolean

    // pick one of a fixed set, the offline default and always the first choice
    data class Choice(
        override val key: String,
        override val label: String,
        val options: List<OfflineOption>,
        val defaultValue: String? = null,
        override val optional: Boolean = false,
    ) : OfflineSlot

    // free text. only where no list can stand in, like the title of a thought you want out of your
    // head right now. everything else stays tappable
    data class FreeText(
        override val key: String,
        override val label: String,
        val hint: String,
        override val optional: Boolean = false,
    ) : OfflineSlot

    // relative day picker, resolves to an ISO yyyy-MM-dd at confirm time
    data class DayPick(
        override val key: String,
        override val label: String,
        override val optional: Boolean = false,
    ) : OfflineSlot

    // coarse time-of-day picker, resolves to HH:mm
    data class TimePick(
        override val key: String,
        override val label: String,
        override val optional: Boolean = true,
    ) : OfflineSlot
}

data class OfflineOption(val value: String, val label: String)

// options for the two slot types whose choices depend on when you're asking. shared by the UI
// that renders them and the runner that receives whatever was picked, so the two can't
// disagree about what Tomorrow resolved to
object OfflineSlotOptions {

    // today through the next few days. values are ISO yyyy-MM-dd
    fun days(
        todayLabel: String,
        tomorrowLabel: String,
        today: java.time.LocalDate = java.time.LocalDate.now(),
    ): List<OfflineOption> =
        (0..4L).map { offset ->
            val date = today.plusDays(offset)
            val label = when (offset) {
                0L -> todayLabel
                1L -> tomorrowLabel
                else -> date.dayOfWeek
                    .getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault())
                    .plus(" ${date.dayOfMonth}")
            }
            OfflineOption(date.toString(), label)
        }

    // coarse times, starting from the next round half-hour so 'later today' is always reachable
    // without a keyboard. values are HH:mm
    fun times(now: java.time.LocalTime = java.time.LocalTime.now()): List<OfflineOption> {
        val start = now.plusMinutes(30L - (now.minute % 30).toLong()).withSecond(0).withNano(0)
        return (0..7).map { step ->
            val t = start.plusMinutes(step * 30L)
            OfflineOption(
                value = "%02d:%02d".format(t.hour, t.minute),
                // the locale's own clock: 2:30 pm in English, 14:30 in Italian
                label = t.format(
                    java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT),
                ),
            )
        }
    }
}

// outcome of running an action, and what the UI shows after confirm
sealed interface OfflineActionResult {
    // the tool ran. message is its own summary, queuedForSync drives the note
    data class Done(val message: String, val queuedForSync: Boolean) : OfflineActionResult

    data class Failed(val message: String) : OfflineActionResult
}
