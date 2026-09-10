package com.muradgalayev.brainbuddy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.muradgalayev.brainbuddy.data.local.entity.AppModeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppModeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mode: AppModeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(modes: List<AppModeEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(mode: AppModeEntity): Long

    // a pull may update a settled row, but it must never replace an offline mutation. keeping the
    // status check in this SQL statement makes that rule atomic with the write
    @Query(
        """
        UPDATE app_modes SET
            name = :name,
            icon = :icon,
            accent = :accent,
            isBuiltIn = :isBuiltIn,
            sortIndex = :sortIndex,
            scheduleJson = :scheduleJson,
            overridesJson = :overridesJson,
            lastModifiedAt = :lastModifiedAt
        WHERE userId = :userId AND id = :id AND syncStatus = 'SYNCED'
        """,
    )
    suspend fun updateSyncedFromRemote(
        userId: String,
        id: String,
        name: String,
        icon: String,
        accent: String?,
        isBuiltIn: Boolean,
        sortIndex: Int,
        scheduleJson: String?,
        overridesJson: String,
        lastModifiedAt: Long,
    )

    @Query("SELECT * FROM app_modes WHERE userId = :userId AND syncStatus != 'PENDING_DELETE' ORDER BY sortIndex ASC")
    fun observeForUser(userId: String): Flow<List<AppModeEntity>>

    @Query("SELECT * FROM app_modes WHERE userId = :userId AND syncStatus != 'PENDING_DELETE' ORDER BY sortIndex ASC")
    suspend fun getForUser(userId: String): List<AppModeEntity>

    @Query("SELECT * FROM app_modes WHERE id = :id AND userId = :userId")
    suspend fun getById(id: String, userId: String): AppModeEntity?

    @Query("SELECT COUNT(*) FROM app_modes WHERE userId = :userId")
    suspend fun countForUser(userId: String): Int

    @Query("UPDATE app_modes SET syncStatus = 'PENDING_DELETE', lastModifiedAt = :revision WHERE id = :id AND userId = :userId")
    suspend fun markPendingDelete(id: String, userId: String, revision: Long)

    @Query("DELETE FROM app_modes WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: String, userId: String)

    // delete a tombstone only if no newer save replaced it while the remote delete ran
    @Query(
        """
        DELETE FROM app_modes
        WHERE id = :id
          AND userId = :userId
          AND syncStatus = 'PENDING_DELETE'
          AND lastModifiedAt = :expectedLastModifiedAt
        """,
    )
    suspend fun deletePendingRevision(
        id: String,
        userId: String,
        expectedLastModifiedAt: Long,
    ): Int

    // the status predicate closes the race with an edit made while a pull is reconciling
    @Query("DELETE FROM app_modes WHERE id = :id AND userId = :userId AND syncStatus = 'SYNCED'")
    suspend fun deleteSyncedById(id: String, userId: String)

    @Query("SELECT * FROM app_modes WHERE syncStatus != 'SYNCED' AND userId = :userId")
    suspend fun getPendingSyncItems(userId: String): List<AppModeEntity>

    // acknowledge exactly the row revision that was sent. a save can land while the network upsert
    // is in flight, and updating by id alone would mark that newer, never-sent revision as SYNCED.
    // keeping both the previous status and the local revision in the predicate makes the
    // acknowledgement an atomic compare-and-set
    @Query(
        """
        UPDATE app_modes SET syncStatus = :newStatus
        WHERE id = :id
          AND userId = :userId
          AND syncStatus = :expectedStatus
          AND lastModifiedAt = :expectedLastModifiedAt
        """,
    )
    suspend fun acknowledgeSync(
        id: String,
        userId: String,
        expectedStatus: String,
        expectedLastModifiedAt: Long,
        newStatus: String,
    ): Int

    @Query("DELETE FROM app_modes WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Transaction
    suspend fun mergeRemote(mode: AppModeEntity) {
        if (insertIfAbsent(mode) != -1L) return
        updateSyncedFromRemote(
            userId = mode.userId,
            id = mode.id,
            name = mode.name,
            icon = mode.icon,
            accent = mode.accent,
            isBuiltIn = mode.isBuiltIn,
            sortIndex = mode.sortIndex,
            scheduleJson = mode.scheduleJson,
            overridesJson = mode.overridesJson,
            lastModifiedAt = mode.lastModifiedAt,
        )
    }
}
