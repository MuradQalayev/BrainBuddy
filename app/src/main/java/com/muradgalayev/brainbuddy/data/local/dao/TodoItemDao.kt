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

    @Query("SELECT * FROM todo_items ORDER BY date ASC, startTime ASC")
    fun getAllTodoItems(): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_items WHERE id = :id")
    suspend fun getTodoItemById(id: String): TodoItemEntity?

    @Query("SELECT * FROM todo_items WHERE date = :date ORDER BY startTime ASC")
    fun getTodoItemsByDate(date: String): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_items WHERE isCompleted = 0 ORDER BY date ASC, startTime ASC")
    fun getActiveTodoItems(): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_items WHERE isCompleted = 1 ORDER BY date ASC")
    fun getCompletedTodoItems(): Flow<List<TodoItemEntity>>

    @Query("SELECT * FROM todo_items WHERE date >= :startDate AND date <= :endDate ORDER BY startTime ASC")
    fun getTodoItemsInRange(startDate: String, endDate: String): Flow<List<TodoItemEntity>>
}

