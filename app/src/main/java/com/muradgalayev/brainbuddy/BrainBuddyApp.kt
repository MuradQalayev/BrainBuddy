package com.muradgalayev.brainbuddy

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.muradgalayev.brainbuddy.data.google.GoogleCalendarTokenStore
import com.muradgalayev.brainbuddy.data.notifications.NotificationChannels
import com.muradgalayev.brainbuddy.data.notifications.ReminderBootstrapper
import com.muradgalayev.brainbuddy.data.sync.CalendarSyncScheduler
import com.muradgalayev.brainbuddy.data.sync.SyncCoordinator
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BrainBuddyApp : Application(), Configuration.Provider {

    @Inject lateinit var reminderBootstrapper: ReminderBootstrapper
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var calendarSyncScheduler: CalendarSyncScheduler
    @Inject lateinit var googleCalendarTokenStore: GoogleCalendarTokenStore
    @Inject lateinit var syncCoordinator: SyncCoordinator

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)

        appScope.launch {
            try {
                reminderBootstrapper.rescheduleAll()
            } catch (t: Throwable) {
                Log.w("BrainBuddyApp", "Initial reschedule failed: ${t.message}")
            }
        }

        // Re-arm periodic Google Calendar sync after process restarts. KEEP
        // policy is a no-op when the worker is already scheduled.
        if (googleCalendarTokenStore.isLinked()) {
            calendarSyncScheduler.schedulePeriodic()
        }

        // One-shot Supabase pull on start + re-pull on network reconnect. Never blocks UI.
        syncCoordinator.start()
    }
}
