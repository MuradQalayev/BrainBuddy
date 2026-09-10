package com.muradgalayev.brainbuddy.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// the rule that decides whether a stored yes still authorises sending wellness data. pinned
// because the failure is silent and in the wrong direction: get it wrong and the app keeps
// sending health data to a third-party model on the strength of an answer the user gave to a
// different question
class AiConsentValidityTest {

    @Test
    fun `consent given against the current wording holds`() {
        assertTrue(isConsentStillValid(granted = true, storedVersion = 1, currentVersion = 1))
    }

    @Test
    fun `consent given against older wording stops counting when the version is bumped`() {
        assertFalse(isConsentStillValid(granted = true, storedVersion = 1, currentVersion = 2))
    }

    @Test
    fun `a newer stored version still counts`() {
        // a second device on a newer build answered the current question first, so its yes is at least
        // as informed as what we'd ask for here
        assertTrue(isConsentStillValid(granted = true, storedVersion = 3, currentVersion = 2))
    }

    @Test
    fun `a refusal is never resurrected by a version bump`() {
        assertFalse(isConsentStillValid(granted = false, storedVersion = 2, currentVersion = 1))
        assertFalse(isConsentStillValid(granted = false, storedVersion = 1, currentVersion = 2))
    }

    @Test
    fun `never decided reads as no`() {
        // version 0 is the no-decision-recorded sentinel, and must not authorise anything
        assertFalse(isConsentStillValid(granted = true, storedVersion = 0, currentVersion = 1))
    }
}
