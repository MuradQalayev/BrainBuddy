package com.muradgalayev.brainbuddy.data.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.muradgalayev.brainbuddy.MainActivity
import com.muradgalayev.brainbuddy.R
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

// fires the daily 'come focus' nudge, then re-arms itself for the next day. the frequency and
// time preference is read by ReminderScheduler.scheduleFocusNudge
@AndroidEntryPoint
class FocusNudgeReceiver : BroadcastReceiver() {

    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var modeManager: com.muradgalayev.brainbuddy.data.local.ModeManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val slot = intent.getIntExtra(ReminderScheduler.EXTRA_NUDGE_SLOT, 0)
        val pending = goAsync()
        scope.launch {
            try {
                // held back, but still re-armed in the finally below: a mode silencing today's nudge must not
                // cancel tomorrow's
                val allowed = modeManager.isNotificationAllowedNow(
                    com.muradgalayev.brainbuddy.domain.model.ModeNotificationKind.POMODORO_NUDGES
                )
                if (allowed && hasPostNotificationsPermission(context)) {
                    postNudge(context, slot)
                }
            } catch (t: Throwable) {
                Log.w("FocusNudge", "Failed to post nudge: ${t.message}")
            } finally {
                // re-arm for the next occurrence, reading the latest saved preference
                runCatching { scheduler.scheduleFocusNudge() }
                pending.finish()
            }
        }
    }

    private fun postNudge(context: Context, slot: Int) {
        // vary the copy by day so back-to-back nudges don't read identically
        val seed = (System.currentTimeMillis() / (24L * 60 * 60 * 1000)).toInt() + slot
        val (heading, body) = ReminderCopy.focusNudge(context, seed)

        val tapIntent = PendingIntent.getActivity(
            context,
            NUDGE_TAP_REQUEST_CODE + slot,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("navigate_to", "pomodoro")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.FOCUS_NUDGE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(heading)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(context).notify(NUDGE_NOTIFICATION_ID + slot, notification)
    }

    private fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val NUDGE_NOTIFICATION_ID = 0x7F000030
        private const val NUDGE_TAP_REQUEST_CODE = 0x7F000032
    }
}
