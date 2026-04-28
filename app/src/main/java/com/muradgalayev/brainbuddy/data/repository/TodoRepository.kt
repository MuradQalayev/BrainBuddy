package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.muradgalayev.brainbuddy.data.local.dao.TodoItemDao
import com.muradgalayev.brainbuddy.data.local.entity.SyncStatus
import com.muradgalayev.brainbuddy.data.mapper.toDomain
import com.muradgalayev.brainbuddy.data.mapper.toDto
import com.muradgalayev.brainbuddy.data.mapper.toEntity
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
    private val authRepository: AuthRepository
) {
    companion object {
        private const val TAG = "TodoRepository"
    }

    private fun getCurrentUserId(): String? = authRepository.getCurrentUserId()

    // ── Read operations (always from Room, filtered by userId) ──

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

    // ── Write operations ──

    suspend fun insertTodoItem(todoItem: TodoItem) {
        val userId = getCurrentUserId() ?: return
        val entity = todoItem.toEntity(userId).copy(
            syncStatus = SyncStatus.PENDING_INSERT.name,
            lastModifiedAt = System.currentTimeMillis()
        )
        todoItemDao.insertTodoItem(entity)
        tryRemoteUpsert(entity)
    }

    suspend fun updateTodoItem(todoItem: TodoItem) {
        val userId = getCurrentUserId() ?: return
        val entity = todoItem.toEntity(userId).copy(
            syncStatus = SyncStatus.PENDING_UPDATE.name,
            lastModifiedAt = System.currentTimeMillis()
        )
        todoItemDao.updateTodoItem(entity)
        tryRemoteUpsert(entity)
    }

    suspend fun deleteTodoItem(todoItem: TodoItem) {
        val userId = getCurrentUserId() ?: return
        val entity = todoItem.toEntity(userId).copy(
            syncStatus = SyncStatus.PENDING_DELETE.name
        )
        todoItemDao.updateTodoItem(entity)
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
        tryRemoteUpsert(updated)
    }

    // ── Sync operations ──

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
            // Only pull remote items for the currently authenticated user
            val remoteItems = remoteDataSource.getAll(userId)
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

    // Clear local data for a specific user (used on sign out to avoid leaking tasks
    // between different accounts on the same device).
    suspend fun clearLocalForUser(userId: String) {
        todoItemDao.deleteAllForUser(userId)
    }
}
