package com.muradgalayev.brainbuddy.ui.calendar

import android.app.PendingIntent
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.google.GoogleCalendarAuthClient
import com.muradgalayev.brainbuddy.data.google.GoogleCalendarTokenStore
import com.muradgalayev.brainbuddy.data.local.ModeManager
import com.muradgalayev.brainbuddy.data.local.PomodoroQueueItem
import com.muradgalayev.brainbuddy.data.local.PomodoroQueueState
import com.muradgalayev.brainbuddy.data.local.PomodoroTimerManager
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.health.HealthConnectManager
import com.muradgalayev.brainbuddy.data.health.HealthConnectUiState
import com.muradgalayev.brainbuddy.data.network.NetworkObserver
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.ExportResult
import com.muradgalayev.brainbuddy.data.repository.GoogleCalendarRepository
import com.muradgalayev.brainbuddy.data.repository.HabitTimingRepository
import com.muradgalayev.brainbuddy.data.sync.CalendarSyncScheduler
import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import com.muradgalayev.brainbuddy.domain.model.CalendarSubtask
import com.muradgalayev.brainbuddy.domain.model.SubtaskKind
import com.muradgalayev.brainbuddy.domain.scheduling.BusyInterval
import com.muradgalayev.brainbuddy.domain.scheduling.DayContext
import com.muradgalayev.brainbuddy.domain.scheduling.EnergySignals
import com.muradgalayev.brainbuddy.domain.scheduling.MINUTES_PER_DAY
import com.muradgalayev.brainbuddy.domain.scheduling.SleepWindow
import com.muradgalayev.brainbuddy.domain.scheduling.TimeSuggestion
import com.muradgalayev.brainbuddy.domain.scheduling.TimeSuggestionEngine
import com.muradgalayev.brainbuddy.domain.scheduling.toBusyIntervalOrNull
import com.muradgalayev.brainbuddy.domain.scheduling.toEnergySignalsOrNull
import com.muradgalayev.brainbuddy.domain.scheduling.WorkingHours
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import com.muradgalayev.brainbuddy.ui.together.ShareOutcome
import com.muradgalayev.brainbuddy.ui.together.SharedItemKind
import com.muradgalayev.brainbuddy.ui.together.writableFor
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID
import javax.inject.Inject

data class CalendarSubtaskUi(
    val id: String,
    val title: String,
    val durationMinutes: Int,
    val completed: Boolean,
    val kind: SubtaskKind,
)

data class CalendarTaskUi(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val location: String? = null,
    val link: String? = null,
    val timeRange: String? = null,
    val totalMinutes: Int = 0,
    // null when the times couldn't be parsed
    val endMillis: Long? = null,
    val accent: Color,
    // ticked off by hand. isDone also counts plans
    val completed: Boolean = false,
    val flagged: Boolean = false,
    // who put this event on your calendar, null when you made it yourself.
    // drives the 'Added by' badge, without it an event you never made just turns up in your day
    val addedByName: String? = null,
    val subtasks: List<CalendarSubtaskUi> = emptyList(),
    // a dose from the medication list rather than an event the user made, spotted by the id prefix
    val isMedication: Boolean = false
) {
    // done directly, or every station in its breakdown done. derived rather than stored so
    // un-ticking a station re-opens the event instead of leaving a stale flag behind
    val isDone: Boolean
        get() = completed || (subtasks.isNotEmpty() && subtasks.all { it.completed })
}

// one marker under a date cell. done covers both routes, so the grid agrees with the day list
data class CalendarDayDot(val color: Color, val done: Boolean)

// what a date cell should draw. a bare Set<LocalDate> only said 'this day has something',
// which left the grid inventing a dot count out of the day number
data class CalendarDayMarks(
    val dots: List<CalendarDayDot> = emptyList(),
    val total: Int = 0,
) {
    // drawn hollow rather than filled
    val allDone: Boolean get() = total > 0 && dots.all { it.done }
    val hasMore: Boolean get() = total > dots.size
}

