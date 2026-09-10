package com.muradgalayev.brainbuddy.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import com.muradgalayev.brainbuddy.R

object NotificationChannels {
    // bump the suffix when changing sound or importance: Android locks channel settings after
    // creation, so a new sound needs a new channel id
    const val EVENT_REMINDERS = "event_reminders_v3"
    const val DAILY_SUMMARY = "daily_summary_v3"
    const val FOCUS_NUDGE = "focus_nudge_v1"
    const val POMODORO_ALERTS = "pomodoro_alerts_v1"
    const val QUESTIONNAIRE_REMINDERS = "questionnaire_reminders_v1"

    private val LEGACY_CHANNELS = listOf(
        "event_reminders", "daily_summary",
        "event_reminders_v2", "daily_summary_v2",
    )

    fun createAll(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        LEGACY_CHANNELS.forEach { nm.deleteNotificationChannel(it) }

        val customSoundUri = resolveCustomSoundOrDefault(context)
        val audioAttrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        nm.createNotificationChannel(
            NotificationChannel(
                EVENT_REMINDERS,
                "Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Heads-up nudges before your events and tasks."
                setSound(customSoundUri, audioAttrs)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 200, 250)
                setShowBadge(true)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                DAILY_SUMMARY,
                "Daily summary",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "A gentle morning rundown of your day."
                setSound(customSoundUri, audioAttrs)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200)
                setShowBadge(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                FOCUS_NUDGE,
                "Focus nudges",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Gentle prompts to start a Pomodoro focus session."
                setSound(customSoundUri, audioAttrs)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200)
                setShowBadge(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                QUESTIONNAIRE_REMINDERS,
                "Profile check-ins",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Occasional reminders to continue an unfinished Myndora profile."
                setSound(customSoundUri, audioAttrs)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 200)
                setShowBadge(false)
            }
        )

        nm.createNotificationChannel(
            NotificationChannel(
                POMODORO_ALERTS,
                "Pomodoro breaks",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when a focus session or break finishes."
                setSound(customSoundUri, audioAttrs)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 200, 250)
                setShowBadge(false)
            }
        )
    }

    private fun resolveCustomSoundOrDefault(context: Context): Uri {
        val resId = context.resources.getIdentifier(
            "brain_buddy_chime", "raw", context.packageName
        )
        return if (resId != 0) {
            Uri.parse("android.resource://${context.packageName}/$resId")
        } else {
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        }
    }
}
