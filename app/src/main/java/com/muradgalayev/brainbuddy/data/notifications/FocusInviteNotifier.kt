package com.muradgalayev.brainbuddy.data.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.muradgalayev.brainbuddy.MainActivity
import com.muradgalayev.brainbuddy.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// 'Ali wants to focus with you.' posted when the poll finds an invite the user hasn't been told
// about. a local notification raised by the app's own polling, not a push, so it only fires
// while something in the app is looking. real delivery to a closed app needs FCM and a server.
// cancelled the moment the invite is answered, in either direction: a notification for a
// session you already joined is the kind of small wrongness that makes people stop trusting
// the ones that matter
@Singleton
class FocusInviteNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun notifyInvite(hostName: String, minutes: Int, sessionId: String) {
        if (!canPost()) return

        val open = PendingIntent.getActivity(
            context,
            sessionId.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.FOCUS_NUDGE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("$hostName wants to focus with you")
            .setContentText("$minutes min · starts when you both tap start")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SOCIAL)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        runCatching { manager.notify(sessionId.notificationId(), notification) }
    }

    fun cancel(sessionId: String) {
        runCatching { manager.cancel(sessionId.notificationId()) }
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    // stable per session, so the same invite can't stack up across polls
    private fun String.notificationId(): Int = NOTIFICATION_BASE + (hashCode() and 0xFFFF)

    private companion object {
        const val NOTIFICATION_BASE = 92_000
    }
}
