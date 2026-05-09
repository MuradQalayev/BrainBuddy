package com.muradgalayev.brainbuddy.data.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.muradgalayev.brainbuddy.MainActivity
import com.muradgalayev.brainbuddy.R

class EventReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val itemId = intent.getStringExtra(ReminderScheduler.EXTRA_ITEM_ID) ?: return
        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: return
        val timeLabel = intent.getStringExtra(ReminderScheduler.EXTRA_TIME_LABEL).orEmpty()
        val kind = ReminderKind.fromName(intent.getStringExtra(ReminderScheduler.EXTRA_KIND))
            ?: return

        if (!hasPostNotificationsPermission(context)) return

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
            .setContentTitle(ReminderCopy.titleFor(kind))
            .setContentText(ReminderCopy.bodyFor(kind, title, timeLabel))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(ReminderCopy.bodyFor(kind, title, timeLabel))
            )
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        val notifId = (itemId.hashCode() and 0x0FFFFFFF) shl 3 or kind.offsetCode
        NotificationManagerCompat.from(context).notify(notifId, notification)
    }

    private fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}
