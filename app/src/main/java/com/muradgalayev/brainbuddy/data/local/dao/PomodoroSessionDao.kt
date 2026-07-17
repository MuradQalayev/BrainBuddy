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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<PomodoroSessionEntity>)

    @Update
    suspend fun updateSession(session: PomodoroSessionEntity)

    @Query("SELECT * FROM pomodoro_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): PomodoroSessionEntity?

    @Query("SELECT * FROM pomodoro_sessions WHERE userId = :userId ORDER BY startTime DESC")
    fun getAllSessions(userId: String): Flow<List<PomodoroSessionEntity>>

    @Query("SELECT * FROM pomodoro_sessions WHERE userId = :userId AND sessionType = :type ORDER BY startTime DESC")
    fun getSessionsByType(userId: String, type: String): Flow<List<PomodoroSessionEntity>>

    @Query("SELECT * FROM pomodoro_sessions WHERE userId = :userId AND completionStatus = :status ORDER BY startTime DESC")
    fun getSessionsByStatus(userId: String, status: String): Flow<List<PomodoroSessionEntity>>

    @Query("DELETE FROM pomodoro_sessions WHERE id = :id")
    suspend fun deleteSession(id: String)

    @Query("DELETE FROM pomodoro_sessions WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("""
    SELECT COALESCE(SUM(actualDurationMs), 0)
    FROM pomodoro_sessions
    WHERE userId = :userId
      AND sessionType = :sessionType
      AND completionStatus = :completionStatus
      AND endTime BETWEEN :startOfDay AND :endOfDay
""")
    suspend fun getCompletedDurationForDay(
        userId: String,
        sessionType: String,
        completionStatus: String,
        startOfDay: Long,
        endOfDay: Long
    ): Long

    /** Flow variant so ViewModels can subscribe instead of polling. */
    @Query("""
    SELECT COALESCE(SUM(actualDurationMs), 0)
    FROM pomodoro_sessions
    WHERE userId = :userId
      AND sessionType = :sessionType
      AND completionStatus = :completionStatus
      AND endTime BETWEEN :startOfDay AND :endOfDay
""")
    fun observeCompletedDurationForRange(
        userId: String,
        sessionType: String,
        completionStatus: String,
        startOfDay: Long,
        endOfDay: Long
    ): Flow<Long>

    @Query("""
    SELECT COUNT(*)
    FROM pomodoro_sessions
    WHERE userId = :userId
      AND sessionType = :sessionType
      AND completionStatus = :completionStatus
      AND endTime BETWEEN :startOfDay AND :endOfDay
""")
    suspend fun getCompletedSessionsCountForDay(
        userId: String,
        sessionType: String,
        completionStatus: String,
        startOfDay: Long,
        endOfDay: Long
    ): Int

    @Query("""
    SELECT * FROM pomodoro_sessions
    WHERE userId = :userId
      AND completionStatus = :status
    ORDER BY endTime DESC
    LIMIT :limit
""")
    fun getRecentCompletedSessions(
        userId: String,
        status: String,
        limit: Int
    ): Flow<List<PomodoroSessionEntity>>
}
