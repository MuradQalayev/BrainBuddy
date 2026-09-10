package com.muradgalayev.brainbuddy.data.repository

import android.content.Context
import android.util.Log
import com.muradgalayev.brainbuddy.data.local.dao.ActivityTimeStatDao
import com.muradgalayev.brainbuddy.data.local.dao.CalendarEventDao
import com.muradgalayev.brainbuddy.data.local.entity.ActivityTimeStatEntity
import com.muradgalayev.brainbuddy.data.local.entity.SyncStatus
import com.muradgalayev.brainbuddy.data.mapper.toDto
import com.muradgalayev.brainbuddy.data.mapper.toEntity
import com.muradgalayev.brainbuddy.data.mapper.toStat
import com.muradgalayev.brainbuddy.data.remote.SupabaseActivityTimeStatDataSource
import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import com.muradgalayev.brainbuddy.domain.scheduling.ActivityClassifier
import com.muradgalayev.brainbuddy.domain.scheduling.ActivityKind
import com.muradgalayev.brainbuddy.domain.scheduling.DayType
import com.muradgalayev.brainbuddy.domain.scheduling.HabitKey
import com.muradgalayev.brainbuddy.domain.scheduling.HabitStatLookup
import com.muradgalayev.brainbuddy.domain.scheduling.HabitTimeStat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

