package com.muradgalayev.brainbuddy.data.local

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.Condition
import android.service.notification.ZenPolicy
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import com.muradgalayev.brainbuddy.R

// independent reasons Myndora may currently need its Do Not Disturb rule
enum class DndOwner(val storedKey: String) {
    POMODORO("pomodoro"),
    MODE("mode"),
}

// silences the phone for the length of a focus session.
// why this owns a zen rule rather than setting the global filter: apps targeting API 35+
// can't change global DND at all, setInterruptionFilter is quietly redirected into an
// implicit AutomaticZenRule the app can't name, configure or see. we target 36, so that was
// already happening, and owning an explicit rule buys three things the implicit one can't:
// - state that outlives the process. the old version tracked 'we turned DND on' in a field,
//   and a 25-minute session with the screen off is exactly when Android reclaims a process,
//   so disableDnd returned early at its own guard and the phone stayed silent.
// - no stomping. the old code snapshotted the global filter and wrote it back, which could
//   switch off a DND the user had turned on mid-session. rules combine most-restrictive-wins.
// - a policy of our own, see focusPolicy.
// below API 29 there is no setAutomaticZenRuleState, so those devices keep the global filter
// path. they predate the Android 15 change, so it still genuinely works there
@Singleton
class FocusModeManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // SharedPreferences rather than DataStore: every caller here is synchronous, and the Pomodoro
    // timer decides whether to silence the phone on the same tick it starts
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val supportsZenRules = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private var storedRuleId: String?
        get() = prefs.getString(KEY_RULE_ID, null)
        set(value) = prefs.edit().apply {
            if (value == null) remove(KEY_RULE_ID) else putString(KEY_RULE_ID, value)
        }.apply()

    // every in-app owner currently holding the rule. a set matters: a Pomodoro can overlap a Work
    // mode, and ending either one must leave DND armed for the other. older installs only have
    // KEY_ACTIVE, which belonged to Pomodoro before modes existed, so it migrates to that owner
    private var activeOwners: Set<String>
        get() = prefs.getStringSet(KEY_ACTIVE_OWNERS, null)?.toSet()
            ?: if (prefs.getBoolean(KEY_ACTIVE, false)) {
                setOf(DndOwner.POMODORO.storedKey)
            } else {
                emptySet()
            }
        set(value) {
            prefs.edit()
                .putStringSet(KEY_ACTIVE_OWNERS, value.toSet())
                .putBoolean(KEY_ACTIVE, value.isNotEmpty())
                .apply()
        }

    // only used on the pre-29 path, where restoring the previous filter is still our job
    private var legacyPreviousFilter: Int
        get() = prefs.getInt(KEY_LEGACY_FILTER, NotificationManager.INTERRUPTION_FILTER_ALL)
        set(value) = prefs.edit().putInt(KEY_LEGACY_FILTER, value).apply()

    fun hasPermission(): Boolean = notificationManager.isNotificationPolicyAccessGranted

    fun getPermissionIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun isActiveByUs(owner: DndOwner = DndOwner.POMODORO): Boolean =
        owner.storedKey in activeOwners

    // true when the rule exists but the user has switched it off in system settings. that's a
    // deliberate opt-out and must not be overridden, but the Pomodoro screen shouldn't go on
    // claiming focus mode is armed either, so it's reported rather than silently swallowed
    fun isRuleDisabledByUser(): Boolean {
        if (!supportsZenRules || !hasPermission()) return false
        val id = storedRuleId ?: return false
        return existingRule(id)?.isEnabled == false
    }

    // returns false when it couldn't: no permission, or the user has disabled our rule
    @Synchronized
    fun enableDnd(
        owner: DndOwner = DndOwner.POMODORO,
        allowCallsFromStarred: Boolean = true,
        allowRepeatCallers: Boolean = true,
    ): Boolean {
        if (!hasPermission()) return false
        var owners = activeOwners
        if (supportsZenRules && owners.isNotEmpty()) {
            val ruleStillExists = storedRuleId?.let(::existingRule) != null
            if (!ruleStillExists) {
                // the user can delete our rule while we still hold durable leases. those leases no longer
                // silence anything, so discard them and recreate the rule through the first-owner path below
                owners.mapNotNull { storedKey ->
                    DndOwner.entries.firstOrNull { it.storedKey == storedKey }
                }.forEach(::clearOwnerPolicy)
                activeOwners = emptySet()
                owners = emptySet()
            }
        }
        if (supportsZenRules && owners.isNotEmpty() && isRuleDisabledByUser()) return false

        val nextOwners = owners + owner.storedKey
        val nextPolicy = if (supportsZenRules) {
            combinedPolicy(
                owners = nextOwners,
                changedOwner = owner,
                allowCallsFromStarred = allowCallsFromStarred,
                allowRepeatCallers = allowRepeatCallers,
            )
        } else {
            null
        }

        // re-entering with the same owner isn't a no-op: editing an active mode's caller exceptions
        // has to update the system rule immediately
        if (owner.storedKey in owners) {
            val updated = !supportsZenRules || updateActiveRulePolicy(checkNotNull(nextPolicy))
            if (updated) {
                setOwnerPolicy(owner, allowCallsFromStarred, allowRepeatCallers)
            }
            return updated
        }

        // the shared rule is already on, so record the extra lease without touching the device. this
        // is the overlap path for Work mode plus an active Pomodoro
        if (owners.isNotEmpty()) {
            val updated = !supportsZenRules || updateActiveRulePolicy(checkNotNull(nextPolicy))
            if (!updated) return false
            setOwnerPolicy(owner, allowCallsFromStarred, allowRepeatCallers)
            activeOwners = nextOwners
            return updated
        }

        val enabled = if (supportsZenRules) {
            enableViaZenRule(checkNotNull(nextPolicy))
        } else {
            enableViaGlobalFilter()
        }
        if (enabled) {
            setOwnerPolicy(owner, allowCallsFromStarred, allowRepeatCallers)
            activeOwners = nextOwners
        }
        return enabled
    }

    // releases one owner, lifting silence only after the last one lets go
    @Synchronized
    fun disableDnd(owner: DndOwner = DndOwner.POMODORO): Boolean {
        val owners = activeOwners
        if (owner.storedKey !in owners) return false
        val remaining = owners - owner.storedKey

        val deviceUpdated = if (remaining.isNotEmpty()) {
            // the shared rule stays active but its caller exceptions now have to reflect only the owners
            // still holding it. don't forget the departing lease until Android confirms that policy
            // update, or a failed update leaves device state we can no longer reconcile accurately
            !supportsZenRules || updateActiveRulePolicy(combinedPolicy(remaining))
        } else {
            if (!hasPermission()) return false
            if (supportsZenRules) disableViaZenRule() else disableViaGlobalFilter()
        }

        if (!deviceUpdated) return false
        persistReleasedOwner(owner = owner, remaining = remaining)
        return true
    }

    // clears a focus silence left behind by a process that died mid-session. called once at
    // startup, before anything can start a new one. safe when nothing is active, it does nothing
    // unless our own flag says we left it on
    @Synchronized
    fun reconcile() {
        retireImplicitRule()
        val owners = activeOwners
        if (owners.isEmpty()) return
        Log.w(TAG, "Focus DND was still on from a previous process — clearing it")
        if (!hasPermission()) {
            // keep the leases so a later launch after access is restored can retry. forgetting them here
            // could strand an active rule with no durable owner left to clean it up
            Log.w(TAG, "Cannot reconcile focus DND without policy access; will retry")
            return
        }
        // process death invalidates every in-memory task that owned a lease. ModeManager re-acquires
        // MODE immediately if a persisted or scheduled mode still needs DND
        val disabled = if (supportsZenRules) disableViaZenRule() else disableViaGlobalFilter()
        if (disabled) {
            persistClearedOwners(owners)
        } else {
            // keep ownership durable: clearing it after a failed platform call would make the next
            // startup believe there is nothing left to restore
            Log.w(TAG, "Could not reconcile focus DND; retaining owners for retry")
        }
    }

    // leaves behind the implicit rule the old implementation caused. every setInterruptionFilter
    // call this app made on Android 15+ created or updated an unnamed AutomaticZenRule owned by
    // us, so upgrading users arrive with one already sitting in their DND schedules, possibly
    // still active. one INTERRUPTION_FILTER_ALL deactivates it, and on API 35+ that call is
    // scoped to our own rule. guarded to 35+ for that reason: older platforms would undo the
    // user's own setting
    private fun retireImplicitRule() {
        if (Build.VERSION.SDK_INT < 35) return
        if (prefs.getBoolean(KEY_IMPLICIT_RETIRED, false)) return
        if (!hasPermission()) return // Retry on a later launch, once access is granted.
        runCatching {
            notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
        }.onFailure {
            Log.w(TAG, "Could not retire the implicit zen rule: ${it.message}")
        }
        prefs.edit().putBoolean(KEY_IMPLICIT_RETIRED, true).apply()
    }

    // API 29+: our own rule

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun enableViaZenRule(policy: ZenPolicy): Boolean = try {
        val id = ensureRule(policy)
        if (id == null) {
            false
        } else if (existingRule(id)?.isEnabled == false) {
            // the user turned our schedule off in Settings, respect it
            Log.i(TAG, "Focus zen rule is disabled by the user — not silencing")
            false
        } else {
            notificationManager.setAutomaticZenRuleState(
                id,
                Condition(CONDITION_ID, context.getString(R.string.focus_condition_on), Condition.STATE_TRUE),
            )
            true
        }
    } catch (e: Exception) {
        Log.w(TAG, "Could not activate focus zen rule: ${e.message}")
        false
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun disableViaZenRule(): Boolean = try {
        val id = storedRuleId
        if (id != null) {
            notificationManager.setAutomaticZenRuleState(
                id,
                Condition(CONDITION_ID, context.getString(R.string.focus_condition_off), Condition.STATE_FALSE),
            )
        }
        // cleared even when the id had gone stale: whatever the rule is doing we're no longer
        // claiming responsibility for it, and holding the flag would block every future cleanup
        true
    } catch (e: Exception) {
        Log.w(TAG, "Could not deactivate focus zen rule: ${e.message}")
        false
    }

    // the rule id, creating the rule on first use. lazy on purpose: addAutomaticZenRule makes it
    // visible in the device's DND schedules straight away, so registering at startup would put a
    // Myndora entry in the system settings of everyone who never turns focus mode on. also
    // re-creates it if the user deleted it, which otherwise leaves a stored id that does nothing
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun ensureRule(policy: ZenPolicy): String? {
        storedRuleId?.let { existing ->
            existingRule(existing)?.let { rule ->
                return try {
                    rule.zenPolicy = policy
                    if (notificationManager.updateAutomaticZenRule(existing, rule)) existing
                    else null
                } catch (e: Exception) {
                    Log.w(TAG, "Could not update focus zen rule: ${e.message}")
                    null
                }
            }
            Log.i(TAG, "Focus zen rule was deleted — recreating")
        }
        return try {
            val rule = AutomaticZenRule(
                RULE_NAME,
                null,
                null,
                CONDITION_ID,
                policy,
                NotificationManager.INTERRUPTION_FILTER_PRIORITY,
                true,
            )
            notificationManager.addAutomaticZenRule(rule).also { storedRuleId = it }
        } catch (e: Exception) {
            Log.w(TAG, "Could not create focus zen rule: ${e.message}")
            null
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun existingRule(id: String): AutomaticZenRule? =
        runCatching { notificationManager.getAutomaticZenRule(id) }.getOrNull()

    // updates caller exceptions without toggling or replacing the active shared rule
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun updateActiveRulePolicy(policy: ZenPolicy): Boolean {
        return try {
            val id = storedRuleId ?: return false
            val rule = existingRule(id) ?: return false
            if (!rule.isEnabled) return false
            rule.zenPolicy = policy
            notificationManager.updateAutomaticZenRule(id, rule) && run {
                // some Android builds briefly mark an updated rule as inactive. reasserting our condition is
                // scoped to this rule and leaves every other DND source alone
                notificationManager.setAutomaticZenRuleState(
                    id,
                    Condition(CONDITION_ID, context.getString(R.string.focus_condition_on), Condition.STATE_TRUE),
                )
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not update focus zen policy: ${e.message}")
            false
        }
    }

    private data class OwnerPolicy(
        val allowCallsFromStarred: Boolean,
        val allowRepeatCallers: Boolean,
    )

    private fun ownerPolicy(ownerKey: String): OwnerPolicy = OwnerPolicy(
        allowCallsFromStarred = prefs.getBoolean(
            "$KEY_ALLOW_STARRED_PREFIX$ownerKey",
            true,
        ),
        allowRepeatCallers = prefs.getBoolean(
            "$KEY_ALLOW_REPEAT_PREFIX$ownerKey",
            true,
        ),
    )

    private fun setOwnerPolicy(
        owner: DndOwner,
        allowCallsFromStarred: Boolean,
        allowRepeatCallers: Boolean,
    ) {
        prefs.edit()
            .putBoolean("$KEY_ALLOW_STARRED_PREFIX${owner.storedKey}", allowCallsFromStarred)
            .putBoolean("$KEY_ALLOW_REPEAT_PREFIX${owner.storedKey}", allowRepeatCallers)
            .apply()
    }

    private fun clearOwnerPolicy(owner: DndOwner) {
        prefs.edit()
            .remove("$KEY_ALLOW_STARRED_PREFIX${owner.storedKey}")
            .remove("$KEY_ALLOW_REPEAT_PREFIX${owner.storedKey}")
            .apply()
    }

    // commits a lease release atomically, after the matching device update succeeded
    private fun persistReleasedOwner(owner: DndOwner, remaining: Set<String>) {
        prefs.edit()
            .putStringSet(KEY_ACTIVE_OWNERS, remaining.toSet())
            .putBoolean(KEY_ACTIVE, remaining.isNotEmpty())
            .remove("$KEY_ALLOW_STARRED_PREFIX${owner.storedKey}")
            .remove("$KEY_ALLOW_REPEAT_PREFIX${owner.storedKey}")
            .apply()
    }

    // clears stale process leases only once Android confirms its DND state is off
    private fun persistClearedOwners(owners: Set<String>) {
        val editor = prefs.edit()
            .putStringSet(KEY_ACTIVE_OWNERS, emptySet())
            .putBoolean(KEY_ACTIVE, false)
        owners.forEach { ownerKey ->
            editor
                .remove("$KEY_ALLOW_STARRED_PREFIX$ownerKey")
                .remove("$KEY_ALLOW_REPEAT_PREFIX$ownerKey")
        }
        editor.apply()
    }

    // the shared rule allows an exception only when every active owner permits it
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun combinedPolicy(
        owners: Set<String>,
        changedOwner: DndOwner? = null,
        allowCallsFromStarred: Boolean = true,
        allowRepeatCallers: Boolean = true,
    ): ZenPolicy {
        fun policyFor(ownerKey: String): OwnerPolicy =
            if (ownerKey == changedOwner?.storedKey) {
                OwnerPolicy(allowCallsFromStarred, allowRepeatCallers)
            } else {
                ownerPolicy(ownerKey)
            }

        return focusPolicy(
            allowCallsFromStarred = owners.all { policyFor(it).allowCallsFromStarred },
            allowRepeatCallers = owners.all { policyFor(it).allowRepeatCallers },
        )
    }

    // what still gets through during a focus session. not total silence: an alarm you set is the
    // thing that ends the session, and someone calling twice in a row is the closest the phone
    // has to 'this is urgent'. blocking either turns a focus tool into something people stop
    // trusting with their phone
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun focusPolicy(
        allowCallsFromStarred: Boolean,
        allowRepeatCallers: Boolean,
    ): ZenPolicy = ZenPolicy.Builder()
        .allowAlarms(true)
        .allowMedia(true)
        .allowRepeatCallers(allowRepeatCallers)
        .allowCalls(
            if (allowCallsFromStarred) ZenPolicy.PEOPLE_TYPE_STARRED
            else ZenPolicy.PEOPLE_TYPE_NONE
        )
        .allowMessages(ZenPolicy.PEOPLE_TYPE_NONE)
        .allowReminders(false)
        .allowEvents(false)
        .allowSystem(false)
        // held notifications stay listed in the shade, silenced rather than hidden. hiding them
        // outright is a stronger setting than 'focus timer' implies, and someone who pulls the shade
        // down has already chosen to look
        .showInNotificationList(true)
        .showPeeking(false)
        .showFullScreenIntent(false)
        .showLights(false)
        .showBadges(false)
        .build()

    // API 26-28: the old global filter

    private fun enableViaGlobalFilter(): Boolean = try {
        legacyPreviousFilter = notificationManager.currentInterruptionFilter
        notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
        true
    } catch (e: SecurityException) {
        false
    }

    private fun disableViaGlobalFilter(): Boolean = try {
        notificationManager.setInterruptionFilter(legacyPreviousFilter)
        true
    } catch (e: SecurityException) {
        false
    }

    private companion object {
        const val TAG = "FocusModeManager"
        const val PREFS = "focus_mode"
        const val KEY_RULE_ID = "zen_rule_id"
        const val KEY_ACTIVE = "activated_by_us"
        const val KEY_ACTIVE_OWNERS = "active_owners"
        const val KEY_ALLOW_STARRED_PREFIX = "allow_starred_"
        const val KEY_ALLOW_REPEAT_PREFIX = "allow_repeat_"
        const val KEY_LEGACY_FILTER = "legacy_previous_filter"
        const val KEY_IMPLICIT_RETIRED = "implicit_rule_retired"

        // shown to the user in the device's Do Not Disturb schedules
        const val RULE_NAME = "Myndora focus"

        val CONDITION_ID: Uri = Uri.parse("myndora://focus-session")
    }
}
