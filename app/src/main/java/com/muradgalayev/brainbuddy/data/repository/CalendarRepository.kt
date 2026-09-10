package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.muradgalayev.brainbuddy.data.local.dao.CalendarEventDao
import com.muradgalayev.brainbuddy.data.local.dao.CalendarSubtaskDao
import com.muradgalayev.brainbuddy.data.local.entity.CalendarEventEntity
import com.muradgalayev.brainbuddy.data.local.entity.CalendarSubtaskEntity
import com.muradgalayev.brainbuddy.data.local.entity.SyncStatus
import com.muradgalayev.brainbuddy.data.mapper.toDomain
import com.muradgalayev.brainbuddy.data.mapper.toDto
import com.muradgalayev.brainbuddy.data.mapper.toEntity
import com.muradgalayev.brainbuddy.data.notifications.ReminderScheduler
import com.muradgalayev.brainbuddy.data.remote.SupabaseCalendarEventDataSource
import com.muradgalayev.brainbuddy.data.remote.SupabaseCalendarSubtaskDataSource
import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import com.muradgalayev.brainbuddy.domain.model.CalendarSubtask
import com.muradgalayev.brainbuddy.domain.model.SubtaskKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.muradgalayev.brainbuddy.data.mapper.toEntity as dtoToEntity
import com.muradgalayev.brainbuddy.data.mapper.toDto as subtaskEntityToDto
import com.muradgalayev.brainbuddy.data.mapper.toEntity as subtaskDtoToEntity

