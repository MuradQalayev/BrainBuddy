package com.muradgalayev.brainbuddy.data.repository

import com.muradgalayev.brainbuddy.data.local.dao.PomodoroSessionDao
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class PomodoroRepository @Inject constructor(
    private val pomodoroSessionDao: PomodoroSessionDao
) {
    fun getAllSessions(): Flow<List<PomodoroSessionEntity>> =
        pomodoroSessionDao.getAllSessions()

    fun getSessionsByType(type: String): Flow<List<PomodoroSessionEntity>> =
        pomodoroSessionDao.getSessionsByType(type)

    fun getSessionsByStatus(status: String): Flow<List<PomodoroSessionEntity>> =
        pomodoroSessionDao.getSessionsByStatus(status)

    suspend fun getSessionById(id: String): PomodoroSessionEntity? =
        pomodoroSessionDao.getSessionById(id)

    suspend fun insertSession(session: PomodoroSessionEntity) =
        pomodoroSessionDao.insertSession(session)

    suspend fun updateSession(session: PomodoroSessionEntity) =
        pomodoroSessionDao.updateSession(session)

    suspend fun deleteSession(id: String) =
        pomodoroSessionDao.deleteSession(id)
}
