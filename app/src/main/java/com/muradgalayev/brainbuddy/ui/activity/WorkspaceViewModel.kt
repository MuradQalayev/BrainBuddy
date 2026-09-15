package com.muradgalayev.brainbuddy.ui.activity

import com.muradgalayev.brainbuddy.ui.pomodoro.formatFocusMinutes
import com.muradgalayev.brainbuddy.ui.utils.localizedDateFormatter
import com.muradgalayev.brainbuddy.ui.utils.uiText
import com.muradgalayev.brainbuddy.ui.utils.asUiText
import com.muradgalayev.brainbuddy.ui.utils.UiText
import com.muradgalayev.brainbuddy.R
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.local.PomodoroTimerManager
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.local.TimerState
import com.muradgalayev.brainbuddy.data.local.entity.PomodoroSessionType
import com.muradgalayev.brainbuddy.data.repository.PomodoroRepository
import com.muradgalayev.brainbuddy.data.repository.PlacesRepository
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.data.network.NetworkObserver
import com.muradgalayev.brainbuddy.data.health.HealthConnectManager
import com.muradgalayev.brainbuddy.domain.model.Medication
import com.muradgalayev.brainbuddy.domain.model.MedicationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class ActivityGoals(
    val steps: Int = 8_000,
    val exerciseMinutes: Int = 30,
    val energyKcal: Int = 500,
)

