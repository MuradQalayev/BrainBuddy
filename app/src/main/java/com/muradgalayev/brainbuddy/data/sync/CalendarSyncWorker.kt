package com.muradgalayev.brainbuddy.data.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.muradgalayev.brainbuddy.data.google.GoogleCalendarTokenStore
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
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
    private val preferencesManager: PreferencesManager,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!googleCalendarTokenStore.isLinked()) {
            // the user disconnected since we were scheduled. don't retry, the scheduler is re-armed if
            // they reconnect
            Log.i(TAG, "Skipping sync; Google Calendar not linked")
            record("Skipped — Google not connected")
            return Result.success()
        }
        // GoogleCalendarRepository handles silent token refresh internally, so if we still get
        // NeedsGoogleSignIn back then interactive consent is genuinely required and retrying from a
        // worker won't help
        return when (val result = googleCalendarRepository.exportAllEvents()) {
            is ExportResult.Success -> {
                Log.i(
                    TAG,
                    "Sync ok: pushed=${result.pushed} existed=${result.alreadyExisted} failed=${result.failed}"
                )
                record(
                    if (result.pushed > 0) "Pushed ${result.pushed} event(s)"
                    else "Up to date"
                )
                // per-event push failures are transient, network or 5xx. asking WorkManager to retry reruns
                // the whole export, which is cheap since Google returns 409 for already-pushed events
                if (result.failed > 0) Result.retry() else Result.success()
            }
            ExportResult.NeedsGoogleSignIn -> {
                Log.w(TAG, "Sync needs interactive sign-in; deferring until user reconnects")
                // silent until now: the token had expired beyond refresh and every scheduled run quietly
                // no-opped, which looks exactly like sync never running at all. surface it so the user knows
                // to reconnect
                record("Needs you to reconnect Google")
                Result.success()
            }
        }
    }

    private suspend fun record(outcome: String) {
        runCatching {
            preferencesManager.recordCalendarSync(System.currentTimeMillis(), outcome)
        }
    }

    private companion object {
        const val TAG = "CalendarSyncWorker"
    }
}
