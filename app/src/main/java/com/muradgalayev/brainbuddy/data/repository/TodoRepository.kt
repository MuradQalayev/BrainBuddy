package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.muradgalayev.brainbuddy.data.local.dao.TodoItemDao
import com.muradgalayev.brainbuddy.data.local.entity.SyncStatus
import com.muradgalayev.brainbuddy.data.mapper.toDomain
import com.muradgalayev.brainbuddy.data.mapper.toDto
import com.muradgalayev.brainbuddy.data.mapper.toEntity
import com.muradgalayev.brainbuddy.data.notifications.ReminderScheduler
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.remote.SupabaseTodoDataSource
import com.muradgalayev.brainbuddy.domain.model.TodoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import com.muradgalayev.brainbuddy.data.mapper.toEntity as dtoToEntity
import kotlinx.coroutines.flow.emptyFlow

@Singleton
class TodoRepository @Inject constructor(
    private val todoItemDao: TodoItemDao,
    private val remoteDataSource: SupabaseTodoDataSource,
    private val authRepository: AuthRepository,
    private val reminderScheduler: ReminderScheduler,
    private val preferencesManager: PreferencesManager,
    // lazy on both: CalendarRepository and MedicationLogRepository sit alongside this one in the
    // graph, and reaching them eagerly risks a construction cycle
    private val calendarRepository: dagger.Lazy<CalendarRepository>,
    private val medicationLogRepository: dagger.Lazy<MedicationLogRepository>,
) {
    companion object {
        private const val TAG = "TodoRepository"
        private val MEDICATION_TODO_ID = Regex("^med-(.+)-(morning|afternoon|evening|night)-(\\d{4}-\\d{2}-\\d{2})$")
    }

    private fun getCurrentUserId(): String? = authRepository.getCurrentOrCachedUserId()

    // reads, always from Room and filtered by userId

    fun getAllTodoItems(): Flow<List<TodoItem>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return todoItemDao.getAllTodoItems(userId).map { items ->
            items.filter { it.syncStatus != SyncStatus.PENDING_DELETE.name }
                .map { it.toDomain() }
        }
    }

    fun getActiveTodoItems(): Flow<List<TodoItem>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return todoItemDao.getActiveTodoItems(userId).map { items ->
            items.filter { it.syncStatus != SyncStatus.PENDING_DELETE.name }
                .map { it.toDomain() }
        }
    }

    fun getCompletedTodoItems(): Flow<List<TodoItem>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return todoItemDao.getCompletedTodoItems(userId).map { items ->
            items.filter { it.syncStatus != SyncStatus.PENDING_DELETE.name }
                .map { it.toDomain() }
        }
    }

    fun getTodoItemsByDate(date: String): Flow<List<TodoItem>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return todoItemDao.getTodoItemsByDate(date, userId).map { items ->
            items.filter { it.syncStatus != SyncStatus.PENDING_DELETE.name }
                .map { it.toDomain() }
        }
    }

    fun getTodoItemsInRange(startDate: String, endDate: String): Flow<List<TodoItem>> {
        val userId = getCurrentUserId() ?: return emptyFlow()
        return todoItemDao.getTodoItemsInRange(startDate, endDate, userId).map { items ->
            items.filter { it.syncStatus != SyncStatus.PENDING_DELETE.name }
                .map { it.toDomain() }
        }
    }

    suspend fun getTodoItemById(id: String): TodoItem? {
        val userId = getCurrentUserId() ?: return null
        return todoItemDao.getTodoItemById(id, userId)?.toDomain()
    }

    // writes

    suspend fun insertTodoItem(todoItem: TodoItem) {
        val userId = getCurrentUserId() ?: return
        val entity = todoItem.toEntity(userId).copy(
            syncStatus = SyncStatus.PENDING_INSERT.name,
            lastModifiedAt = System.currentTimeMillis()
        )
        todoItemDao.insertTodoItem(entity)
        scheduleReminderIfDue(todoItem)
        tryRemoteUpsert(entity)
    }

    // Room only, no network call. for bulk writers like the medication rebuild. insertTodoItem
    // pushes to Supabase per row, which is right for one to-do a person just typed and ruinous
    // for fifty-six generated ones: a two-a-day medication over the fortnight window meant a
    // delete round-trip and an insert round-trip each, awaited in sequence, and the screen sat
    // there for the length of all of them. nothing is lost by skipping it, the row is marked
    // PENDING_INSERT which is exactly what sync() looks for
    suspend fun insertTodoItemLocal(todoItem: TodoItem) {
        val userId = getCurrentUserId() ?: return
        val entity = todoItem.toEntity(userId).copy(
            syncStatus = SyncStatus.PENDING_INSERT.name,
            lastModifiedAt = System.currentTimeMillis(),
        )
        todoItemDao.insertTodoItem(entity)
        scheduleReminderIfDue(todoItem)
    }

    // Room only. see insertTodoItemLocal
    suspend fun deleteTodoItemLocal(todoItem: TodoItem) {
        val userId = getCurrentUserId() ?: return
        val entity = todoItem.toEntity(userId).copy(
            syncStatus = SyncStatus.PENDING_DELETE.name,
        )
        todoItemDao.updateTodoItem(entity)
        reminderScheduler.cancelForItem(todoItem.id)
    }

    // a row deleted on the server by someone else. sync's pull never removes rows, so this is the only
    // way a to-do a connection took back leaves this phone
    suspend fun applyRemoteDelete(todoId: String) {
        val userId = getCurrentUserId() ?: return
        todoItemDao.deleteById(todoId, userId)
        reminderScheduler.cancelForItem(todoId)
    }

    suspend fun updateTodoItem(todoItem: TodoItem) {
        val userId = getCurrentUserId() ?: return
        // TodoItem carries no authorship, so toEntity() stamps createdBy with the current user. carry
        // the stored value over instead: for a task a Together connection added, overwriting it would
        // silently cut off their access to the row they created the moment the owner edited it
        val existingCreatedBy = todoItemDao.getTodoItemById(todoItem.id, userId)?.createdBy
        val entity = todoItem.toEntity(userId).copy(
            createdBy = existingCreatedBy ?: userId,
            syncStatus = SyncStatus.PENDING_UPDATE.name,
            lastModifiedAt = System.currentTimeMillis()
        )
        todoItemDao.updateTodoItem(entity)
        reminderScheduler.cancelForItem(todoItem.id)
        scheduleReminderIfDue(todoItem)
        tryRemoteUpsert(entity)
    }

    suspend fun deleteTodoItem(todoItem: TodoItem) {
        val userId = getCurrentUserId() ?: return
        val entity = todoItem.toEntity(userId).copy(
            syncStatus = SyncStatus.PENDING_DELETE.name
        )
        todoItemDao.updateTodoItem(entity)
        reminderScheduler.cancelForItem(todoItem.id)
        tryRemoteDelete(entity.id, userId)
    }

    suspend fun toggleTodoItemCompletion(id: String) {
        val userId = getCurrentUserId() ?: return
        val item = todoItemDao.getTodoItemById(id, userId) ?: return
        val updated = item.copy(
            isCompleted = !item.isCompleted,
            syncStatus = SyncStatus.PENDING_UPDATE.name,
            lastModifiedAt = System.currentTimeMillis()
        )
        todoItemDao.updateTodoItem(updated)
        medicationLogKey(id)?.let { syncMedicationDose(it, updated.isCompleted) }
        if (updated.isCompleted) reminderScheduler.cancelForItem(id)
        tryRemoteUpsert(updated)
    }

    suspend fun setTodoItemCompletion(id: String, completed: Boolean) {
        val userId = getCurrentUserId() ?: return
        val item = todoItemDao.getTodoItemById(id, userId) ?: return
        if (item.isCompleted == completed) return
        val updated = item.copy(
            isCompleted = completed,
            syncStatus = SyncStatus.PENDING_UPDATE.name,
            lastModifiedAt = System.currentTimeMillis(),
        )
        todoItemDao.updateTodoItem(updated)
        // same mirroring as the toggle above. this path is what the Medications and Calendar screens
        // call, so without it the sync only worked one way
        medicationLogKey(id)?.let { syncMedicationDose(it, completed) }
        if (completed) reminderScheduler.cancelForItem(id) else scheduleReminderIfDue(updated.toDomain())
        tryRemoteUpsert(updated)
    }

    // writes a dose to the one place that counts, and mirrors it to the calendar. this used to
    // write to DataStore while the Medications screen read Room, two stores for one fact, so
    // ticking a dose off in the to-do list left the Medications screen showing it outstanding.
    // Room is the source of truth, the DataStore copy is kept only because older code reads it.
    // the calendar write is best-effort: the event only exists when that destination is on
    private suspend fun syncMedicationDose(logKey: String, taken: Boolean) {
        runCatching { medicationLogRepository.get().setTaken(logKey, taken) }
        preferencesManager.setMedicationDoseLogged(logKey, taken)

        val parts = logKey.split('|')
        if (parts.size != 3) return
        val (date, medicationId, slot) = parts
        val eventId = MedicationTodoSyncer.MEDICATION_EVENT_PREFIX + "$medicationId-$slot-$date"
        runCatching { calendarRepository.get().setEventCompleted(eventId, taken) }
    }

    private fun medicationLogKey(todoId: String): String? {
        val match = MEDICATION_TODO_ID.matchEntire(todoId) ?: return null
        val (medicationId, slot, date) = match.destructured
        return "$date|$medicationId|$slot"
    }

    private suspend fun scheduleReminderIfDue(todo: TodoItem) {
        if (todo.isCompleted) return
        if (todo.date.isBlank() || todo.startTime.isBlank()) return
        reminderScheduler.scheduleForItem(
            itemId = todo.id,
            title = todo.title,
            dateIso = todo.date,
            timeIso = todo.startTime,
            category = com.muradgalayev.brainbuddy.data.notifications.ReminderCategory.TODO,
        )
    }

    // sync

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
        val pendingItems = todoItemDao.getPendingSyncItems(userId)

        for (item in pendingItems) {
            try {
                when (item.syncStatus) {
                    SyncStatus.PENDING_INSERT.name,
                    SyncStatus.PENDING_UPDATE.name -> {
                        remoteDataSource.upsert(item.toDto(userId))
                        todoItemDao.updateSyncStatus(item.id, userId, SyncStatus.SYNCED.name)
                    }
                    SyncStatus.PENDING_DELETE.name -> {
                        remoteDataSource.delete(item.id)
                        todoItemDao.deleteById(item.id, userId)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to sync item ${item.id}: ${e.message}")
            }
        }
    }

    private suspend fun pullRemoteChanges(userId: String) {
        try {
            val remoteItems = remoteDataSource.getAll()
            val pendingItems = todoItemDao.getPendingSyncItems(userId)
            val pendingIds = pendingItems.map { it.id }.toSet()

            for (remoteItem in remoteItems) {
                if (remoteItem.id !in pendingIds) {
                    todoItemDao.insertTodoItem(remoteItem.dtoToEntity().copy(userId = userId))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to pull remote changes: ${e.message}")
        }
    }

    private suspend fun tryRemoteUpsert(
        entity: com.muradgalayev.brainbuddy.data.local.entity.TodoItemEntity
    ) {
        try {
            val userId = getCurrentUserId() ?: return
            remoteDataSource.upsert(entity.toDto(userId))
            todoItemDao.updateSyncStatus(entity.id, userId, SyncStatus.SYNCED.name)
        } catch (e: Exception) {
            Log.w(TAG, "Remote upsert failed, will sync later: ${e.message}")
        }
    }

    private suspend fun tryRemoteDelete(id: String, userId: String) {
        try {
            remoteDataSource.delete(id)
            todoItemDao.deleteById(id, userId)
        } catch (e: Exception) {
            Log.w(TAG, "Remote delete failed, will sync later: ${e.message}")
        }
    }

    suspend fun clearLocalForUser(userId: String) {
        todoItemDao.deleteAllForUser(userId)
    }

}
