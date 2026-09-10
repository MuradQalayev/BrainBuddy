package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.muradgalayev.brainbuddy.data.local.dao.PomodoroSessionDao
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroCompletionStatus
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionEntity
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType
import com.muradgalayev.brainbuddy.data.mapper.toDomain
import com.muradgalayev.brainbuddy.data.mapper.toDto
import com.muradgalayev.brainbuddy.data.mapper.toEntity
import com.muradgalayev.brainbuddy.data.remote.SupabasePomodoroSessionDataSource
import com.muradgalayev.brainbuddy.domain.model.PomodoroSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PomodoroRepository @Inject constructor(
    private val pomodoroSessionDao: PomodoroSessionDao,
    private val remoteDataSource: SupabasePomodoroSessionDataSource,
    private val authRepository: AuthRepository,
) {
    companion object {
        private const val TAG = "PomodoroRepository"
    }

    private fun getCurrentUserId(): String? = authRepository.getCurrentOrCachedUserId()

    fun getAllSessions(): Flow<List<PomodoroSession>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return pomodoroSessionDao.getAllSessions(userId).map { sessions ->
            sessions.map { it.toDomain() }
        }
    }

    fun getRecentCompletedSessions(limit: Int): Flow<List<PomodoroSession>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return pomodoroSessionDao.getRecentCompletedSessions(
            userId = userId,
            status = PomodoroCompletionStatus.COMPLETED.name,
            limit = limit
        ).map { list ->
            list.map { it.toDomain() }
        }
    }

    // completed focus sessions in a window, the raw material for the history chart
    fun observeCompletedFocusSessionsInRange(
        startMs: Long,
        endMs: Long
    ): Flow<List<PomodoroSession>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return pomodoroSessionDao.observeCompletedSessionsInRange(
            userId = userId,
            sessionType = PomodoroSessionType.FOCUS.name,
            completionStatus = PomodoroCompletionStatus.COMPLETED.name,
            startMs = startMs,
            endMs = endMs
        ).map { list -> list.map { it.toDomain() } }
    }

    fun getSessionsByType(type: String): Flow<List<PomodoroSession>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return pomodoroSessionDao.getSessionsByType(userId, type).map { sessions ->
            sessions.map { it.toDomain() }
        }
    }

    fun getSessionsByStatus(status: String): Flow<List<PomodoroSession>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return pomodoroSessionDao.getSessionsByStatus(userId, status).map { sessions ->
            sessions.map { it.toDomain() }
        }
    }

    suspend fun getSessionById(id: String): PomodoroSession? =
        pomodoroSessionDao.getSessionById(id)?.toDomain()

    suspend fun insertSession(session: PomodoroSession) {
        val userId = getCurrentUserId() ?: return
        val entity = session.toEntity().copy(userId = userId)
        pomodoroSessionDao.insertSession(entity)
        tryRemoteUpsert(entity, userId)
    }

    suspend fun updateSession(session: PomodoroSession) {
        val userId = getCurrentUserId() ?: return
        val entity = session.toEntity().copy(userId = userId)
        pomodoroSessionDao.updateSession(entity)
        tryRemoteUpsert(entity, userId)
    }

    suspend fun deleteSession(id: String) {
        pomodoroSessionDao.deleteSession(id)
        tryRemoteDelete(id)
    }

    suspend fun getCompletedFocusMinutesForRange(
        startMs: Long,
        endMs: Long
    ): Int {
        val userId = getCurrentUserId() ?: return 0
        val totalMs = pomodoroSessionDao.getCompletedDurationForDay(
            userId = userId,
            sessionType = PomodoroSessionType.FOCUS.name,
            completionStatus = PomodoroCompletionStatus.COMPLETED.name,
            startOfDay = startMs,
            endOfDay = endMs
        )
        return (totalMs / 60000L).toInt()
    }

    // emits whenever completed focus sessions in the range change, so no polling
    fun observeCompletedFocusMinutesForRange(startMs: Long, endMs: Long): Flow<Int> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return pomodoroSessionDao.observeCompletedDurationForRange(
            userId = userId,
            sessionType = PomodoroSessionType.FOCUS.name,
            completionStatus = PomodoroCompletionStatus.COMPLETED.name,
            startOfDay = startMs,
            endOfDay = endMs
        ).map { (it / 60000L).toInt() }
    }

    suspend fun getCompletedFocusMinutesForDay(
        startOfDay: Long,
        endOfDay: Long
    ): Int = getCompletedFocusMinutesForRange(startOfDay, endOfDay)

    suspend fun sync() {
        val userId = getCurrentUserId() ?: return
        try {
            val remoteItems = remoteDataSource.getAll(userId)
            if (remoteItems.isNotEmpty()) {
                pomodoroSessionDao.insertSessions(remoteItems.map { it.toEntity() })
            }
        } catch (e: Exception) {
            Log.w(TAG, "Pull remote pomodoro sessions failed: ${e.message}")
        }
    }

    suspend fun clearLocalForUser(userId: String) {
        pomodoroSessionDao.deleteAllForUser(userId)
    }

    private suspend fun tryRemoteUpsert(entity: PomodoroSessionEntity, userId: String) {
        try {
            remoteDataSource.upsert(entity.toDto(userId))
        } catch (e: Exception) {
            Log.w(TAG, "Remote upsert failed for session ${entity.id}: ${e.message}")
        }
    }

    private suspend fun tryRemoteDelete(id: String) {
        try {
            remoteDataSource.delete(id)
        } catch (e: Exception) {
            Log.w(TAG, "Remote delete failed for session $id: ${e.message}")
        }
    }
}
