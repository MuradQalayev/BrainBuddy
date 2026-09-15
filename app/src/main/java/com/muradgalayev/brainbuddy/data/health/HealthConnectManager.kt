package com.muradgalayev.brainbuddy.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import android.util.Log
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.RestingHeartRateRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.R

enum class HealthConnectAvailability { AVAILABLE, INSTALL_OR_UPDATE, NOT_SUPPORTED }

data class DailySleepSummary(val date: LocalDate, val hours: Double)

// one readable health data type, and whether recording it needs a wearable
data class HealthPermissionInfo(
    val permission: String,
    @androidx.annotation.StringRes val labelRes: Int,
    val needsDevice: Boolean,
)

data class HealthConnectUiState(
    val availability: HealthConnectAvailability = HealthConnectAvailability.NOT_SUPPORTED,
    val connected: Boolean = false,
    // a read is in flight. routine, happens on every resume
    val loading: Boolean = false,
    // the user asked to disconnect and the revoke is in flight. separate from loading: the two were
    // one flag, so an ordinary refresh turned Disconnect into a disabled 'Disconnecting' spinner
    // every time the screen resumed
    val disconnecting: Boolean = false,
    val grantedPermissions: Set<String> = emptySet(),
    val todaySteps: Long? = null,
    val stepsLast7Days: Long? = null,
    val latestHeartRateBpm: Long? = null,
    // mean of every heart-rate sample recorded today. a single latest reading is noise when a watch
    // measures several times a day, one spike after climbing stairs is not the day's heart rate
    val todayAverageHeartRateBpm: Long? = null,
    // how many samples that average is built from, so the UI can be honest about it
    val todayHeartRateSamples: Int = 0,
    // mean across the last 7 days, steadier still, and what trends should read from
    val weekAverageHeartRateBpm: Long? = null,
    val restingHeartRateBpm: Long? = null,
    val lastSleepHours: Double? = null,
    val sleepLast7Days: List<DailySleepSummary> = emptyList(),
    val exerciseMinutesThisWeek: Long? = null,
    val caloriesBurnedToday: Long? = null,
    val allPermissionsGranted: Boolean = false,
    val error: String? = null,
)

private const val TAG = "HealthConnect"

