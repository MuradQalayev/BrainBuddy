package com.muradgalayev.brainbuddy.ui.together

import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.domain.model.Connection
import com.muradgalayev.brainbuddy.domain.model.ShareScope
import com.muradgalayev.brainbuddy.ui.utils.UiText
import com.muradgalayev.brainbuddy.ui.utils.uiText

// shared logic for 'create this for someone else', used identically by the calendar and the
// to-do list. both fan an item out to one or more connections and then have to say what
// actually happened. keeping the rules here means the two flows can't drift apart, and the one
// genuinely dangerous decision (whether an item that landed nowhere gets rescued onto the
// author's own list) is in one testable place rather than duplicated in two ViewModels

// which flow is sharing, purely so the copy reads naturally
enum class SharedItemKind { EVENT, TASK }

// names of the connections a fan-out succeeded and failed for
data class ShareOutcome(
    val succeeded: List<String> = emptyList(),
    val failed: List<String> = emptyList(),
) {
    // true when the item landed nowhere at all. only possible when the author opted out of their
    // own calendar or list: every copy was rejected, so without rescuing it locally the item, and
    // whatever they typed, would simply vanish. something they can move or delete beats losing it
    fun needsLocalRescue(keptOnMine: Boolean): Boolean =
        !keptOnMine && succeeded.isEmpty()

    // what to tell the user. never claims success for a connection that rejected the write: a
    // partial failure names both sides, because 'saved' printed over a silently dropped copy is
    // how someone ends up believing their partner was told about an appointment they never got
    fun message(keptOnMine: Boolean, kind: SharedItemKind): UiText {
        val added = UiText.Names(succeeded)
        val notAdded = UiText.Names(failed)
        val event = kind == SharedItemKind.EVENT
        return when {
            needsLocalRescue(keptOnMine) -> uiText(
                if (event) R.string.share_rescued_calendar else R.string.share_rescued_list,
                notAdded,
            )

            failed.isEmpty() && keptOnMine -> uiText(R.string.share_added_for_you_and, added)

            failed.isEmpty() -> uiText(
                if (event) R.string.share_added_to_calendar_of else R.string.share_added_to_list_of,
                added,
            )

            succeeded.isEmpty() -> uiText(
                if (event) R.string.share_saved_calendar_off else R.string.share_saved_tasks_off,
                notAdded,
            )

            else -> uiText(R.string.share_partial, added, notAdded)
        }
    }
}

// connections I may write to for this scope. filtered on grantedToMe, never grantedByMe: what
// matters is whether they opened their calendar or list to me, not what I've opened to them.
// getting it backwards would offer people I can't write to, and every send would bounce off RLS
fun List<Connection>.writableFor(scope: ShareScope): List<Connection> =
    filter { it.canUse(scope) }

// whether the add dialog's save button should be live. the audience switcher makes 'save
// nowhere' reachable: in Together mode with nobody picked, or with your own calendar unticked
// and no recipients. both have to be blocked before the user can lose what they typed
fun canSaveForAudience(
    hasTitle: Boolean,
    hasTimeError: Boolean,
    addToMine: Boolean,
    selectedCount: Int,
    isTogetherMode: Boolean,
): Boolean {
    if (!hasTitle || hasTimeError) return false
    // must land somewhere
    if (!addToMine && selectedCount == 0) return false
    // Together mode is for someone, and without a recipient it has no meaning
    if (isTogetherMode && selectedCount == 0) return false
    return true
}
