package com.muradgalayev.brainbuddy.ui.together

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import com.muradgalayev.brainbuddy.data.repository.TogetherRepository
import com.muradgalayev.brainbuddy.data.remote.dto.CalendarEventDto
import com.muradgalayev.brainbuddy.data.remote.dto.SharedWellnessDto
import com.muradgalayev.brainbuddy.data.remote.dto.TodoItemDto
import com.muradgalayev.brainbuddy.domain.model.Connection
import com.muradgalayev.brainbuddy.domain.model.ShareScope
import com.muradgalayev.brainbuddy.domain.scheduling.BusyInterval
import com.muradgalayev.brainbuddy.domain.scheduling.DayContext
import com.muradgalayev.brainbuddy.domain.scheduling.SleepWindow
import com.muradgalayev.brainbuddy.domain.scheduling.TimeSuggestion
import com.muradgalayev.brainbuddy.domain.scheduling.TimeSuggestionEngine
import com.muradgalayev.brainbuddy.domain.scheduling.toBusyIntervalOrNull
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import javax.inject.Inject
import com.muradgalayev.brainbuddy.R

// what the user is composing when adding something to a connection's calendar or list
data class ComposeItemDraft(
    val title: String = "",
    val description: String = "",
    val date: LocalDate = LocalDate.now(),
    val startTime: LocalTime = LocalTime.of(9, 0),
    val endTime: LocalTime = LocalTime.of(10, 0),
    val saving: Boolean = false,
    val error: String? = null,
) {
    val canSave: Boolean get() = title.isNotBlank() && !saving
}

