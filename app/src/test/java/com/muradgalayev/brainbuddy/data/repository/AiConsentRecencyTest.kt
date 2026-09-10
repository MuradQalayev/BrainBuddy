package com.muradgalayev.brainbuddy.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// which of two decisions wins when a device that was offline finally reaches the server.
// without this the upsert is last-write-wins by arrival: a phone that granted consent hours ago
// could reconnect and overwrite a revoke the user made since on another device, silently
// resuming wellness data going to the model
class AiConsentRecencyTest {

    private val earlier = "2026-08-12T09:00:00Z"
    private val later = "2026-08-12T11:00:00Z"

    @Test
    fun `a newer local decision overwrites the account`() {
        assertTrue(shouldOverwriteRemote(localDecidedAt = later, remoteDecidedAt = earlier))
    }

    @Test
    fun `a stale local decision does not overwrite a newer one`() {
        assertFalse(shouldOverwriteRemote(localDecidedAt = earlier, remoteDecidedAt = later))
    }

    @Test
    fun `an equal timestamp writes rather than deadlocking the retry`() {
        // refusing both would leave pushPending retrying the same write forever
        assertTrue(shouldOverwriteRemote(localDecidedAt = later, remoteDecidedAt = later))
    }

    @Test
    fun `postgres offset format compares against our instant format`() {
        // what Postgres returns for timestamptz, versus what Instant.toString() writes
        val postgresStyle = "2026-08-12T11:00:00.123456+00:00"
        val instantStyle = "2026-08-12T09:00:00Z"
        assertFalse(shouldOverwriteRemote(localDecidedAt = instantStyle, remoteDecidedAt = postgresStyle))
        assertTrue(shouldOverwriteRemote(localDecidedAt = postgresStyle, remoteDecidedAt = instantStyle))
    }

    @Test
    fun `a bare local timestamp is still understood`() {
        assertTrue(shouldOverwriteRemote("2026-08-12T11:00:00", "2026-08-12T09:00:00Z"))
    }

    @Test
    fun `an unreadable timestamp falls back to writing rather than stranding the device`() {
        assertTrue(shouldOverwriteRemote(localDecidedAt = "not-a-date", remoteDecidedAt = later))
        assertTrue(shouldOverwriteRemote(localDecidedAt = later, remoteDecidedAt = "not-a-date"))
    }
}
