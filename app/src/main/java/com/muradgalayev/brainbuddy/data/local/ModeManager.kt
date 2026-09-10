package com.muradgalayev.brainbuddy.data.local

import android.util.Log
import com.muradgalayev.brainbuddy.data.repository.ModeRepository
import com.muradgalayev.brainbuddy.data.sync.CalendarSyncFrequency
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType
import com.muradgalayev.brainbuddy.domain.model.AppMode
import com.muradgalayev.brainbuddy.domain.model.ModeSelection
import com.muradgalayev.brainbuddy.domain.model.ModeNotificationKind
import com.muradgalayev.brainbuddy.domain.model.nextModeScheduleBoundary
import com.muradgalayev.brainbuddy.domain.model.resolveActiveMode
import com.muradgalayev.brainbuddy.domain.model.withAvailableModes
import com.muradgalayev.brainbuddy.domain.scheduling.WorkingHours
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

// pomodoro values after laying the active mode over the user's normal preference
data class EffectivePomodoroSettings(
    val focusMinutes: Int = PomodoroTimerManager.DEFAULT_FOCUS_MINUTES,
    val breakMinutes: Int = PomodoroTimerManager.DEFAULT_BREAK_MINUTES,
    val autoDndOnFocusSession: Boolean = false,
) {
    fun minutesFor(type: PomodoroSessionType): Int = when (type) {
        PomodoroSessionType.FOCUS -> focusMinutes
        PomodoroSessionType.BREAK -> breakMinutes
    }
}

internal fun resolveEffectivePomodoroSettings(
    mode: AppMode?,
    baseAutoDnd: Boolean,
): EffectivePomodoroSettings = EffectivePomodoroSettings(
    focusMinutes = mode?.overrides?.pomodoroFocusMinutes
        ?.coerceIn(MIN_MODE_FOCUS_MINUTES, MAX_MODE_FOCUS_MINUTES)
        ?: PomodoroTimerManager.DEFAULT_FOCUS_MINUTES,
    breakMinutes = mode?.overrides?.pomodoroBreakMinutes
        ?.coerceIn(MIN_MODE_BREAK_MINUTES, MAX_MODE_BREAK_MINUTES)
        ?: PomodoroTimerManager.DEFAULT_BREAK_MINUTES,
    autoDndOnFocusSession = mode?.overrides?.autoDndOnFocusSession ?: baseAutoDnd,
)

private const val MIN_MODE_FOCUS_MINUTES = 5
private const val MAX_MODE_FOCUS_MINUTES = 90
private const val MIN_MODE_BREAK_MINUTES = 3
private const val MAX_MODE_BREAK_MINUTES = 30

