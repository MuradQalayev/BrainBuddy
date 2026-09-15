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
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@AndroidEntryPoint
class MorningSummaryReceiver : BroadcastReceiver() {

    @Inject lateinit var calendarRepository: CalendarRepository
    @Inject lateinit var todoRepository: TodoRepository
    @Inject lateinit var scheduler: ReminderScheduler
    @Inject lateinit var preferencesManager: com.muradgalayev.brainbuddy.data.local.PreferencesManager
    @Inject lateinit var modeManager: com.muradgalayev.brainbuddy.data.local.ModeManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            val enabled = runCatching { preferencesManager.dailySummaryEnabledSnapshot() }
                .getOrDefault(true)
            try {
                if (!enabled) {
                    // turned off since the last schedule, so post nothing and don't re-arm
                    scheduler.cancelMorningSummary()
                    return@launch
                }
                // A mode holding the summary back is temporary, unlike the setting above:
                // skip today but keep the alarm alive for tomorrow.
                val allowedByMode = modeManager.isNotificationAllowedNow(
                    com.muradgalayev.brainbuddy.domain.model.ModeNotificationKind.DAILY_SUMMARY
                )
                if (!allowedByMode) return@launch
                val today = LocalDate.now().toString()
                val events = runCatching {
                    calendarRepository.getEventsByDate(today).first()
                }.getOrDefault(emptyList())
                val todos = runCatching {
                    todoRepository.getTodoItemsByDate(today).first()
                }.getOrDefault(emptyList())

                val titles = events.map { it.title } +
                    todos.filter { !it.isCompleted }.map { it.title }

                if (hasPostNotificationsPermission(context)) {
                    postSummary(context, titles)
                }
            } catch (t: Throwable) {
                Log.w("MorningSummary", "Failed to build summary: ${t.message}")
            } finally {
                if (enabled) runCatching { scheduler.scheduleMorningSummary() }
                pending.finish()
            }
        }
    }

    private fun postSummary(context: Context, titles: List<String>) {
        val (heading, body) = ReminderCopy.morningSummary(context, titles)

        val tapIntent = PendingIntent.getActivity(
            context,
            SUMMARY_TAP_REQUEST_CODE,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.DAILY_SUMMARY)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(heading)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()

        NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, notification)
    }

    private fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val SUMMARY_NOTIFICATION_ID = 0x7F000010
        private const val SUMMARY_TAP_REQUEST_CODE = 0x7F000011
    }
}
