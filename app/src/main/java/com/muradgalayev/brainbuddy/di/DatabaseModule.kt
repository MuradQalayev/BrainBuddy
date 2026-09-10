package com.muradgalayev.brainbuddy.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import com.muradgalayev.brainbuddy.data.local.MyndoraDatabase
import com.muradgalayev.brainbuddy.data.local.dao.ActivityTimeStatDao
import com.muradgalayev.brainbuddy.data.local.dao.CalendarEventDao
import com.muradgalayev.brainbuddy.data.local.dao.MedicationDoseLogDao
import com.muradgalayev.brainbuddy.data.local.dao.CalendarSubtaskDao
import com.muradgalayev.brainbuddy.data.local.dao.PomodoroSessionDao
import com.muradgalayev.brainbuddy.data.local.dao.AppModeDao
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

    // app mode ids are intentionally stable, for example every account has `work`. version 16
    // incorrectly made that id globally unique on the device, so saving one account's built-in
    // could replace another account's row. SQLite requires a rebuild when changing a primary key,
    // and every column, pending sync metadata included, is copied verbatim
    internal val MIGRATION_16_17 = Migration(16, 17) { database ->
        database.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `app_modes_new` (
                `id` TEXT NOT NULL,
                `userId` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `icon` TEXT NOT NULL,
                `accent` TEXT,
                `isBuiltIn` INTEGER NOT NULL,
                `sortIndex` INTEGER NOT NULL,
                `scheduleJson` TEXT,
                `overridesJson` TEXT NOT NULL,
                `syncStatus` TEXT NOT NULL,
                `lastModifiedAt` INTEGER NOT NULL,
                PRIMARY KEY(`userId`, `id`)
            )
            """.trimIndent(),
        )
        database.execSQL(
            """
            INSERT INTO `app_modes_new` (
                `id`, `userId`, `name`, `icon`, `accent`, `isBuiltIn`, `sortIndex`,
                `scheduleJson`, `overridesJson`, `syncStatus`, `lastModifiedAt`
            )
            SELECT
                `id`, `userId`, `name`, `icon`, `accent`, `isBuiltIn`, `sortIndex`,
                `scheduleJson`, `overridesJson`, `syncStatus`, `lastModifiedAt`
            FROM `app_modes`
            """.trimIndent(),
        )
        database.execSQL("DROP TABLE `app_modes`")
        database.execSQL("ALTER TABLE `app_modes_new` RENAME TO `app_modes`")
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_app_modes_userId` ON `app_modes` (`userId`)",
        )
    }

    @Singleton
    @Provides
    fun provideMyndoraDatabase(
        @ApplicationContext context: Context
    ): MyndoraDatabase {
        return Room.databaseBuilder(
            context,
            MyndoraDatabase::class.java,
            MyndoraDatabase.DATABASE_NAME
        )
            .addMigrations(MIGRATION_16_17)
            // historical development schemas shipped no migrations. keep their old behaviour, but
            // deliberately exclude v16: mode rows have to use the safe path above
            .fallbackToDestructiveMigrationFrom(
                dropAllTables = true,
                *IntArray(15) { index -> index + 1 },
            )
            .build()
    }

    @Singleton
    @Provides
    fun provideTodoItemDao(database: MyndoraDatabase): TodoItemDao {
        return database.todoItemDao()
    }

    @Singleton
    @Provides
    fun providePomodoroSessionDao(database: MyndoraDatabase): PomodoroSessionDao {
        return database.pomodoroSessionDao()
    }

    @Singleton
    @Provides
    fun provideCalendarEventDao(database: MyndoraDatabase): CalendarEventDao {
        return database.calendarEventDao()
    }

    @Singleton
    @Provides
    fun provideCalendarSubtaskDao(database: MyndoraDatabase): CalendarSubtaskDao {
        return database.calendarSubtaskDao()
    }

    @Singleton
    @Provides
    fun provideActivityTimeStatDao(database: MyndoraDatabase): ActivityTimeStatDao {
        return database.activityTimeStatDao()
    }

    @Singleton
    @Provides
    fun provideMedicationDoseLogDao(database: MyndoraDatabase): MedicationDoseLogDao {
        return database.medicationDoseLogDao()
    }

    @Singleton
    @Provides
    fun provideAppModeDao(database: MyndoraDatabase): AppModeDao {
        return database.appModeDao()
    }
}
