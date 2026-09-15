package com.muradgalayev.brainbuddy.ui.together

import com.muradgalayev.brainbuddy.ui.utils.resolve
import com.muradgalayev.brainbuddy.testing.TestStrings
import com.muradgalayev.brainbuddy.domain.model.Connection
import com.muradgalayev.brainbuddy.domain.model.ConnectionRelation
import com.muradgalayev.brainbuddy.domain.model.ShareScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// the calendar-to-Together and to-do-to-Together join. this is the orchestration both + flows
// share: who may be written to, whether the save button is live, what happens when some copies
// land and others bounce, and the one that can cost the user their work, whether an item that
// reached nobody gets rescued onto their own list
class ShareOutcomeTest {

    private fun connection(
        id: String,
        name: String,
        grantedByMe: Set<ShareScope> = emptySet(),
        grantedToMe: Set<ShareScope> = emptySet(),
    ) = Connection(
        connectionId = "c-$id",
        userId = id,
        displayName = name,
        username = name.lowercase(),
        avatarUrl = null,
        relation = ConnectionRelation.PARTNER,
        grantedByMe = grantedByMe,
        grantedToMe = grantedToMe,
    )

    // who may I write to?

    @Test
    fun `only connections who opened that scope to me are writable`() {
        val people = listOf(
            connection("a", "Aylin", grantedToMe = setOf(ShareScope.CALENDAR)),
            connection("b", "Bahar", grantedToMe = setOf(ShareScope.TODOS)),
            connection("c", "Cem"),
        )

        assertEquals(listOf("a"), people.writableFor(ShareScope.CALENDAR).map { it.userId })
        assertEquals(listOf("b"), people.writableFor(ShareScope.TODOS).map { it.userId })
    }

    @Test
    fun `what I share outward never makes their calendar writable to me`() {
        // the direction is the whole point. reading grantedByMe here would offer people I can't write
        // to, and every send would bounce off RLS
        val people = listOf(
            connection("a", "Aylin", grantedByMe = setOf(ShareScope.CALENDAR)),
        )

        assertTrue(people.writableFor(ShareScope.CALENDAR).isEmpty())
    }

    @Test
    fun `scopes are independent of each other`() {
        val people = listOf(
            connection("a", "Aylin", grantedToMe = setOf(ShareScope.CALENDAR)),
        )

        assertEquals(1, people.writableFor(ShareScope.CALENDAR).size)
        assertTrue(people.writableFor(ShareScope.TODOS).isEmpty())
        assertTrue(people.writableFor(ShareScope.WELLNESS).isEmpty())
    }

    // can this be saved at all?

    @Test
    fun `a plain personal item saves with nobody selected`() {
        assertTrue(
            canSaveForAudience(
                hasTitle = true, hasTimeError = false,
                addToMine = true, selectedCount = 0, isTogetherMode = false,
            )
        )
    }

    @Test
    fun `Together mode with nobody picked cannot be saved`() {
        assertFalse(
            canSaveForAudience(
                hasTitle = true, hasTimeError = false,
                addToMine = false, selectedCount = 0, isTogetherMode = true,
            )
        )
    }

    @Test
    fun `Together mode with a recipient saves even without my own copy`() {
        assertTrue(
            canSaveForAudience(
                hasTitle = true, hasTimeError = false,
                addToMine = false, selectedCount = 1, isTogetherMode = true,
            )
        )
    }

    @Test
    fun `unticking my own calendar with nobody selected cannot be saved`() {
        // reachable by picking someone, unticking 'add to mine', then deselecting
        assertFalse(
            canSaveForAudience(
                hasTitle = true, hasTimeError = false,
                addToMine = false, selectedCount = 0, isTogetherMode = false,
            )
        )
    }

    @Test
    fun `a blank title blocks saving in every mode`() {
        assertFalse(
            canSaveForAudience(
                hasTitle = false, hasTimeError = false,
                addToMine = true, selectedCount = 0, isTogetherMode = false,
            )
        )
        assertFalse(
            canSaveForAudience(
                hasTitle = false, hasTimeError = false,
                addToMine = false, selectedCount = 2, isTogetherMode = true,
            )
        )
    }

    @Test
    fun `a time error blocks saving even with a valid audience`() {
        assertFalse(
            canSaveForAudience(
                hasTitle = true, hasTimeError = true,
                addToMine = true, selectedCount = 1, isTogetherMode = false,
            )
        )
    }

    // rescue: did the item land anywhere?

