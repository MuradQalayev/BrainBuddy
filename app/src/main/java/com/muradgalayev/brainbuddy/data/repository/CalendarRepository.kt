package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.muradgalayev.brainbuddy.data.local.dao.CalendarEventDao
import com.muradgalayev.brainbuddy.data.local.entity.CalendarEventEntity
import com.muradgalayev.brainbuddy.data.local.entity.SyncStatus
import com.muradgalayev.brainbuddy.data.mapper.toDomain
import com.muradgalayev.brainbuddy.data.mapper.toDto
import com.muradgalayev.brainbuddy.data.mapper.toEntity
import com.muradgalayev.brainbuddy.data.notifications.ReminderScheduler
import com.muradgalayev.brainbuddy.data.remote.SupabaseCalendarEventDataSource
import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.muradgalayev.brainbuddy.data.mapper.toEntity as dtoToEntity

@Singleton
class CalendarRepository @Inject constructor(
    private val calendarEventDao: CalendarEventDao,
    private val remoteDataSource: SupabaseCalendarEventDataSource,
    private val authRepository: AuthRepository,
    private val reminderScheduler: ReminderScheduler,
) {
    companion object {
        private const val TAG = "CalendarRepository"
    }

    private fun getCurrentUserId(): String? = authRepository.getCurrentUserId()

    // ── Read ──

    fun getEventsByDate(date: String): Flow<List<CalendarEvent>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        // ISO datetimes start with the date, so a "YYYY-MM-DD%" prefix match works.
        return calendarEventDao.getEventsByDatePrefix("$date%", userId).map { items ->
            items.filter { it.syncStatus != SyncStatus.PENDING_DELETE.name }
                .map { it.toDomain() }
        }
    }

    /**
     * Events whose `startTime` falls on a date in `[startDate, endDate]` (inclusive).
     * Pass plain `YYYY-MM-DD` strings.
     */
    fun getEventsInDateRange(startDate: String, endDate: String): Flow<List<CalendarEvent>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        // endExclusive = day after endDate at 00:00 — string comparison stops correctly.
        val endExclusive = java.time.LocalDate.parse(endDate).plusDays(1).toString() + "T00:00:00"
        val startInclusive = "${startDate}T00:00:00"
        return calendarEventDao.getEventsInRange(startInclusive, endExclusive, userId).map { items ->
            items.filter { it.syncStatus != SyncStatus.PENDING_DELETE.name }
                .map { it.toDomain() }
        }
    }

    fun getAllEvents(): Flow<List<CalendarEvent>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return calendarEventDao.getAllEvents(userId).map { items ->
            items.filter { it.syncStatus != SyncStatus.PENDING_DELETE.name }
                .map { it.toDomain() }
        }
    }

    suspend fun getEventById(id: String): CalendarEvent? {
        val userId = getCurrentUserId() ?: return null
        return calendarEventDao.getEventById(id, userId)?.toDomain()
    }

    // ── Write ──

    suspend fun insertEvent(event: CalendarEvent) {
        val userId = getCurrentUserId() ?: return
        val entity = event.toEntity(userId).copy(
            syncStatus = SyncStatus.PENDING_INSERT.name,
            lastModifiedAt = System.currentTimeMillis()
        )
        calendarEventDao.insertEvent(entity)
        reminderScheduler.scheduleForItem(event.id, event.title, event.startTime)
        tryRemoteUpsert(entity)
    }

    suspend fun updateEvent(event: CalendarEvent) {
        val userId = getCurrentUserId() ?: return
        val entity = event.toEntity(userId).copy(
            syncStatus = SyncStatus.PENDING_UPDATE.name,
            lastModifiedAt = System.currentTimeMillis()
        )
        calendarEventDao.updateEvent(entity)
        reminderScheduler.cancelForItem(event.id)
        reminderScheduler.scheduleForItem(event.id, event.title, event.startTime)
        tryRemoteUpsert(entity)
    }

    suspend fun deleteEvent(event: CalendarEvent) {
        val userId = getCurrentUserId() ?: return
        val entity = event.toEntity(userId).copy(
            syncStatus = SyncStatus.PENDING_DELETE.name
        )
        calendarEventDao.updateEvent(entity)
        reminderScheduler.cancelForItem(event.id)
        tryRemoteDelete(entity.id, userId)
    }

    // ── Sync ──

    suspend fun sync() {
        val userId = getCurrentUserId() ?: return
        try {
            pushPendingChanges(userId)
            pullRemoteChanges(userId)
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed: ${e.message}")
        }
    }

    private suspend fun pushPendingChanges(userId: String) {
        val pending = calendarEventDao.getPendingSyncItems(userId)
        for (item in pending) {
            try {
                when (item.syncStatus) {
                    SyncStatus.PENDING_INSERT.name,
                    SyncStatus.PENDING_UPDATE.name -> {
                        remoteDataSource.upsert(item.toDto(userId))
                        calendarEventDao.updateSyncStatus(item.id, userId, SyncStatus.SYNCED.name)
                    }
                    SyncStatus.PENDING_DELETE.name -> {
                        remoteDataSource.delete(item.id)
                        calendarEventDao.deleteById(item.id, userId)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync event ${item.id}: ${e.message}")
            }
        }
    }

    private suspend fun pullRemoteChanges(userId: String) {
        try {
            val remoteItems = remoteDataSource.getAll(userId)
            val pending = calendarEventDao.getPendingSyncItems(userId).map { it.id }.toSet()
            for (remote in remoteItems) {
                if (remote.id !in pending) {
                    calendarEventDao.insertEvent(remote.dtoToEntity().copy(userId = userId))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to pull remote changes: ${e.message}")
        }
    }

    private suspend fun tryRemoteUpsert(entity: CalendarEventEntity) {
        try {
            val userId = getCurrentUserId() ?: return
            remoteDataSource.upsert(entity.toDto(userId))
            calendarEventDao.updateSyncStatus(entity.id, userId, SyncStatus.SYNCED.name)
        } catch (e: Exception) {
            Log.w(TAG, "Remote upsert failed, will sync later: ${e.message}")
        }
    }

    private suspend fun tryRemoteDelete(id: String, userId: String) {
        try {
            remoteDataSource.delete(id)
            calendarEventDao.deleteById(id, userId)
        } catch (e: Exception) {
            Log.w(TAG, "Remote delete failed, will sync later: ${e.message}")
        }
    }

    suspend fun clearLocalForUser(userId: String) {
        calendarEventDao.deleteAllForUser(userId)
    }
}
