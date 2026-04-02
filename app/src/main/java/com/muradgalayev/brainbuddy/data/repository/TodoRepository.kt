package com.muradgalayev.brainbuddy.data.repository

import com.muradgalayev.brainbuddy.data.local.dao.TodoItemDao
import com.muradgalayev.brainbuddy.data.local.entity.TodoItemEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class TodoRepository @Inject constructor(
    private val todoItemDao: TodoItemDao
) {
    fun getAllTodoItems(): Flow<List<TodoItemEntity>> = todoItemDao.getAllTodoItems()

    fun getActiveTodoItems(): Flow<List<TodoItemEntity>> = todoItemDao.getActiveTodoItems()

    fun getCompletedTodoItems(): Flow<List<TodoItemEntity>> = todoItemDao.getCompletedTodoItems()

    fun getTodoItemsByDate(date: String): Flow<List<TodoItemEntity>> = todoItemDao.getTodoItemsByDate(date)

    suspend fun getTodoItemById(id: String): TodoItemEntity? = todoItemDao.getTodoItemById(id)

    suspend fun insertTodoItem(todoItem: TodoItemEntity) = todoItemDao.insertTodoItem(todoItem)

    suspend fun updateTodoItem(todoItem: TodoItemEntity) = todoItemDao.updateTodoItem(todoItem)

    suspend fun deleteTodoItem(todoItem: TodoItemEntity) = todoItemDao.deleteTodoItem(todoItem)

    suspend fun toggleTodoItemCompletion(id: String) {
        val item = todoItemDao.getTodoItemById(id) ?: return
        todoItemDao.updateTodoItem(item.copy(isCompleted = !item.isCompleted))
    }
}

