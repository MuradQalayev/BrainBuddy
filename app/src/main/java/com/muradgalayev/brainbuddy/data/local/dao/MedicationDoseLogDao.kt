package com.muradgalayev.brainbuddy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.muradgalayev.brainbuddy.data.local.entity.MedicationDoseLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDoseLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: MedicationDoseLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<MedicationDoseLogEntity>)

    // only the ticks that are still taken, which is what the UI actually draws
    @Query("SELECT logKey FROM medication_dose_logs WHERE userId = :userId AND taken = 1")
    fun observeTakenKeys(userId: String): Flow<List<String>>

    @Query("SELECT * FROM medication_dose_logs WHERE userId = :userId")
    suspend fun getAll(userId: String): List<MedicationDoseLogEntity>

    @Query("SELECT * FROM medication_dose_logs WHERE syncStatus != 'SYNCED' AND userId = :userId")
    suspend fun getPendingSyncItems(userId: String): List<MedicationDoseLogEntity>

    @Query("UPDATE medication_dose_logs SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: String)

    @Query("SELECT COUNT(*) FROM medication_dose_logs WHERE userId = :userId")
    suspend fun countForUser(userId: String): Int

    @Query("DELETE FROM medication_dose_logs WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)
}
