package com.muradgalayev.brainbuddy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.muradgalayev.brainbuddy.data.local.entity.CalendarEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: CalendarEventEntity)

    @Update
    suspend fun updateEvent(event: CalendarEventEntity)

    @Query("SELECT * FROM calendar_events WHERE userId = :userId ORDER BY startTime ASC")
    fun getAllEvents(userId: String): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE id = :id AND userId = :userId")
    suspend fun getEventById(id: String, userId: String): CalendarEventEntity?

    // ISO datetimes sort lexicographically, so a prefix LIKE picks every event starting on a date
    @Query(
        "SELECT * FROM calendar_events WHERE userId = :userId AND startTime LIKE :datePrefix " +
            "ORDER BY startTime ASC"
    )
    fun getEventsByDatePrefix(datePrefix: String, userId: String): Flow<List<CalendarEventEntity>>

    @Query(
        "SELECT * FROM calendar_events WHERE userId = :userId " +
            "AND startTime >= :startInclusive AND startTime < :endExclusive " +
            "ORDER BY startTime ASC"
    )
    fun getEventsInRange(
        startInclusive: String,
        endExclusive: String,
        userId: String
    ): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE syncStatus != 'SYNCED' AND userId = :userId")
    suspend fun getPendingSyncItems(userId: String): List<CalendarEventEntity>

    // one-shot chronological read, used to seed the learned event timings from history the user
    // already has. ascending order is required rather than incidental: the stats are replayed in
    // the order the events happened, so recency decay lands where it would have if they'd been
    // recorded live
    @Query(
        "SELECT * FROM calendar_events WHERE userId = :userId AND startTime >= :fromIso " +
            "ORDER BY startTime ASC LIMIT :limit"
    )
    suspend fun getEventsSince(
        userId: String,
        fromIso: String,
        limit: Int
    ): List<CalendarEventEntity>

    @Query("DELETE FROM calendar_events WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: String, userId: String)

    @Query("UPDATE calendar_events SET syncStatus = :status WHERE id = :id AND userId = :userId")
    suspend fun updateSyncStatus(id: String, userId: String, status: String)

    @Query("DELETE FROM calendar_events WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
