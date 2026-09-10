package com.muradgalayev.brainbuddy.data.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
) {
    private val alarmManager: AlarmManager? =
        context.getSystemService(AlarmManager::class.java)

    // re-schedules the lead-time reminders for a single item, honouring the user's per-category
    // frequency preference: enabled kinds get an exact alarm, and disabled kinds have any
    // previously scheduled alarm cancelled, so a toggle-off takes effect on the next reschedule
    suspend fun scheduleForItem(
        itemId: String,
        title: String,
        dateIso: String,
        timeIso: String? = null,
        category: ReminderCategory,
    ) {
        val start = parseDateTime(dateIso, timeIso) ?: return
        val startMillis = start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val timeLabel = formatTimeLabel(start.toLocalTime())

        val enabled = preferencesManager.reminderKindsSnapshot(category)

        ReminderKind.entries.forEach { kind ->
            if (kind in enabled) {
                scheduleOne(
                    itemId = itemId,
                    title = title,
                    timeLabel = timeLabel,
                    kind = kind,
                    category = category,
                    triggerMillis = startMillis - kind.leadMillis,
                )
            } else {
                cancelOne(itemId, kind)
            }
        }
    }

    fun cancelForItem(itemId: String) {
        ReminderKind.entries.forEach { kind -> cancelOne(itemId, kind) }
    }

    private fun cancelOne(itemId: String, kind: ReminderKind) {
        val pi = PendingIntent.getBroadcast(
            context,
            requestCodeFor(itemId, kind),
            Intent(context, EventReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager?.cancel(pi)
        pi.cancel()
    }

    fun scheduleMorningSummary(time: LocalTime = DEFAULT_WAKE_TIME) {
        val triggerMillis = nextOccurrenceMillis(time)
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

    fun cancelMorningSummary() {
        val pi = PendingIntent.getBroadcast(
            context,
            MORNING_SUMMARY_REQUEST_CODE,
            Intent(context, MorningSummaryReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager?.cancel(pi)
        pi.cancel()
    }

    // schedules or clears the daily 'come focus' nudge according to the saved frequency and time.
    // safe to call repeatedly, it fully reconciles state
    suspend fun scheduleFocusNudge() {
        val freq = preferencesManager.pomodoroNudgeFrequencySnapshot()
        val baseTime = parseHhMm(preferencesManager.pomodoroNudgeTimeSnapshot()) ?: DEFAULT_NUDGE_TIME

        cancelFocusNudge() // clear both slots first, then re-arm the active ones

        when (freq) {
            PomodoroNudgeFrequency.OFF -> Unit
            PomodoroNudgeFrequency.ONCE -> scheduleFocusNudgeSlot(0, baseTime)
            PomodoroNudgeFrequency.TWICE -> {
                scheduleFocusNudgeSlot(0, baseTime)
                scheduleFocusNudgeSlot(1, baseTime.plusHours(SECOND_NUDGE_OFFSET_HOURS))
            }
        }
    }

    private fun scheduleFocusNudgeSlot(slot: Int, time: LocalTime) {
        val triggerMillis = nextOccurrenceMillis(time)
        val intent = Intent(context, FocusNudgeReceiver::class.java).apply {
            putExtra(EXTRA_NUDGE_SLOT, slot)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            FOCUS_NUDGE_REQUEST_CODE_BASE + slot,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            setExactIfAllowed(triggerMillis, pi)
        } catch (e: SecurityException) {
            Log.w(TAG, "Focus nudge alarm failed: ${e.message}")
        }
    }

    fun cancelFocusNudge() {
        for (slot in 0..1) {
            val pi = PendingIntent.getBroadcast(
                context,
                FOCUS_NUDGE_REQUEST_CODE_BASE + slot,
                Intent(context, FocusNudgeReceiver::class.java),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            ) ?: continue
            alarmManager?.cancel(pi)
            pi.cancel()
        }
    }

    private fun scheduleOne(
        itemId: String,
        title: String,
        timeLabel: String,
        kind: ReminderKind,
        category: ReminderCategory,
        triggerMillis: Long,
    ) {
        if (triggerMillis <= System.currentTimeMillis()) return
        val intent = Intent(context, EventReminderReceiver::class.java).apply {
            putExtra(EXTRA_ITEM_ID, itemId)
            putExtra(EXTRA_TITLE, title)
            putExtra(EXTRA_TIME_LABEL, timeLabel)
            putExtra(EXTRA_KIND, kind.name)
            putExtra(EXTRA_CATEGORY, category.name)
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

    private fun nextOccurrenceMillis(time: LocalTime): Long {
        val now = LocalDateTime.now()
        var next = LocalDateTime.of(LocalDate.now(), time)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
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

    private fun parseHhMm(raw: String): LocalTime? =
        runCatching { LocalTime.parse(raw) }.getOrNull()

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
        const val EXTRA_CATEGORY = "category"
        const val EXTRA_NUDGE_SLOT = "nudge_slot"

        private const val TAG = "ReminderScheduler"
        private const val SECOND_NUDGE_OFFSET_HOURS = 6L
        private const val MORNING_SUMMARY_REQUEST_CODE = 0x7F000001
        private const val FOCUS_NUDGE_REQUEST_CODE_BASE = 0x7F000020
        private val DEFAULT_WAKE_TIME: LocalTime = LocalTime.of(8, 0)
        private val DEFAULT_NUDGE_TIME: LocalTime = LocalTime.of(10, 0)
    }
}
