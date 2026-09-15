package com.muradgalayev.brainbuddy.data.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.muradgalayev.brainbuddy.R

// how often background Google Calendar sync runs. chosen by the user
enum class CalendarSyncFrequency(@androidx.annotation.StringRes val labelRes: Int, val intervalHours: Long?) {
    MANUAL(R.string.sync_manual, null),
    WEEKLY(R.string.sync_weekly, 24 * 7),
    DAILY(R.string.sync_daily, 24),
    TWICE_DAILY(R.string.sync_twice, 12),
    EVERY_6H(R.string.sync_6h, 6);

    companion object {
        val DEFAULT = TWICE_DAILY
        fun fromNameOrNull(name: String?): CalendarSyncFrequency? =
            entries.firstOrNull { it.name == name }

        fun fromName(name: String?): CalendarSyncFrequency =
            fromNameOrNull(name) ?: DEFAULT
    }
}

@Singleton
class CalendarSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // cancels for MANUAL, otherwise re-schedules at the chosen cadence
    fun applyFrequency(frequency: CalendarSyncFrequency) {
        val hours = frequency.intervalHours
        if (hours == null) cancel() else schedulePeriodic(hours)
    }

    fun schedulePeriodic(intervalHours: Long = CalendarSyncFrequency.DEFAULT.intervalHours!!) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // a flex window, about a sixth of the interval and at least an hour, lets WorkManager batch
        // with other jobs and respect Doze rather than firing at an exact instant
        val flexHours = (intervalHours / 6).coerceAtLeast(1)
        val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
            intervalHours, TimeUnit.HOURS,
            flexHours, TimeUnit.HOURS,
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        // UPDATE so a changed cadence replaces the existing schedule
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "calendar_sync_periodic"
    }
}
