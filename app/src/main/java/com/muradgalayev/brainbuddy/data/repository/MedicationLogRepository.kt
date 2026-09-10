package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.local.dao.MedicationDoseLogDao
import com.muradgalayev.brainbuddy.data.local.entity.MedicationDoseLogEntity
import com.muradgalayev.brainbuddy.data.local.entity.SyncStatus
import com.muradgalayev.brainbuddy.data.remote.SupabaseMedicationDoseLogDataSource
import com.muradgalayev.brainbuddy.data.remote.dto.MedicationDoseLogDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// which doses have actually been taken. these used to live only in a DataStore string set, so
// the record disappeared on reinstall and never reached a second device, while the medication
// list synced normally, which made the app look like it was keeping a history it wasn't. Room
// is the source of truth and Supabase mirrors it, matching every other repository here: a tick
// registers instantly and offline, and catches up later
@Singleton
class MedicationLogRepository @Inject constructor(
    private val dao: MedicationDoseLogDao,
    private val remoteDataSource: SupabaseMedicationDoseLogDataSource,
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager,
) {
    companion object {
        private const val TAG = "MedicationLog"
    }

    private fun userId(): String? = authRepository.getCurrentOrCachedUserId()

    // keys of every dose currently marked taken
    fun observeTakenKeys(): Flow<Set<String>> {
        val id = userId() ?: return emptyFlow()
        return dao.observeTakenKeys(id).map { it.toSet() }
    }

    suspend fun setTaken(logKey: String, taken: Boolean) {
        val id = userId() ?: return
        val entity = MedicationDoseLogEntity(
            id = MedicationDoseLogEntity.idFor(id, logKey),
            userId = id,
            logKey = logKey,
            taken = taken,
            updatedAt = System.currentTimeMillis(),
            syncStatus = SyncStatus.PENDING_UPDATE.name,
        )
        dao.upsert(entity)
        tryRemoteUpsert(entity)
    }

    // sync

    suspend fun sync() {
        val id = userId() ?: return
        runCatching { migrateFromPreferences(id) }
        pushPending(id)
        pullRemote(id)
    }

    // moves the old DataStore ticks into Room, once. runs only while Room is empty for this user,
    // so it can't resurrect doses since un-ticked. the DataStore set is left in place rather than
    // cleared: it costs nothing, and it's the only copy if this ever needs re-running
    private suspend fun migrateFromPreferences(userId: String) {
        if (dao.countForUser(userId) > 0) return
        val legacy = preferencesManager.medicationDoseLogs.first()
        if (legacy.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.upsertAll(
            legacy.map { key ->
                MedicationDoseLogEntity(
                    id = MedicationDoseLogEntity.idFor(userId, key),
                    userId = userId,
                    logKey = key,
                    taken = true,
                    updatedAt = now,
                    syncStatus = SyncStatus.PENDING_INSERT.name,
                )
            },
        )
        Log.d(TAG, "Migrated ${legacy.size} dose logs out of DataStore")
    }

    private suspend fun pushPending(userId: String) {
        val pending = runCatching { dao.getPendingSyncItems(userId) }.getOrNull().orEmpty()
        for (entity in pending) tryRemoteUpsert(entity)
    }

    // newest write per dose wins. un-ticking is a real edit, so a remote row with taken = false
    // must be able to overwrite a local true, which is exactly why the row carries a flag instead
    // of being deleted
    private suspend fun pullRemote(userId: String) {
        val remote = runCatching { remoteDataSource.getAll(userId) }
            .onFailure { Log.w(TAG, "Dose log pull failed: ${it.message}") }
            .getOrNull() ?: return
        val local = dao.getAll(userId).associateBy { it.id }
        val incoming = remote.mapNotNull { dto ->
            val mine = local[dto.id]
            if (mine != null && mine.updatedAt >= dto.updatedAtMillis) return@mapNotNull null
            MedicationDoseLogEntity(
                id = dto.id,
                userId = dto.userId,
                logKey = dto.logKey,
                taken = dto.taken,
                updatedAt = dto.updatedAtMillis,
                syncStatus = SyncStatus.SYNCED.name,
            )
        }
        if (incoming.isNotEmpty()) dao.upsertAll(incoming)
    }

    private suspend fun tryRemoteUpsert(entity: MedicationDoseLogEntity) {
        runCatching {
            remoteDataSource.upsert(
                MedicationDoseLogDto(
                    id = entity.id,
                    userId = entity.userId,
                    logKey = entity.logKey,
                    taken = entity.taken,
                    updatedAtMillis = entity.updatedAt,
                ),
            )
        }
            .onSuccess { dao.updateSyncStatus(entity.id, SyncStatus.SYNCED.name) }
            // offline is expected, not an error: the row stays pending and SyncCoordinator re-pushes it
            // on the next reconnect
            .onFailure { Log.d(TAG, "Dose log upsert deferred: ${it.message}") }
    }

    suspend fun clearLocalForUser(userId: String) {
        runCatching { dao.deleteAllForUser(userId) }
    }
}
