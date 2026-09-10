package com.muradgalayev.brainbuddy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// one 'I took this dose' tick. these used to live only in DataStore as a set of strings, so a
// user's entire medication history vanished on reinstall and never followed them to a second
// device, while the medication list synced fine, which made the app look like it was keeping
// records it wasn't.
// taken is stored rather than the row being deleted on un-tick, because a deletion and a
// never-recorded dose are indistinguishable once synced: without it, un-ticking on one device
// would be silently undone by the next pull from another
@Entity(tableName = "medication_dose_logs")
data class MedicationDoseLogEntity(
    // userId|logKey, see logKey
    @PrimaryKey
    val id: String,
    val userId: String,
    // date|medicationId|slot, the key the UI already used in DataStore. kept verbatim so the
    // one-time migration is a straight copy and existing ticks survive the move
    val logKey: String,
    val taken: Boolean,
    val updatedAt: Long = System.currentTimeMillis(),
    val syncStatus: String = SyncStatus.PENDING_INSERT.name,
) {
    companion object {
        fun idFor(userId: String, logKey: String): String = "$userId|$logKey"
    }
}
