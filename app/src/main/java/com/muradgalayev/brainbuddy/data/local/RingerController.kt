package com.muradgalayev.brainbuddy.data.local

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.muradgalayev.brainbuddy.domain.model.RingerSetting
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

// silences the phone, or lets it buzz, for as long as a mode says so. changing the ringer to
// silent or vibrate needs ACCESS_NOTIFICATION_POLICY, the same DND access already granted for
// the focus zen rule, so a mode that mutes costs the user no additional permission.
// the ringer the user had before is persisted rather than held in a field, for the same reason
// FocusModeManager persists its rule state: the process dying while a mode is active must not
// leave a phone permanently on silent with nothing left that knows to undo it
@Singleton
class RingerController @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var changedByUs: Boolean
        get() = prefs.getBoolean(KEY_CHANGED, false)
        set(value) = prefs.edit().putBoolean(KEY_CHANGED, value).apply()

    private var previousMode: Int
        get() = prefs.getInt(KEY_PREVIOUS, AudioManager.RINGER_MODE_NORMAL)
        set(value) = prefs.edit().putInt(KEY_PREVIOUS, value).apply()

    fun hasPermission(): Boolean = notificationManager.isNotificationPolicyAccessGranted

    // applies the setting, remembering what to go back to. the snapshot is only taken on the first
    // change, so a mode switching from silent to vibrate still restores the ringer the user
    // actually chose rather than the one the previous mode imposed
    fun apply(setting: RingerSetting): Boolean {
        val manager = audioManager ?: return false
        if (!hasPermission()) return false
        return try {
            if (!changedByUs) {
                previousMode = manager.ringerMode
                changedByUs = true
            }
            manager.ringerMode = when (setting) {
                RingerSetting.NORMAL -> AudioManager.RINGER_MODE_NORMAL
                RingerSetting.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
                // silent is no sound and no vibration, the 'make the vibration zero' case. RINGER_MODE_VIBRATE
                // would still buzz
                RingerSetting.SILENT -> AudioManager.RINGER_MODE_SILENT
            }
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Ringer change refused: ${e.message}")
            false
        }
    }

    // puts back whatever the user had, if and only if we were the ones who changed it
    fun restore(): Boolean {
        val manager = audioManager ?: return false
        if (!changedByUs) return false
        if (!hasPermission()) {
            // access revoked while a mode held the phone silent. we can't undo it, and holding the claim
            // would block every future attempt, so drop it and let the user's own setting stand
            changedByUs = false
            return false
        }
        return try {
            manager.ringerMode = previousMode
            changedByUs = false
            true
        } catch (e: SecurityException) {
            Log.w(TAG, "Ringer restore refused: ${e.message}")
            changedByUs = false
            false
        }
    }

    // clears a ringer change left behind by a process that died while a mode was on
    fun reconcile() {
        if (!changedByUs) return
        Log.w(TAG, "Ringer was still overridden from a previous process — restoring")
        restore()
    }

    private companion object {
        const val TAG = "RingerController"
        const val PREFS = "mode_ringer"
        const val KEY_CHANGED = "changed_by_us"
        const val KEY_PREVIOUS = "previous_ringer_mode"
    }
}
