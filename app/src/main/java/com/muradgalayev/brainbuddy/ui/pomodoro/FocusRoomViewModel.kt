package com.muradgalayev.brainbuddy.ui.pomodoro

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.local.PomodoroTimerManager
import com.muradgalayev.brainbuddy.data.repository.FocusPhase
import com.muradgalayev.brainbuddy.data.repository.FocusRoomMember
import com.muradgalayev.brainbuddy.data.repository.FocusSession
import com.muradgalayev.brainbuddy.data.repository.FocusInviteWatcher
import com.muradgalayev.brainbuddy.data.repository.FocusTogetherRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// how often the room re-reads who's in it. both people are watching this screen
private const val ROOM_POLL_MS = 2_500L

data class FocusRoomUiState(
    val session: FocusSession? = null,
    val members: List<FocusRoomMember> = emptyList(),
    val loading: Boolean = true,
    // set once the room is over, so the screen can close itself
    val closed: Boolean = false,
) {
    val everyoneReady: Boolean
        get() = members.isNotEmpty() && members.all { it.isReady }

    val readyCount: Int get() = members.count { it.isReady }

    val iAmReady: Boolean
        get() = session?.myState == com.muradgalayev.brainbuddy.data.repository
            .FocusParticipantState.JOINED

    val isRunning: Boolean get() = session?.isRunning == true

    val onBreak: Boolean
        get() = session?.phase == com.muradgalayev.brainbuddy.data.repository.FocusPhase.BREAK
}

// the waiting room, and then the shared session. polls faster than the app-wide watcher: this
// is the one screen where both people are staring at it waiting for the other to tap, and a
// two-second lag there reads as the other person not having done it yet
@HiltViewModel
class FocusRoomViewModel @Inject constructor(
    private val repository: FocusTogetherRepository,
    private val watcher: FocusInviteWatcher,
    private val timerManager: PomodoroTimerManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FocusRoomUiState())
    val uiState: StateFlow<FocusRoomUiState> = _uiState.asStateFlow()

    private var sessionId: String? = null

    // the phase whose timer we've already started, so re-polling doesn't restart it. keyed on
    // session and phase, because a break is a second start within the same session and would
    // otherwise be swallowed
    private var startedFor: String? = null

    fun open(sessionId: String) {
        if (this.sessionId == sessionId) return
        this.sessionId = sessionId
        watcher.clearNotification(sessionId)

        viewModelScope.launch {
            while (true) {
                refresh(sessionId)
                delay(ROOM_POLL_MS)
            }
        }
    }

    private suspend fun refresh(sessionId: String) {
        val session = repository.listSessions().firstOrNull { it.id == sessionId }
        val members = repository.listRoom(sessionId)

        // gone from the list means it ended, was cancelled, or expired. either way there is no room
        // left to sit in
        if (session == null) {
            _uiState.update { it.copy(loading = false, closed = true) }
            return
        }

        _uiState.update { state ->
            if (state.session == session && state.members == members) state.copy(loading = false)
            else state.copy(session = session, members = members, loading = false)
        }

        // the clock ran out. whoever notices first tells the server to move on, and it ignores anyone
        // early or late, so no client has to be the designated one
        if (session.isRunning && session.secondsRemaining <= 0) {
            repository.advancePhase(sessionId)
            return
        }

        // everyone tapped. start the local timer anchored to the server's clock, see the migration for
        // why the number comes from Postgres rather than from this device
        val phaseKey = "${session.id}:${session.phase}"
        if (session.isRunning && startedFor != phaseKey) {
            startedFor = phaseKey
            timerManager.startSharedSession(
                totalMinutes = when (session.phase) {
                    FocusPhase.FOCUS -> session.focusMinutes
                    FocusPhase.BREAK -> session.breakMinutes
                },
                remainingMs = session.secondsRemaining * 1000L,
                isBreak = session.phase == FocusPhase.BREAK,
            )
        }
    }

    // tap to say you're ready. the last person to do this starts it for everyone
    fun markReady() {
        val id = sessionId ?: return
        viewModelScope.launch {
            repository.join(id)
            refresh(id)
        }
    }

    // leaves without ending it for anyone else. the host's own session ends through
    // FocusTogetherRepository.endSession, not this
    fun leave() {
        val id = sessionId ?: return
        _uiState.update { it.copy(closed = true) }

        // stop the clock too. leaving only told the server, so the local timer carried on as an
        // orphaned solo session: you'd back out of a session you had just quit and find it still
        // counting down on the Focus Timer, with nothing behind it to explain where it came from
        timerManager.stop()

        viewModelScope.launch {
            repository.leave(id)
            watcher.refreshNow()
        }
    }
}
