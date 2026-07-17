package com.muradgalayev.brainbuddy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.muradgalayev.brainbuddy.data.local.entity.CalendarSubtaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarSubtaskDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subtask: CalendarSubtaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subtasks: List<CalendarSubtaskEntity>)

    @Update
    suspend fun update(subtask: CalendarSubtaskEntity)

    @Query("SELECT * FROM calendar_subtasks WHERE userId = :userId AND eventId = :eventId ORDER BY orderIndex ASC")
    fun observeForEvent(eventId: String, userId: String): Flow<List<CalendarSubtaskEntity>>

    @Query("SELECT * FROM calendar_subtasks WHERE userId = :userId ORDER BY eventId, orderIndex ASC")
    fun observeAllForUser(userId: String): Flow<List<CalendarSubtaskEntity>>

    @Query("SELECT * FROM calendar_subtasks WHERE id = :id AND userId = :userId")
    suspend fun getById(id: String, userId: String): CalendarSubtaskEntity?

    @Query("UPDATE calendar_subtasks SET completed = :completed, syncStatus = :status, lastModifiedAt = :now WHERE id = :id AND userId = :userId")
    suspend fun setCompleted(
        id: String,
        userId: String,
        completed: Boolean,
        status: String = "PENDING_UPDATE",
        now: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM calendar_subtasks WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: String, userId: String)

    @Query("UPDATE calendar_subtasks SET syncStatus = :status, lastModifiedAt = :now WHERE id = :id AND userId = :userId")
    suspend fun markPendingDelete(
        id: String,
        userId: String,
        status: String = "PENDING_DELETE",
        now: Long = System.currentTimeMillis()
    )

    @Query("DELETE FROM calendar_subtasks WHERE eventId = :eventId AND userId = :userId")
    suspend fun deleteForEvent(eventId: String, userId: String)

    @Query("UPDATE calendar_subtasks SET syncStatus = :status, lastModifiedAt = :now WHERE eventId = :eventId AND userId = :userId")
    suspend fun markEventSubtasksPendingDelete(
        eventId: String,
        userId: String,
        status: String = "PENDING_DELETE",
        now: Long = System.currentTimeMillis()
    )

    @Query("SELECT * FROM calendar_subtasks WHERE userId = :userId AND eventId = :eventId")
    suspend fun getAllForEvent(eventId: String, userId: String): List<CalendarSubtaskEntity>

    @Query("SELECT * FROM calendar_subtasks WHERE syncStatus != 'SYNCED' AND userId = :userId")
    suspend fun getPendingSyncItems(userId: String): List<CalendarSubtaskEntity>

    @Query("UPDATE calendar_subtasks SET syncStatus = :status WHERE id = :id AND userId = :userId")
    suspend fun updateSyncStatus(id: String, userId: String, status: String)

    @Query("DELETE FROM calendar_subtasks WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}