    @Test
    fun `nothing is rescued when the item is on my own calendar anyway`() {
        val outcome = ShareOutcome(succeeded = emptyList(), failed = listOf("Aylin"))

        assertFalse(outcome.needsLocalRescue(keptOnMine = true))
    }

    @Test
    fun `a friend-only item that reached nobody is rescued`() {
        val outcome = ShareOutcome(succeeded = emptyList(), failed = listOf("Aylin"))

        assertTrue(outcome.needsLocalRescue(keptOnMine = false))
    }

    @Test
    fun `a friend-only item that reached someone is not rescued`() {
        // rescuing here would put an unwanted copy on the author's own calendar
        val outcome = ShareOutcome(succeeded = listOf("Aylin"), failed = listOf("Bahar"))

        assertFalse(outcome.needsLocalRescue(keptOnMine = false))
    }

    // what do we tell the user?

    @Test
    fun `full success on both calendars names the recipient`() {
        val message = ShareOutcome(succeeded = listOf("Aylin"))
            .message(keptOnMine = true, kind = SharedItemKind.EVENT).resolve(TestStrings.en)

        assertEquals("Added for you and Aylin", message)
    }

    @Test
    fun `friend-only success says it is theirs alone`() {
        val message = ShareOutcome(succeeded = listOf("Aylin"))
            .message(keptOnMine = false, kind = SharedItemKind.EVENT).resolve(TestStrings.en)

        assertEquals("Added to Aylin's calendar", message)
    }

    @Test
    fun `to-do wording says list rather than calendar`() {
        val message = ShareOutcome(succeeded = listOf("Aylin"))
            .message(keptOnMine = false, kind = SharedItemKind.TASK).resolve(TestStrings.en)

        assertEquals("Added to Aylin's list", message)
    }

    @Test
    fun `a partial failure names both sides rather than claiming success`() {
        // reporting only the successes is how someone believes their partner was told about an
        // appointment that never reached them
        val message = ShareOutcome(succeeded = listOf("Aylin"), failed = listOf("Bahar"))
            .message(keptOnMine = true, kind = SharedItemKind.EVENT).resolve(TestStrings.en)

        assertTrue(message, message.contains("Aylin"))
        assertTrue(message, message.contains("Bahar"))
        assertTrue(message, message.contains("couldn't add for"))
    }

    @Test
    fun `total failure with my own copy kept explains the likely cause`() {
        val message = ShareOutcome(failed = listOf("Aylin"))
            .message(keptOnMine = true, kind = SharedItemKind.EVENT).resolve(TestStrings.en)

        assertTrue(message, message.startsWith("Saved,"))
        assertTrue(message, message.contains("calendar sharing off"))
    }

    @Test
    fun `total failure names task sharing for the to-do flow`() {
        val message = ShareOutcome(failed = listOf("Aylin"))
            .message(keptOnMine = true, kind = SharedItemKind.TASK).resolve(TestStrings.en)

        assertTrue(message, message.contains("task sharing off"))
    }

    @Test
    fun `rescue message tells the user where their work went`() {
        val message = ShareOutcome(failed = listOf("Aylin"))
            .message(keptOnMine = false, kind = SharedItemKind.TASK).resolve(TestStrings.en)

        assertTrue(message, message.contains("Aylin"))
        assertTrue(message, message.contains("saved to your list instead"))
    }

    @Test
    fun `several recipients are listed readably`() {
        val message = ShareOutcome(succeeded = listOf("Aylin", "Bahar"))
            .message(keptOnMine = false, kind = SharedItemKind.EVENT).resolve(TestStrings.en)

        assertEquals("Added to Aylin and Bahar's calendar", message)
    }

    @Test
    fun `no message ever claims success for a failed recipient`() {
        // sweep every combination: a name in `failed` must never appear in a sentence fragment that
        // reads as success
        val combos = listOf(
            ShareOutcome(succeeded = listOf("A"), failed = emptyList()),
            ShareOutcome(succeeded = emptyList(), failed = listOf("B")),
            ShareOutcome(succeeded = listOf("A"), failed = listOf("B")),
        )
        for (outcome in combos) {
            for (kept in listOf(true, false)) {
                for (kind in SharedItemKind.entries) {
                    val message = outcome.message(kept, kind).resolve(TestStrings.en)
                    if (outcome.failed.isNotEmpty()) {
                        assertTrue(
                            "\"$message\" hides a failure for ${outcome.failed}",
                            message.contains("Couldn't add") || message.contains("couldn't add"),
                        )
                    }
                }
            }
        }
    }
}