@HiltViewModel
class WorkspaceViewModel @Inject constructor(
    private val todoRepository: TodoRepository,
    private val calendarRepository: CalendarRepository,
    private val pomodoroTimerManager: PomodoroTimerManager,
    private val pomodoroRepository: PomodoroRepository,
    private val preferencesManager: PreferencesManager,
    private val healthConnectManager: HealthConnectManager,
    private val adhdProfileRepository: AdhdProfileRepository,
    private val medicationLogRepository: com.muradgalayev.brainbuddy.data.repository.MedicationLogRepository,
    private val medicationTodoSyncer: com.muradgalayev.brainbuddy.data.repository.MedicationTodoSyncer,
    private val modeManager: com.muradgalayev.brainbuddy.data.local.ModeManager,
    private val authRepository: AuthRepository,
    private val placesRepository: PlacesRepository,
    togetherRepository: com.muradgalayev.brainbuddy.data.repository.TogetherRepository,
    focusInviteWatcher: com.muradgalayev.brainbuddy.data.repository.FocusInviteWatcher,
    networkObserver: NetworkObserver,
    private val planRepository: com.muradgalayev.brainbuddy.data.repository.PlanRepository,
) : ViewModel(){
    // the Workspace assistant handle is a Plus feature
    val plan = planRepository.plan

    // drives the Together card: avatar stack plus a 'needs you' badge
    val togetherConnections = togetherRepository.connections
    val togetherIncomingRequests = togetherRepository.incomingRequests

    // the same 'needs you' idea on the Pomodoro tile. passed straight through from the watcher
    // rather than copied into WorkspaceUiState: it changes on a poll this screen has nothing to do
    // with, and a copy is one more thing that can sit stale behind the truth
    val focusInviteCount = focusInviteWatcher.pendingInviteCount

    // where medication doses appear
    val medicationInTodo = preferencesManager.medicationInTodo
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val medicationInCalendar = preferencesManager.medicationInCalendar
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // writes the preference and rebuilds both destinations immediately, so the doses appear or
    // disappear on the tap rather than at some later sync
    fun setMedicationDestination(todo: Boolean?, calendar: Boolean?) {
        viewModelScope.launch {
            todo?.let { preferencesManager.setMedicationInTodo(it) }
            calendar?.let { preferencesManager.setMedicationInCalendar(it) }
            runCatching { adhdProfileRepository.refreshMedicationTodos() }
        }
    }

    // seed the simplified flag synchronously so the first frame is already right, otherwise the
    // full content flashes for a frame before the async read hides it
    private val _uiState = MutableStateFlow(
        WorkspaceUiState(isSimplified = preferencesManager.peekSimplifiedWorkspace())
    )
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()
    private val _voiceHandlePosition = MutableStateFlow<Float?>(null)
    val voiceHandlePosition: StateFlow<Float?> = _voiceHandlePosition.asStateFlow()
    val healthState = healthConnectManager.state
    // straight off the repository's shared profile stream, so a medication edited anywhere (the
    // survey, the assistant, another device syncing) shows up here at once. this used to be a
    // private snapshot refreshed only in init, so the Workspace kept showing the old list until
    // its ViewModel happened to be rebuilt
    val medications: StateFlow<List<Medication>> = adhdProfileRepository.medications
        .map { meds -> meds.filter { it.name.isNotBlank() } }
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            adhdProfileRepository.peekProfile()?.medications.orEmpty()
                .filter { it.name.isNotBlank() },
        )
    // Room-backed now, so a tick survives reinstall and reaches other devices
    val medicationDoseLogs: StateFlow<Set<String>> = medicationLogRepository.observeTakenKeys()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    fun toggleMedicationDose(logKey: String) {
        viewModelScope.launch {
            val willBeLogged = logKey !in medicationDoseLogs.value
            medicationLogRepository.setTaken(logKey, willBeLogged)
            val parts = logKey.split('|')
            if (parts.size == 3) {
                val (date, medicationId, slot) = parts
                todoRepository.setTodoItemCompletion("med-$medicationId-$slot-$date", willBeLogged)
                // and the calendar copy, when that destination is on. the tick has to land everywhere the dose
                // appears: taken in one place and outstanding in another is worse than not syncing at all,
                // because now the user has to work out which one is lying
                val eventId = com.muradgalayev.brainbuddy.data.repository
                    .MedicationTodoSyncer.MEDICATION_EVENT_PREFIX +
                    "$medicationId-$slot-$date"
                runCatching { calendarRepository.setEventCompleted(eventId, willBeLogged) }
            }
        }
    }

    // saves the list and scrubs anything dropped from it. the syncer's rebuild only covers its own
    // two-week window, so a medication removed today would leave rows on last week's dates and
    // beyond the horizon. working out what disappeared here is what makes delete mean gone
    fun saveMedications(medications: List<Medication>, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val profile = adhdProfileRepository.peekProfile()
                ?: adhdProfileRepository.getProfile()
            if (profile == null) {
                onResult(false)
                return@launch
            }
            runCatching {
                val cleaned = medications.filter { it.name.isNotBlank() }
                // adding a medication is the answer to 'do you take medication'. leaving the survey field
                // alone meant someone who skipped that question, or answered it before starting a
                // prescription, kept a profile that said No while listing three drugs, and everything gated
                // on that field silently did nothing
                val status = if (cleaned.isNotEmpty()) {
                    MedicationStatus.Yes
                } else {
                    profile.medicationStatus
                }
                // saveProfile updates the shared stream this flow reads, assigning here as well would just race
                val removedIds = profile.medications.map { it.id }.toSet() -
                    cleaned.map { it.id }.toSet()

                adhdProfileRepository.saveProfile(
                    profile.copy(medications = cleaned, medicationStatus = status)
                )

                removedIds.forEach { id ->
                    runCatching { medicationTodoSyncer.purgeMedication(id) }
                }
            }.onSuccess { onResult(true) }.onFailure { onResult(false) }
        }
    }
    private val _avatarUrl = MutableStateFlow(
        authRepository.peekProfile()?.avatarUrl ?: authRepository.getCurrentUserAvatarUrl(),
    )
    val avatarUrl: StateFlow<String?> = _avatarUrl.asStateFlow()
    val activityGoals: StateFlow<ActivityGoals> = combine(
        preferencesManager.wellnessStepsGoal,
        preferencesManager.wellnessExerciseGoal,
        preferencesManager.wellnessEnergyGoal,
    ) { steps, exercise, energy -> ActivityGoals(steps, exercise, energy) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ActivityGoals())
    // seeded from the synchronous cache, not the DEFAULT constant: starting at the default meant
    // the summary painted the stock order and visibly reshuffled a frame later
    val wellnessCardOrder: StateFlow<List<String>> = preferencesManager.wellnessCardOrder
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            preferencesManager.peekWellnessCardOrder() ?: PreferencesManager.DEFAULT_WELLNESS_CARDS,
        )
    val wellnessPinnedCards: StateFlow<Set<String>> = preferencesManager.wellnessPinnedCards
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            preferencesManager.peekWellnessPinnedCards()
                ?: PreferencesManager.DEFAULT_WELLNESS_CARDS.toSet(),
        )

    // NetworkObserver.isOnline is already hot and process-wide
    val isOnline: StateFlow<Boolean> = networkObserver.isOnline

    fun setActivityGoals(steps: Int, exerciseMinutes: Int, energyKcal: Int) {
        viewModelScope.launch { preferencesManager.setWellnessGoals(steps, exerciseMinutes, energyKcal) }
    }

    fun refreshHealth() {
        viewModelScope.launch { runCatching { healthConnectManager.refresh() } }
    }

    fun setWellnessCardPinned(card: String, pinned: Boolean) {
        viewModelScope.launch { preferencesManager.setWellnessCardPinned(card, pinned) }
    }

    fun moveWellnessCard(card: String, direction: Int) {
        val order = wellnessCardOrder.value.toMutableList()
        val from = order.indexOf(card)
        val to = (from + direction).coerceIn(0, order.lastIndex)
        if (from < 0 || from == to) return
        order.removeAt(from)
        order.add(to, card)
        viewModelScope.launch { preferencesManager.setWellnessCardOrder(order) }
    }

    fun setWellnessCardOrder(order: List<String>) {
        viewModelScope.launch { preferencesManager.setWellnessCardOrder(order) }
    }

    init {
        // warm Care Nearby while Workspace is already visible. both repositories cache their result,
        // so opening Care can paint from memory immediately
        viewModelScope.launch { placesRepository.listCities() }
        viewModelScope.launch {
            val profile = adhdProfileRepository.peekProfile() ?: adhdProfileRepository.getProfile()
            profile?.cityId?.let { placesRepository.listPlacesInCity(it) }
        }
        observeWorkspaceData()
        observePomodoroState()
        observePomodoroHistory()
        observeSimplifiedMode()
        viewModelScope.launch {
            _avatarUrl.value = authRepository.peekProfile()?.avatarUrl
                ?: authRepository.getCurrentUserAvatarUrl()
        }
        viewModelScope.launch {
            runCatching { healthConnectManager.refresh() }
            // pulls the profile so the shared stream is warm, the flow above picks up whatever lands
            runCatching { adhdProfileRepository.getProfile() }
        }
        viewModelScope.launch {
            preferencesManager.calendarVoiceHandlePosition.collect {
                _voiceHandlePosition.value = it
            }
        }
    }

    private fun observeSimplifiedMode() {
        viewModelScope.launch {
            // the mode's answer when it has one, the user's otherwise
            modeManager.effectiveSimplifiedWorkspace.collect { simplified ->
                _uiState.update { it.copy(isSimplified = simplified) }
            }
        }
    }

    fun toggleSimplifiedMode() {
        viewModelScope.launch {
            val current = _uiState.value.isSimplified
            preferencesManager.setSimplifiedWorkspace(!current)
        }
    }

    fun saveVoiceHandlePosition(position: Float) {
        val normalized = position.coerceIn(-1f, 1f)
        _voiceHandlePosition.value = normalized
        viewModelScope.launch { preferencesManager.setCalendarVoiceHandlePosition(normalized) }
    }

    private fun observePomodoroState() {
        viewModelScope.launch {
            pomodoroTimerManager.state.collect { timer ->
                val totalSeconds = timer.remainingMs / 1000
                val minutes = totalSeconds / 60
                val seconds = totalSeconds % 60
                val formattedTime = "%02d:%02d".format(minutes, seconds)

                val statusText = uiText(
                    when (timer.sessionType) {
                        PomodoroSessionType.FOCUS -> when (timer.timerState) {
                            TimerState.RUNNING -> R.string.home_focus_widget
                            TimerState.PAUSED -> R.string.ws_pomo_focus_paused
                            TimerState.COMPLETED -> R.string.ws_pomo_focus_complete
                            TimerState.IDLE -> R.string.ws_pomo_focus_ready
                        }
                        PomodoroSessionType.BREAK -> when (timer.timerState) {
                            TimerState.RUNNING -> R.string.bd_break
                            TimerState.PAUSED -> R.string.ws_pomo_break_paused
                            TimerState.COMPLETED -> R.string.ws_pomo_break_complete
                            TimerState.IDLE -> R.string.ws_pomo_break_ready
                        }
                    }
                )

                _uiState.update {
                    it.copy(
                        pomodoroStatusText = statusText,
                        pomodoroTimeText = formattedTime,
                        pomodoroProgress = timer.progress,
                        pomodoroIsActive = timer.timerState == TimerState.RUNNING ||
                                timer.timerState == TimerState.PAUSED
                    )
                }
            }
        }
    }
    // one agenda entry, from either source. the hero used to read todos only, so a to-do surfaced
    // as 'your next meeting' and real calendar events never appeared at all
    private data class AgendaEntry(
        val title: String,
        val start: java.time.LocalDateTime,
        val end: java.time.LocalDateTime?,
        val isEvent: Boolean,
    )

    private fun observeWorkspaceData() {
        viewModelScope.launch {
            combine(
                todoRepository.getActiveTodoItems(),
                todoRepository.getCompletedTodoItems(),
                todoRepository.getAllTodoItems(),
                calendarRepository.getAllEvents(),
            ) { activeItems, completedItems, allItems, events ->
                listOf(activeItems, completedItems, allItems, events)
            }.collect { parts ->
                @Suppress("UNCHECKED_CAST")
                val activeItems = parts[0] as List<com.muradgalayev.brainbuddy.domain.model.TodoItem>
                @Suppress("UNCHECKED_CAST")
                val completedItems = parts[1] as List<com.muradgalayev.brainbuddy.domain.model.TodoItem>
                @Suppress("UNCHECKED_CAST")
                val allItems = parts[2] as List<com.muradgalayev.brainbuddy.domain.model.TodoItem>
                @Suppress("UNCHECKED_CAST")
                val events = parts[3] as List<com.muradgalayev.brainbuddy.domain.model.CalendarEvent>

                val today = LocalDate.now().toString()
                val nowDate = LocalDate.now()
                val nowTime = LocalTime.now()
                val now = java.time.LocalDateTime.of(nowDate, nowTime)
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

                // calendar events
                val eventEntries = events.mapNotNull { event ->
                    val start = parseIsoLocal(event.startTime) ?: return@mapNotNull null
                    AgendaEntry(
                        title = event.title,
                        start = start,
                        end = parseIsoLocal(event.endTime),
                        isEvent = true,
                    )
                }

                // unfinished to-dos
                val todoEntries = allItems.filter { !it.isCompleted }.mapNotNull { item ->
                    val date = runCatching { LocalDate.parse(item.date) }.getOrNull()
                        ?: return@mapNotNull null
                    val time = if (item.startTime.isNotBlank()) {
                        runCatching { LocalTime.parse(item.startTime, timeFormatter) }
                            .getOrNull() ?: LocalTime.MAX
                    } else LocalTime.MAX
                    AgendaEntry(
                        title = item.title,
                        start = java.time.LocalDateTime.of(date, time),
                        end = null,
                        isEvent = false,
                    )
                }

                val agenda = (eventEntries + todoEntries).sortedBy { it.start }

                // the Calendar card speaks for the calendar only, showing a to-do there was the bug
                val nextEvent = eventEntries
                    .filter { (it.end ?: it.start).isAfter(now) }
                    .minByOrNull { it.start }
                val nextMeetingText = nextEvent?.let { entry ->
                    val d = entry.start.toLocalDate()
                    val dayLabel = when {
                        d.isEqual(nowDate) -> uiText(R.string.common_today)
                        d.isEqual(nowDate.plusDays(1)) -> uiText(R.string.common_tomorrow)
                        else -> d.format(localizedDateFormatter("MMMd")).asUiText()
                    }
                    uiText(R.string.ws_day_time, dayLabel, entry.start.toLocalTime().format(timeFormatter))
                }

                // hero: what's happening now, else what's next
                val runningNow = agenda.firstOrNull { entry ->
                    !entry.start.isAfter(now) && entry.end != null && entry.end.isAfter(now)
                }
                val upcoming = agenda.firstOrNull { it.start.isAfter(now) }
                val heroEntry = runningNow ?: upcoming

                val heroWhen: UiText? = when {
                    runningNow != null -> uiText(R.string.ws_happening_now)
                    upcoming != null -> {
                        val d = upcoming.start.toLocalDate()
                        val hasTime = upcoming.start.toLocalTime() != LocalTime.MAX
                        val clock = upcoming.start.toLocalTime().format(timeFormatter)
                        when {
                            d.isEqual(nowDate) && hasTime -> {
                                val mins = java.time.Duration.between(now, upcoming.start).toMinutes()
                                when {
                                    mins <= 0L -> uiText(R.string.agenda_starting_now)
                                    mins < 60L -> uiText(R.string.agenda_in, uiText(R.string.common_minutes_short, mins))
                                    else -> uiText(R.string.ws_day_time, uiText(R.string.common_today), clock)
                                }
                            }
                            d.isEqual(nowDate) -> uiText(R.string.common_today)
                            d.isEqual(nowDate.plusDays(1)) ->
                                if (hasTime) uiText(R.string.ws_day_time, uiText(R.string.common_tomorrow), clock)
                                else uiText(R.string.common_tomorrow)
                            else -> d.format(localizedDateFormatter("MMMd")).asUiText()
                        }
                    }
                    else -> null
                }

                val todayActive = activeItems.count { it.date == today }
                val todayCompleted = completedItems.count { it.date == today }
                val todayTotal = todayActive + todayCompleted
                val progress = if (todayTotal > 0) todayCompleted.toFloat() / todayTotal else 0f
                val completedText = when (todayCompleted) {
                    0 -> uiText(R.string.ws_no_tasks_completed)
                    1 -> uiText(R.string.ws_completed_one)
                    else -> uiText(R.string.ws_completed_n, todayCompleted)
                }

                val weekStart = nowDate.minusDays(6)
                val weekCompleted = completedItems.count { item ->
                    runCatching { LocalDate.parse(item.date) }.getOrNull()
                        ?.let { !it.isBefore(weekStart) && !it.isAfter(nowDate) } == true
                }

                _uiState.update {
                    it.copy(
                        todoTasksLeft = todayActive,
                        todayCompletedTasks = todayCompleted,
                        todayTotalTasks = todayTotal,
                        todoProgress = progress,
                        isLoading = false,
                        nextMeetingText = nextMeetingText,
                        todayCompletedText = completedText,
                        nextUpTitle = heroEntry?.title,
                        nextUpWhen = heroWhen,
                        nextUpIsNow = runningNow != null,
                        nextUpIsEvent = heroEntry?.isEvent == true,
                        weekCompletedTasks = weekCompleted,
                    )
                }
            }
        }
    }

    // tolerates both '...T10:00' and '...T10:00:00' shapes coming out of storage
    private fun parseIsoLocal(iso: String): java.time.LocalDateTime? = runCatching {
        java.time.LocalDateTime.parse(
            if (iso.length == 16 && iso.contains('T')) "$iso:00" else iso
        )
    }.getOrNull()

    private fun observePomodoroHistory() {
        viewModelScope.launch {
            while (true) {
                val today = LocalDate.now()
                val yesterday = today.minusDays(1)
                val zone = ZoneId.systemDefault()

                val todayStart = today.atStartOfDay(zone).toInstant().toEpochMilli()
                val todayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

                val yesterdayStart = yesterday.atStartOfDay(zone).toInstant().toEpochMilli()
                val yesterdayEnd = today.atStartOfDay(zone).toInstant().toEpochMilli() - 1

                val todayMinutes = pomodoroRepository.getCompletedFocusMinutesForDay(todayStart, todayEnd)
                val yesterdayMinutes = pomodoroRepository.getCompletedFocusMinutesForDay(yesterdayStart, yesterdayEnd)

                val comparisonText = when {
                    todayMinutes == 0 && yesterdayMinutes == 0 -> uiText(R.string.ws_no_focus_sessions)
                    todayMinutes > yesterdayMinutes -> uiText(R.string.ws_min_more, todayMinutes - yesterdayMinutes)
                    todayMinutes < yesterdayMinutes -> uiText(R.string.ws_min_less, yesterdayMinutes - todayMinutes)
                    else -> uiText(R.string.ws_same_as_yesterday)
                }

                val (heroTitle, heroSubtitle) = buildFocusHeroCopy(
                    today = todayMinutes,
                    yesterday = yesterdayMinutes
                )
                val completedToday = _uiState.value.todayCompletedTasks
                val (bannerTitle, bannerMessage) = buildActivityBannerCopy(
                    completedToday = completedToday,
                    todayMinutes = todayMinutes
                )

                _uiState.update {
                    it.copy(
                        todayFocusMinutes = todayMinutes,
                        yesterdayFocusMinutes = yesterdayMinutes,
                        focusComparisonText = comparisonText,
                        focusHeroTitle = heroTitle,
                        focusHeroSubtitle = heroSubtitle,
                        activityBannerTitle = bannerTitle,
                        activityBannerMessage = bannerMessage
                    )
                }

                delay(5000)
            }
        }
    }
}

