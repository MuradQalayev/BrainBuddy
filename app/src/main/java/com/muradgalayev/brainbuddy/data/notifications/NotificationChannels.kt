package com.muradgalayev.brainbuddy.data.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import com.muradgalayev.brainbuddy.R

object NotificationChannels {
    // Bump the suffix when changing sound/importance — Android locks channel
    // settings after creation, so a new sound needs a new channel ID.
    const val EVENT_REMINDERS = "event_reminders_v3"
    const val DAILY_SUMMARY = "daily_summary_v3"

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
