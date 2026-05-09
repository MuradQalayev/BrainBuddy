package com.muradgalayev.brainbuddy

import android.app.Application
import android.util.Log
import com.muradgalayev.brainbuddy.data.notifications.NotificationChannels
import com.muradgalayev.brainbuddy.data.notifications.ReminderBootstrapper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BrainBuddyApp : Application() {

    @Inject lateinit var reminderBootstrapper: ReminderBootstrapper

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
    }
}
