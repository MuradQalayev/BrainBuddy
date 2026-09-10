package com.muradgalayev.brainbuddy.data.health

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class HealthSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val healthConnectManager: HealthConnectManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = runCatching {
        healthConnectManager.refresh()
        Result.success()
    }.getOrElse { Result.retry() }
}
