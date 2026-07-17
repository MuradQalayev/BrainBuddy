package com.muradgalayev.brainbuddy.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.muradgalayev.brainbuddy.data.google.GoogleCalendarTokenStore
import com.muradgalayev.brainbuddy.data.repository.ExportResult
import com.muradgalayev.brainbuddy.data.repository.GoogleCalendarRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class CalendarSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val googleCalendarRepository: GoogleCalendarRepository,
    private val googleCalendarTokenStore: GoogleCalendarTokenStore,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!googleCalendarTokenStore.isLinked()) {
            // User disconnected since we were scheduled. Don't retry — the
            // scheduler will be re-armed if they reconnect.
            Log.i(TAG, "Skipping sync; Google Calendar not linked")
            return Result.success()
        }
        // GoogleCalendarRepository handles silent token refresh internally,
        // so if we still get NeedsGoogleSignIn back, interactive consent is
        // genuinely required and retrying from a worker won't help.
        return when (val result = googleCalendarRepository.exportAllEvents()) {
            is ExportResult.Success -> {
                Log.i(
                    TAG,
                    "Sync ok: pushed=${result.pushed} existed=${result.alreadyExisted} failed=${result.failed}"
                )
                // Per-event push failures are transient (network, 5xx). Asking
                // WorkManager to retry will rerun the whole export which is
                // cheap — Google returns 409 for already-pushed events.
                if (result.failed > 0) Result.retry() else Result.success()
            }
            ExportResult.NeedsGoogleSignIn -> {
                Log.w(TAG, "Sync needs interactive sign-in; deferring until user reconnects")
                Result.success()
            }
        }
    }

    private companion object {
        const val TAG = "CalendarSyncWorker"
    }
}