@Singleton
class HealthConnectManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val connectionStore: HealthConnectionStore,
    private val authRepository: AuthRepository,
) {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(RestingHeartRateRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
    )

    // human labels per permission, so the UI can show which types were granted rather than one
    // all-or-nothing flag. Health Connect lets the user tick types individually and stops showing
    // the consent dialog after a couple of dismissals, so a partial grant is both easy to end up
    // with and impossible to diagnose from a binary connected state.
    // needsDevice marks types a phone can't record alone. heart rate essentially needs a watch or a
    // chest strap: the permission can be granted and still yield nothing, which looks like a failure
    val permissionLabels: List<HealthPermissionInfo> = listOf(
        HealthPermissionInfo(
            HealthPermission.getReadPermission(StepsRecord::class), R.string.health_steps, false),
        HealthPermissionInfo(
            HealthPermission.getReadPermission(HeartRateRecord::class), R.string.health_heart_rate, true),
        HealthPermissionInfo(
            HealthPermission.getReadPermission(RestingHeartRateRecord::class), R.string.wellness_resting_hr, true),
        HealthPermissionInfo(
            HealthPermission.getReadPermission(SleepSessionRecord::class), R.string.health_sleep, true),
        HealthPermissionInfo(
            HealthPermission.getReadPermission(ExerciseSessionRecord::class), R.string.health_exercise, false),
        HealthPermissionInfo(
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class), R.string.health_calories, false),
    )

    // reads a metric, logging any failure instead of discarding it. every read here used to be
    // runCatching{}.getOrNull(), which collapsed three different outcomes (permission refused,
    // Health Connect error, genuinely no data) into an identical null. that is why 'steps work,
    // heart rate doesn't' was impossible to diagnose from inside the app
    private inline fun <T> readOrLog(label: String, block: () -> T): T? =
        runCatching(block)
            .onFailure { Log.w(TAG, "Health read failed for $label: ${it.message}", it) }
            .getOrNull()

    private val _state = MutableStateFlow(HealthConnectUiState())
    val state: StateFlow<HealthConnectUiState> = _state.asStateFlow()
    private val operationMutex = Mutex()

    suspend fun refresh() = operationMutex.withLock {
        refreshLocked()
    }

    private suspend fun refreshLocked() {
        val sdkStatus = HealthConnectClient.getSdkStatus(context)
        val availability = when (sdkStatus) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.INSTALL_OR_UPDATE
            else -> HealthConnectAvailability.NOT_SUPPORTED
        }
        if (availability != HealthConnectAvailability.AVAILABLE) {
            _state.value = HealthConnectUiState(availability = availability)
            return
        }

        val userId = authRepository.getCurrentOrCachedUserId()
        // Android grants Health Connect access to the installed app, not to a Myndora login. never
        // read device health records until this account has independently opted in
        if (userId == null || !connectionStore.isEnabled(userId)) {
            _state.value = HealthConnectUiState(availability = availability)
            return
        }

        val client = HealthConnectClient.getOrCreate(context)
        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrElse {
                _state.value = HealthConnectUiState(
                    availability = availability,
                    error = context.getString(R.string.health_check_failed),
                )
                return
            }
        val relevantGranted = granted.intersect(permissions)
        _state.value = _state.value.copy(
            availability = availability,
            connected = relevantGranted.isNotEmpty(),
            loading = relevantGranted.isNotEmpty(),
            disconnecting = false,
            grantedPermissions = relevantGranted,
            allPermissionsGranted = relevantGranted.containsAll(permissions),
            error = null,
        )
        if (relevantGranted.isEmpty()) {
            connectionStore.markDisconnected(userId)
            runCatching {
                authRepository.setHealthConnection(false, null, null, emptyList())
            }
            return
        }

        val now = Instant.now()
        val startOfToday = now.truncatedTo(ChronoUnit.DAYS)
        val dayAgo = now.minus(1, ChronoUnit.DAYS)
        val weekAgo = now.minus(7, ChronoUnit.DAYS)

        val stepsPermission = HealthPermission.getReadPermission(StepsRecord::class)
        val heartRatePermission = HealthPermission.getReadPermission(HeartRateRecord::class)
        val restingHeartRatePermission = HealthPermission.getReadPermission(RestingHeartRateRecord::class)
        val sleepPermission = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val exercisePermission = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        val caloriesPermission = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)

        val steps = if (stepsPermission in relevantGranted) readOrLog("steps today") {
            client.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    TimeRangeFilter.between(startOfToday, now),
                ),
            ).records.sumOf { it.count }
        } else null

        val stepsLast7Days = if (stepsPermission in relevantGranted) readOrLog("steps 7d") {
            client.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    TimeRangeFilter.between(weekAgo, now),
                ),
            ).records.sumOf { it.count }
        } else null

        // read the week once and derive every heart-rate figure from it: a single reading swings far
        // too much between measurements to be worth showing on its own, so today's mean leads
        val heartSamples = (if (heartRatePermission in relevantGranted) readOrLog("heart rate") {
            client.readRecords(
                ReadRecordsRequest(
                    HeartRateRecord::class,
                    // 7 days, not 24h. a watch that hasn't synced today would otherwise report no heart rate while
                    // steps, read from the phone itself, keep working, which is exactly the asymmetry reported
                    TimeRangeFilter.between(weekAgo, now),
                ),
            ).records.flatMap { it.samples }
        } else null).orEmpty()

        val heartZone = ZoneId.systemDefault()
        val heartToday = heartSamples.filter {
            it.time.atZone(heartZone).toLocalDate() == LocalDate.now(heartZone)
        }
        val latestHeartRate = heartSamples.maxByOrNull { it.time }?.beatsPerMinute
        val todayAverageHeartRate = heartToday
            .takeIf { it.isNotEmpty() }
            ?.map { it.beatsPerMinute }?.average()?.toLong()
        val weekAverageHeartRate = heartSamples
            .takeIf { it.isNotEmpty() }
            ?.map { it.beatsPerMinute }?.average()?.toLong()

        val restingHeartRate = if (restingHeartRatePermission in relevantGranted) readOrLog("resting heart rate") {
            client.readRecords(
                ReadRecordsRequest(
                    RestingHeartRateRecord::class,
                    TimeRangeFilter.between(weekAgo, now),
                ),
            ).records.maxByOrNull { it.time }?.beatsPerMinute
        } else null

        val sleepRecords = if (sleepPermission in relevantGranted) readOrLog("sleep") {
            client.readRecords(
                ReadRecordsRequest(
                    SleepSessionRecord::class,
                    TimeRangeFilter.between(weekAgo, now),
                ),
            ).records
        }.orEmpty() else emptyList()
        val sleepHours = sleepRecords.maxByOrNull { it.endTime }?.let {
            java.time.Duration.between(it.startTime, it.endTime).toMinutes() / 60.0
        }
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val sleepLast7Days = (6L downTo 0L).map { daysAgo ->
            val date = today.minusDays(daysAgo)
            val minutes = sleepRecords
                .filter { it.endTime.atZone(zone).toLocalDate() == date }
                .sumOf { java.time.Duration.between(it.startTime, it.endTime).toMinutes() }
            DailySleepSummary(date, minutes / 60.0)
        }

        val exerciseMinutes = if (exercisePermission in relevantGranted) readOrLog("exercise") {
            client.readRecords(
                ReadRecordsRequest(
                    ExerciseSessionRecord::class,
                    TimeRangeFilter.between(weekAgo, now),
                ),
            ).records.sumOf {
                java.time.Duration.between(it.startTime, it.endTime).toMinutes()
            }
        } else null

        val calories = if (caloriesPermission in relevantGranted) readOrLog("calories") {
            client.readRecords(
                ReadRecordsRequest(
                    TotalCaloriesBurnedRecord::class,
                    TimeRangeFilter.between(startOfToday, now),
                ),
            ).records.sumOf { it.energy.inKilocalories }.toLong()
        } else null

        _state.value = _state.value.copy(
            loading = false,
            todaySteps = steps,
            stepsLast7Days = stepsLast7Days,
            latestHeartRateBpm = latestHeartRate,
            todayAverageHeartRateBpm = todayAverageHeartRate,
            todayHeartRateSamples = heartToday.size,
            weekAverageHeartRateBpm = weekAverageHeartRate,
            restingHeartRateBpm = restingHeartRate,
            lastSleepHours = sleepHours,
            sleepLast7Days = sleepLast7Days,
            exerciseMinutesThisWeek = exerciseMinutes,
            caloriesBurnedToday = calories,
        )
        val connectedAt = connectionStore.markConnected(userId)
        runCatching {
            authRepository.setHealthConnection(
                linked = true,
                connectedAt = connectedAt,
                lastSyncedAt = Instant.now().toString(),
                dataTypes = grantedDataTypeNames(relevantGranted),
            )
        }
    }

    // seven days of per-day figures, read live. each record type is fetched once for the whole
    // window and then bucketed by local date, rather than 7 x 6 separate range queries. days with
    // no data keep null fields. returns an empty report when Health Connect isn't connected, so a
    // caller can still produce a document containing the profile alone
    suspend fun weeklyReport(): WeeklyHealthReport = operationMutex.withLock {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val from = today.minusDays(6)
        val empty = WeeklyHealthReport(from = from, to = today)

        if (!_state.value.connected) return@withLock empty
        val client = runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull()
            ?: return@withLock empty

        val granted = runCatching { client.permissionController.getGrantedPermissions() }
            .getOrDefault(emptySet())
            .intersect(permissions)

        val windowStart = from.atStartOfDay(zone).toInstant()
        val now = Instant.now()
        val filter = TimeRangeFilter.between(windowStart, now)

        // steps are bucketed by the day the interval starts in. a record straddling midnight is rare
        // and lands on the earlier day, which is what a reader expects from a daily table
        val stepsByDay = if (HealthPermission.getReadPermission(StepsRecord::class) in granted) {
            readOrLog("weekly steps") {
                client.readRecords(ReadRecordsRequest(StepsRecord::class, filter)).records
            }.orEmpty().groupBy { it.startTime.atZone(zone).toLocalDate() }
                .mapValues { (_, recs) -> recs.sumOf { it.count } }
        } else emptyMap()

        val heartByDay = if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) {
            readOrLog("weekly heart rate") {
                client.readRecords(ReadRecordsRequest(HeartRateRecord::class, filter)).records
            }.orEmpty().flatMap { it.samples }
                .groupBy { it.time.atZone(zone).toLocalDate() }
                .mapValues { (_, samples) ->
                    samples.map { it.beatsPerMinute }.average().toLong()
                }
        } else emptyMap()

        val restingByDay =
            if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in granted) {
                readOrLog("weekly resting heart rate") {
                    client.readRecords(ReadRecordsRequest(RestingHeartRateRecord::class, filter)).records
                }.orEmpty().groupBy { it.time.atZone(zone).toLocalDate() }
                    .mapValues { (_, recs) -> recs.map { it.beatsPerMinute }.average().toLong() }
            } else emptyMap()

        val caloriesByDay =
            if (HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in granted) {
                readOrLog("weekly calories") {
                    client.readRecords(ReadRecordsRequest(TotalCaloriesBurnedRecord::class, filter)).records
                }.orEmpty().groupBy { it.startTime.atZone(zone).toLocalDate() }
                    .mapValues { (_, recs) -> recs.sumOf { it.energy.inKilocalories }.toLong() }
            } else emptyMap()

        // sleep is attributed to the day it ends on: a night from 23:40 to 07:10 belongs to the
        // morning you woke up, which is how people read sleep logs
        val sleepByDay = if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) {
            readOrLog("weekly sleep") {
                client.readRecords(ReadRecordsRequest(SleepSessionRecord::class, filter)).records
            }.orEmpty().groupBy { it.endTime.atZone(zone).toLocalDate() }
                .mapValues { (_, recs) ->
                    recs.sumOf {
                        java.time.Duration.between(it.startTime, it.endTime).toMinutes()
                    } / 60.0
                }
        } else emptyMap()

        val exerciseByDay =
            if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) {
                readOrLog("weekly exercise") {
                    client.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, filter)).records
                }.orEmpty().groupBy { it.startTime.atZone(zone).toLocalDate() }
                    .mapValues { (_, recs) ->
                        recs.sumOf {
                            java.time.Duration.between(it.startTime, it.endTime).toMinutes()
                        }
                    }
            } else emptyMap()

        WeeklyHealthReport(
            from = from,
            to = today,
            days = (0L..6L).map { offset ->
                val date = from.plusDays(offset)
                DailyHealthEntry(
                    date = date,
                    steps = stepsByDay[date],
                    averageHeartRate = heartByDay[date],
                    restingHeartRate = restingByDay[date],
                    caloriesKcal = caloriesByDay[date],
                    sleepHours = sleepByDay[date],
                    exerciseMinutes = exerciseByDay[date],
                )
            },
        )
    }

    suspend fun disconnect() = operationMutex.withLock {
        disconnectLocked()
    }

    private suspend fun disconnectLocked() {
        val availability = _state.value.availability
        val userId = authRepository.getCurrentOrCachedUserId()
        // stop app-side syncing immediately, before the provider IPC completes. this stays
        // authoritative because Health Connect may expose stale grants
        if (userId != null) connectionStore.markDisconnected(userId)
        // hold connected until the revoke returns, so the spinner has somewhere to live. the whole
        // connected block used to vanish on the same frame the 'Disconnecting' label appeared, which
        // made that label unreachable. markDisconnected above has already stopped syncing, so nothing
        // reads health data during this window
        _state.value = _state.value.copy(disconnecting = true, error = null)
        if (HealthConnectClient.getSdkStatus(context) != HealthConnectClient.SDK_AVAILABLE) {
            _state.value = HealthConnectUiState(availability = availability)
            return
        }
        val revokeResult = runCatching {
            HealthConnectClient.getOrCreate(context).permissionController.revokeAllPermissions()
        }
        if (revokeResult.isSuccess) {
            // clear local values immediately. a later lifecycle refresh confirms the provider state
            // without briefly restoring stale permission information
            _state.value = HealthConnectUiState(availability = availability)
            runCatching {
                authRepository.setHealthConnection(
                    linked = false,
                    connectedAt = null,
                    lastSyncedAt = null,
                    dataTypes = emptyList(),
                )
            }
        } else {
            _state.value = _state.value.copy(
                disconnecting = false,
                error = context.getString(R.string.health_disconnect_failed),
            )
        }
    }

    fun prepareToConnect() {
        val userId = authRepository.getCurrentOrCachedUserId() ?: return
        connectionStore.prepareToConnect(userId)
    }

    // clears in-memory values during logout without changing that account's saved consent
    fun clearForAccountSwitch() {
        _state.value = HealthConnectUiState(availability = _state.value.availability)
    }

    private fun grantedDataTypeNames(granted: Set<String>): List<String> = buildList {
        if (HealthPermission.getReadPermission(StepsRecord::class) in granted) add("steps")
        if (HealthPermission.getReadPermission(HeartRateRecord::class) in granted) add("heart_rate")
        if (HealthPermission.getReadPermission(RestingHeartRateRecord::class) in granted) add("resting_heart_rate")
        if (HealthPermission.getReadPermission(SleepSessionRecord::class) in granted) add("sleep")
        if (HealthPermission.getReadPermission(ExerciseSessionRecord::class) in granted) add("exercise")
        if (HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class) in granted) add("calories")
    }
}
