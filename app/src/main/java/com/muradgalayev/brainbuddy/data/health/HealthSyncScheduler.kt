package com.muradgalayev.brainbuddy.data.health

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleHourly() {
        val request = PeriodicWorkRequestBuilder<HealthSyncWorker>(
            1, TimeUnit.HOURS,
            15, TimeUnit.MINUTES,
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private companion object {
        const val WORK_NAME = "health_connect_hourly_refresh"
    }
}