// learns when the user actually does things and hands that back to the suggestion engine.
// local first: Room is the source of truth and Supabase is a mirror so the habits follow the
// user to a new device, which is why a suggestion never waits on the network. reads come from
// an in-memory snapshot, because the engine runs on every keystroke and can't suspend
@Singleton
class HabitTimingRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val dao: ActivityTimeStatDao,
    // read directly rather than through CalendarRepository: all that's needed is a one-shot
    // historical scan for the backfill, and routing it through another repository would couple
    // the two for a single query
    private val calendarEventDao: CalendarEventDao,
    private val remoteDataSource: SupabaseActivityTimeStatDataSource,
    private val authRepository: AuthRepository,
) {
    private val prefs = context.getSharedPreferences("habit_timing", Context.MODE_PRIVATE)

    companion object {
        private const val TAG = "HabitTiming"

        // how much each kind of evidence is worth. the ordering is the point: what the user did
        // outranks what they typed, and a time they chose over one we offered outranks both
        const val WEIGHT_PLANNED = 0.5
        const val WEIGHT_COMPLETED = 1.0
        const val WEIGHT_OVERRODE_SUGGESTION = 1.5

        // a past event found during backfill that was never ticked off. it almost certainly happened,
        // people don't tick off imported Google events, but it isn't confirmation either
        const val WEIGHT_BACKFILL = 0.7

        // habits untouched for this long are dropped on the next sync
        private const val PRUNE_AFTER_DAYS = 365L

        // how far back the backfill reads. a year covers seasonal routines, and anything older has
        // decayed to near-nothing before it's even loaded
        private const val BACKFILL_WINDOW_DAYS = 365L

        // ceiling on the one-time scan, so a heavily-imported calendar can't stall start-up
        private const val BACKFILL_MAX_EVENTS = 2_000
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    // every habit for the signed-in user, keyed habitKey|dayType. a volatile map swap rather than
    // a lock: readers want a consistent snapshot, not the very latest write, and a suggestion a
    // few milliseconds stale is indistinguishable from a fresh one
    @Volatile
    private var snapshot: Map<String, HabitTimeStat> = emptyMap()

    private var observedUserId: String? = null

    // safe to call repeatedly, only a change of user does any work
    fun observeForCurrentUser() {
        val userId = authRepository.getCurrentOrCachedUserId() ?: return
        if (observedUserId == userId) return
        observedUserId = userId
        scope.launch {
            dao.observeAll(userId).collectLatest { rows ->
                snapshot = rows.associate { row ->
                    cacheKey(row.habitKey, row.dayType) to row.toStat()
                }
            }
        }
    }

    // a lookup bound to a point in time, so every stat in one suggestion run decays against the
    // same clock. reading the snapshot directly would let a habit from six months ago out-vote
    // last week's
    fun lookupAt(nowMillis: Long): HabitStatLookup = HabitStatLookup { habitKey, dayType ->
        snapshot[cacheKey(habitKey, dayType.key)]?.decayedTo(nowMillis)
    }

    // folds one observation into every bucket it belongs to. four rows move: the specific title
    // and its activity family, each in the any-day pool and in the weekday/weekend bucket.
    // writing the pooled row too is what makes a habit useful after three occurrences, not thirty
    fun record(
        title: String,
        description: String,
        date: LocalDate,
        startMinutes: Int,
        durationMinutes: Int,
        weight: Double,
    ) {
        if (title.isBlank() || durationMinutes <= 0) return
        val userId = authRepository.getCurrentOrCachedUserId() ?: return
        val kind = ActivityClassifier.classify(title, description).kind

        val keys = keysFor(title, kind)
        if (keys.isEmpty()) return

        val dayTypes = listOf(DayType.Any, dayTypeOf(date))
        val at = System.currentTimeMillis()

        scope.launch {
            writeMutex.withLock {
                for (key in keys) {
                    for (dayType in dayTypes) {
                        val id = ActivityTimeStatEntity.idFor(userId, key, dayType.key)
                        val existing = dao.getById(id)?.toStat() ?: HabitTimeStat()
                        val updated = existing.observe(
                            startMinutes = startMinutes,
                            durationMinutes = durationMinutes,
                            atMillis = at,
                            weight = weight,
                        )
                        val entity = updated.toEntity(
                            userId = userId,
                            habitKey = key,
                            dayType = dayType.key,
                            syncStatus = SyncStatus.PENDING_UPDATE,
                        )
                        dao.upsert(entity)
                        tryRemoteUpsert(entity)
                    }
                }
            }
        }
    }

    // convenience for the common case, an event just saved or ticked off. silently ignores events
    // whose stored times don't parse: a malformed row should cost a data point, not a save
    // every bucket one observation belongs in. HabitKey.ALL_EVENTS is always included, even for
    // events we couldn't classify, especially those: pooling them into a General habit would be
    // meaningless, but 'when does this person schedule anything' is the one useful thing left
    private fun keysFor(title: String, kind: ActivityKind): List<String> = buildList {
        HabitKey.forTitle(title)?.let { add(it) }
        if (kind != ActivityKind.General) add(HabitKey.forKind(kind))
        add(HabitKey.ALL_EVENTS)
    }

    fun recordEvent(event: CalendarEvent, weight: Double) {
        val start = runCatching { LocalDateTime.parse(event.startTime.padIsoSeconds()) }.getOrNull()
            ?: return
        val end = runCatching { LocalDateTime.parse(event.endTime.padIsoSeconds()) }.getOrNull()
            ?: return
        val duration = java.time.Duration.between(start, end).toMinutes().toInt()
        if (duration <= 0) return
        record(
            title = event.title,
            description = event.description,
            date = start.toLocalDate(),
            startMinutes = start.hour * 60 + start.minute,
            durationMinutes = duration,
            weight = weight,
        )
    }

    // sync

    suspend fun sync() {
        val userId = authRepository.getCurrentOrCachedUserId() ?: return
        pushPending(userId)
        pullRemote(userId)
        // after the pull, deliberately: if another device has already learned this user's history,
        // replaying it here would count every event twice
        runCatching { backfillIfNeeded(userId) }
        runCatching {
            val cutoff = System.currentTimeMillis() - PRUNE_AFTER_DAYS * 24 * 60 * 60 * 1000L
            dao.pruneOlderThan(userId, cutoff)
        }
    }

    // backfill

    // seeds the habits from calendar history the user already has. without it the feature starts
    // from nothing on the day it ships and takes two months to become personal, while the answer
    // has been sitting in calendar_events all along, Google imports included. runs at most once
    // per account: a persisted flag stops it re-running here, and a non-empty stats table stops
    // it running at all on a second device that already pulled the results
    private suspend fun backfillIfNeeded(userId: String) {
        if (prefs.getBoolean(backfillKey(userId), false)) return
        if (dao.getAll(userId).isNotEmpty()) {
            markBackfilled(userId)
            return
        }

        val from = LocalDate.now().minusDays(BACKFILL_WINDOW_DAYS).atStartOfDay().toString()
        val events = calendarEventDao.getEventsSince(userId, from, BACKFILL_MAX_EVENTS)
            .filter { it.syncStatus != SyncStatus.PENDING_DELETE.name }
        if (events.isEmpty()) {
            // nothing to learn from, but don't re-scan on every sync forever
            markBackfilled(userId)
            return
        }

        val now = System.currentTimeMillis()
        // accumulated in memory and written once. per-event round-trips would be four reads and four
        // writes each, thousands of transactions on a busy calendar, on the same coroutine as app start
        val accumulated = LinkedHashMap<Pair<String, String>, HabitTimeStat>()

        for (event in events) {
            val start = runCatching { LocalDateTime.parse(event.startTime.padIsoSeconds()) }
                .getOrNull() ?: continue
            val end = runCatching { LocalDateTime.parse(event.endTime.padIsoSeconds()) }
                .getOrNull() ?: continue
            val duration = java.time.Duration.between(start, end).toMinutes().toInt()
            if (duration <= 0) continue

            val kind = ActivityClassifier.classify(event.title, event.description).kind
            val keys = keysFor(event.title, kind)
            if (keys.isEmpty()) continue

            // replaying at the event's own timestamp makes recency decay work for free: a two-year-old
            // dinner arrives already faded and last week's at nearly full strength, with no special
            // casing. clamped to now so a far-future plan can't push lastObservedAt past the clock and
            // freeze every later decay
            val at = minOf(start.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), now)
            val weight = when {
                event.completed -> WEIGHT_COMPLETED
                // still in the future: a plan, not evidence of what they did
                start.isAfter(LocalDateTime.now()) -> WEIGHT_PLANNED
                // in the past and not ticked off. most imported events never are, so this can't be read as a
                // failure to show up, but it isn't confirmation either
                else -> WEIGHT_BACKFILL
            }

            val dayTypes = listOf(DayType.Any, dayTypeOf(start.toLocalDate()))
            for (key in keys) {
                for (dayType in dayTypes) {
                    val cacheKey = key to dayType.key
                    accumulated[cacheKey] = (accumulated[cacheKey] ?: HabitTimeStat())
                        .observe(
                            startMinutes = start.hour * 60 + start.minute,
                            durationMinutes = duration,
                            atMillis = at,
                            weight = weight,
                        )
                }
            }
        }

        val rows = accumulated.map { (key, stat) ->
            stat.toEntity(
                userId = userId,
                habitKey = key.first,
                dayType = key.second,
                syncStatus = SyncStatus.PENDING_UPDATE,
            )
        }
        dao.upsertAll(rows)
        markBackfilled(userId)
        Log.d(TAG, "Backfilled ${rows.size} habits from ${events.size} events")
        for (row in rows) tryRemoteUpsert(row)
    }

    private fun backfillKey(userId: String) = "backfilled_$userId"

    private fun markBackfilled(userId: String) {
        prefs.edit().putBoolean(backfillKey(userId), true).apply()
    }

    private suspend fun pushPending(userId: String) {
        val pending = runCatching { dao.getPendingSyncItems(userId) }.getOrNull().orEmpty()
        for (entity in pending) tryRemoteUpsert(entity)
    }

    // pulls the remote copy in, keeping whichever side saw the newer observation. these rows are
    // commutative in principle but not in practice: two devices that each recorded a dinner hold
    // different partial sums, and there's no safe way to add them without double-counting shared
    // history. newest wins loses at most the observations one device made while offline
    private suspend fun pullRemote(userId: String) {
        val remote = runCatching { remoteDataSource.getAll(userId) }
            .onFailure { Log.w(TAG, "Habit stat pull failed: ${it.message}") }
            .getOrNull() ?: return
        val local = dao.getAll(userId).associateBy { it.id }
        val incoming = remote
            .map { it.toEntity() }
            .filter { row ->
                val mine = local[row.id]
                mine == null || row.lastObservedAt > mine.lastObservedAt
            }
        if (incoming.isNotEmpty()) dao.upsertAll(incoming)
    }

    private suspend fun tryRemoteUpsert(entity: ActivityTimeStatEntity) {
        runCatching { remoteDataSource.upsert(entity.toDto()) }
            .onSuccess { dao.updateSyncStatus(entity.id, SyncStatus.SYNCED.name) }
            .onFailure {
                // offline is the expected case, not an error. the row stays pending and SyncCoordinator
                // re-pushes it on the next reconnect
                Log.d(TAG, "Habit stat upsert deferred: ${it.message}")
            }
    }

    suspend fun clearLocalForUser(userId: String) {
        snapshot = emptyMap()
        observedUserId = null
        // let a future sign-in rebuild from history. leaving the flag set would strand the account
        // with no habits and no way to get them back
        prefs.edit().remove(backfillKey(userId)).apply()
        runCatching { dao.deleteAllForUser(userId) }
    }

    private fun cacheKey(habitKey: String, dayType: String) = "$habitKey|$dayType"

    private fun dayTypeOf(date: LocalDate): DayType =
        if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
            DayType.Weekend
        } else {
            DayType.Weekday
        }

    // older rows stored '...T10:00' without seconds, and LocalDateTime.parse needs them
    private fun String.padIsoSeconds(): String =
        if (length == 16) "$this:00" else this
}
