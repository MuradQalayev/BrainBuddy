package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.remote.AiConsentDto
import com.muradgalayev.brainbuddy.data.remote.SupabaseAiConsentDataSource
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

// what the user has allowed Myndora's AI to use, kept where it can survive the device. consent
// used to live only in DataStore, which made it a setting rather than a record: clearing app
// data erased it with no trace it had ever been given, a second device knew nothing about it,
// and 'did this user consent, and to what?' had no answer anywhere.
// local-first like everything else, so the switch takes effect immediately and never waits on
// the network, with pushPending settling up later. the one thing not deferred is the direction
// of failure: a revoke applies locally the instant it's made, so a phone with no signal still
// stops sending wellness data
@Singleton
class AiConsentRepository @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val remote: SupabaseAiConsentDataSource,
    private val authRepository: AuthRepository,
) {

    // records a decision about wellness personalisation. version is the wording it was given
    // against, see WELLNESS_CONSENT_VERSION
    suspend fun setWellnessConsent(granted: Boolean, version: Int = WELLNESS_CONSENT_VERSION) {
        val decidedAt = Instant.now().toString()
        // local first, and marked as owed to the server until it lands. a decision that failed to
        // upload must not look identical to one that was never made
        preferencesManager.recordAiHealthConsent(
            granted = granted,
            version = version,
            decidedAt = decidedAt,
            pendingPush = true,
        )
        val userId = authRepository.getCurrentOrCachedUserId() ?: return
        if (push(userId, granted, version, decidedAt)) {
            preferencesManager.setAiConsentPendingPush(false)
        }
    }

    // re-sends a decision made offline. no-op when nothing is owed
    suspend fun pushPending() {
        if (!preferencesManager.aiConsentPendingPush.first()) return
        val userId = authRepository.getCurrentUserId() ?: return
        val granted = preferencesManager.aiHealthPersonalization.first()
        val version = preferencesManager.aiHealthConsentVersion.first()
        val decidedAt = preferencesManager.aiHealthConsentDecidedAt.first()
            ?: Instant.now().toString()
        if (push(userId, granted, version.coerceAtLeast(1), decidedAt)) {
            preferencesManager.setAiConsentPendingPush(false)
        }
    }

    // applies the account's stored consent to this device. skipped entirely while a local decision
    // is still owed to the server: that decision is newer than anything the server can return, and
    // applying the stale row would flip the switch back under the user, which on a revoke would
    // silently resume sending health data they had just turned off.
    // a device that has never been asked adopts the account's answer, which is the point of
    // storing it: reinstalling or signing in on a second phone no longer loses a decision
    suspend fun pullRemoteAndApply() {
        if (preferencesManager.aiConsentPendingPush.first()) return
        val userId = authRepository.getCurrentUserId() ?: return
        val remoteConsent = runCatching { remote.getAll(userId) }
            .onFailure { Log.w(TAG, "Consent pull failed: ${it.message}") }
            .getOrNull()
            ?.firstOrNull { it.scope == SCOPE_WELLNESS }
            ?: return

        val stillValid = isConsentStillValid(
            granted = remoteConsent.granted,
            storedVersion = remoteConsent.consentVersion,
            currentVersion = WELLNESS_CONSENT_VERSION,
        )

        val localGranted = preferencesManager.aiHealthPersonalization.first()
        val localVersion = preferencesManager.aiHealthConsentVersion.first()
        if (localGranted == stillValid && localVersion == remoteConsent.consentVersion) return

        preferencesManager.recordAiHealthConsent(
            granted = stillValid,
            version = remoteConsent.consentVersion,
            decidedAt = remoteConsent.decidedAt,
            pendingPush = false,
        )
    }

    // writes a decision to the account, unless the account already holds a newer one. the upsert
    // is last-write-wins by arrival, which is wrong for consent as soon as two devices are
    // involved: a phone that revoked while offline can reconnect hours later and overwrite a grant
    // made since, or in the direction that actually matters, a stale grant can overwrite a revoke
    // made afterwards on another device and quietly resume sending wellness data.
    // comparing decided_at first makes the most recent decision win regardless of who reaches the
    // server last. when the account's copy is newer it's adopted locally instead, which visibly
    // reverts the tap, and that's correct: the user did decide otherwise, more recently
    private suspend fun push(
        userId: String,
        granted: Boolean,
        version: Int,
        decidedAt: String,
    ): Boolean = runCatching {
        val existing = remote.getAll(userId).firstOrNull { it.scope == SCOPE_WELLNESS }
        if (existing != null && !shouldOverwriteRemote(decidedAt, existing.decidedAt)) {
            Log.i(TAG, "Account holds a newer consent decision — adopting it instead")
            adoptRemote(existing)
            return@runCatching
        }
        remote.upsert(
            AiConsentDto(
                userId = userId,
                scope = SCOPE_WELLNESS,
                granted = granted,
                consentVersion = version,
                decidedAt = decidedAt,
            )
        )
    }.onFailure {
        Log.w(TAG, "Consent push failed, queued for retry: ${it.message}")
    }.isSuccess

    private suspend fun adoptRemote(remoteConsent: AiConsentDto) {
        preferencesManager.recordAiHealthConsent(
            granted = isConsentStillValid(
                granted = remoteConsent.granted,
                storedVersion = remoteConsent.consentVersion,
                currentVersion = WELLNESS_CONSENT_VERSION,
            ),
            version = remoteConsent.consentVersion,
            decidedAt = remoteConsent.decidedAt,
            pendingPush = false,
        )
    }

    companion object {
        private const val TAG = "AiConsentRepo"
        const val SCOPE_WELLNESS = "WELLNESS"

        // bump this whenever the wellness consent wording changes, or Myndora starts sending something
        // it didn't send before. existing yes answers stop counting at that point and the user is
        // asked again: consent given to a narrower question must not be read as agreement to a wider one
        const val WELLNESS_CONSENT_VERSION = 1
    }
}

// whether a stored decision still authorises anything today. a yes only counts while it was
// given against wording at least as current as what we now send, so bumping the version closes
// the gate until the user answers again. the record of the old answer is kept either way
internal fun isConsentStillValid(
    granted: Boolean,
    storedVersion: Int,
    currentVersion: Int,
): Boolean = granted && storedVersion >= currentVersion

// whether our decision is at least as recent as the one already on the account. ties go to the
// writer: two decisions stamped the same instant are indistinguishable, and refusing both would
// leave pushPending retrying forever. an unparseable timestamp means we can't tell, and
// blocking the write on 'can't tell' would strand the device permanently out of sync, so it
// writes, which is what this did before the comparison existed
internal fun shouldOverwriteRemote(localDecidedAt: String, remoteDecidedAt: String): Boolean {
    val local = parseDecidedAt(localDecidedAt) ?: return true
    val remote = parseDecidedAt(remoteDecidedAt) ?: return true
    return !local.isBefore(remote)
}

// Postgres hands timestamptz back with an offset while ours is written as an Instant. both
// are accepted, plus a bare local timestamp as a last resort, because a consent write must
// not fail over a formatting detail
private fun parseDecidedAt(raw: String): Instant? =
    runCatching { OffsetDateTime.parse(raw).toInstant() }
        .recoverCatching { Instant.parse(raw) }
        .recoverCatching { LocalDateTime.parse(raw).toInstant(ZoneOffset.UTC) }
        .getOrNull()
