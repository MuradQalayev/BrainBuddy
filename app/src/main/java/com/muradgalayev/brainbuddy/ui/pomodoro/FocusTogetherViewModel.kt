package com.muradgalayev.brainbuddy.ui.pomodoro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.local.PomodoroTimerManager
import com.muradgalayev.brainbuddy.data.repository.FocusInviteWatcher
import com.muradgalayev.brainbuddy.data.local.TimerState
import com.muradgalayev.brainbuddy.data.repository.FocusSession
import com.muradgalayev.brainbuddy.data.repository.FocusTogetherRepository
import com.muradgalayev.brainbuddy.data.repository.TogetherRepository
import com.muradgalayev.brainbuddy.domain.model.Connection
import com.muradgalayev.brainbuddy.domain.model.ShareScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// a connection as the picker sees them: invitable, or there but not yet allowed
data class FocusCandidate(
    val userId: String,
    val name: String,
    val avatarUrl: String?,
    // false when they haven't switched on Focus together for you
    val allowed: Boolean,
)

data class FocusTogetherUiState(
    // every connection, allowed or not. the ones who haven't granted FOCUS are greyed rather than
    // hidden: hiding them made the feature look broken to the person who had done everything right
    // on their own side, they add a friend, come here, and see nothing, with no way to learn that
    // the other person has a switch to flip
    val candidates: List<FocusCandidate> = emptyList(),
    val invites: List<FocusSession> = emptyList(),
    val waitingSession: FocusSession? = null,
    val activeSession: FocusSession? = null,
    val sending: Boolean = false,
    val error: String? = null,
    // set once for a session we just created, so the caller can walk into the room. consumed
    // rather than left set, or coming back here later would send you into a room you already left
    val createdSessionId: String? = null,
) {
    val hasConnections: Boolean get() = candidates.isNotEmpty()
    val canInvite: Boolean get() = candidates.any { it.allowed }
}

@HiltViewModel
class FocusTogetherViewModel @Inject constructor(
    private val focusRepository: FocusTogetherRepository,
    private val togetherRepository: TogetherRepository,
    private val timerManager: PomodoroTimerManager,
    private val watcher: FocusInviteWatcher,
) : ViewModel() {

    // the session whose timer we already started, so a later poll can't restart it
    private var startedFor: String? = null

    private val _uiState = MutableStateFlow(FocusTogetherUiState())
    val uiState: StateFlow<FocusTogetherUiState> = _uiState.asStateFlow()

    init {
        loadInvitable()
        // the watcher owns the polling now, so invites and the badge stay in step across screens and
        // this view-model just reacts to what it reports
        viewModelScope.launch {
            watcher.sessions.collect { sessions -> applySessions(sessions) }
        }
    }

    // who can be invited: connections who granted me FOCUS. grantedToMe, not grantedByMe, because
    // what matters is whether they decided I may ask, not whether I opened my focus to them.
    // getting this backwards is how you build a feature that pings anyone you've ever added
    private fun loadInvitable() {
        viewModelScope.launch {
            runCatching { togetherRepository.refresh() }
            togetherRepository.connections.collect { connections ->
                val next = connections.map { c ->
                    FocusCandidate(
                        userId = c.userId,
                        name = c.name,
                        avatarUrl = c.avatarUrl,
                        allowed = ShareScope.FOCUS in c.grantedToMe,
                    )
                }.sortedByDescending { it.allowed }
                _uiState.update { state ->
                    if (state.candidates == next) state else state.copy(candidates = next)
                }
            }
        }
    }

    fun refresh() = watcher.refreshNow()

    private fun applySessions(sessions: List<FocusSession>) {
        run {
            val invites = sessions.filter { it.isInvite }
            val waiting = sessions.firstOrNull { it.isWaitingForOthers }
            val active = sessions.firstOrNull { it.isRunning }

            _uiState.update { state ->
                // equality-guarded: this runs every twenty seconds and an unchanged answer must not restart
                // the card's entrance animation
                if (state.invites == invites &&
                    state.waitingSession == waiting &&
                    state.activeSession == active
                ) state
                else state.copy(invites = invites, waitingSession = waiting, activeSession = active)
            }

            // the moment everyone is in. both devices see this on their own poll, so both timers start
            // within a poll interval of each other without either side telling the other anything
            if (active != null && startedFor != active.id) {
                startedFor = active.id
                // anchored to the server's end instant rather than started fresh: both devices land on the
                // same remaining time however far apart their polls were. see startSharedSession
                val remainingMs = active.secondsRemaining * 1000L
                viewModelScope.launch {
                    timerManager.startSharedSession(active.focusMinutes, remainingMs)
                }
            }
            if (active == null) startedFor = null
        }
    }

    fun invite(connectionUserIds: List<String>, focusMinutes: Int) {
        if (connectionUserIds.isEmpty()) return
        _uiState.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            // the host's own break length travels with the invite, so a 50/10 person doesn't get handed a
            // five-minute break by a default
            val breakMinutes = runCatching {
                timerManager.refreshEffectiveSettingsNow().breakMinutes
            }.getOrDefault(PomodoroTimerManager.DEFAULT_BREAK_MINUTES)

            focusRepository.createSession(connectionUserIds, focusMinutes, breakMinutes)
                .onSuccess { id -> _uiState.update { it.copy(createdSessionId = id) } }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(error = "Couldn't send that — ${e.message ?: "try again"}.")
                    }
                }
            _uiState.update { it.copy(sending = false) }
            refresh()
        }
    }

    // says yes, and does not start the timer. under mutual start the session is still PENDING
    // until everyone has tapped, so starting here would leave one person running while the other
    // is still deciding, which is the exact failure this design removes. refresh() starts the
    // timer when the server reports ACTIVE, which happens for both devices on the same transition
    fun join(sessionId: String) {
        watcher.clearNotification(sessionId)
        viewModelScope.launch {
            focusRepository.join(sessionId)
            refresh()
        }
    }

    fun decline(sessionId: String) {
        // removed locally first. a declined invite lingering for up to twenty seconds reads as the
        // tap not registering, and the retap declines a session that no longer exists
        _uiState.update { it.copy(invites = it.invites.filterNot { i -> i.id == sessionId }) }
        watcher.clearNotification(sessionId)
        viewModelScope.launch {
            focusRepository.decline(sessionId)
            refresh()
        }
    }

    fun leave(sessionId: String) {
        _uiState.update { it.copy(activeSession = null) }
        viewModelScope.launch {
            focusRepository.leave(sessionId)
            refresh()
        }
    }

    fun consumeCreatedSession() {
        _uiState.update { it.copy(createdSessionId = null) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
