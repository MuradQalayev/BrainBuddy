package com.muradgalayev.brainbuddy.data.local

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.muradgalayev.brainbuddy.MainActivity
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType
import com.muradgalayev.brainbuddy.data.notifications.NotificationChannels
import com.muradgalayev.brainbuddy.data.notifications.ReminderCopy
import com.muradgalayev.brainbuddy.domain.model.ModeNotificationKind
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class PomodoroTimerService : Service() {

    companion object {
        const val CHANNEL_ID = "pomodoro_timer_channel"
        const val NOTIFICATION_ID = 1001
        private const val BREAK_NOTIFICATION_ID = 1002
        private const val BREAK_TAP_REQUEST_CODE = 1003

        const val ACTION_PAUSE = "com.muradgalayev.brainbuddy.PAUSE"
        const val ACTION_RESUME = "com.muradgalayev.brainbuddy.RESUME"
        const val ACTION_STOP = "com.muradgalayev.brainbuddy.STOP"

        fun start(context: Context) {
            val intent = Intent(context, PomodoroTimerService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, PomodoroTimerService::class.java)
            context.stopService(intent)
        }
    }

    @Inject
    lateinit var timerManager: PomodoroTimerManager

    @Inject
    lateinit var preferencesManager: PreferencesManager

    @Inject
    lateinit var modeManager: ModeManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var updateJob: Job? = null
    // partial wake lock held while the timer runs, so the CPU stays available with the screen
    // off. without it, aggressive OEM battery savers can suspend the foreground service's
    // coroutines and the countdown appears to stop until the screen wakes up
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        timerManager.onTimerStopped = {
            releaseWakeLock()
            stopSelf()
        }
        timerManager.onTimerCompleted = {
            // capture the type that just finished, before the next session is primed
            val finishedType = timerManager.state.value.sessionType
            // update the notification one last time, then stop after a brief delay
            updateNotification()
            serviceScope.launch {
                maybePostBreakReminder(finishedType)
                delay(3000)
                releaseWakeLock()
                stopSelf()
            }
        }
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Myndora:PomodoroTimer"
        ).apply {
            setReferenceCounted(false)
            // hard cap matches the longest single session, so a forgotten release can't drain the battery
            // indefinitely
            acquire(2 * 60 * 60 * 1000L) // 2 hours
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> {
                timerManager.pause()
                // pause doesn't need the wake lock, the countdown isn't ticking
                releaseWakeLock()
            }
            ACTION_RESUME -> {
                timerManager.resume()
                acquireWakeLock()
            }
            ACTION_STOP -> timerManager.stop()
            else -> {
                // start foreground
                val notification = buildNotification()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                acquireWakeLock()
                startUpdatingNotification()
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        releaseWakeLock()
        timerManager.onTimerStopped = null
        timerManager.onTimerCompleted = null
    }

    private fun startUpdatingNotification() {
        updateJob?.cancel()
        updateJob = serviceScope.launch {
            while (true) {
                updateNotification()
                delay(1000)
            }
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val state = timerManager.state.value
        val minutes = (state.remainingMs / 1000) / 60
        val seconds = (state.remainingMs / 1000) % 60
        val timeText = "%02d:%02d".format(minutes, seconds)

        val sessionLabel = when (state.sessionType) {
            PomodoroSessionType.FOCUS -> "Focus Session"
            PomodoroSessionType.BREAK -> "Break"
        }

        val statusText = when (state.timerState) {
            TimerState.RUNNING -> "$sessionLabel — $timeText"
            TimerState.PAUSED -> "$sessionLabel — Paused ($timeText)"
            TimerState.COMPLETED -> "$sessionLabel — Completed!"
            TimerState.IDLE -> "$sessionLabel — Ready"
        }

        // tap the notification to open the app
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "pomodoro")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Myndora Timer")
            .setContentText(statusText)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        // progress bar
        if (state.timerState == TimerState.RUNNING || state.timerState == TimerState.PAUSED) {
            builder.setProgress(1000, (state.progress * 1000).toInt(), false)
        }

        // action buttons
        when (state.timerState) {
            TimerState.RUNNING -> {
                builder.addAction(
                    R.drawable.ic_launcher_foreground,
                    "Pause",
                    createActionIntent(ACTION_PAUSE)
                )
                builder.addAction(
                    R.drawable.ic_launcher_foreground,
                    "Stop",
                    createActionIntent(ACTION_STOP)
                )
            }
            TimerState.PAUSED -> {
                builder.addAction(
                    R.drawable.ic_launcher_foreground,
                    "Resume",
                    createActionIntent(ACTION_RESUME)
                )
                builder.addAction(
                    R.drawable.ic_launcher_foreground,
                    "Stop",
                    createActionIntent(ACTION_STOP)
                )
            }
            else -> {}
        }

        return builder.build()
    }

    // posts a heads-up alert when a session finishes, 'break time' after focus and 'break's over'
    // after a break, gated by the user's break-reminder preference
    private suspend fun maybePostBreakReminder(finishedType: PomodoroSessionType) {
        if (!runCatching { preferencesManager.pomodoroBreakRemindersSnapshot() }.getOrDefault(true)) return
        if (!runCatching {
                modeManager.isNotificationAllowedNow(ModeNotificationKind.BREAK_REMINDERS)
            }.getOrDefault(false)
        ) return
        if (!hasPostNotificationsPermission()) return

        val (heading, body) = when (finishedType) {
            PomodoroSessionType.FOCUS -> ReminderCopy.pomodoroBreakStart()
            PomodoroSessionType.BREAK -> ReminderCopy.pomodoroBreakOver()
        }

        val tapIntent = PendingIntent.getActivity(
            this,
            BREAK_TAP_REQUEST_CODE,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "pomodoro")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, NotificationChannels.POMODORO_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(heading)
            .setContentText(body)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(this).notify(BREAK_NOTIFICATION_ID, notification)
    }

    private fun hasPostNotificationsPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            this, android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun createActionIntent(action: String): PendingIntent {
        val intent = Intent(this, PomodoroTimerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Pomodoro Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows the active Pomodoro timer progress"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }
}
