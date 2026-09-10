package com.muradgalayev.brainbuddy.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.muradgalayev.brainbuddy.data.local.dao.ActivityTimeStatDao
import com.muradgalayev.brainbuddy.data.local.dao.AppModeDao
import com.muradgalayev.brainbuddy.data.local.dao.CalendarEventDao
import com.muradgalayev.brainbuddy.data.local.dao.CalendarSubtaskDao
import com.muradgalayev.brainbuddy.data.local.dao.MedicationDoseLogDao
import com.muradgalayev.brainbuddy.data.local.dao.PomodoroSessionDao
import com.muradgalayev.brainbuddy.data.local.dao.TodoItemDao
import com.muradgalayev.brainbuddy.data.local.entity.ActivityTimeStatEntity
import com.muradgalayev.brainbuddy.data.local.entity.AppModeEntity
import com.muradgalayev.brainbuddy.data.local.entity.CalendarEventEntity
import com.muradgalayev.brainbuddy.data.local.entity.CalendarSubtaskEntity
import com.muradgalayev.brainbuddy.data.local.entity.MedicationDoseLogEntity
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionEntity
import com.muradgalayev.brainbuddy.data.local.entity.TodoItemEntity

@Database(
    entities = [
        TodoItemEntity::class,
        PomodoroSessionEntity::class,
        CalendarEventEntity::class,
        CalendarSubtaskEntity::class,
        ActivityTimeStatEntity::class,
        MedicationDoseLogEntity::class,
        AppModeEntity::class,
    ],
    // 13 adds createdBy to calendar_events and todo_items for Together.
    // 14 adds activity_time_stats, the learned event-timing habits.
    // 15 adds medication_dose_logs, moving dose ticks out of DataStore so they sync.
    // 16 adds app_modes.
    // 17 keys app_modes by owner + id, so stable built-in ids can't collide across accounts
    version = 17,
    exportSchema = false
)
abstract class MyndoraDatabase : RoomDatabase() {
    abstract fun todoItemDao(): TodoItemDao
    abstract fun pomodoroSessionDao(): PomodoroSessionDao
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun calendarSubtaskDao(): CalendarSubtaskDao
    abstract fun activityTimeStatDao(): ActivityTimeStatDao
    abstract fun medicationDoseLogDao(): MedicationDoseLogDao
    abstract fun appModeDao(): AppModeDao

    companion object {
        const val DATABASE_NAME = "brainbuddy_db"
    }
}
