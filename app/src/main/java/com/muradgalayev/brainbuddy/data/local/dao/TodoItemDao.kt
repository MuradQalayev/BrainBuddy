package com.muradgalayev.brainbuddy.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.muradgalayev.brainbuddy.data.local.entity.TodoItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TodoItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTodoItem(todoItem: TodoItemEntity)

    @Update
    suspend fun updateTodoItem(todoItem: TodoItemEntity)

    @Delete
    suspend fun deleteTodoItem(todoItem: TodoItemEntity)

    @Query("SELECT * FROM todo_items WHERE userId = :userId ORDER BY date ASC, startTime ASC")
    fun getAllTodoItems(userId: String): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_items WHERE id = :id AND userId = :userId")
    suspend fun getTodoItemById(id: String, userId: String): TodoItemEntity?

    @Query("SELECT * FROM todo_items WHERE date = :date AND userId = :userId ORDER BY startTime ASC")
    fun getTodoItemsByDate(date: String, userId: String): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_items WHERE isCompleted = 0 AND userId = :userId ORDER BY date ASC, startTime ASC")
    fun getActiveTodoItems(userId: String): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_items WHERE isCompleted = 1 AND userId = :userId ORDER BY date ASC")
    fun getCompletedTodoItems(userId: String): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_items WHERE date >= :startDate AND date <= :endDate AND userId = :userId ORDER BY startTime ASC")
    fun getTodoItemsInRange(startDate: String, endDate: String, userId: String): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_items WHERE syncStatus != 'SYNCED' AND userId = :userId")
    suspend fun getPendingSyncItems(userId: String): List<TodoItemEntity>

    @Query("SELECT * FROM todo_items WHERE syncStatus = 'PENDING_DELETE' AND userId = :userId")
    suspend fun getPendingDeleteItems(userId: String): List<TodoItemEntity>

    @Query("DELETE FROM todo_items WHERE id = :id AND userId = :userId")
    suspend fun deleteById(id: String, userId: String)

    @Query("DELETE FROM todo_items WHERE userId = :userId")
    suspend fun deleteAllForUser(userId: String)

    @Query("UPDATE todo_items SET syncStatus = :status WHERE id = :id AND userId = :userId")
    suspend fun updateSyncStatus(id: String, userId: String, status: String)
}
