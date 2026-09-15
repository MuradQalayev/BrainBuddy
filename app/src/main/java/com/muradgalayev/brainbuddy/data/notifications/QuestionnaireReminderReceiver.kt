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
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.domain.model.SurveyVersion
import dagger.hilt.android.AndroidEntryPoint
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@AndroidEntryPoint
class QuestionnaireReminderReceiver : BroadcastReceiver() {
    @Inject lateinit var scheduler: QuestionnaireReminderScheduler
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var adhdProfileRepository: AdhdProfileRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SNOOZE) {
            scheduler.snooze()
            NotificationManagerCompat.from(context)
                .cancel(QuestionnaireReminderScheduler.NOTIFICATION_ID)
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                val progress = scheduler.progress.value ?: return@launch
                val currentUser = authRepository.getCurrentOrCachedUserId()
                if (currentUser == null) {
                    // Session restoration can lag behind a boot alarm. Try later instead of deleting
                    // a valid reminder because authentication was temporarily unknown.
                    scheduler.snooze(days = 1)
                    return@launch
                }
                if (currentUser != progress.userId) {
                    scheduler.cancelForUser(progress.userId)
                    return@launch
                }
                val completed = adhdProfileRepository.peekProfile()?.surveyCompleted == true ||
                    runCatching { adhdProfileRepository.surveyCompletedOrNull() }.getOrNull() == true
                if (completed) {
                    scheduler.cancelForUser(progress.userId)
                    return@launch
                }

                if (hasPostNotificationsPermission(context)) {
                    postReminder(context, progress)
                    scheduler.scheduleNextAfterDelivery()
                } else {
                    // Keep a single pending reminder available if permission is granted later.
                    scheduler.snooze(days = 7)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private fun postReminder(context: Context, progress: QuestionnaireReminderProgress) {
        val route = when (progress.version) {
            SurveyVersion.Quick -> QuestionnaireReminderContract.ROUTE_QUICK
            SurveyVersion.Deep -> QuestionnaireReminderContract.ROUTE_DEEP
            SurveyVersion.None -> return
        }
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(QuestionnaireReminderContract.EXTRA_OPEN_QUESTIONNAIRE, route)
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            OPEN_REQUEST_CODE,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            SNOOZE_REQUEST_CODE,
            Intent(context, QuestionnaireReminderReceiver::class.java).apply { action = ACTION_SNOOZE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val (title, body) = questionnaireReminderCopy(progress.answered, progress.total, context::getString)
        val notification = NotificationCompat.Builder(
            context,
            NotificationChannels.QUESTIONNAIRE_REMINDERS,
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openPendingIntent)
            .addAction(0, context.getString(R.string.common_continue), openPendingIntent)
            .addAction(0, context.getString(R.string.qr_remind_3_days), snoozePendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context)
            .notify(QuestionnaireReminderScheduler.NOTIFICATION_ID, notification)
    }

    private fun hasPostNotificationsPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        const val ACTION_FIRE = "com.muradgalayev.brainbuddy.QUESTIONNAIRE_REMINDER"
        const val ACTION_SNOOZE = "com.muradgalayev.brainbuddy.QUESTIONNAIRE_SNOOZE"
        private const val OPEN_REQUEST_CODE = 0x7F000042
        private const val SNOOZE_REQUEST_CODE = 0x7F000043
    }
}

// lookup is Context::getString in the receiver and strings.xml in the tests
internal fun questionnaireReminderCopy(
    answered: Int,
    total: Int,
    lookup: (Int) -> String,
): Pair<String, String> {
    val safeTotal = total.coerceAtLeast(0)
    val safeAnswered = answered.coerceIn(0, safeTotal)
    val remaining = safeTotal - safeAnswered
    val minutes = TimeUnit.SECONDS.toMinutes((remaining * 12L) + 59L).coerceIn(1, 6)
    fun text(id: Int, vararg args: Any) = if (args.isEmpty()) lookup(id) else lookup(id).format(*args)
    return when {
        remaining == 0 -> text(R.string.qr_ready_title) to text(R.string.qr_ready_body, safeTotal)
        safeAnswered == 0 -> text(R.string.qr_start_title) to text(R.string.qr_start_body)
        remaining == 1 -> text(R.string.qr_one_title) to text(R.string.qr_one_body, safeAnswered)
        remaining <= 3 ->
            text(R.string.qr_nearly_title) to text(R.string.qr_nearly_body, safeAnswered, safeTotal, remaining)
        else ->
            text(R.string.qr_minutes_title, minutes) to text(R.string.qr_minutes_body, safeAnswered, safeTotal)
    }
}
