package com.muradgalayev.brainbuddy.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.data.repository.ProfileDto
import com.muradgalayev.brainbuddy.data.health.HealthConnectManager
import com.muradgalayev.brainbuddy.data.local.PomodoroTimerManager
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.local.TimerState
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.PlacesRepository
import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import com.muradgalayev.brainbuddy.data.repository.WeatherRepository
import com.muradgalayev.brainbuddy.data.repository.WeatherSnapshot
import com.muradgalayev.brainbuddy.domain.model.AdhdProfile
import com.muradgalayev.brainbuddy.domain.model.SurveyVersion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

// who the header greets and whose photo it shows. nulls mean not signed in yet
data class HomeIdentity(
    val firstName: String? = null,
    val fullName: String? = null,
    val avatarUrl: String? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val profiles: AdhdProfileRepository,
    private val authRepository: AuthRepository,
    private val healthConnectManager: HealthConnectManager,
    private val preferencesManager: PreferencesManager,
    private val placesRepository: PlacesRepository,
    private val weatherRepository: WeatherRepository,
    private val calendarRepository: CalendarRepository,
    private val todoRepository: TodoRepository,
    private val timerManager: PomodoroTimerManager,
    private val modeManager: com.muradgalayev.brainbuddy.data.local.ModeManager,
) : ViewModel() {
    // straight off the repository's shared stream, seeded from its synchronous cache so the first
    // frame is already right. this used to be a private snapshot only refresh() wrote to, and
    // refresh runs once per composition of Home. Home sits in the nav back-stack, so deleting a
    // medication on the health screens and tabbing back left the dose ring still counting it
    val profile: StateFlow<AdhdProfile?> = profiles.profile
        .stateIn(viewModelScope, SharingStarted.Eagerly, profiles.peekProfile())

    // seeded from cache so the header paints a name and photo on the first frame, refresh()
    // revalidates behind it. without the seed the greeting lands a beat after the rest of the
    // screen, and it's the one line you actually look at on open
    private val _identity = MutableStateFlow(authRepository.peekProfile().toHomeIdentity())
    val identity: StateFlow<HomeIdentity> = _identity.asStateFlow()
    val healthState = healthConnectManager.state
    val medicationDoseLogs = preferencesManager.medicationDoseLogs
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    private val _weather = MutableStateFlow<WeatherSnapshot?>(null)
    val weather: StateFlow<WeatherSnapshot?> = _weather.asStateFlow()

    // re-read on every refresh so a session left open overnight rolls onto the new day
    private val day = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val agenda: StateFlow<List<HomeAgendaItem>> = day
        .flatMapLatest { today ->
            combine(
                calendarRepository.getEventsByDate(today.toString()),
                todoRepository.getTodoItemsByDate(today.toString()),
            ) { events, todos ->
                (events.mapNotNull { it.toAgendaItem() } + todos.map { it.toAgendaItem(today) })
                    .sortedWith(compareBy(nullsLast()) { it.start })
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // tiles the user has taken off home, with opt-in extras starting here until they're added.
    // read through ModeManager rather than straight from preferences, so a mode can carry its own
    // home screen. when the active mode says nothing about tiles, this is exactly the user's choice
    val hiddenWidgets: StateFlow<Set<String>> = modeManager.effectiveHiddenHomeWidgets
        .map { it ?: HomeWidget.defaultHidden }
        .stateIn(viewModelScope, SharingStarted.Eagerly, HomeWidget.defaultHidden)

    // the whole home arrangement: what's on it, in what order, at what width. order and widths are
    // the user's own and stay theirs under a mode, which overlays only which tiles are shown.
    // rearranging Home inside Work mode and finding your normal Home rearranged would be nasty
    val layout: StateFlow<HomeLayout> = combine(
        preferencesManager.homeWidgetOrder,
        hiddenWidgets,
        preferencesManager.homeWidgetSpans,
    ) { order, hidden, spans ->
        homeLayout(storedOrder = order, hidden = hidden, storedSpans = spans)
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        homeLayout(null, HomeWidget.defaultHidden, emptyMap()),
    )

    // the one nudge towards Reduce motion. it waits for the app to have been used a few times,
    // stands down the moment motion is already reduced (by the switch or by a mode), and once it
    // has been answered either way it never comes back. asking about movement is only fair after
    // someone has sat with the movement
    val motionTipVisible: StateFlow<Boolean> = combine(
        preferencesManager.homeOpens,
        preferencesManager.motionTipDone,
        modeManager.effectiveReduceMotion,
    ) { opens, answered, reduced ->
        opens >= PreferencesManager.MOTION_TIP_AFTER_OPENS && !answered && !reduced
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun noteHomeOpened() {
        viewModelScope.launch { preferencesManager.noteHomeOpened() }
    }

    fun keepMotion() {
        viewModelScope.launch { preferencesManager.setMotionTipDone() }
    }

    // the tip's whole point. writes the same preference the switch in Settings writes, so the
    // switch is already on when they go looking for how to undo it
    fun reduceMotionFromTip() {
        viewModelScope.launch {
            preferencesManager.setReduceMotion(true)
            preferencesManager.setMotionTipDone()
        }
    }

    // returns false when a mode owns the screen, so the caller can say why rather than do nothing
    fun moveWidget(from: Int, to: Int): Boolean = editBaseLayout {
        preferencesManager.setHomeWidgetOrder(layout.value.reordered(from, to).map { it.id })
    }

    // switches one tile between full and half width
    fun setWidgetSpan(widget: HomeWidget, span: WidgetSpan): Boolean = editBaseLayout {
        val current = layout.value.spans
        preferencesManager.setHomeWidgetSpans(
            // back to its declared width means dropping the override, not storing the default, so a later
            // change to that default still reaches this user
            if (span == widget.span) {
                current - widget.id
            } else {
                current + (widget.id to span)
            }.mapValues { it.value.name },
        )
    }

    // every write to the user's own layout goes through here. a mode's Home is an overlay edited
    // from the mode editor, so writing the base layout while that overlay is on screen would
    // change a different screen than the one being looked at. checked twice: once to answer the
    // caller now, once inside the coroutine in case a scheduled mode started in between
    private fun editBaseLayout(block: suspend () -> Unit): Boolean {
        if (activeMode.value != null) return false
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            block()
        }
        return true
    }

    // the user's modes and which one is on, for the home mode tile
    val modes: StateFlow<List<com.muradgalayev.brainbuddy.domain.model.AppMode>> = modeManager.modes
    val activeMode: StateFlow<com.muradgalayev.brainbuddy.domain.model.AppMode?> = modeManager.activeMode

    val activeModeStatus: StateFlow<com.muradgalayev.brainbuddy.domain.model.ModeStatus?> =
        combine(modeManager.activeMode, modeManager.selection) { mode, selection ->
            mode?.let { com.muradgalayev.brainbuddy.domain.model.modeStatus(it, selection) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // false until DataStore answers, so someone who already dismissed it never sees it flash in
    val showModesIntro: StateFlow<Boolean> = preferencesManager.modesIntroSeen
        .map { !it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun dismissModesIntro() {
        viewModelScope.launch { preferencesManager.setModesIntroSeen() }
    }

    // picking a mode is the clearest sign the intro has done its job
    fun selectMode(id: String?) {
        modeManager.selectMode(id)
        dismissModesIntro()
    }

    val timerRunning: StateFlow<Boolean> = timerManager.state
        .map { it.timerState == TimerState.RUNNING || it.timerState == TimerState.PAUSED }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init { refresh() }

    // turns one tile on or off
    fun setWidgetVisible(widget: HomeWidget, visible: Boolean): Boolean = editBaseLayout {
        val current = hiddenWidgets.value
        preferencesManager.setHiddenHomeWidgets(
            if (visible) current - widget.id else current + widget.id
        )
    }

    // the Focus tile's one-tap blocks. same guard as startFocus: never interrupt a run
    fun startQuickFocus(minutes: Int, onStarted: () -> Unit) {
        if (timerManager.isRunningOrPaused) {
            onStarted()
            return
        }
        viewModelScope.launch {
            val effective = timerManager.refreshEffectiveSettingsNow()
            timerManager.selectSessionType(PomodoroSessionType.FOCUS)
            timerManager.setCustomDuration(minutes)
            timerManager.start(effective.autoDndOnFocusSession)
            onStarted()
        }
    }

    // puts the item into a focus block and hands back to the caller to navigate. a session already
    // running is left alone, interrupting one to start another is the opposite of the point
    fun startFocus(item: HomeAgendaItem, onStarted: () -> Unit) {
        if (timerManager.isRunningOrPaused) {
            onStarted()
            return
        }
        viewModelScope.launch {
            val effective = timerManager.refreshEffectiveSettingsNow()
            timerManager.selectSessionType(PomodoroSessionType.FOCUS)
            timerManager.setCustomDuration(focusMinutesFor(item, effective.focusMinutes))
            timerManager.start(effective.autoDndOnFocusSession)
            onStarted()
        }
    }

    // no longer than the thing itself, and never so long it's daunting to begin
    private fun focusMinutesFor(item: HomeAgendaItem, defaultFocusMinutes: Int): Int {
        val start = item.start ?: return defaultFocusMinutes
        val end = item.end ?: return defaultFocusMinutes
        val minutes = java.time.Duration.between(start, end).toMinutes().toInt()
        return minutes.coerceIn(5, defaultFocusMinutes)
    }

    fun markDone(item: HomeAgendaItem) {
        viewModelScope.launch {
            when (item.kind) {
                AgendaKind.Task -> todoRepository.setTodoItemCompletion(item.id, true)
                AgendaKind.Event -> calendarRepository.getEventById(item.id)?.let {
                    calendarRepository.updateEvent(it.copy(completed = true))
                }
            }
        }
    }

    // pushes a task back by half an hour. tasks only: an event is an agreement with the rest of
    // the world, and quietly moving one on the home screen would be a lie
    fun snooze(item: HomeAgendaItem, minutes: Long = 30) {
        if (item.kind != AgendaKind.Task) return
        viewModelScope.launch {
            val todo = todoRepository.getTodoItemById(item.id) ?: return@launch
            val current = todo.startTime.takeIf { it.isNotBlank() }
                ?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
                ?: LocalTime.now()
            val moved = current.plusMinutes(minutes)
            // past midnight it would land on a day this list isn't showing, so pin to the end of today
            // rather than teleporting the task out of view
            val pinned = if (moved.isBefore(current)) LocalTime.of(23, 45) else moved
            todoRepository.updateTodoItem(todo.copy(startTime = pinned.format(HOUR_MINUTE)))
        }
    }

    fun refresh() {
        day.value = LocalDate.now()
        viewModelScope.launch {
            // equality-guarded, and a failed fetch leaves the cached identity rather than blanking the header
            runCatching { authRepository.getProfile() }.getOrNull()?.toHomeIdentity()
                ?.let { if (it != _identity.value) _identity.value = it }
            // getProfile() caches through to the repository's stream, there's no local mirror left to assign
            val cityId = (profiles.getProfile() ?: profiles.peekProfile())?.cityId
            val cities = placesRepository.peekCities() ?: placesRepository.listCities().getOrDefault(emptyList())
            val city = cities.firstOrNull { it.id == cityId }?.name
            if (city != null) {
                // paint whatever we already have, then let the repository decide whether its cache has aged
                // out: a tab switch shouldn't clear the tile or hit the network. never assign null over a
                // snapshot we're already showing
                weatherRepository.peek(city)?.let { if (it != _weather.value) _weather.value = it }
                weatherRepository.current(city)
                    .onSuccess { if (it != _weather.value) _weather.value = it }
            }
            healthConnectManager.refresh()
        }
    }

    private companion object {
        val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

private fun ProfileDto?.toHomeIdentity(): HomeIdentity {
    val dto = this ?: return HomeIdentity()
    return HomeIdentity(
        firstName = firstNameFrom(dto.displayName, dto.username, dto.email),
        fullName = dto.displayName?.takeIf { it.isNotBlank() }
            ?: dto.username?.takeIf { it.isNotBlank() },
        avatarUrl = dto.avatarUrl?.takeIf { it.isNotBlank() },
    )
}

fun AdhdProfile?.personalizationProgress(): Float {
    val profile = this ?: return 0f
    val answered = listOf(
        profile.surveyVersion != SurveyVersion.None,
        profile.cityId != null, profile.ageRange.isNotBlank(),
        profile.diagnosisStatus != null, profile.primarySymptoms.isNotEmpty(),
        profile.topGoals.isNotEmpty(),
        profile.productiveTime != null, profile.focusDurationMinutes != null,
        profile.sleepBedtime.isNotBlank() && profile.sleepWakeTime.isNotBlank(),
        profile.medicationStatus != null, profile.copingStrategies.isNotEmpty(),
        profile.painPoint.isNotBlank(), profile.aiTonePreference != null,
    ).count { it }
    return answered / 13f
}

// Deep Dive's optional questions may be blank, these are the fields required on submit
fun AdhdProfile?.isPersonalizationComplete(): Boolean {
    val profile = this ?: return false
    return profile.surveyVersion == SurveyVersion.Deep &&
        profile.surveyCompleted &&
        profile.ageRange.isNotBlank() &&
        profile.diagnosisStatus != null &&
        profile.primarySymptoms.isNotEmpty() &&
        profile.topGoals.isNotEmpty() &&
        profile.productiveTime != null &&
        profile.focusDurationMinutes != null &&
        profile.medicationStatus != null &&
        profile.aiTonePreference != null
}
