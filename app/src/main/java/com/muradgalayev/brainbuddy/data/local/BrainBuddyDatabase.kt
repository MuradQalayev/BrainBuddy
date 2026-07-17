package com.muradgalayev.brainbuddy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.muradgalayev.brainbuddy.data.local.dao.CalendarEventDao
import com.muradgalayev.brainbuddy.data.local.dao.CalendarSubtaskDao
import com.muradgalayev.brainbuddy.data.local.dao.PomodoroSessionDao
import com.muradgalayev.brainbuddy.data.local.dao.TodoItemDao
import com.muradgalayev.brainbuddy.data.local.entity.CalendarEventEntity
import com.muradgalayev.brainbuddy.data.local.entity.CalendarSubtaskEntity
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionEntity
import com.muradgalayev.brainbuddy.data.local.entity.TodoItemEntity

@Database(
    entities = [
        TodoItemEntity::class,
        PomodoroSessionEntity::class,
        CalendarEventEntity::class,
        CalendarSubtaskEntity::class,
    ],
    version = 11,
    exportSchema = false
)
abstract class BrainBuddyDatabase : RoomDatabase() {
    abstract fun todoItemDao(): TodoItemDao
    abstract fun pomodoroSessionDao(): PomodoroSessionDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun calendarSubtaskDao(): CalendarSubtaskDao

    companion object {
        const val DATABASE_NAME = "brainbuddy_db"
    }
}
