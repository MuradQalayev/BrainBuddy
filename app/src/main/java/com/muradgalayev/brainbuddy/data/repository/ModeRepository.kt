package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.muradgalayev.brainbuddy.data.local.dao.AppModeDao
import com.muradgalayev.brainbuddy.data.local.entity.AppModeEntity
import com.muradgalayev.brainbuddy.data.local.entity.SyncStatus
import com.muradgalayev.brainbuddy.data.remote.AppModeDto
import com.muradgalayev.brainbuddy.data.remote.SupabaseAppModeDataSource
import com.muradgalayev.brainbuddy.domain.model.AppMode
import com.muradgalayev.brainbuddy.domain.model.BuiltInModes
import com.muradgalayev.brainbuddy.domain.model.ModeOverrides
import com.muradgalayev.brainbuddy.domain.model.ModeSchedule
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import javax.inject.Inject
import javax.inject.Singleton

// the user's modes, local-first and synced. Room is what the UI reads, Supabase is where a
// mode goes so it survives a reinstall and reaches a second device. nothing here blocks on
// the network: an edit lands in Room marked pending, and sync() settles up on the next pass
@Singleton
class ModeRepository @Inject constructor(
    private val appModeDao: AppModeDao,
    private val remoteDataSource: SupabaseAppModeDataSource,
    private val authRepository: AuthRepository,
) {
    private val localMutationMutex = Mutex()
    // orders every remote mutation so an older request can never finish after a newer one
    private val remoteOperationMutex = Mutex()

    private fun userId(): String? = authRepository.getCurrentOrCachedUserId()

    // rebind Room observation whenever Supabase restores, replaces or clears a session. looking
    // the user up once here would freeze this singleton onto whichever account existed at
    // construction, often none on a cold start. during async session restore the disk-backed
    // cached id keeps the right offline rows visible, and an account change switches to the new
    // owner's query rather than letting the old flow keep emitting
    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeModes(): Flow<List<AppMode>> = authRepository.sessionStatus
        .map { status ->
            when (status) {
                is SessionStatus.Authenticated -> status.session.user?.id
                    ?: authRepository.getCurrentUserId()
                SessionStatus.Initializing,
                is SessionStatus.RefreshFailure,
                -> authRepository.getCurrentOrCachedUserId()
                is SessionStatus.NotAuthenticated -> null
            }
        }
        .distinctUntilChanged()
        .flatMapLatest { userId ->
            if (userId == null) {
                flowOf(emptyList())
            } else {
                appModeDao.observeForUser(userId)
            }
        }
        .map { rows -> rows.map { it.toDomain() } }

    suspend fun getModes(): List<AppMode> {
        val userId = userId() ?: return emptyList()
        return appModeDao.getForUser(userId).map { it.toDomain() }
    }

    suspend fun getMode(id: String): AppMode? =
        userId()?.let { appModeDao.getById(id, it)?.toDomain() }

    // the initial authenticated sync. seeds Work and Weekend only after a successful remote read
    // proves the account really has no modes: a cached identity isn't enough, since the local
    // table may be empty because the database was rebuilt while the account still has customised
    // built-ins on the server, and seeding first would make the push overwrite them
    suspend fun seedBuiltInsIfEmpty() {
        if (authRepository.getCurrentUserId() == null) return
        sync()
    }

    // saves locally and reports whether there was an account to own the row. a network failure
    // doesn't make this false: Room stays the source of truth and the pending status is kept
    suspend fun save(mode: AppMode): Boolean {
        val userId = userId() ?: return false
        val row = localMutationMutex.withLock {
            val existing = appModeDao.getById(mode.id, userId)
            val status = when {
                existing == null -> SyncStatus.PENDING_INSERT
                existing.syncStatus == SyncStatus.PENDING_INSERT.name -> SyncStatus.PENDING_INSERT
                else -> SyncStatus.PENDING_UPDATE
            }
            val revision = nextModeRevision(
                clockMillis = System.currentTimeMillis(),
                previousRevision = existing?.lastModifiedAt,
            )
            mode.toEntity(userId, status, revision).also { appModeDao.upsert(it) }
        }
        runCatching { pushOne(row, userId) }
        return true
    }

    // deletes a user-created mode. built-ins are refused rather than silently ignored: they can
    // be edited or left without a schedule, but removing them could leave an empty picker
    suspend fun delete(id: String): Boolean {
        val userId = userId() ?: return false
        val tombstone = localMutationMutex.withLock {
            val existing = appModeDao.getById(id, userId) ?: return false
            if (existing.isBuiltIn) return false

            // even a PENDING_INSERT may already have reached the server while its local ack is in flight.
            // always tombstone and issue a serialised remote delete, treating it as local-only orphans a row
            val revision = nextModeRevision(System.currentTimeMillis(), existing.lastModifiedAt)
            appModeDao.markPendingDelete(id, userId, revision)
            existing.copy(
                syncStatus = SyncStatus.PENDING_DELETE.name,
                lastModifiedAt = revision,
            )
        }
        runCatching { pushDelete(tombstone, userId) }
            .onFailure { Log.w(TAG, "Mode delete deferred: ${it.safeLogReason()}") }
        return true
    }

    suspend fun sync() {
        val userId = authRepository.getCurrentUserId() ?: return
        val remotePullSucceeded = if (appModeDao.countForUser(userId) == 0) {
            // empty local storage can mean database recovery, not a new account. pull first so customised
            // server-side built-ins are restored rather than overwritten
            pullRemote(userId)
        } else {
            true
        }
        // only the empty-local path pulls before pushing. existing local rows may include offline
        // edits, so their normal sync stays push-first and can't be undone by an older snapshot
        if (!remotePullSucceeded) return
        pushPending(userId)
        // now that local pending revisions have won, pull once more to reconcile remote deletions
        // and rows created on another device, without replacing pending mutations
        val finalPullSucceeded = pullRemote(userId)
        // bootstrap() runs before Supabase has restored a cold-start session, so seed here too and a
        // brand-new account or an account switch still gets the built-ins. never seed on a failed
        // pull: an empty local database doesn't prove the remote account is empty
        val localCount = appModeDao.countForUser(userId)
        if (shouldSeedBuiltInModes(localCount, finalPullSucceeded) && seedBuiltIns(userId)) {
            pushPending(userId)
        }
    }

    private suspend fun seedBuiltIns(userId: String): Boolean {
        Log.i(TAG, "Seeding built-in modes for current account")
        // IGNORE rather than REPLACE closes the race with a simultaneous remote pull or edit
        return BuiltInModes.all.fold(false) { insertedAny, mode ->
            val inserted = appModeDao.insertIfAbsent(
                mode.toEntity(userId, SyncStatus.PENDING_INSERT),
            ) != -1L
            insertedAny || inserted
        }
    }

    private suspend fun pushPending(userId: String) {
        for (row in appModeDao.getPendingSyncItems(userId)) {
            try {
                if (row.syncStatus == SyncStatus.PENDING_DELETE.name) {
                    pushDelete(row, userId)
                } else {
                    pushOne(row, userId)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Mode ${row.id} failed to sync: ${e.safeLogReason()}")
            }
        }
    }

    private suspend fun pushOne(row: AppModeEntity, userId: String) {
        remoteOperationMutex.withLock {
            // the row may have been edited or tombstoned while this waited behind another request. never
            // send a stale snapshot after a newer one
            val current = appModeDao.getById(row.id, userId)
            if (!current.matchesPendingRevision(row)) return@withLock

            remoteDataSource.upsert(row.toDto())
            val acknowledged = appModeDao.acknowledgeSync(
                id = row.id,
                userId = userId,
                expectedStatus = row.syncStatus,
                expectedLastModifiedAt = row.lastModifiedAt,
                newStatus = SyncStatus.SYNCED.name,
            )
            if (acknowledged == 0) {
                // a newer local revision won the race while this was in flight. it stays pending, and its
                // eventual push is ordered after this old request
                Log.d(TAG, "Mode ${row.id} changed while syncing; keeping the newer revision pending")
            }
        }
    }

    private suspend fun pushDelete(row: AppModeEntity, userId: String) {
        remoteOperationMutex.withLock {
            val current = appModeDao.getById(row.id, userId)
            if (!current.matchesPendingRevision(row)) return@withLock

            // delete the remote row first: if it throws, the tombstone stays in Room and the next sync retries
            remoteDataSource.delete(id = row.id, userId = userId)
            appModeDao.deletePendingRevision(row.id, userId, row.lastModifiedAt)
        }
    }

    // applies the account's modes to this device. rows still pending locally are left alone,
    // they're newer than anything the server can return and overwriting would undo an offline edit
    private suspend fun pullRemote(userId: String): Boolean {
        val remote = runCatching { remoteDataSource.getAll(userId) }
            .onFailure { Log.w(TAG, "Mode pull failed: ${it.safeLogReason()}") }
            .getOrNull() ?: return false
        // RLS already guarantees this, but rejecting a mismatched payload makes ownership an
        // invariant even if a backend policy is ever accidentally loosened
        val ownedRemote = remote.filter { it.userId == userId }
        val remoteIds = ownedRemote.mapTo(mutableSetOf()) { it.id }
        val missingSyncedIds = syncedModeIdsMissingRemotely(
            localRows = appModeDao.getForUser(userId),
            remoteIds = remoteIds,
        )

        // a mode deleted on another device has to disappear here too. the DAO repeats the SYNCED
        // predicate so a concurrent local edit can't be deleted after this read
        missingSyncedIds.forEach { id -> appModeDao.deleteSyncedById(id, userId) }

        // mergeRemote checks the sync status in the UPDATE itself, which closes the race where an
        // offline edit lands after the pull's initial read but before its merge
        ownedRemote.forEach { dto -> appModeDao.mergeRemote(dto.toEntity(userId)) }
        return true
    }

    suspend fun clearLocalForUser(userId: String) = appModeDao.deleteAllForUser(userId)

    // mapping

    private fun AppMode.toEntity(
        userId: String,
        status: SyncStatus,
        lastModifiedAt: Long = System.currentTimeMillis(),
    ) = AppModeEntity(
        id = id,
        userId = userId,
        name = name,
        icon = icon,
        accent = accent,
        isBuiltIn = isBuiltIn,
        sortIndex = sortIndex,
        scheduleJson = schedule?.let { json.encodeToString(ModeSchedule.serializer(), it) },
        overridesJson = json.encodeToString(ModeOverrides.serializer(), overrides),
        syncStatus = status.name,
        lastModifiedAt = lastModifiedAt,
    )

    private fun AppModeEntity.toDomain() = AppMode(
        id = id,
        name = name,
        icon = icon,
        accent = accent,
        isBuiltIn = isBuiltIn,
        sortIndex = sortIndex,
        // a blob that won't parse, written by a newer build or corrupted, degrades to 'no schedule'
        // rather than taking the whole mode list down. a dead mode is fixable by editing, a crash isn't
        schedule = scheduleJson?.let {
            runCatching { json.decodeFromString(ModeSchedule.serializer(), it) }.getOrNull()
        },
        overrides = runCatching {
            json.decodeFromString(ModeOverrides.serializer(), overridesJson)
        }.getOrElse { ModeOverrides() },
    )

    private fun AppModeEntity.toDto() = AppModeDto(
        id = id,
        userId = userId,
        name = name,
        icon = icon,
        accent = accent,
        isBuiltIn = isBuiltIn,
        sortIndex = sortIndex,
        schedule = scheduleJson?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() },
        overrides = runCatching { json.parseToJsonElement(overridesJson) }
            .getOrElse { json.parseToJsonElement("{}") },
    )

    private fun AppModeDto.toEntity(userId: String) = AppModeEntity(
        id = id,
        userId = userId,
        name = name,
        icon = icon,
        accent = accent,
        isBuiltIn = isBuiltIn,
        sortIndex = sortIndex,
        scheduleJson = schedule?.takeIf { it != JsonNull }?.toString(),
        overridesJson = overrides.takeIf { it != JsonNull }?.toString() ?: "{}",
        syncStatus = SyncStatus.SYNCED.name,
        lastModifiedAt = System.currentTimeMillis(),
    )

    private companion object {
        const val TAG = "ModeRepository"

        // tolerant of unknown keys so a mode created on a newer build, one overriding a setting this
        // version has never heard of, still loads with the overrides it does understand
        val json = Json { ignoreUnknownKeys = true; encodeDefaults = false }
    }
}

