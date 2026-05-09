package com.muradgalayev.brainbuddy.data.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val alarmManager: AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    fun scheduleForItem(
        itemId: String,
        title: String,
        dateIso: String,
        timeIso: String? = null
    ) {
        val start = parseDateTime(dateIso, timeIso) ?: return
        val startMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val timeLabel = formatTimeLabel(start.toLocalTime())

        scheduleOne(itemId, title, timeLabel, ReminderKind.DAY_BEFORE,
            triggerMillis = startMillis - DAY_MS)
        scheduleOne(itemId, title, timeLabel, ReminderKind.MIN_30_BEFORE,
            triggerMillis = startMillis - 30 * MINUTE_MS)
        scheduleOne(itemId, title, timeLabel, ReminderKind.AT_START,
            triggerMillis = startMillis)
    }

    fun cancelForItem(itemId: String) {
        ReminderKind.entries.forEach { kind ->
            val pi = PendingIntent.getBroadcast(
                context,
                requestCodeFor(itemId, kind),
                Intent(context, EventReminderReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: return@forEach
            alarmManager?.cancel(pi)
            pi.cancel()
        }
    }

    fun scheduleMorningSummary(time: LocalTime = DEFAULT_WAKE_TIME) {
        val now = LocalDateTime.now()
        var next = LocalDateTime.of(LocalDate.now(), time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        val triggerMillis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val intent = Intent(context, MorningSummaryReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            MORNING_SUMMARY_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            setExactIfAllowed(triggerMillis, pi)
        } catch (e: SecurityException) {
            Log.w(TAG, "Morning summary alarm failed: ${e.message}")
        }
    }

    private fun scheduleOne(
        itemId: String,
        title: String,
        timeLabel: String,
        kind: ReminderKind,
        triggerMillis: Long,
    ) {
        if (triggerMillis <= System.currentTimeMillis()) return
        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            putExtra(EXTRA_ITEM_ID, itemId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TIME_LABEL, timeLabel)
            putExtra(EXTRA_KIND, kind.name)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCodeFor(itemId, kind),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            setExactIfAllowed(triggerMillis, pi)
        } catch (e: SecurityException) {
            Log.w(TAG, "Failed to schedule $kind for $itemId: ${e.message}")
        }
    }

    private fun setExactIfAllowed(triggerMillis: Long, pi: PendingIntent) {
        val am = alarmManager ?: return
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            am.canScheduleExactAlarms()
        } else true

        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pi)
        }
    }

    private fun requestCodeFor(itemId: String, kind: ReminderKind): Int =
        ((itemId.hashCode() and 0x0FFFFFFF) shl 3) or kind.offsetCode

    private fun parseDateTime(dateIso: String, timeIso: String?): LocalDateTime? {
        return try {
            if (timeIso.isNullOrBlank()) {
                LocalDateTime.parse(dateIso)
            } else {
                val date = LocalDate.parse(dateIso)
                val time = LocalTime.parse(timeIso)
                LocalDateTime.of(date, time)
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatTimeLabel(t: LocalTime): String {
        val hour12 = ((t.hour % 12).takeIf { it != 0 } ?: 12)
        val ampm = if (t.hour < 12) "AM" else "PM"
        return "%d:%02d %s".format(hour12, t.minute, ampm)
    }

    companion object {
        const val EXTRA_ITEM_ID = "item_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TIME_LABEL = "time_label"
        const val EXTRA_KIND = "kind"

        private const val TAG = "ReminderScheduler"
        private const val MINUTE_MS = 60_000L
        private const val DAY_MS = 24L * 60L * 60L * 1000L
        private const val MORNING_SUMMARY_REQUEST_CODE = 0x7F000001
        private val DEFAULT_WAKE_TIME: LocalTime = LocalTime.of(8, 0)
    }
}