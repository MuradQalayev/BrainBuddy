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
import dagger.Lazy
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

    // needed on the main thread before the first frame: reconcile has to beat any session start,
    // and the lifecycle callback reads the other two on every resume
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var networkObserver: NetworkObserver
    @Inject lateinit var focusModeManager: com.muradgalayev.brainbuddy.data.local.FocusModeManager
    @Inject lateinit var ringerController: com.muradgalayev.brainbuddy.data.local.RingerController
    @Inject lateinit var modeManager: com.muradgalayev.brainbuddy.data.local.ModeManager

    // everything else only does background work, so it's Lazy and first built on appScope. eager,
    // these graphs (Supabase, Room, Health Connect, WorkManager) were all constructed on the main
    // thread before the first frame could draw
    @Inject lateinit var reminderBootstrapper: Lazy<ReminderBootstrapper>
    @Inject lateinit var calendarSyncScheduler: Lazy<CalendarSyncScheduler>
    @Inject lateinit var googleCalendarTokenStore: Lazy<GoogleCalendarTokenStore>
    @Inject lateinit var syncCoordinator: Lazy<SyncCoordinator>
    @Inject lateinit var healthConnectManager: Lazy<HealthConnectManager>
    @Inject lateinit var healthSyncScheduler: Lazy<HealthSyncScheduler>
    @Inject lateinit var deviceTokenRepository: Lazy<com.muradgalayev.brainbuddy.data.repository.DeviceTokenRepository>
    @Inject lateinit var adhdProfileRepository: Lazy<com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository>
    @Inject lateinit var togetherRealtime: Lazy<com.muradgalayev.brainbuddy.data.sync.TogetherRealtime>

    // activities between onStart and onStop. main thread only
    private var startedActivities = 0

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(com.muradgalayev.brainbuddy.data.local.AppLocale.wrap(base))
    }

    // a language switch arrives here as a configuration change. channel names are only read when a
    // channel is created, so re-creating them is what renames them in the system settings
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        NotificationChannels.createAll(this)
    }

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
            runCatching { deviceTokenRepository.get().registerCurrentDevice() }
                .onFailure { Log.w("MyndoraApp", "Token registration failed: ${it.message}") }
        }

        // keeps the medication to-do window rolling forward. nothing else runs on a schedule to do it,
        // so without this the list quietly runs dry
        appScope.launch {
            runCatching { adhdProfileRepository.get().refreshMedicationTodos() }
                .onFailure { Log.w("MyndoraApp", "Medication to-dos not refreshed: ${it.message}") }
        }

        appScope.launch {
            runCatching { healthSyncScheduler.get().scheduleHourly() }
                .onFailure { Log.w("MyndoraApp", "Health sync not scheduled: ${it.message}") }
            runCatching { healthConnectManager.get().refresh() }
                .onFailure { Log.w("MyndoraApp", "Health refresh failed: ${it.message}") }
        }

        appScope.launch {
            try {
                reminderBootstrapper.get().rescheduleAll()
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
                googleCalendarTokenStore.get().linkedEmail,
                modeManager.effectiveCalendarSyncFrequency,
            ) { linkedEmail, frequency -> linkedEmail to frequency }
                .distinctUntilChanged()
                .collect { (linkedEmail, frequency) ->
                    if (linkedEmail == null) {
                        calendarSyncScheduler.get().cancel()
                    } else {
                        calendarSyncScheduler.get().applyFrequency(frequency)
                    }
                }
        }

        // one-shot pull on start plus a re-pull on reconnect. never blocks the UI
        appScope.launch { syncCoordinator.get().start() }
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
                    // live calendar and list changes while the app is on screen
                    appScope.launch { togetherRealtime.get().onForeground() }
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) {
                    startedActivities++
                }
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) {
                    if (--startedActivities == 0) appScope.launch { togetherRealtime.get().onBackground() }
                }
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }
}
