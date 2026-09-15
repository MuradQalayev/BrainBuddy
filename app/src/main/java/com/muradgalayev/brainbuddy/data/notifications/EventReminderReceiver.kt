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
import com.muradgalayev.brainbuddy.data.local.ModeManager
import com.muradgalayev.brainbuddy.domain.model.ModeNotificationKind
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class EventReminderReceiver : BroadcastReceiver() {

    @Inject lateinit var modeManager: ModeManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(ReminderScheduler.EXTRA_ITEM_ID) ?: return
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: return
        val timeLabel = intent.getStringExtra(ReminderScheduler.EXTRA_TIME_LABEL).orEmpty()
        val kind = ReminderKind.fromName(intent.getStringExtra(ReminderScheduler.EXTRA_KIND))
            ?: return
        val modeKind = if (MEDICATION_TODO_ID.matches(itemId)) {
            // medication doses are represented by to-do rows so they share the user's lead-time
            // preference, but a mode controls them independently: Work may silence ordinary to-dos and
            // must still let a dose reminder through
            ModeNotificationKind.MEDICATION_REMINDERS
        } else {
            intent.getStringExtra(ReminderScheduler.EXTRA_CATEGORY)
                ?.let { stored -> runCatching { ReminderCategory.valueOf(stored) }.getOrNull() }
                ?.toModeNotificationKind()
        }

        val pending = goAsync()
        scope.launch {
            try {
                // old alarms created before category was added carry no extra. let those keep their previous
                // behaviour, app-start rescheduling upgrades them
                val allowed = modeKind?.let { modeManager.isNotificationAllowedNow(it) } ?: true
                if (allowed && hasPostNotificationsPermission(context)) {
                    postReminder(context, itemId, title, timeLabel, kind)
                }
            } catch (t: Throwable) {
                // if the current mode policy can't be established, don't risk posting a reminder the user
                // explicitly asked that mode to silence
                Log.w(TAG, "Could not evaluate event reminder policy: ${t.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private fun postReminder(
        context: Context,
        itemId: String,
        title: String,
        timeLabel: String,
        kind: ReminderKind,
    ) {
        val tapIntent = PendingIntent.getActivity(
            context,
            itemId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.EVENT_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(ReminderCopy.titleFor(context, kind))
            .setContentText(ReminderCopy.bodyFor(context, kind, title, timeLabel))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(ReminderCopy.bodyFor(context, kind, title, timeLabel))
            )
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notifId = (itemId.hashCode() and 0x0FFFFFFF) shl 3 or kind.offsetCode
        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    private fun ReminderCategory.toModeNotificationKind(): ModeNotificationKind = when (this) {
        ReminderCategory.TODO -> ModeNotificationKind.TODO_REMINDERS
        ReminderCategory.CALENDAR -> ModeNotificationKind.CALENDAR_REMINDERS
    }

    private fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private companion object {
        const val TAG = "EventReminder"
        val MEDICATION_TODO_ID =
            Regex("^med-(.+)-(morning|afternoon|evening|night)-(\\d{4}-\\d{2}-\\d{2})$")
    }
}
