package com.muradgalayev.brainbuddy.data.repository

import com.muradgalayev.brainbuddy.data.local.entity.AppModeEntity
import com.muradgalayev.brainbuddy.data.local.entity.SyncStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ModeReconciliationTest {

    @Test
    fun `remote absence removes only synced rows`() {
        val local = listOf(
            row("remote-deleted", SyncStatus.SYNCED),
            row("still-remote", SyncStatus.SYNCED),
            row("offline-new", SyncStatus.PENDING_INSERT),
            row("offline-edit", SyncStatus.PENDING_UPDATE),
            row("offline-delete", SyncStatus.PENDING_DELETE),
        )

        val missing = syncedModeIdsMissingRemotely(
            localRows = local,
            remoteIds = setOf("still-remote"),
        )

        assertEquals(setOf("remote-deleted"), missing)
    }

    @Test
    fun `built-ins seed only after a successful empty remote pull`() {
        assertEquals(false, shouldSeedBuiltInModes(localCount = 0, remotePullSucceeded = false))
        assertEquals(false, shouldSeedBuiltInModes(localCount = 1, remotePullSucceeded = true))
        assertEquals(true, shouldSeedBuiltInModes(localCount = 0, remotePullSucceeded = true))
    }

    @Test
    fun `mode revision advances when edits share a wall clock millisecond`() {
        assertEquals(1_001L, nextModeRevision(clockMillis = 1_000L, previousRevision = 1_000L))
        assertEquals(1_001L, nextModeRevision(clockMillis = 999L, previousRevision = 1_000L))
        assertEquals(1_001L, nextModeRevision(clockMillis = 1_001L, previousRevision = 1_000L))
    }

    @Test
    fun `queued remote mutation is skipped after edit or tombstone replaces its revision`() {
        val pending = row("focus", SyncStatus.PENDING_INSERT).copy(lastModifiedAt = 10L)

        assertEquals(true, pending.matchesPendingRevision(pending))
        assertEquals(
            false,
            pending.copy(lastModifiedAt = 11L).matchesPendingRevision(pending),
        )
        assertEquals(
            false,
            pending.copy(syncStatus = SyncStatus.PENDING_DELETE.name)
                .matchesPendingRevision(pending),
        )
        assertEquals(false, null.matchesPendingRevision(pending))
    }

    private fun row(id: String, status: SyncStatus) = AppModeEntity(
        id = id,
        userId = "user-a",
        name = id,
        icon = "mode",
        accent = null,
        isBuiltIn = false,
        sortIndex = 0,
        scheduleJson = null,
        overridesJson = "{}",
        syncStatus = status.name,
        lastModifiedAt = 1L,
    )
}
