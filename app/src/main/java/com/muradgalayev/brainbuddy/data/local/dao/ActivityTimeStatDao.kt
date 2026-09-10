package com.muradgalayev.brainbuddy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.muradgalayev.brainbuddy.data.local.entity.ActivityTimeStatEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityTimeStatDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(stat: ActivityTimeStatEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(stats: List<ActivityTimeStatEntity>)

    @Query("SELECT * FROM activity_time_stats WHERE userId = :userId")
    fun observeAll(userId: String): Flow<List<ActivityTimeStatEntity>>

    @Query("SELECT * FROM activity_time_stats WHERE userId = :userId")
    suspend fun getAll(userId: String): List<ActivityTimeStatEntity>

    @Query("SELECT * FROM activity_time_stats WHERE id = :id")
    suspend fun getById(id: String): ActivityTimeStatEntity?

    @Query("SELECT * FROM activity_time_stats WHERE syncStatus != 'SYNCED' AND userId = :userId")
    suspend fun getPendingSyncItems(userId: String): List<ActivityTimeStatEntity>

    @Query("UPDATE activity_time_stats SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    // drops habits whose evidence has decayed to nothing. nothing depends on them, since a
    // zero-weight row scores identically to an absent one, so this is purely housekeeping to stop
    // a long-lived account carrying dead keys forever
    @Query("DELETE FROM activity_time_stats WHERE userId = :userId AND lastObservedAt < :before")
    suspend fun pruneOlderThan(userId: String, before: Long)

    @Query("DELETE FROM activity_time_stats WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
