package com.muradgalayev.brainbuddy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.muradgalayev.brainbuddy.data.local.dao.TodoItemDao
import com.muradgalayev.brainbuddy.data.local.entity.TodoItemEntity

@Database(
    entities = [TodoItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class BrainBuddyDatabase : RoomDatabase() {
    abstract fun todoItemDao(): TodoItemDao

    companion object {
        const val DATABASE_NAME = "brainbuddy_db"
    }
}