@Singleton
class CalendarRepository @Inject constructor(
    private val calendarEventDao: CalendarEventDao,
    private val calendarSubtaskDao: CalendarSubtaskDao,
    private val remoteDataSource: SupabaseCalendarEventDataSource,
    private val subtaskRemoteDataSource: SupabaseCalendarSubtaskDataSource,
    private val authRepository: AuthRepository,
    private val reminderScheduler: ReminderScheduler,
) {
    companion object {
        private const val TAG = "CalendarRepository"
    }

    private fun getCurrentUserId(): String? = authRepository.getCurrentOrCachedUserId()

    // read

    // last emission per date, kept for the life of the process. the Calendar ViewModel is scoped
    // to its nav back-stack entry, so every tab-tap rebuilt it and started from an empty list, and
    // the day list flashed its empty state before Room's first emission landed. that read as
    // fetching even though everything was already local. seeding synchronously from here removes
    // the flash, and the flow still arrives right behind it and corrects anything stale
    private val eventsByDateCache = java.util.concurrent.ConcurrentHashMap<String, List<CalendarEvent>>()

    @Volatile
    private var subtaskCache: List<CalendarSubtask>? = null

    // synchronous peek for instant seeding. null means nothing observed yet
    fun peekEventsForDate(date: String): List<CalendarEvent>? = eventsByDateCache[date]

    // synchronous peek at every subtask seen this process
    fun peekSubtasks(): List<CalendarSubtask>? = subtaskCache

    fun getEventsByDate(date: String): Flow<List<CalendarEvent>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        // ISO datetimes start with the date, so a YYYY-MM-DD% prefix match works
        return calendarEventDao.getEventsByDatePrefix("$date%", userId).map { items ->
            items.filter { it.syncStatus != SyncStatus.PENDING_DELETE.name }
                .map { it.toDomain() }
                .also { eventsByDateCache[date] = it }
        }
    }

    // events whose startTime falls in [startDate, endDate] inclusive. plain YYYY-MM-DD strings
    fun getEventsInDateRange(startDate: String, endDate: String): Flow<List<CalendarEvent>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        // the day after endDate at 00:00, so the string comparison stops correctly
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

    // write

    suspend fun insertEvent(event: CalendarEvent) {
        val userId = getCurrentUserId() ?: return
        val entity = event.toEntity(userId).copy(
            syncStatus = SyncStatus.PENDING_INSERT.name,
            lastModifiedAt = System.currentTimeMillis()
        )
        calendarEventDao.insertEvent(entity)
        reminderScheduler.scheduleForItem(
            event.id, event.title, event.startTime,
            category = com.muradgalayev.brainbuddy.data.notifications.ReminderCategory.CALENDAR
        )
        tryRemoteUpsert(entity)
    }

    // Room only, no per-row network call. see TodoRepository.insertTodoItemLocal
    suspend fun insertEventLocal(event: CalendarEvent) {
        val userId = getCurrentUserId() ?: return
        val entity = event.toEntity(userId).copy(
            syncStatus = SyncStatus.PENDING_INSERT.name,
            lastModifiedAt = System.currentTimeMillis(),
        )
        calendarEventDao.insertEvent(entity)
    }

    // Room only. see insertEventLocal
    suspend fun deleteEventLocal(event: CalendarEvent) {
        val userId = getCurrentUserId() ?: return
        val entity = event.toEntity(userId).copy(
            syncStatus = SyncStatus.PENDING_DELETE.name,
        )
        calendarEventDao.updateEvent(entity)
        calendarSubtaskDao.deleteForEvent(event.id, userId)
        reminderScheduler.cancelForItem(event.id)
    }

    suspend fun updateEvent(event: CalendarEvent) {
        val userId = getCurrentUserId() ?: return
        val entity = event.toEntity(userId).copy(
            syncStatus = SyncStatus.PENDING_UPDATE.name,
            lastModifiedAt = System.currentTimeMillis()
        )
        calendarEventDao.updateEvent(entity)
        reminderScheduler.cancelForItem(event.id)
        reminderScheduler.scheduleForItem(
            event.id, event.title, event.startTime,
            category = com.muradgalayev.brainbuddy.data.notifications.ReminderCategory.CALENDAR
        )
        tryRemoteUpsert(entity)
    }

    // ticks an event off or un-ticks it without touching its other fields. reminders are cancelled
    // on completion: nudging someone about something they've already finished is the fastest way
    // to teach them to ignore the notification. re-opening an event re-arms it
    suspend fun setEventCompleted(eventId: String, completed: Boolean) {
        val userId = getCurrentUserId() ?: return
        val existing = calendarEventDao.getEventById(eventId, userId) ?: return
        val entity = existing.copy(
            completed = completed,
            syncStatus = SyncStatus.PENDING_UPDATE.name,
            lastModifiedAt = System.currentTimeMillis(),
        )
        calendarEventDao.updateEvent(entity)
        if (completed) {
            reminderScheduler.cancelForItem(eventId)
        } else {
            reminderScheduler.scheduleForItem(
                eventId, entity.title, entity.startTime,
                category = com.muradgalayev.brainbuddy.data.notifications.ReminderCategory.CALENDAR,
            )
        }
        tryRemoteUpsert(entity)
    }

    suspend fun deleteEvent(event: CalendarEvent) {
        val userId = getCurrentUserId() ?: return
        val entity = event.toEntity(userId).copy(
            syncStatus = SyncStatus.PENDING_DELETE.name
        )
        calendarEventDao.updateEvent(entity)
        // the remote DB has ON DELETE CASCADE so the server cleans up subtasks, and locally we drop
        // them too, so individual deletes never need syncing
        calendarSubtaskDao.deleteForEvent(event.id, userId)
        reminderScheduler.cancelForItem(event.id)
        tryRemoteDelete(entity.id, userId)
    }

    // subtasks (local-only)

    fun observeSubtasksForUser(): Flow<List<CalendarSubtask>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return calendarSubtaskDao.observeAllForUser(userId).map { items ->
            items.map { it.toSubtaskDomain() }.also { subtaskCache = it }
        }
    }

    fun observeSubtasksForEvent(eventId: String): Flow<List<CalendarSubtask>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return calendarSubtaskDao.observeForEvent(eventId, userId).map { items ->
            items.map { it.toSubtaskDomain() }
        }
    }

    suspend fun replaceSubtasks(eventId: String, subtasks: List<CalendarSubtask>) {
        val userId = getCurrentUserId() ?: return
        // diff: rows missing from the new list become PENDING_DELETE, remaining rows are upserted as
        // PENDING_INSERT or PENDING_UPDATE so the server stays in lockstep
        val existing = calendarSubtaskDao.getAllForEvent(eventId, userId)
        val newIds = subtasks.map { it.id }.toSet()
        val toDelete = existing.filter { it.id !in newIds }
        for (gone in toDelete) {
            if (gone.syncStatus == SyncStatus.PENDING_INSERT.name) {
                // never made it to the server, so just drop it locally
                calendarSubtaskDao.deleteById(gone.id, userId)
            } else {
                calendarSubtaskDao.markPendingDelete(gone.id, userId)
            }
        }
        if (subtasks.isNotEmpty()) {
            val existingMap = existing.associateBy { it.id }
            val rows = subtasks.mapIndexed { index, s ->
                val prior = existingMap[s.id]
                val status = when {
                    prior == null -> SyncStatus.PENDING_INSERT.name
                    prior.syncStatus == SyncStatus.PENDING_INSERT.name -> SyncStatus.PENDING_INSERT.name
                    else -> SyncStatus.PENDING_UPDATE.name
                }
                CalendarSubtaskEntity(
                    id = s.id,
                    userId = userId,
                    eventId = eventId,
                    title = s.title,
                    durationMinutes = s.durationMinutes,
                    orderIndex = index,
                    completed = s.completed,
                    kind = s.kind.name,
                    syncStatus = status,
                    lastModifiedAt = System.currentTimeMillis(),
                )
            }
            calendarSubtaskDao.insertAll(rows)
            tryFlushSubtasks(rows, userId)
        }
        // try to push any pending deletes too, so the UI stays in sync
        if (toDelete.isNotEmpty()) {
            tryFlushSubtaskDeletes(toDelete.map { it.id }, userId)
        }
    }

    suspend fun setSubtaskCompleted(subtaskId: String, completed: Boolean) {
        val userId = getCurrentUserId() ?: return
        calendarSubtaskDao.setCompleted(subtaskId, userId, completed)
        val updated = calendarSubtaskDao.getById(subtaskId, userId) ?: return
        try {
            subtaskRemoteDataSource.upsert(updated.subtaskEntityToDto())
            calendarSubtaskDao.updateSyncStatus(subtaskId, userId, SyncStatus.SYNCED.name)
        } catch (e: Exception) {
            Log.w(TAG, "Subtask remote upsert failed, will retry: ${e.message}")
        }
    }

    private suspend fun tryFlushSubtasks(rows: List<CalendarSubtaskEntity>, userId: String) {
        try {
            subtaskRemoteDataSource.upsertAll(rows.map { it.subtaskEntityToDto() })
            for (row in rows) {
                calendarSubtaskDao.updateSyncStatus(row.id, userId, SyncStatus.SYNCED.name)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Subtask batch upsert failed, will retry: ${e.message}")
        }
    }

    private suspend fun tryFlushSubtaskDeletes(ids: List<String>, userId: String) {
        for (id in ids) {
            try {
                subtaskRemoteDataSource.delete(id)
                calendarSubtaskDao.deleteById(id, userId)
            } catch (e: Exception) {
                Log.w(TAG, "Subtask delete failed, will retry: ${e.message}")
            }
        }
    }

    private fun CalendarSubtaskEntity.toSubtaskDomain(): CalendarSubtask = CalendarSubtask(
        id = id,
        eventId = eventId,
        title = title,
        durationMinutes = durationMinutes,
        orderIndex = orderIndex,
        completed = completed,
        kind = runCatching { SubtaskKind.valueOf(kind) }.getOrDefault(SubtaskKind.FOCUS),
    )

    // sync

    suspend fun sync() {
        val userId = getCurrentUserId() ?: return
        try {
            pushPendingChanges(userId)
            pullRemoteChanges(userId)
            pushPendingSubtaskChanges(userId)
            pullRemoteSubtasks(userId)
        } catch (e: Exception) {
            Log.w(TAG, "Sync failed: ${e.message}")
        }
    }

    private suspend fun pushPendingSubtaskChanges(userId: String) {
        val pending = calendarSubtaskDao.getPendingSyncItems(userId)
        for (item in pending) {
            try {
                when (item.syncStatus) {
                    SyncStatus.PENDING_INSERT.name,
                    SyncStatus.PENDING_UPDATE.name -> {
                        subtaskRemoteDataSource.upsert(item.subtaskEntityToDto())
                        calendarSubtaskDao.updateSyncStatus(item.id, userId, SyncStatus.SYNCED.name)
                    }
                    SyncStatus.PENDING_DELETE.name -> {
                        subtaskRemoteDataSource.delete(item.id)
                        calendarSubtaskDao.deleteById(item.id, userId)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync subtask ${item.id}: ${e.message}")
            }
        }
    }

    private suspend fun pullRemoteSubtasks(userId: String) {
        try {
            val remote = subtaskRemoteDataSource.getAll(userId)
            val pendingIds = calendarSubtaskDao.getPendingSyncItems(userId).map { it.id }.toSet()
            for (dto in remote) {
                if (dto.id !in pendingIds) {
                    calendarSubtaskDao.insert(dto.subtaskDtoToEntity().copy(userId = userId))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to pull remote subtasks: ${e.message}")
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
        calendarSubtaskDao.deleteAllForUser(userId)
    }
}