// Supabase/Ktor messages may append the request URL and Authorization headers on later lines.
// Keep the useful first-line reason only, and reject it too if it resembles request metadata.
private fun Throwable.safeLogReason(): String {
    val type = this::class.simpleName ?: "request failure"
    val firstLine = message
        ?.lineSequence()
        ?.firstOrNull()
        ?.trim()
        ?.take(240)
        ?.takeIf { it.isNotBlank() }
        ?: return type
    val containsRequestMetadata = listOf(
        "authorization",
        "bearer",
        "apikey",
        "http://",
        "https://",
    ).any { marker -> firstLine.contains(marker, ignoreCase = true) }
    return if (containsRequestMetadata) type else firstLine
}

// kept visible to unit tests, because losing pending work is costly
internal fun syncedModeIdsMissingRemotely(
    localRows: List<AppModeEntity>,
    remoteIds: Set<String>,
): Set<String> = localRows.asSequence()
    .filter { it.syncStatus == SyncStatus.SYNCED.name }
    .map { it.id }
    .filterNot { it in remoteIds }
    .toSet()

// seeding is only safe once the server has authoritatively said there are no modes
internal fun shouldSeedBuiltInModes(
    localCount: Int,
    remotePullSucceeded: Boolean,
): Boolean = remotePullSucceeded && localCount == 0

// produces a revision that still changes when two edits land in the same millisecond. the
// DAO uses it as part of its compare-and-set sync acknowledgement
internal fun nextModeRevision(clockMillis: Long, previousRevision: Long?): Long = when {
    previousRevision == null || clockMillis > previousRevision -> clockMillis
    previousRevision == Long.MAX_VALUE -> Long.MAX_VALUE
    else -> previousRevision + 1L
}

// true only while the exact queued mutation is still the current local truth
internal fun AppModeEntity?.matchesPendingRevision(expected: AppModeEntity): Boolean =
    this != null &&
        userId == expected.userId &&
        id == expected.id &&
        syncStatus == expected.syncStatus &&
        lastModifiedAt == expected.lastModifiedAt
