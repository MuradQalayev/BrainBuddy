// filename: data/repository/TodoRepository.kt
package com.muradgalayev.brainbuddy.data.repository

import com.muradgalayev.brainbuddy.data.local.dao.TodoItemDao
import com.muradgalayev.brainbuddy.data.mapper.toDomain
import com.muradgalayev.brainbuddy.data.mapper.toEntity
import com.muradgalayev.brainbuddy.domain.model.TodoItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TodoRepository @Inject constructor(
    private val todoItemDao: TodoItemDao
) {
    fun getAllTodoItems(): Flow<List<TodoItem>> =
        todoItemDao.getAllTodoItems().map { items ->
            items.map { it.toDomain() }
        }

    fun getActiveTodoItems(): Flow<List<TodoItem>> =
        todoItemDao.getActiveTodoItems().map { items ->
            items.map { it.toDomain() }
        }

    fun getCompletedTodoItems(): Flow<List<TodoItem>> =
        todoItemDao.getCompletedTodoItems().map { items ->
            items.map { it.toDomain() }
        }

    fun getTodoItemsByDate(date: String): Flow<List<TodoItem>> =
        todoItemDao.getTodoItemsByDate(date).map { items ->
            items.map { it.toDomain() }
        }

    fun getTodoItemsInRange(startDate: String, endDate: String): Flow<List<TodoItem>> =
        todoItemDao.getTodoItemsInRange(startDate, endDate).map { items ->
            items.map { it.toDomain() }
        }

    suspend fun getTodoItemById(id: String): TodoItem? =
        todoItemDao.getTodoItemById(id)?.toDomain()

    suspend fun insertTodoItem(todoItem: TodoItem) {
        todoItemDao.insertTodoItem(todoItem.toEntity())
    }

    suspend fun updateTodoItem(todoItem: TodoItem) {
        todoItemDao.updateTodoItem(todoItem.toEntity())
    }

    suspend fun deleteTodoItem(todoItem: TodoItem) {
        todoItemDao.deleteTodoItem(todoItem.toEntity())
    }

    suspend fun toggleTodoItemCompletion(id: String) {
        val item = todoItemDao.getTodoItemById(id) ?: return
        todoItemDao.updateTodoItem(
            item.copy(isCompleted = !item.isCompleted)
        )
    }
}