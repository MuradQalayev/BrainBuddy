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

    // ISO datetimes sort lexicographically, so prefix LIKE '2026-05-05%' picks
    // every event that starts on that date.
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

    @Query("DELETE FROM calendar_events WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: String, userId: String)

    @Query("UPDATE calendar_events SET syncStatus = :status WHERE id = :id AND userId = :userId")
    suspend fun updateSyncStatus(id: String, userId: String, status: String)

    @Query("DELETE FROM calendar_events WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
