package com.muradgalayev.brainbuddy.data.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.muradgalayev.brainbuddy.domain.model.SurveyVersion
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class QuestionnaireReminderProgress(
    val userId: String,
    val version: SurveyVersion,
    val answered: Int,
    val total: Int,
    /** Index of the next cadence reminder. */
    val stage: Int,
    val nextAtMillis: Long,
    val preferredHour: Int,
) {
    val remaining: Int get() = (total - answered).coerceAtLeast(0)
    val fraction: Float get() = if (total == 0) 0f else answered.toFloat() / total
}

object QuestionnaireReminderContract {
    const val EXTRA_OPEN_QUESTIONNAIRE = "open_questionnaire"
    const val ROUTE_QUICK = "onboarding_quick"
    const val ROUTE_DEEP = "onboarding_deep"
}

@Singleton
class QuestionnaireReminderScheduler @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val alarmManager: AlarmManager? = context.getSystemService(AlarmManager::class.java)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _progress = MutableStateFlow(readProgress())
    val progress: StateFlow<QuestionnaireReminderProgress?> = _progress.asStateFlow()

    /**
     * Reconciles one unfinished draft. Repeated saves with no new answer keep the existing alarm;
     * making progress restarts the gentle cadence at tomorrow, rather than stacking alarms.
     */
    fun updateProgress(
        userId: String,
        version: SurveyVersion,
        answered: Int,
        total: Int,
        wakeTime: String,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        if (total <= 0 || version == SurveyVersion.None) {
            cancelForUser(userId)
            return
        }
        val safeAnswered = answered.coerceIn(0, total)

        val previous = _progress.value?.takeIf {
            it.userId == userId && it.version == version && it.total == total
        }
        val unchangedAndPending = previous != null &&
            safeAnswered <= previous.answered && previous.nextAtMillis > nowMillis
        if (unchangedAndPending) {
            arm(previous.nextAtMillis)
            return
        }
        if (
            previous != null &&
            safeAnswered <= previous.answered &&
            previous.stage > REMINDER_DELAYS_DAYS.lastIndex &&
            previous.nextAtMillis == NO_ALARM
        ) {
            // The no-progress cadence is exhausted. Only a newly completed step starts it again.
            if (safeAnswered != previous.answered) save(previous.copy(answered = safeAnswered))
            return
        }

        val progressed = previous == null || safeAnswered > previous.answered
        val stage = if (progressed) 0 else previous.stage.coerceIn(0, REMINDER_DELAYS_DAYS.lastIndex)
        val hour = preferredReminderHour(wakeTime).takeIf { wakeTime.isNotBlank() }
            ?: previous?.preferredHour
            ?: DEFAULT_REMINDER_HOUR
        val nextAt = nextQuestionnaireReminderMillis(
            nowMillis = nowMillis,
            delayDays = REMINDER_DELAYS_DAYS[stage],
            preferredHour = hour,
        )
        save(
            QuestionnaireReminderProgress(
                userId = userId,
                version = version,
                answered = safeAnswered,
                total = total,
                stage = stage,
                nextAtMillis = nextAt,
                preferredHour = hour,
            )
        )
        arm(nextAt)
    }

    /** Re-arms the persisted reminder after reboot or an app update. */
    fun rearmSaved(nowMillis: Long = System.currentTimeMillis()) {
        val saved = _progress.value ?: return
        if (saved.nextAtMillis == NO_ALARM) return
        val trigger = if (saved.nextAtMillis > nowMillis) {
            saved.nextAtMillis
        } else {
            nextQuestionnaireReminderMillis(nowMillis, 1, saved.preferredHour)
        }
        if (trigger != saved.nextAtMillis) save(saved.copy(nextAtMillis = trigger))
        arm(trigger)
    }

    /** Called after a notification is posted. There are at most four without fresh progress. */
    fun scheduleNextAfterDelivery(nowMillis: Long = System.currentTimeMillis()) {
        val current = _progress.value ?: return
        val nextStage = current.stage + 1
        if (nextStage > REMINDER_DELAYS_DAYS.lastIndex) {
            cancelAlarm()
            save(current.copy(stage = nextStage, nextAtMillis = NO_ALARM))
            return
        }
        val nextAt = nextQuestionnaireReminderMillis(
            nowMillis,
            REMINDER_DELAYS_DAYS[nextStage],
            current.preferredHour,
        )
        save(current.copy(stage = nextStage, nextAtMillis = nextAt))
        arm(nextAt)
    }

    fun snooze(days: Long = 3, nowMillis: Long = System.currentTimeMillis()) {
        val current = _progress.value ?: return
        val nextAt = nextQuestionnaireReminderMillis(nowMillis, days, current.preferredHour)
        save(current.copy(nextAtMillis = nextAt))
        arm(nextAt)
    }

    fun cancelForUser(userId: String) {
        val current = _progress.value ?: return
        if (current.userId != userId) return
        cancelAlarm()
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        prefs.edit().clear().apply()
        _progress.value = null
    }

    private fun arm(triggerMillis: Long) {
        val manager = alarmManager ?: return
        val pendingIntent = reminderPendingIntent(PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                manager.canScheduleExactAlarms()
            } else true
            if (canExact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            } else {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pendingIntent)
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not schedule questionnaire reminder: ${e.message}")
        }
    }

    private fun cancelAlarm() {
        val pi = reminderPendingIntent(PendingIntent.FLAG_NO_CREATE) ?: return
        alarmManager?.cancel(pi)
        pi.cancel()
    }

    private fun reminderPendingIntent(flags: Int): PendingIntent? = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, QuestionnaireReminderReceiver::class.java).apply {
            action = QuestionnaireReminderReceiver.ACTION_FIRE
        },
        flags or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun save(value: QuestionnaireReminderProgress) {
        prefs.edit()
            .putString(KEY_USER_ID, value.userId)
            .putString(KEY_VERSION, value.version.name)
            .putInt(KEY_ANSWERED, value.answered)
            .putInt(KEY_TOTAL, value.total)
            .putInt(KEY_STAGE, value.stage)
            .putLong(KEY_NEXT_AT, value.nextAtMillis)
            .putInt(KEY_HOUR, value.preferredHour)
            .apply()
        _progress.value = value
    }

    private fun readProgress(): QuestionnaireReminderProgress? {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val version = prefs.getString(KEY_VERSION, null)
            ?.let { raw -> runCatching { SurveyVersion.valueOf(raw) }.getOrNull() }
            ?: return null
        val total = prefs.getInt(KEY_TOTAL, 0)
        if (total <= 0) return null
        return QuestionnaireReminderProgress(
            userId = userId,
            version = version,
            answered = prefs.getInt(KEY_ANSWERED, 0).coerceIn(0, total),
            total = total,
            stage = prefs.getInt(KEY_STAGE, 0).coerceAtLeast(0),
            nextAtMillis = prefs.getLong(KEY_NEXT_AT, NO_ALARM),
            preferredHour = prefs.getInt(KEY_HOUR, DEFAULT_REMINDER_HOUR)
                .coerceIn(EARLIEST_HOUR, LATEST_HOUR),
        )
    }

    companion object {
        const val NOTIFICATION_ID = 0x7F000040
        private const val TAG = "QuestionnaireReminder"
        private const val PREFS_NAME = "questionnaire_reminders"
        private const val REQUEST_CODE = 0x7F000041
        private const val NO_ALARM = 0L
        private const val KEY_USER_ID = "user_id"
        private const val KEY_VERSION = "version"
        private const val KEY_ANSWERED = "answered"
        private const val KEY_TOTAL = "total"
        private const val KEY_STAGE = "stage"
        private const val KEY_NEXT_AT = "next_at"
        private const val KEY_HOUR = "preferred_hour"
        private const val DEFAULT_REMINDER_HOUR = 18
        private const val EARLIEST_HOUR = 10
        private const val LATEST_HOUR = 19
        internal val REMINDER_DELAYS_DAYS = longArrayOf(1, 3, 7, 14)
    }
}

internal fun preferredReminderHour(wakeTime: String): Int {
    val cleaned = wakeTime.trim()
    val parsed = runCatching { LocalTime.parse(cleaned) }.getOrNull()
        ?: runCatching { LocalTime.parse(cleaned, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
        ?: runCatching {
            LocalTime.parse(cleaned.uppercase(), DateTimeFormatter.ofPattern("h:mm a"))
        }.getOrNull()
    return ((parsed?.hour ?: 15) + 3).coerceIn(10, 19)
}

internal fun nextQuestionnaireReminderMillis(
    nowMillis: Long,
    delayDays: Long,
    preferredHour: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    val now = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zoneId)
    return now.toLocalDate()
        .plusDays(delayDays.coerceAtLeast(1))
        .atTime(preferredHour.coerceIn(10, 19), 0)
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
}