@HiltViewModel
class ConnectionProfileViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val repository: TogetherRepository,
    private val calendarRepository: CalendarRepository,
    private val todoRepository: TodoRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val userId: String = savedStateHandle.get<String>("userId").orEmpty()

    // tracks the connection inside the repository's list rather than snapshotting it, so a
    // permission toggle re-renders from the refreshed source and the screen can't drift out of
    // step with what the server actually stored
    val connection: StateFlow<Connection?> = repository.connections
        .map { list -> list.firstOrNull { it.userId == userId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, repository.connection(userId))

    private val _busyScopes = MutableStateFlow<Set<ShareScope>>(emptySet())
    val busyScopes: StateFlow<Set<ShareScope>> = _busyScopes.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    // only ever the items I authored for them, RLS makes the wider read impossible
    private val _myEventsForThem = MutableStateFlow<List<CalendarEventDto>>(emptyList())
    val myEventsForThem: StateFlow<List<CalendarEventDto>> = _myEventsForThem.asStateFlow()

    private val _myTodosForThem = MutableStateFlow<List<TodoItemDto>>(emptyList())
    val myTodosForThem: StateFlow<List<TodoItemDto>> = _myTodosForThem.asStateFlow()

    private val _wellness = MutableStateFlow<SharedWellnessDto?>(null)
    val wellness: StateFlow<SharedWellnessDto?> = _wellness.asStateFlow()

    private val _eventDraft = MutableStateFlow<ComposeItemDraft?>(null)
    val eventDraft: StateFlow<ComposeItemDraft?> = _eventDraft.asStateFlow()

    private val _todoDraft = MutableStateFlow<ComposeItemDraft?>(null)
    val todoDraft: StateFlow<ComposeItemDraft?> = _todoDraft.asStateFlow()

    private val _removed = MutableStateFlow(false)
    val removed: StateFlow<Boolean> = _removed.asStateFlow()

    // their busy hours, or null when we don't know them: not granted, offline, or revoked since
    // the screen opened. declared up here with the rest of the state because init writes to it
    private val _theirBusy = MutableStateFlow<Map<LocalDate, List<BusyInterval>>?>(null)

    // my own commitments over the same window, so I don't double-book myself either
    private val _myBusy = MutableStateFlow<Map<LocalDate, List<BusyInterval>>>(emptyMap())

    init {
        loadSharedContent()
        observeMyOwnDays()
    }

    fun consumeMessage() {
        _message.value = null
    }

    // loads what they've shared with me. each read is attempted unconditionally rather than gated
    // on the locally-known grant: the server is the authority, and a scope revoked elsewhere
    // should show as empty here immediately
    fun loadSharedContent() {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            repository.eventsICreatedFor(userId).onSuccess { _myEventsForThem.value = it }
            repository.todosICreatedFor(userId).onSuccess { _myTodosForThem.value = it }
            repository.wellnessSharedWithMe(userId)
                .onSuccess { _wellness.value = it }
                .onFailure { _wellness.value = null }
            val from = LocalDate.now()
            repository.availabilityFor(userId, from, from.plusDays(AVAILABILITY_WINDOW_DAYS))
                // failure is not 'they're free', it's 'we don't know', and the two have to stay distinguishable
                // all the way to the UI. null suppresses the suggestions rather than proposing into the dark
                .onSuccess { _theirBusy.value = it }
                .onFailure { _theirBusy.value = null }
        }
    }

    fun togglePermission(scope: ShareScope, granted: Boolean) {
        // a select all is still settling this one
        if (userId.isEmpty() || scope in _optimisticGrants.value) return
        viewModelScope.launch {
            _busyScopes.value = _busyScopes.value + scope
            repository.setPermission(userId, scope, granted)
                .onSuccess {
                    _message.value = if (granted) {
                        context.getString(R.string.together_scope_shared, context.getString(scope.labelRes))
                    } else {
                        context.getString(R.string.together_scope_off, context.getString(scope.labelRes))
                    }
                }
                .onFailure { _message.value = it.friendlyMessage(context, R.string.together_permission_failed) }
            _busyScopes.value = _busyScopes.value - scope
        }
    }

    // switches flipped ahead of the server by select all. the screen reads these first, so every switch
    // moves at once instead of each one waiting for its own round trip
    private val _optimisticGrants = MutableStateFlow<Map<ShareScope, Boolean>>(emptyMap())
    val optimisticGrants: StateFlow<Map<ShareScope, Boolean>> = _optimisticGrants.asStateFlow()

    // every scope in one go: the switches move straight away and the grants go out together in the
    // background. only the ones that actually change are sent
    fun setAllPermissions(granted: Boolean) {
        if (userId.isEmpty() || _busyScopes.value.isNotEmpty() || _optimisticGrants.value.isNotEmpty()) return
        val person = connection.value ?: return
        val changing = ShareScope.entries.filter { person.canGrant(it) != granted }
        if (changing.isEmpty()) return
        _optimisticGrants.value = changing.associateWith { granted }
        viewModelScope.launch {
            val failed = changing
                .map { scope -> async { repository.setPermission(userId, scope, granted).isFailure } }
                .awaitAll()
                .any { it }
            // each grant refreshes on its own and those can land out of order, so settle on one last read
            // before letting go. a grant that failed then simply shows its real, unchanged state
            repository.refresh()
            _optimisticGrants.value = emptyMap()
            _message.value = when {
                failed -> context.getString(R.string.together_permission_failed)
                granted -> context.getString(R.string.together_all_shared, person.name)
                else -> context.getString(R.string.together_all_off, person.name)
            }
        }
    }

    fun removeConnection() {
        if (userId.isEmpty()) return
        viewModelScope.launch {
            repository.removeConnection(userId)
                .onSuccess { _removed.value = true }
                .onFailure { _message.value = it.friendlyMessage(context, R.string.together_remove_conn_failed) }
        }
    }

    // adding an event to their calendar

    fun startEventDraft() {
        _eventDraft.value = ComposeItemDraft()
    }

    fun cancelEventDraft() {
        _eventDraft.value = null
    }

    fun updateEventDraft(transform: (ComposeItemDraft) -> ComposeItemDraft) {
        _eventDraft.value = _eventDraft.value?.let(transform)?.copy(error = null)
    }

    fun saveEvent() {
        val draft = _eventDraft.value ?: return
        if (!draft.canSave) return
        viewModelScope.launch {
            _eventDraft.value = draft.copy(saving = true, error = null)
            repository.createEventFor(
                ownerId = userId,
                title = draft.title.trim(),
                description = draft.description.trim(),
                startTime = LocalDateTime.of(draft.date, draft.startTime),
                endTime = LocalDateTime.of(draft.date, normaliseEnd(draft)),
            )
                .onSuccess {
                    _eventDraft.value = null
                    _message.value = context.getString(R.string.together_added_calendar)
                    loadSharedContent()
                }
                .onFailure { error ->
                    _eventDraft.value = _eventDraft.value?.copy(
                        saving = false,
                        // a rejection here usually means the grant was pulled while the sheet was open, so say that
                        error = error.friendlyMessage(
                            context, R.string.together_add_calendar_failed,
                        ),
                    )
                }
        }
    }

    // suggested times, and the point of the AVAILABILITY scope. their day arrives as opaque blocks,
    // hours with nothing attached, and is unioned with the asker's own day, so a proposed slot is
    // one that is genuinely open at both ends. nothing about the content of either calendar is
    // involved in, or recoverable from, the result

    // true once their free/busy has actually arrived, drives the note in the sheet
    val usingTheirAvailability: StateFlow<Boolean> = _theirBusy
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // what the draft is asking about, split out so the flag fields don't re-trigger
    private data class SuggestionKey(
        val title: String,
        val description: String,
        val date: LocalDate,
    )

    @OptIn(FlowPreview::class)
    val eventSuggestions: StateFlow<List<TimeSuggestion>> = combine(
        _eventDraft
            .map { draft -> draft?.let { SuggestionKey(it.title, it.description, it.date) } }
            .distinctUntilChanged()
            // long enough that a word being typed doesn't churn, short enough that the chips are there by
            // the time the user's eyes reach the time fields
            .debounce(220),
        _myBusy,
        _theirBusy,
    ) { key, mine, theirs ->
        computeSuggestions(key, mine, theirs)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

    // proposes times for an event on their calendar. three things the personal suggester uses are
    // deliberately absent, and each omission is the honest answer rather than a gap.
    // no habit history: the learned timings on this device are mine, and placing their dinner at
    // when I eat, under a chip reading 'your usual dinner time', would be a confident claim about
    // the wrong person. an empty lookup falls back to the built-in priors for the kind of
    // activity, which is a statement about dinners in general and therefore true.
    // no chronotype and no energy: my survey answer and my step count say nothing about their day,
    // and theirs are none of my business.
    // a neutral waking day rather than my sleep window, so a suggestion for a night owl isn't cut
    // off at my bedtime.
    // returns nothing at all unless their availability is known. suggesting around only my
    // calendar, for an event on theirs, would be a nudge with no basis, and the user would have
    // no way to see that it had none
    private fun computeSuggestions(
        key: SuggestionKey?,
        mine: Map<LocalDate, List<BusyInterval>>,
        theirs: Map<LocalDate, List<BusyInterval>>?,
    ): List<TimeSuggestion> {
        if (key == null || theirs == null) return emptyList()
        // below this the title is still being typed, and 'din' is not dinner yet
        if (key.title.trim().length < MIN_TITLE_CHARS_FOR_SUGGESTIONS) return emptyList()

        val now = LocalDateTime.now()
        fun contextFor(day: LocalDate) = DayContext(
            date = day,
            now = now,
            busy = mine[day].orEmpty() + theirs[day].orEmpty(),
            sleep = SleepWindow.DEFAULT,
            productiveTime = null,
            energy = null,
        )

        return TimeSuggestionEngine.suggestAcrossDays(
            title = key.title,
            description = key.description,
            selectedDay = contextFor(key.date),
            laterDays = (1..SUGGESTION_LOOKAHEAD_DAYS)
                .map { contextFor(key.date.plusDays(it.toLong())) },
            lookup = { _, _ -> null },
        )
    }

    // moves the draft onto a proposed slot, date included
    fun applySuggestion(suggestion: TimeSuggestion) {
        updateEventDraft {
            it.copy(
                date = suggestion.date,
                startTime = suggestion.startMinutes.asLocalTime(),
                endTime = suggestion.endMinutes.asLocalTime(),
            )
        }
    }

    // minute-of-day to a wall clock, with 24:00 landing on 23:59 rather than throwing
    private fun Int.asLocalTime(): LocalTime =
        if (this >= 24 * 60) LocalTime.of(23, 59) else LocalTime.of(this / 60, this % 60)

    // my calendar and timed to-dos across the same window their availability covers. read from
    // Room, so it's there before any network call returns
    private fun observeMyOwnDays() {
        val from = LocalDate.now()
        val to = from.plusDays(AVAILABILITY_WINDOW_DAYS)
        viewModelScope.launch {
            combine(
                calendarRepository.getEventsInDateRange(from.toString(), to.toString()),
                todoRepository.getTodoItemsInRange(from.toString(), to.toString()),
            ) { events, todos ->
                val eventBlocks = events.mapNotNull { event ->
                    val date = runCatching { LocalDate.parse(event.startTime.take(10)) }.getOrNull()
                    val block = event.toBusyIntervalOrNull()
                    if (date != null && block != null) date to block else null
                }
                val todoBlocks = todos.mapNotNull { todo ->
                    val date = runCatching { LocalDate.parse(todo.date) }.getOrNull()
                    val block = todo.toBusyIntervalOrNull()
                    if (date != null && block != null) date to block else null
                }
                (eventBlocks + todoBlocks).groupBy({ it.first }, { it.second })
            }.collect { _myBusy.value = it }
        }
    }

    // adding a task to their list

    fun startTodoDraft() {
        _todoDraft.value = ComposeItemDraft()
    }

    fun cancelTodoDraft() {
        _todoDraft.value = null
    }

    fun updateTodoDraft(transform: (ComposeItemDraft) -> ComposeItemDraft) {
        _todoDraft.value = _todoDraft.value?.let(transform)?.copy(error = null)
    }

    fun saveTodo() {
        val draft = _todoDraft.value ?: return
        if (!draft.canSave) return
        viewModelScope.launch {
            _todoDraft.value = draft.copy(saving = true, error = null)
            repository.createTodoFor(
                ownerId = userId,
                title = draft.title.trim(),
                description = draft.description.trim(),
                date = draft.date.toString(),
                startTime = draft.startTime.toString().take(5),
                endTime = normaliseEnd(draft).toString().take(5),
            )
                .onSuccess {
                    _todoDraft.value = null
                    _message.value = context.getString(R.string.together_added_list)
                    loadSharedContent()
                }
                .onFailure { error ->
                    _todoDraft.value = _todoDraft.value?.copy(
                        saving = false,
                        error = error.friendlyMessage(
                            context, R.string.together_add_list_failed,
                        ),
                    )
                }
        }
    }

    fun deleteEvent(eventId: String) {
        viewModelScope.launch {
            repository.deleteEventICreated(eventId)
                .onSuccess {
                    _message.value = context.getString(R.string.together_removed_calendar)
                    loadSharedContent()
                }
                .onFailure { _message.value = it.friendlyMessage(context, R.string.together_remove_event_failed) }
        }
    }

    fun deleteTodo(todoId: String) {
        viewModelScope.launch {
            repository.deleteTodoICreated(todoId)
                .onSuccess {
                    _message.value = context.getString(R.string.together_removed_list)
                    loadSharedContent()
                }
                .onFailure { _message.value = it.friendlyMessage(context, R.string.together_remove_task_failed) }
        }
    }

    // guards against an end time at or before the start, which the DB would happily store
    private fun normaliseEnd(draft: ComposeItemDraft): LocalTime =
        if (draft.endTime > draft.startTime) draft.endTime else draft.startTime.plusHours(1)

    private companion object {
        // how far ahead their free/busy is fetched. wide enough to cover every date the sheet can
        // reach, narrow enough that a scheduling aid doesn't become a standing feed of someone's month
        const val AVAILABILITY_WINDOW_DAYS = 14L

        const val MIN_TITLE_CHARS_FOR_SUGGESTIONS = 3

        // matches the personal suggester: today, plus a couple of days to fall back on
        const val SUGGESTION_LOOKAHEAD_DAYS = 2
    }
}
