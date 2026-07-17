package com.muradgalayev.brainbuddy.di

import android.content.Context
import androidx.room.Room
import com.muradgalayev.brainbuddy.data.local.BrainBuddyDatabase
import com.muradgalayev.brainbuddy.data.local.dao.CalendarEventDao
import com.muradgalayev.brainbuddy.data.local.dao.CalendarSubtaskDao
import com.muradgalayev.brainbuddy.data.local.dao.PomodoroSessionDao
import com.muradgalayev.brainbuddy.data.local.dao.TodoItemDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideBrainBuddyDatabase(
        @ApplicationContext context: Context
    ): BrainBuddyDatabase {
        return Room.databaseBuilder(
            context,
            BrainBuddyDatabase::class.java,
            BrainBuddyDatabase.DATABASE_NAME
        ).fallbackToDestructiveMigration()
        .build()
    }

    @Singleton
    @Provides
    fun provideTodoItemDao(database: BrainBuddyDatabase): TodoItemDao {
        return database.todoItemDao()
    }

    @Singleton
    @Provides
    fun providePomodoroSessionDao(database: BrainBuddyDatabase): PomodoroSessionDao {
        return database.pomodoroSessionDao()
    }

    @Singleton
    @Provides
    fun provideCalendarEventDao(database: BrainBuddyDatabase): CalendarEventDao {
        return database.calendarEventDao()
    }

    @Singleton
    @Provides
    fun provideCalendarSubtaskDao(database: BrainBuddyDatabase): CalendarSubtaskDao {
        return database.calendarSubtaskDao()
    }
}

