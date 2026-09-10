package com.muradgalayev.brainbuddy.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

// one learned 'when do I usually do this' habit. stored as running circular sums rather than a
// log of past times, which is what keeps it cheap: recording an observation is an O(1) update
// to one small row, and the table never grows with usage, so a heavy user ends up with a few
// dozen rows rather than thousands.
// keyed on the pair (habit, day type), so weekday and weekend routines are learned separately
// without a second table
@Entity(tableName = "activity_time_stats")
data class ActivityTimeStatEntity(
    // composite of the three identifying columns. Room supports composite primary keys, but a
    // single opaque id is what makes the Supabase upsert, which conflicts on the primary key, a
    // one-liner
    @PrimaryKey
    val id: String,
    val userId: String,
    // title:dinner or kind:dinner, see HabitKey
    val habitKey: String,
    // any, weekday or weekend
    val dayType: String,
    val sinSum: Double,
    val cosSum: Double,
    val weightSum: Double,
    val durationWeightedSum: Double,
    val sampleCount: Int,
    // epoch millis of the newest observation, the anchor recency decay runs from
    val lastObservedAt: Long,
    val syncStatus: String = SyncStatus.PENDING_INSERT.name,
    val lastModifiedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        fun idFor(userId: String, habitKey: String, dayType: String): String =
            "$userId|$habitKey|$dayType"
    }
}