// resolves which mode is in force and lays its overrides over the user's own settings.
// overlay, never a write: every effective* flow below is mode override ?: base preference.
// nothing here writes into PreferencesManager, so leaving a mode restores everything with no
// restore logic to get wrong, which is exactly what the old focus-mode snapshot got wrong.
// the exceptions are the ringer and DND, real device state that has to be put back. both go
// through controllers that persist 'we changed this', so a killed process can't strand a
// silent phone. see RingerController and FocusModeManager
@Singleton
class ModeManager @Inject constructor(
    private val modeRepository: ModeRepository,
    private val preferencesManager: PreferencesManager,
    private val ringerController: RingerController,
    private val focusModeManager: FocusModeManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // bumped whenever the answer to 'what time is it' might have changed the active mode: app
    // start, returning to the foreground, and the boundary alarm firing. schedules are evaluated
    // against this rather than a ticking clock, so nothing recomputes sixty times a minute
    private val clock = MutableStateFlow(LocalDateTime.now())
    private val schedulerRefresh = MutableStateFlow(0L)

    val modes: StateFlow<List<AppMode>> = modeRepository.observeModes()
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val storedSelection: StateFlow<ModeSelection> = preferencesManager.modeSelection
        .stateIn(scope, SharingStarted.Eagerly, ModeSelection.Automatic)

    // so pickers can tell an explicit No mode from idle automatic schedules. a manual id deleted
    // during sync reads as Automatic, leaving it Manual would make the resolver fall back to
    // schedules while the UI and the boundary timer claimed otherwise
    val selection: StateFlow<ModeSelection> = combine(modes, storedSelection) { available, saved ->
        saved.withAvailableModes(available)
    }.stateIn(scope, SharingStarted.Eagerly, ModeSelection.Automatic)

    val schedulesEnabled: StateFlow<Boolean> = selection
        .map { it == ModeSelection.Automatic }
        .stateIn(scope, SharingStarted.Eagerly, true)

    val activeMode: StateFlow<AppMode?> = combine(
        modes,
        selection,
        clock,
    ) { available, currentSelection, now ->
        resolveActiveMode(available, currentSelection, now.dayOfWeek, now.toLocalTime())
    }.distinctUntilChanged().stateIn(scope, SharingStarted.Eagerly, null)

    init {
        // the whole mode, not just its id: editing the active mode's ringer or DND override has to
        // take effect immediately even though it's still the same mode
        scope.launch {
            // wait for Room to answer once before touching device state. activeMode is a combine, which
            // fires as soon as every source has a value, and at construction that's an empty mode list
            // resolving to a null mode indistinguishable from a real No mode. acting on it restores the
            // ringer a fraction of a second before the real mode arrives to set it again, so a silenced
            // phone audibly un-silences itself on every cold start, and stays that way in a short-lived
            // background process that dies before the second emission.
            // collecting a StateFlow after the wait still delivers the current value, so nothing is missed
            runCatching { modeRepository.observeModes().first() }
                .onFailure { Log.w(TAG, "Mode load failed; applying device state anyway") }
            activeMode.collect { applyDeviceState(it) }
        }
        scope.launch {
            combine(modes, selection, schedulerRefresh) { available, currentSelection, _ ->
                available to currentSelection
            }.collectLatest { (available, currentSelection) ->
                if (currentSelection != ModeSelection.Automatic) return@collectLatest
                while (true) {
                    val now = ZonedDateTime.now()
                    clock.value = now.toLocalDateTime()
                    val boundary = nextModeScheduleBoundary(available, now)
                        ?: return@collectLatest
                    // wake just after the minute boundary so LocalTime's minute-granular resolver can't observe
                    // the outgoing minute through timer jitter. the subtraction has to happen on the instant
                    // timeline, local-time subtraction is an hour wrong when daylight saving starts or ends
                    val waitMillis = Duration.between(
                        now.toInstant(),
                        boundary.toInstant(),
                    ).toMillis()
                        .coerceAtLeast(1L) + BOUNDARY_SETTLE_MILLIS
                    delay(waitMillis)
                }
            }
        }
    }

    // call on app start, on foreground, and from the boundary alarm
    fun refresh() {
        clock.value = LocalDateTime.now()
        schedulerRefresh.update { it + 1L }
    }

    // reasserts external device state after startup reconciliation clears stale leases. reads
    // the mode from Room and DataStore rather than from activeMode, which starts null and only
    // becomes real once both have emitted: at the instant Application.onCreate calls this it
    // still says no mode, and applyDeviceState(null) means restore the ringer. so the call meant
    // to reassert a silenced phone was itself the thing un-silencing it
    fun reapplyDeviceState() {
        scope.launch { applyDeviceState(activeModeNow()) }
    }

    // null is an explicit No mode choice, and suspends schedules
    fun selectMode(id: String?) {
        scope.launch {
            if (id == null) {
                preferencesManager.setModeSelection(ModeSelection.NoMode)
            } else {
                // validate against the source of truth, not the possibly-not-loaded StateFlow. a typo or a
                // stale UI event must never become durable state
                if (id.isBlank() || id == ModeSelection.NO_MODE_STORAGE_VALUE ||
                    modeRepository.getMode(id) == null
                ) {
                    Log.w(TAG, "Ignoring unknown mode selection: $id")
                    return@launch
                }
                preferencesManager.setModeSelection(ModeSelection.Manual(id))
            }
            refresh()
        }
    }

    // deliberately hands control back to enabled schedules
    fun useAutomaticModes() {
        scope.launch {
            preferencesManager.setModeSelection(ModeSelection.Automatic)
            refresh()
        }
    }

    // effective settings: the mode's override if it states one, otherwise the user's own

    val effectiveSimplifiedWorkspace: Flow<Boolean> =
        combine(activeMode, preferencesManager.simplifiedWorkspace) { mode, base ->
            mode?.overrides?.simplifiedWorkspace ?: base
        }.distinctUntilChanged()

    val effectiveReduceMotion: Flow<Boolean> =
        combine(activeMode, preferencesManager.reduceMotion) { mode, base ->
            mode?.overrides?.reduceMotion ?: base
        }.distinctUntilChanged()

    val effectiveEnabledNavItems: Flow<Set<String>> =
        combine(activeMode, preferencesManager.enabledNavItems) { mode, base ->
            mode?.overrides?.enabledNavItems ?: base
        }.distinctUntilChanged()

    // which home tiles are hidden right now, a per-mode home screen. stays nullable through this
    // layer: null means nobody has an opinion, which the home screen turns into its own default
    // set. collapsing it to an empty set would read as 'the user chose to hide nothing'
    val effectiveHiddenHomeWidgets: Flow<Set<String>?> =
        combine(activeMode, preferencesManager.hiddenHomeWidgets) { mode, base ->
            mode?.overrides?.hiddenHomeWidgets ?: base
        }.distinctUntilChanged()

    // the accent a mode paints the app with, so the current mode is visible without reading
    val effectiveAccentKey: Flow<String?> = activeMode.map { it?.accent }.distinctUntilChanged()

    // timer defaults and focus-session silence currently in force. pomodoro lengths have no
    // separate persisted base setting today, so an absent override falls back to the product
    // defaults. the DND switch does have a user preference, so it stays an overlay
    val effectivePomodoroSettings: Flow<EffectivePomodoroSettings> =
        combine(activeMode, preferencesManager.focusModeEnabled) { mode, baseAutoDnd ->
            resolveEffectivePomodoroSettings(mode, baseAutoDnd)
        }.distinctUntilChanged()

    // authoritative one-shot lookup for timer launches that may cold-start the app
    suspend fun effectivePomodoroSettingsNow(): EffectivePomodoroSettings =
        resolveEffectivePomodoroSettings(
            mode = activeModeNow(),
            baseAutoDnd = preferencesManager.focusModeEnabled.first(),
        )

    // now simply the user's setting, modes stopped overriding it. kept as a property rather than
    // inlined at the call sites so the cadence has one authority, which is what made removing
    // the override a one-line change
    val effectiveCalendarSyncFrequency: Flow<CalendarSyncFrequency> =
        preferencesManager.calendarSyncFrequency.distinctUntilChanged()

    // optional bounds the active mode puts on automatic event suggestions
    val effectiveWorkingHours: Flow<WorkingHours?> = activeMode
        .map { mode ->
            WorkingHours.fromNullable(
                startMinute = mode?.overrides?.workingHoursStartMinute,
                endMinute = mode?.overrides?.workingHoursEndMinute,
            )
        }
        .distinctUntilChanged()

    // one-shot lookup for cold-start paths like connecting a Google account, where the eager
    // flows may not have heard from Room or DataStore yet
    suspend fun effectiveCalendarSyncFrequencyNow(): CalendarSyncFrequency =
        preferencesManager.getCalendarSyncFrequency()

    // a mode that lists nothing has no opinion and everything is allowed: 'I didn't configure
    // notifications' must not read as 'silence all of them'
    fun isNotificationAllowed(kind: ModeNotificationKind): Boolean {
        val allowed = activeMode.value?.overrides?.allowedNotifications ?: return true
        return kind in allowed
    }

    // resolves notification policy from durable state at the instant a notification fires. a
    // receiver can be the component that cold-starts the process, and in that window the eager
    // StateFlows still hold their initial values, so reading activeMode would allow through a
    // notification the current mode silences. Room and DataStore direct makes this authoritative
    suspend fun isNotificationAllowedNow(kind: ModeNotificationKind): Boolean {
        val currentMode = activeModeNow()
        val allowed = currentMode?.overrides?.allowedNotifications ?: return true
        return kind in allowed
    }

    suspend fun activeModeNow(): AppMode? {
        val available = modeRepository.getModes()
        val currentSelection = preferencesManager.modeSelection.first()
            .withAvailableModes(available)
        val now = LocalDateTime.now()
        clock.value = now
        return resolveActiveMode(
            modes = available,
            selection = currentSelection,
            day = now.dayOfWeek,
            time = now.toLocalTime(),
        )
    }

    // clears device-local mode state before the departing account is signed out
    suspend fun clearForSignOut() {
        preferencesManager.setModeSelection(ModeSelection.Automatic)
        // don't wait for the flows to catch up: external device state belongs to the account that
        // is leaving and has to be released here, synchronously
        applyDeviceState(null)
        refresh()
    }

    // seeds Work and Weekend for a new account, then evaluates
    fun bootstrap() {
        scope.launch {
            runCatching { modeRepository.seedBuiltInsIfEmpty() }
                .onFailure { Log.w(TAG, "Mode seeding failed: ${it.message}") }
            refresh()
        }
    }

    // restores before applying, so switching between two modes that both touch the ringer
    // doesn't leave the second one's 'previous' pointing at the first one's imposition
    private fun applyDeviceState(mode: AppMode?) {
        val overrides = mode?.overrides

        when (overrides?.ringer) {
            null -> ringerController.restore()
            else -> {
                ringerController.restore()
                ringerController.apply(overrides.ringer)
            }
        }

        when (overrides?.doNotDisturb) {
            // starred contacts and repeat callers always get through. this was a pair of per-mode
            // toggles and is fixed policy now: the only answer worth having is that an emergency still
            // reaches you, and that isn't a decision to put in front of someone setting up a focus mode
            true -> focusModeManager.enableDnd(
                owner = DndOwner.MODE,
                allowCallsFromStarred = true,
                allowRepeatCallers = true,
            )
            // release only the mode's lease, a Pomodoro lease or the user's own DND stays untouched
            false, null -> focusModeManager.disableDnd(DndOwner.MODE)
        }
    }

    private companion object {
        const val TAG = "ModeManager"
        const val BOUNDARY_SETTLE_MILLIS = 50L
    }
}