data class CalendarUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val tasksForSelectedDate: List<CalendarTaskUi> = emptyList(),
    val datesWithTasks: Map<LocalDate, CalendarDayMarks> = emptyMap(),
    val visibleMonth: YearMonth = YearMonth.now(),
    val showAddTaskDialog: Boolean = false,
    val editingEvent: CalendarEvent? = null,
    val expandedEventId: String? = null,
    val isLoading: Boolean = true,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val calendarRepository: CalendarRepository,
    private val googleCalendarRepository: GoogleCalendarRepository,
    private val googleCalendarAuthClient: GoogleCalendarAuthClient,
    private val googleCalendarTokenStore: GoogleCalendarTokenStore,
    private val pomodoroTimerManager: PomodoroTimerManager,
    private val calendarSyncScheduler: CalendarSyncScheduler,
    private val preferencesManager: PreferencesManager,
    private val modeManager: ModeManager,
    private val togetherRepository: com.muradgalayev.brainbuddy.data.repository.TogetherRepository,
    private val todoRepository: com.muradgalayev.brainbuddy.data.repository.TodoRepository,
    private val habitTimingRepository: HabitTimingRepository,
    private val adhdProfileRepository: AdhdProfileRepository,
    private val medicationLogRepository: com.muradgalayev.brainbuddy.data.repository.MedicationLogRepository,
    private val healthConnectManager: HealthConnectManager,
    private val aiNavigator: com.muradgalayev.brainbuddy.domain.ai.AiNavigator,
    networkObserver: NetworkObserver,
) : ViewModel() {

    init {
        // the assistant can hand a job over instead of doing it ('I can't add to their calendar,
        // but here's the form'). it latches a flag and navigates here, this is the other end
        viewModelScope.launch {
            aiNavigator.pendingCalendarAdd.collect { pending ->
                if (!pending) return@collect
                showAddTaskDialog()
                // cleared straight away so coming back later doesn't pop the form open again
                aiNavigator.consumeCalendarAdd()
            }
        }
    }

    // a connection whose calendar I'm allowed to add to
    data class ShareTarget(val userId: String, val name: String)

    // filtered on grantedToMe, not grantedByMe: what matters is whether they opened their
    // calendar to me. the write is still authorised server-side, this only decides what to show
    val calendarShareTargets: StateFlow<List<ShareTarget>> = togetherRepository.connections
        .map { connections ->
            connections
                .writableFor(com.muradgalayev.brainbuddy.domain.model.ShareScope.CALENDAR)
                .map { ShareTarget(it.userId, it.name) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _togetherShareMessage = MutableStateFlow<String?>(null)
    val togetherShareMessage: StateFlow<String?> = _togetherShareMessage.asStateFlow()

    fun clearTogetherShareMessage() {
        _togetherShareMessage.value = null
    }

    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()
    // NetworkObserver.isOnline is already hot and process-wide
    val isOnline: StateFlow<Boolean> = networkObserver.isOnline

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _visibleMonth = MutableStateFlow(YearMonth.now())

    // unformatted events for the selected day, what the time suggester reasons over
    private val _eventsForSelectedDate = MutableStateFlow<List<CalendarEvent>>(emptyList())

    // events across the whole look-ahead, by date. separate from _eventsForSelectedDate because
    // the cross-day pass has to see tomorrow before it can claim tomorrow is better
    private val _eventsInSuggestionWindow =
        MutableStateFlow<Map<LocalDate, List<CalendarEvent>>>(emptyMap())

    // timed to-dos in the same window, already reduced to blocked intervals. a to-do with a time
    // on it is a commitment like any other, the user doesn't get the hour back because it lives
    // in a different list
    private val _todoBlocksInWindow =
        MutableStateFlow<Map<LocalDate, List<BusyInterval>>>(emptyMap())

    // who the dialog currently says this event is for
    private val _selectedShareTargets = MutableStateFlow<Set<String>>(emptySet())

    // their busy hours, merged. empty when nobody is selected or nobody shares
    private val _shareTargetBusy = MutableStateFlow<Map<LocalDate, List<BusyInterval>>>(emptyMap())

    // only the people who actually shared it. a selected connection who hasn't granted
    // AVAILABILITY is silently absent rather than implied, claiming to work around a calendar
    // we can't see would be worse than saying nothing
    private val _availabilityNames = MutableStateFlow<List<String>>(emptyList())

    private val _isSyncingToGoogle = MutableStateFlow(false)
    val isSyncingToGoogle: StateFlow<Boolean> = _isSyncingToGoogle.asStateFlow()

    private val _googleSyncMessage = MutableStateFlow<String?>(null)
    val googleSyncMessage: StateFlow<String?> = _googleSyncMessage.asStateFlow()

    private val _googleAuthRequest = MutableStateFlow<PendingIntent?>(null)
    val googleAuthRequest: StateFlow<PendingIntent?> = _googleAuthRequest.asStateFlow()

    // intro animation flying a shape into the Calendar AI button. plays the first few visits,
    // then stops for good
    private val _showAiIntro = MutableStateFlow(false)
    val showAiIntro: StateFlow<Boolean> = _showAiIntro.asStateFlow()
    private val _showVoiceTip = MutableStateFlow(false)
    val showVoiceTip: StateFlow<Boolean> = _showVoiceTip.asStateFlow()
    // null until DataStore emits, otherwise it jumps in from centre for one frame
    private val _voiceHandlePosition = MutableStateFlow<Float?>(null)
    val voiceHandlePosition: StateFlow<Float?> = _voiceHandlePosition.asStateFlow()

    init {
        seedFromCache()
        observeEventsForSelectedDate()
        observeDatesWithEvents()
        observeSuggestionWindow()
        observeShareTargetAvailability()
        // idempotent, SyncCoordinator calls it too. repeated here so someone who opens Calendar
        // before the first sync still gets suggestions out of whatever Room already holds
        habitTimingRepository.observeForCurrentUser()
        viewModelScope.launch {
            if (preferencesManager.getCalendarAiIntroCount() < AI_INTRO_PLAYS) {
                _showAiIntro.value = true
            }
        }
        viewModelScope.launch {
            _showVoiceTip.value = !preferencesManager.isCalendarVoiceTipDismissed()
        }
        viewModelScope.launch {
            preferencesManager.calendarVoiceHandlePosition.collect {
                _voiceHandlePosition.value = it
            }
        }
        // Room emits reactively and SyncCoordinator already pulls on start and on reconnect. firing
        // another sync here just made every tab-tap on Calendar feel like a refetch flash
    }

    // counts the play toward the cap
    fun markAiIntroShown() {
        if (!_showAiIntro.value) return
        _showAiIntro.value = false
        viewModelScope.launch { preferencesManager.incrementCalendarAiIntroCount() }
    }

    fun dismissVoiceTip() {
        _showVoiceTip.value = false
        viewModelScope.launch { preferencesManager.dismissCalendarVoiceTip() }
    }

    fun saveVoiceHandlePosition(position: Float) {
        val normalized = position.coerceIn(-1f, 1f)
        _voiceHandlePosition.value = normalized
        viewModelScope.launch { preferencesManager.setCalendarVoiceHandlePosition(normalized) }
    }

    // fills today's list from the in-process cache before any flow emits, so re-entering
    // Calendar shows the day instead of flashing its empty state
    // user id to display name for every current connection, for turning createdByOther into
    // something readable. an author who is no longer a connection falls back to a generic label
    // rather than leaking a raw uuid into the UI
    private fun authorNames(): Map<String, String> =
        togetherRepository.connections.value.associate { it.userId to it.name }

    private fun seedFromCache() {
        val date = _selectedDate.value.toString()
        val cached = calendarRepository.peekEventsForDate(date) ?: return
        val subtasks = calendarRepository.peekSubtasks().orEmpty().groupBy { it.eventId }
        val names = authorNames()
        _uiState.update { state ->
            state.copy(
                tasksForSelectedDate = cached.map { it.toCalendarTaskUi(subtasks[it.id].orEmpty(), names) },
                isLoading = false,
            )
        }
    }

    private fun observeEventsForSelectedDate() {
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                // connections are in the combine so a name arriving after the events (first sync of the
                // process) re-labels the day, instead of leaving 'Added by a connection' until the next write
                combine(
                    calendarRepository.getEventsByDate(date.toString()),
                    calendarRepository.observeSubtasksForUser(),
                    togetherRepository.connections,
                ) { events, allSubtasks, connections ->
                    val grouped = allSubtasks.groupBy { it.eventId }
                    val names = connections.associate { it.userId to it.name }
                    // the raw events travel alongside the UI rows: the suggester needs real start/end minutes,
                    // which the formatted string in CalendarTaskUi can't give it
                    events to events.map { event ->
                        event.toCalendarTaskUi(grouped[event.id].orEmpty(), names)
                    }
                }
            }.collect { (rawEvents, items) ->
                _eventsForSelectedDate.value = rawEvents
                _uiState.update { state ->
                    // equality-guarded: Room re-emits on any table write, and swapping in an identical list
                    // recomposes the whole day for nothing. that's what made adding one event feel like a refetch
                    if (state.tasksForSelectedDate == items && !state.isLoading) state
                    else state.copy(tasksForSelectedDate = items, isLoading = false)
                }
            }
        }
    }

    // keeps the next few days loaded so a 'try tomorrow' suggestion is checked against tomorrow's
    // real commitments rather than an assumed empty day
    private fun observeSuggestionWindow() {
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                calendarRepository.getEventsInDateRange(
                    date.toString(),
                    date.plusDays(SUGGESTION_LOOKAHEAD_DAYS.toLong()).toString(),
                )
            }.collect { events ->
                _eventsInSuggestionWindow.value = events.groupBy { event ->
                    runCatching { LocalDate.parse(event.startTime.take(10)) }.getOrNull()
                }.mapNotNull { (date, list) -> date?.let { it to list } }.toMap()
            }
        }
        viewModelScope.launch {
            _selectedDate.flatMapLatest { date ->
                todoRepository.getTodoItemsInRange(
                    date.toString(),
                    date.plusDays(SUGGESTION_LOOKAHEAD_DAYS.toLong()).toString(),
                )
            }.collect { todos ->
                _todoBlocksInWindow.value = todos
                    .mapNotNull { todo ->
                        val date = runCatching { LocalDate.parse(todo.date) }.getOrNull()
                        val block = todo.toBusyIntervalOrNull()
                        if (date != null && block != null) date to block else null
                    }
                    .groupBy({ it.first }, { it.second })
            }
        }
    }

    private fun observeDatesWithEvents() {
        viewModelScope.launch {
            _visibleMonth.flatMapLatest { month ->
                val start = month.atDay(1).toString()
                val end = month.atEndOfMonth().toString()
                combine(
                    calendarRepository.getEventsInDateRange(start, end),
                    calendarRepository.observeSubtasksForUser(),
                ) { events, subtasks -> events to subtasks.groupBy { it.eventId } }
            }.collect { (items, subtasksByEvent) ->
                val marks = items
                    .mapNotNull { event ->
                        runCatching { LocalDate.parse(event.startTime.take(10)) }
                            .getOrNull()?.let { it to event }
                    }
                    .groupBy({ it.first }, { it.second })
                    .mapValues { (_, events) ->
                        val ordered = events.sortedBy { it.startTime }
                        CalendarDayMarks(
                            // past three they stop being countable at this size, hasMore carries the rest
                            dots = ordered.take(MAX_DAY_DOTS).map { event ->
                                val subtasks = subtasksByEvent[event.id].orEmpty()
                                CalendarDayDot(
                                    color = resolveEventColor(event.color).accent,
                                    done = event.completed ||
                                        (subtasks.isNotEmpty() && subtasks.all { it.completed }),
                                )
                            },
                            total = ordered.size,
                        )
                    }
                _uiState.update { it.copy(datesWithTasks = marks) }
            }
        }
    }

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
        val cached = calendarRepository.peekEventsForDate(date.toString())
        val subtasks = calendarRepository.peekSubtasks().orEmpty().groupBy { it.eventId }
        _uiState.update { state ->
            state.copy(
                selectedDate = date,
                expandedEventId = null,
                // seed from cache when we have it, otherwise keep the previous list on screen until the new
                // one lands rather than blanking the sheet
                tasksForSelectedDate = cached?.map { it.toCalendarTaskUi(subtasks[it.id].orEmpty()) }
                    ?: state.tasksForSelectedDate,
            )
        }
    }

    // moves to a date that may not be on screen, dragging the month grid with it. selectDate()
    // deliberately doesn't: it's called from the grid itself, where forcing the month would fight
    // the pager on the leading and trailing days of a month
    fun jumpToDate(date: LocalDate) {
        selectDate(date)
        val month = YearMonth.from(date)
        if (_visibleMonth.value != month) updateVisibleMonth(month)
    }

    fun updateVisibleMonth(month: YearMonth) {
        _visibleMonth.value = month
        _uiState.update { it.copy(visibleMonth = month) }
    }

    fun showAddTaskDialog() {
        _suggestionInput.value = SuggestionInput()
        _selectedShareTargets.value = emptySet()
        _uiState.update { it.copy(showAddTaskDialog = true, editingEvent = null) }
    }

    fun dismissAddTaskDialog() {
        _suggestionInput.value = SuggestionInput()
        // otherwise the next event silently inherits the last recipient's hours
        _selectedShareTargets.value = emptySet()
        _uiState.update { it.copy(showAddTaskDialog = false, editingEvent = null) }
    }

    // suggested times. entirely on-device: a lexicon, the user's own past timings, their sleep
    // survey and Health Connect. no model, no network, no per-suggestion cost, which is what
    // makes it cheap enough to recompute on every keystroke.
    // the one exception is a connection's free/busy, which has to be fetched. it arrives as bare
    // hours (see ShareScope.AVAILABILITY) and is just another constraint once it lands

    // what the dialog currently has typed in it
    private data class SuggestionInput(val title: String = "", val description: String = "")

    private val _suggestionInput = MutableStateFlow(SuggestionInput())

    // one line under the suggestion strip, or null when there is nothing to say. it names who,
    // and it says what isn't happening: 'we looked at your friend's calendar' is alarming until
    // you know it was only ever the outline of it
    val suggestionAvailabilityNote: StateFlow<String?> = _availabilityNames
        .map { names ->
            val who = when {
                names.isEmpty() -> return@map null
                names.size == 1 -> "${names.first()} is"
                names.size == 2 -> "${names[0]} and ${names[1]} are"
                else -> "${names.size} people are"
            }
            "Working around the hours $who busy — Myndora never shows you what those are."
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), null)

    // called from the add-event dialog as the 'who is this for' selection changes
    fun onShareTargetsChanged(userIds: Set<String>) {
        if (_selectedShareTargets.value != userIds) _selectedShareTargets.value = userIds
    }

    // keeps the recipients' free/busy in step with who is selected and which day we're writing
    // to. collectLatest so a fast run through the chips leaves only the last fetch standing, and
    // each pass rebuilds from scratch: a deselected person's hours must not linger as walls
    private fun observeShareTargetAvailability() {
        viewModelScope.launch {
            combine(_selectedShareTargets, _selectedDate) { ids, date -> ids to date }
                .distinctUntilChanged()
                .collectLatest { (ids, date) ->
                    if (ids.isEmpty()) {
                        _shareTargetBusy.value = emptyMap()
                        _availabilityNames.value = emptyList()
                        return@collectLatest
                    }
                    val merged = mutableMapOf<LocalDate, MutableList<BusyInterval>>()
                    val names = mutableListOf<String>()
                    for (id in ids) {
                        val blocks = togetherRepository.availabilityFor(
                            ownerId = id,
                            from = date,
                            to = date.plusDays(SUGGESTION_LOOKAHEAD_DAYS.toLong()),
                        ).getOrNull() ?: continue
                        blocks.forEach { (day, list) ->
                            merged.getOrPut(day) { mutableListOf() } += list
                        }
                        calendarShareTargets.value.firstOrNull { it.userId == id }
                            ?.let { names += it.name }
                    }
                    _shareTargetBusy.value = merged
                    _availabilityNames.value = names
                }
        }
    }

    // the last set we showed, so saveTask can tell whether the user took a suggestion or picked
    // something else. a rejected suggestion is the best signal we get about their real routine
    @Volatile
    private var lastOffered: List<TimeSuggestion> = emptyList()

    // folded into one flow only so the combine below stays inside the typed overloads' arity
    @OptIn(FlowPreview::class)
    private val suggestionSeed = combine(
        // long enough that a word being typed doesn't churn, short enough that the chips are there
        // by the time the user's eyes reach the time fields
        _suggestionInput.debounce(220),
        _shareTargetBusy,
    ) { input, targetBusy -> input to targetBusy }

    val timeSuggestions: StateFlow<List<TimeSuggestion>> = combine(
        suggestionSeed,
        _selectedDate,
        _eventsInSuggestionWindow,
        _todoBlocksInWindow,
        modeManager.effectiveWorkingHours,
    ) { (input, targetBusy), date, eventsByDate, todoBlocksByDate, workingHours ->
        computeSuggestions(input, targetBusy, date, eventsByDate, todoBlocksByDate, workingHours)
    }
        .onEach { lastOffered = it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

    // called from the add-event dialog as the user types
    fun onSuggestionInputChanged(title: String, description: String) {
        val next = SuggestionInput(title, description)
        if (_suggestionInput.value != next) _suggestionInput.value = next
    }

    private fun computeSuggestions(
        input: SuggestionInput,
        targetBusy: Map<LocalDate, List<BusyInterval>>,
        date: LocalDate,
        eventsByDate: Map<LocalDate, List<CalendarEvent>>,
        todoBlocksByDate: Map<LocalDate, List<BusyInterval>>,
        workingHours: WorkingHours?,
    ): List<TimeSuggestion> {
        // below this the title is still being typed, and 'din' is not dinner yet
        if (input.title.trim().length < MIN_TITLE_CHARS_FOR_SUGGESTIONS) return emptyList()

        val profile = adhdProfileRepository.peekProfile()
        val sleep = SleepWindow.parse(profile?.sleepBedtime, profile?.sleepWakeTime)
        val now = LocalDateTime.now()
        // the event being edited isn't a conflict with itself, or the dialog would refuse to suggest
        // the slot the event already occupies
        val editingId = _uiState.value.editingEvent?.id

        fun contextFor(day: LocalDate) = DayContext(
            date = day,
            now = now,
            // calendar events and timed to-dos are one list here, the user has one day however many
            // lists we keep it in. mine, plus the opaque blocks of anyone this event is being added for:
            // their hours constrain the same way mine do, and what fills them never arrives here
            busy = eventsByDate[day].orEmpty()
                .filter { it.id != editingId }
                .mapNotNull { it.toBusyIntervalOrNull() } +
                todoBlocksByDate[day].orEmpty() +
                targetBusy[day].orEmpty(),
            sleep = sleep,
            productiveTime = profile?.productiveTime,
            // health data describes today and only today. a future day gets null, meaning unknown rather
            // than rested, since tomorrow's step count doesn't exist yet
            energy = if (day == now.toLocalDate()) {
                healthConnectManager.state.value.toEnergySignalsOrNull()
            } else {
                null
            },
            workingHours = workingHours,
        )

        return TimeSuggestionEngine.suggestAcrossDays(
            title = input.title,
            description = input.description,
            selectedDay = contextFor(date),
            laterDays = (1..SUGGESTION_LOOKAHEAD_DAYS).map { contextFor(date.plusDays(it.toLong())) },
            lookup = habitTimingRepository.lookupAt(System.currentTimeMillis()),
        )
    }

    fun editEvent(taskId: String) {
        viewModelScope.launch {
            val event = calendarRepository.getEventById(taskId) ?: return@launch
            _uiState.update {
                it.copy(showAddTaskDialog = true, editingEvent = event)
            }
        }
    }

    fun toggleExpanded(taskId: String) {
        _uiState.update {
            it.copy(expandedEventId = if (it.expandedEventId == taskId) null else taskId)
        }
    }

    // saves the event on my calendar and/or on the calendars of the connections in alsoAddFor.
    // each copy is its own row owned by that person with created_by set to me, not a shared
    // reference, which is what makes the visibility rule hold.
    // addToMyCalendar can be false to create an event purely for someone else. when it's true my
    // event is written first and independently, so a rejected copy can't cost the user theirs
    fun saveTask(
        title: String,
        description: String,
        startTime: String,  // "HH:mm"
        endTime: String,    // "HH:mm"
        location: String,
        color: String,
        link: String,
        alsoAddFor: Set<String> = emptySet(),
        addToMyCalendar: Boolean = true,
    ) {
        if (title.isBlank()) return
        // nothing to write anywhere, no-op rather than quietly discarding what the user typed
        if (!addToMyCalendar && alsoAddFor.isEmpty()) return
        val date = _uiState.value.selectedDate
        val editing = _uiState.value.editingEvent
        viewModelScope.launch {
            val event = CalendarEvent(
                id = editing?.id ?: UUID.randomUUID().toString(),
                title = title.trim(),
                description = description.trim(),
                startTime = combineDateAndTime(date, startTime),
                endTime = combineDateAndTime(date, endTime),
                location = location.trim(),
                color = color,
                link = link.trim(),
            )
            if (addToMyCalendar) {
                if (editing == null) {
                    calendarRepository.insertEvent(event)
                } else {
                    calendarRepository.updateEvent(event)
                }
                learnFromSave(event, startTime)
            }
            _suggestionInput.value = SuggestionInput()
            _uiState.update { it.copy(showAddTaskDialog = false, editingEvent = null) }
            if (alsoAddFor.isNotEmpty()) {
                shareEventWith(alsoAddFor, event, date, startTime, endTime, addToMyCalendar)
            }
        }
    }

    // folds a just-saved event into the learned timings. the weight is the interesting part:
    // a time picked while looking at our suggestions and not taking any of them counts triple,
    // they were shown our guess and said no. taking a suggestion is much weaker evidence, at
    // full weight the feature would just train on its own output
    private fun learnFromSave(event: CalendarEvent, startTime: String) {
        val chosen = parseHourMinuteOrNull(startTime) ?: return
        val chosenMinutes = chosen.hour * 60 + chosen.minute
        val offered = lastOffered
        val tookSuggestion = offered.any { it.startMinutes == chosenMinutes }
        val weight = when {
            offered.isEmpty() -> HabitTimingRepository.WEIGHT_PLANNED
            tookSuggestion -> HabitTimingRepository.WEIGHT_PLANNED * 0.5
            else -> HabitTimingRepository.WEIGHT_OVERRODE_SUGGESTION
        }
        habitTimingRepository.recordEvent(event, weight)
    }

    private suspend fun shareEventWith(
        targetIds: Set<String>,
        event: CalendarEvent,
        date: LocalDate,
        startTime: String,
        endTime: String,
        keptOnMyCalendar: Boolean,
    ) {
        val targets = calendarShareTargets.value.associateBy { it.userId }
        val start = parseHourMinuteOrNull(startTime) ?: LocalTime.of(9, 0)
        val end = parseHourMinuteOrNull(endTime)?.takeIf { it > start } ?: start.plusHours(1)
        val succeeded = mutableListOf<String>()
        val failed = mutableListOf<String>()

        for (id in targetIds) {
            val name = targets[id]?.name ?: "your connection"
            togetherRepository.createEventFor(
                ownerId = id,
                title = event.title,
                description = event.description,
                startTime = LocalDateTime.of(date, start),
                endTime = LocalDateTime.of(date, end),
                location = event.location,
                color = event.color,
            ).fold(
                onSuccess = { succeeded += name },
                onFailure = { failed += name },
            )
        }

        val outcome = ShareOutcome(succeeded = succeeded, failed = failed)
        if (outcome.needsLocalRescue(keptOnMyCalendar)) {
            runCatching { calendarRepository.insertEvent(event) }
        }
        _togetherShareMessage.value = outcome.message(keptOnMyCalendar, SharedItemKind.EVENT)
    }

    private fun parseHourMinuteOrNull(hhmm: String): LocalTime? = runCatching {
        val (h, m) = hhmm.split(":").map { it.trim().toInt() }
        LocalTime.of(h, m)
    }.getOrNull()

    // marks an event done or re-opens it. with a plan, un-ticking is ambiguous since the event
    // may be done only because every station is, so clear the stations too and the tap means
    // exactly what it says
    fun toggleTaskCompleted(taskId: String) {
        val task = _uiState.value.tasksForSelectedDate.firstOrNull { it.id == taskId } ?: return
        val markDone = !task.isDone
        viewModelScope.launch {
            calendarRepository.setEventCompleted(taskId, markDone)

            // a dose is one fact recorded in three places. ticking it here has to reach the log the
            // Medications screen reads and the to-do that mirrors it, or the same dose shows taken on
            // one screen and outstanding on the other
            if (task.isMedication) {
                medicationLogKeyForEvent(taskId)?.let { logKey ->
                    medicationLogRepository.setTaken(logKey, markDone)
                    val parts = logKey.split('|')
                    if (parts.size == 3) {
                        val (date, medId, slot) = parts
                        todoRepository.setTodoItemCompletion("med-$medId-$slot-$date", markDone)
                    }
                }
            }
            // ticking it off upgrades the evidence: a time they didn't just plan but kept. only on the
            // way to done, un-ticking says nothing about timing, and recording on every toggle would let
            // one indecisive tap outweigh a month of real days
            if (markDone) {
                calendarRepository.getEventById(taskId)?.let {
                    habitTimingRepository.recordEvent(it, HabitTimingRepository.WEIGHT_COMPLETED)
                }
            }
            if (markDone && task.subtasks.isNotEmpty()) {
                // half a plan left open under a completed event reads as a bug
                task.subtasks.filterNot { it.completed }.forEach {
                    calendarRepository.setSubtaskCompleted(it.id, true)
                }
            } else if (!markDone && task.subtasks.isNotEmpty() && task.subtasks.all { it.completed }) {
                calendarRepository.setSubtaskCompleted(task.subtasks.last().id, false)
            }
        }
    }

    // a med-evt id back into the date|medId|slot key the dose log uses. null for anything else
    private fun medicationLogKeyForEvent(eventId: String): String? {
        val prefix = com.muradgalayev.brainbuddy.data.repository
            .MedicationTodoSyncer.MEDICATION_EVENT_PREFIX
        if (!eventId.startsWith(prefix)) return null
        val rest = eventId.removePrefix(prefix)
        // the date is the last three dash-separated pieces, the slot the one before
        val parts = rest.split('-')
        if (parts.size < 5) return null
        val date = parts.takeLast(3).joinToString("-")
        val slot = parts[parts.size - 4]
        val medId = parts.dropLast(4).joinToString("-")
        if (medId.isBlank()) return null
        return "$date|$medId|$slot"
    }

    fun toggleSubtaskCompleted(subtaskId: String, completed: Boolean) {
        viewModelScope.launch {
            calendarRepository.setSubtaskCompleted(subtaskId, completed)
        }
    }

    // primes the Pomodoro timer from the event's subtasks when they look like a plan (at least
    // one FOCUS and one BREAK) and tells the caller to navigate
    fun launchInPomodoro(eventId: String): Boolean {
        val task = _uiState.value.tasksForSelectedDate.firstOrNull { it.id == eventId }
            ?: return false
        if (!task.subtasks.isPomodoroPlan()) return false
        // skip completed leading items so the user picks up where they left off
        val firstUnfinished = task.subtasks.indexOfFirst { !it.completed }.let {
            if (it < 0) 0 else it
        }
        val items = task.subtasks.map {
            PomodoroQueueItem(
                subtaskId = it.id,
                title = it.title,
                durationMs = it.durationMinutes * 60_000L,
                isFocus = it.kind == SubtaskKind.FOCUS,
            )
        }
        pomodoroTimerManager.loadCalendarQueue(
            PomodoroQueueState(
                eventId = task.id,
                eventTitle = task.title,
                items = items,
                currentIndex = firstUnfinished,
            )
        )
        return true
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            val event = calendarRepository.getEventById(taskId) ?: return@launch
            calendarRepository.deleteEvent(event)
        }
    }

    fun syncToGoogleCalendar() {
        if (_isSyncingToGoogle.value) return
        _isSyncingToGoogle.value = true
        viewModelScope.launch {
            val first = googleCalendarRepository.exportAllEvents()
            if (first !is ExportResult.NeedsGoogleSignIn) {
                publishSyncResult(first)
                _isSyncingToGoogle.value = false
                return@launch
            }
            try {
                when (val step = googleCalendarAuthClient.requestAuthorization()) {
                    is GoogleCalendarAuthClient.AuthorizationStep.AccessToken -> {
                        googleCalendarAuthClient.fetchAndStoreUserEmail(step.token)
                        // honour the user's cadence. this used to call schedulePeriodic() with no argument, which
                        // silently reset them to the 12h default (and cancelled nothing for MANUAL) every time
                        // Google was reconnected
                        calendarSyncScheduler.applyFrequency(
                            modeManager.effectiveCalendarSyncFrequencyNow()
                        )
                        publishSyncResult(googleCalendarRepository.exportAllEvents())
                        _isSyncingToGoogle.value = false
                    }
                    is GoogleCalendarAuthClient.AuthorizationStep.NeedsUserConsent -> {
                        _googleAuthRequest.value = step.pendingIntent
                    }
                }
            } catch (e: Exception) {
                _googleSyncMessage.value =
                    "Could not connect Google account: ${e.message ?: "unknown error"}"
                _isSyncingToGoogle.value = false
            }
        }
    }

    fun consumeGoogleAuthRequest() {
        _googleAuthRequest.value = null
    }

    fun onGoogleAuthResult(data: Intent?) {
        viewModelScope.launch {
            val token = googleCalendarAuthClient.extractFromActivityResult(data)
            if (token == null) {
                _googleSyncMessage.value = "Calendar connection cancelled"
                _isSyncingToGoogle.value = false
                return@launch
            }
            googleCalendarAuthClient.fetchAndStoreUserEmail(token)
            // same as above, respect the chosen cadence rather than forcing the default
            calendarSyncScheduler.applyFrequency(
                modeManager.effectiveCalendarSyncFrequencyNow()
            )
            publishSyncResult(googleCalendarRepository.exportAllEvents())
            _isSyncingToGoogle.value = false
        }
    }

    fun clearGoogleSyncMessage() {
        _googleSyncMessage.value = null
    }

    private fun publishSyncResult(result: ExportResult) {
        _googleSyncMessage.value = when (result) {
            is ExportResult.Success -> {
                val parts = mutableListOf<String>()
                if (result.pushed > 0) parts += "${result.pushed} added"
                if (result.alreadyExisted > 0) parts += "${result.alreadyExisted} already there"
                if (result.failed > 0) parts += "${result.failed} failed"
                val core = if (parts.isEmpty()) "Nothing to sync" else parts.joinToString(", ")
                val email = googleCalendarTokenStore.getLinkedEmail()
                if (email != null && parts.isNotEmpty()) "$core → $email" else core
            }
            ExportResult.NeedsGoogleSignIn ->
                "Connect a Google account to enable Calendar sync"
        }
    }

    private fun combineDateAndTime(date: LocalDate, hhmm: String): String {
        if (hhmm.isBlank()) return "${date}T00:00:00"
        val parts = hhmm.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
        val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
        return LocalDateTime.of(date, java.time.LocalTime.of(hour, minute)).toString()
            .let { if (it.length == 16) "$it:00" else it } // ensure trailing :00 seconds
    }

    private fun extractHourMinute(iso: String): String? {
        // ISO local datetime is like "2026-05-05T10:00" or "2026-05-05T10:00:00"
        val tIdx = iso.indexOf('T')
        if (tIdx < 0 || iso.length < tIdx + 6) return null
        return iso.substring(tIdx + 1, tIdx + 6)
    }

    // local-time ISO to epoch millis, so the UI can compare against now
    private fun parseEpochMillis(iso: String): Long? = runCatching {
        LocalDateTime.parse(iso.padIsoSeconds())
            .atZone(java.time.ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
    }.getOrNull()

    private fun computeTotalMinutes(start: String, end: String): Int {
        return runCatching {
            val s = LocalDateTime.parse(start.padIsoSeconds())
            val e = LocalDateTime.parse(end.padIsoSeconds())
            Duration.between(s, e).toMinutes().toInt().coerceAtLeast(0)
        }.getOrDefault(0)
    }

    private fun String.padIsoSeconds(): String =
        if (length == 16 && contains('T')) "$this:00" else this

    private fun CalendarEvent.toCalendarTaskUi(
        subtasks: List<CalendarSubtask>,
        authorNames: Map<String, String> = emptyMap(),
    ): CalendarTaskUi {
        val s = extractHourMinute(startTime)
        val e = extractHourMinute(endTime)
        val timeRange = if (s != null && e != null) "$s – $e" else null
        val total = computeTotalMinutes(startTime, endTime)

        return CalendarTaskUi(
            id = this.id,
            title = this.title,
            subtitle = this.description.ifEmpty { null },
            location = this.location.ifEmpty { null },
            link = this.link.ifEmpty { null },
            timeRange = timeRange,
            totalMinutes = total,
            endMillis = parseEpochMillis(endTime),
            accent = resolveEventColor(this.color).accent,
            completed = this.completed,
            flagged = false,
            addedByName = this.createdByOther?.let { authorNames[it] ?: "a connection" },
            isMedication = this.id.startsWith(
                com.muradgalayev.brainbuddy.data.repository
                    .MedicationTodoSyncer.MEDICATION_EVENT_PREFIX
            ),
            subtasks = subtasks
                .sortedBy { it.orderIndex }
                .map {
                    CalendarSubtaskUi(
                        id = it.id,
                        title = it.title,
                        durationMinutes = it.durationMinutes,
                        completed = it.completed,
                        kind = it.kind,
                    )
                },
        )
    }

    companion object {
        // calendar visits the AI intro animation plays for before stopping
        private const val AI_INTRO_PLAYS = 3

        // dots stop being countable past three at this cell size
        const val MAX_DAY_DOTS = 3

        // shortest title worth classifying. anything shorter is a mid-typing prefix, and 'di' would
        // flicker the chips through three activities before the user finishes the word
        private const val MIN_TITLE_CHARS_FOR_SUGGESTIONS = 3

        // how far past the selected day the suggester may go. two is enough for 'tomorrow, or the
        // day after', further out and it stops being about the event being written
        private const val SUGGESTION_LOOKAHEAD_DAYS = 2

        // assumed length of a to-do that has a start time but no end time
        private const val UNTIMED_TODO_BLOCK_MINUTES = 30
    }
}

enum class SplitPreset { HALVES, THIRDS, QUARTERS, POMODORO }

// a plan is any subtask list with at least one FOCUS and at least one BREAK. strict
// alternation isn't required, users can add custom stations after applying the preset
fun List<CalendarSubtaskUi>.isPomodoroPlan(): Boolean {
    if (size < 2) return false
    var hasFocus = false
    var hasBreak = false
    for (s in this) {
        when (s.kind) {
            SubtaskKind.FOCUS -> hasFocus = true
            SubtaskKind.BREAK -> hasBreak = true
        }
        if (hasFocus && hasBreak) return true
    }
    return false
}
