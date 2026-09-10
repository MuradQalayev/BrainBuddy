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
        val (title, body) = questionnaireReminderCopy(progress.answered, progress.total)
        val notification = NotificationCompat.Builder(
            context,
            NotificationChannels.QUESTIONNAIRE_REMINDERS,
        )
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openPendingIntent)
            .addAction(0, "Continue", openPendingIntent)
            .addAction(0, "Remind me in 3 days", snoozePendingIntent)
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

internal fun questionnaireReminderCopy(answered: Int, total: Int): Pair<String, String> {
    val safeTotal = total.coerceAtLeast(0)
    val safeAnswered = answered.coerceIn(0, safeTotal)
    val remaining = safeTotal - safeAnswered
    val minutes = TimeUnit.SECONDS.toMinutes((remaining * 12L) + 59L).coerceIn(1, 6)
    return when {
        remaining == 0 -> "Your profile is ready to finish" to
            "All $safeTotal steps are saved. Open Myndora and finish your profile when you’re ready."
        safeAnswered == 0 -> "A small start still counts" to
            "Begin your Myndora profile a few questions at a time."
        remaining == 1 -> "You’re one answer away" to
            "$safeAnswered answers are safely saved. Continue with the final question."
        remaining <= 3 -> "Your profile is nearly ready" to
            "$safeAnswered of $safeTotal answers are saved. Only $remaining left."
        else -> "Continue when you have $minutes min" to
            "$safeAnswered of $safeTotal answers are safely saved. Pick up at the next one."
    }
}
