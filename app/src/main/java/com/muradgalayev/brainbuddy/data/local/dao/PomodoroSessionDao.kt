package com.muradgalayev.brainbuddy.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PomodoroSessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: PomodoroSessionEntity)

    @Update
    suspend fun updateSession(session: PomodoroSessionEntity)

    @Query("SELECT * FROM pomodoro_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): PomodoroSessionEntity?

    @Query("SELECT * FROM pomodoro_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<PomodoroSessionEntity>>

    @Query("SELECT * FROM pomodoro_sessions WHERE sessionType = :type ORDER BY startTime DESC")
    fun getSessionsByType(type: String): Flow<List<PomodoroSessionEntity>>

    @Query("SELECT * FROM pomodoro_sessions WHERE completionStatus = :status ORDER BY startTime DESC")
    fun getSessionsByStatus(status: String): Flow<List<PomodoroSessionEntity>>

    @Query("DELETE FROM pomodoro_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)
}
