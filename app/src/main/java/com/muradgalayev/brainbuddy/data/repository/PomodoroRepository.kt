package com.muradgalayev.brainbuddy.data.repository

import com.muradgalayev.brainbuddy.data.local.dao.PomodoroSessionDao
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroCompletionStatus
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType
import com.muradgalayev.brainbuddy.data.mapper.toDomain
import com.muradgalayev.brainbuddy.data.mapper.toEntity
import com.muradgalayev.brainbuddy.domain.model.PomodoroSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PomodoroRepository @Inject constructor(
    private val pomodoroSessionDao: PomodoroSessionDao
) {
    fun getAllSessions(): Flow<List<PomodoroSession>> =
        pomodoroSessionDao.getAllSessions().map { sessions ->
            sessions.map { it.toDomain() }
        }
    fun getRecentCompletedSessions(limit: Int): Flow<List<PomodoroSession>> =
        pomodoroSessionDao.getRecentCompletedSessions(
            status = PomodoroCompletionStatus.COMPLETED.name,
            limit = limit
        ).map { list ->
            list.map { it.toDomain() }
        }

    fun getSessionsByType(type: String): Flow<List<PomodoroSession>> =
        pomodoroSessionDao.getSessionsByType(type).map { sessions ->
            sessions.map { it.toDomain() }
        }

    fun getSessionsByStatus(status: String): Flow<List<PomodoroSession>> =
        pomodoroSessionDao.getSessionsByStatus(status).map { sessions ->
            sessions.map { it.toDomain() }
        }

    suspend fun getSessionById(id: String): PomodoroSession? =
        pomodoroSessionDao.getSessionById(id)?.toDomain()

    suspend fun insertSession(session: PomodoroSession) =
        pomodoroSessionDao.insertSession(session.toEntity())

    suspend fun updateSession(session: PomodoroSession) =
        pomodoroSessionDao.updateSession(session.toEntity())

    suspend fun deleteSession(id: String) =
        pomodoroSessionDao.deleteSession(id)

    suspend fun getCompletedFocusMinutesForDay(
        startOfDay: Long,
        endOfDay: Long
    ): Int {
        val totalMs = pomodoroSessionDao.getCompletedDurationForDay(
            sessionType = PomodoroSessionType.FOCUS.name,
            completionStatus = PomodoroCompletionStatus.COMPLETED.name,
            startOfDay = startOfDay,
            endOfDay = endOfDay
        )
        return (totalMs / 60000L).toInt()
    }



}