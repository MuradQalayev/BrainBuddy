package com.muradgalayev.brainbuddy

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.muradgalayev.brainbuddy.data.google.GoogleCalendarTokenStore
import com.muradgalayev.brainbuddy.data.notifications.NotificationChannels
import com.muradgalayev.brainbuddy.data.network.NetworkObserver
import com.muradgalayev.brainbuddy.data.notifications.ReminderBootstrapper
import com.muradgalayev.brainbuddy.data.sync.CalendarSyncScheduler
import com.muradgalayev.brainbuddy.data.sync.SyncCoordinator
import com.muradgalayev.brainbuddy.data.health.HealthConnectManager
import com.muradgalayev.brainbuddy.data.health.HealthSyncScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MyndoraApp : Application(), Configuration.Provider {

    @Inject lateinit var reminderBootstrapper: ReminderBootstrapper
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var calendarSyncScheduler: CalendarSyncScheduler
    @Inject lateinit var googleCalendarTokenStore: GoogleCalendarTokenStore
    @Inject lateinit var syncCoordinator: SyncCoordinator
    @Inject lateinit var healthConnectManager: HealthConnectManager
    @Inject lateinit var healthSyncScheduler: HealthSyncScheduler
    @Inject lateinit var networkObserver: NetworkObserver
    @Inject lateinit var focusModeManager: com.muradgalayev.brainbuddy.data.local.FocusModeManager
    @Inject lateinit var ringerController: com.muradgalayev.brainbuddy.data.local.RingerController
    @Inject lateinit var modeManager: com.muradgalayev.brainbuddy.data.local.ModeManager
    @Inject lateinit var deviceTokenRepository: com.muradgalayev.brainbuddy.data.repository.DeviceTokenRepository
    @Inject lateinit var adhdProfileRepository: com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.createAll(this)

        // before anything can start a session: if the last process was killed mid-focus, the phone is
        // still silenced and nothing else will ever lift it
        focusModeManager.reconcile()
        // same reasoning for the ringer: a mode that silenced the phone before the process died has
        // nothing left that knows to undo it
        ringerController.reconcile()
        // ModeManager can be constructed before onCreate(), so its eager collector may already have
        // taken the mode lease reconcile just cleared. reassert the persisted or scheduled mode after
        // the stale process state is cleaned up
        modeManager.reapplyDeviceState()
        modeManager.bootstrap()

        observeForegroundForConnectivity()

        // every launch, not only at sign-in: tokens rotate, and a stale one fails silently in a way
        // nobody can diagnose from the outside
        appScope.launch {
            runCatching { deviceTokenRepository.registerCurrentDevice() }
                .onFailure { Log.w("MyndoraApp", "Token registration failed: ${it.message}") }
        }

        // keeps the medication to-do window rolling forward. nothing else runs on a schedule to do it,
        // so without this the list quietly runs dry
        appScope.launch {
            runCatching { adhdProfileRepository.refreshMedicationTodos() }
                .onFailure { Log.w("MyndoraApp", "Medication to-dos not refreshed: ${it.message}") }
        }

        healthSyncScheduler.scheduleHourly()
        appScope.launch {
            runCatching { healthConnectManager.refresh() }
                .onFailure { Log.w("MyndoraApp", "Health refresh failed: ${it.message}") }
        }

        appScope.launch {
            try {
                reminderBootstrapper.rescheduleAll()
            } catch (t: Throwable) {
                Log.w("MyndoraApp", "Initial reschedule failed: ${t.message}")
            }
        }

        // re-arm periodic Google Calendar sync after a process restart, and whenever a mode boundary
        // changes the cadence. the mode value is an overlay only, the user's stored cadence is
        // untouched and comes back on mode exit. observing the nullable link also cancels orphaned
        // work after a disconnect
        appScope.launch {
            combine(
                googleCalendarTokenStore.linkedEmail,
                modeManager.effectiveCalendarSyncFrequency,
            ) { linkedEmail, frequency -> linkedEmail to frequency }
                .distinctUntilChanged()
                .collect { (linkedEmail, frequency) ->
                    if (linkedEmail == null) {
                        calendarSyncScheduler.cancel()
                    } else {
                        calendarSyncScheduler.applyFrequency(frequency)
                    }
                }
        }

        // one-shot pull on start plus a re-pull on reconnect. never blocks the UI
        syncCoordinator.start()
    }

    // re-reads connectivity every time an activity comes back to the foreground. Android doesn't
    // promise to deliver network callbacks to a backgrounded process, so the observed state can
    // be stale by the time the user is looking at the screen again, the common case being a phone
    // put down on cellular and picked up on wifi. asking the system directly on resume is cheap
    private fun observeForegroundForConnectivity() {
        registerActivityLifecycleCallbacks(
            object : ActivityLifecycleCallbacks {
                override fun onActivityResumed(activity: Activity) {
                    networkObserver.refresh()
                    // schedules are evaluated against a clock that only moves when asked, and coming back to the
                    // foreground is when it matters most: the phone may have been in a pocket across a boundary
                    modeManager.refresh()
                    // activeMode suppresses equal values, but returning from system DND settings can make an
                    // unchanged mode newly applicable, so reassert its external state even when it didn't change
                    modeManager.reapplyDeviceState()
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }
}