private fun buildFocusHeroCopy(today: Int, yesterday: Int): Pair<UiText, UiText> {
    val focusedToday = uiText(R.string.ws_focused_today, formatFocusMinutes(today))
    return when {
        today == 0 && yesterday == 0 ->
            uiText(R.string.ws_hero_fresh) to uiText(R.string.ws_hero_fresh_sub)
        today == 0 && yesterday > 0 ->
            uiText(R.string.focus_empty_title_today) to
                uiText(R.string.ws_hero_yesterday, formatFocusMinutes(yesterday))
        yesterday == 0 && today > 0 -> focusedToday to uiText(R.string.focus_cmp_first)
        today > yesterday ->
            focusedToday to uiText(R.string.focus_cmp_more, formatFocusMinutes(today - yesterday))
        today < yesterday ->
            focusedToday to uiText(R.string.focus_cmp_less, formatFocusMinutes(yesterday - today))
        else -> focusedToday to uiText(R.string.ws_hero_same)
    }
}

// whole sentences per case rather than 'You've $tasks and $focus': the fragments don't survive
// translation, Italian needs the verb to agree with whatever ends up in the slot
private fun buildActivityBannerCopy(completedToday: Int, todayMinutes: Int): Pair<UiText, UiText> {
    val tasks = if (completedToday == 1) uiText(R.string.ws_task_count_one)
    else uiText(R.string.ws_task_count_n, completedToday)
    val focus = formatFocusMinutes(todayMinutes)
    return when {
        completedToday == 0 && todayMinutes == 0 ->
            uiText(R.string.ws_banner_fresh) to uiText(R.string.ws_banner_fresh_sub)
        completedToday == 0 ->
            uiText(R.string.ws_banner_great) to uiText(R.string.ws_banner_focus_only, focus)
        todayMinutes == 0 ->
            uiText(R.string.ws_banner_great) to uiText(R.string.ws_banner_tasks_only, tasks)
        else ->
            uiText(R.string.ws_banner_great) to uiText(R.string.ws_banner_both, tasks, focus)
    }
}
