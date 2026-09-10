package com.muradgalayev.brainbuddy.data.repository

import com.muradgalayev.brainbuddy.data.notifications.FocusInviteNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// two cadences, because the cost of a delay is wildly different in each. while something is in
// flight (an invite waiting on an answer, a session about to begin) seconds matter: both
// people are looking at the screen right now, and a lag there is the difference between 'we
// started together' and 'it's broken'. the rest of the time nothing is happening and a request
// every three seconds is battery paid for nothing.
// this does not fix first delivery. someone who isn't expecting an invite is on the idle
// cadence, so that is the floor on how fast they can hear about one. only push removes that
private const val IDLE_POLL_MS = 10_000L
private const val LIVE_POLL_MS = 3_000L

// how close to a phase boundary counts as watch-this-closely
private const val LIVE_WINDOW_SECONDS = 10

// the one place that knows what focus sessions are in flight. polling used to live in the
// Pomodoro screen's view-model, which meant invites only existed while that screen was open,
// so the notification couldn't fire for anyone who wasn't already looking at the right place,
// and no other screen could show that something was waiting. hoisting it to a singleton makes
// the answer app-wide: the workspace badge, the notification and the card all read one flow.
// polls for the life of the process. that's the cost of not having push, and when FCM lands
// this loop is the thing it replaces
@Singleton
class FocusInviteWatcher @Inject constructor(
    private val focusRepository: FocusTogetherRepository,
    private val notifier: FocusInviteNotifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _sessions = MutableStateFlow<List<FocusSession>>(emptyList())
    val sessions: StateFlow<List<FocusSession>> = _sessions.asStateFlow()

    // drives the red badge. invites awaiting this user's answer
    val pendingInviteCount: StateFlow<Int> = sessions
        .map { list -> list.count { it.isInvite } }
        .stateIn(scope, SharingStarted.Eagerly, 0)

    // ids already raised as a notification, so a poll can't repeat one
    private val announced = mutableSetOf<String>()

    init {
        scope.launch {
            while (true) {
                refresh()
                delay(if (somethingInFlight()) LIVE_POLL_MS else IDLE_POLL_MS)
            }
        }
    }

    // forced refresh after acting, so the UI doesn't wait out the interval
    fun refreshNow() {
        scope.launch { refresh() }
    }

    private suspend fun refresh() {
        val sessions = focusRepository.listSessions()

        // whoever notices the clock ran out moves the session on, focus into break and break into
        // ended. this lives in the app-wide watcher rather than in the room screen: both people are
        // encouraged to leave that screen while they work, and with the advance tied to it a session
        // nobody was looking at would sit at 0:00 for ever
        sessions.filter { it.isRunning && it.secondsRemaining <= 0 }
            .forEach { focusRepository.advancePhase(it.id) }

        sessions.filter { it.isInvite && announced.add(it.id) }
            .forEach { notifier.notifyInvite(it.hostName, it.focusMinutes, it.id) }

        // anything no longer inviting has had its notification dealt with, so drop it from the
        // seen-set and a later invite from the same person isn't swallowed
        val liveInviteIds = sessions.filter { it.isInvite }.map { it.id }.toSet()
        announced.retainAll(liveInviteIds)

        // equality-guarded: this fires every twenty seconds and an unchanged answer must not repaint
        if (_sessions.value != sessions) _sessions.value = sessions
    }

    // true while a session is waiting on somebody. both people are watching in this window, so
    // it's the one that earns a fast poll
    private fun somethingInFlight(): Boolean = _sessions.value.any {
        it.isInvite || it.isWaitingForOthers ||
            // a phase boundary is about to need someone to notice it
            (it.isRunning && it.secondsRemaining <= LIVE_WINDOW_SECONDS)
    }

    fun clearNotification(sessionId: String) {
        notifier.cancel(sessionId)
        announced.remove(sessionId)
    }
}
