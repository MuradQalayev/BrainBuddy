package com.muradgalayev.brainbuddy.domain.ai

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

// a one-way bus that lets domain-layer AI tools ask the UI to navigate. tools have no
// NavController reference, so NavigateTool emits a route here and the NavGraph collects it and
// drives the actual navigation. singleton, so the tool and the UI share one instance
@Singleton
class AiNavigator @Inject constructor() {

    // a small buffer so an emit from a suspend tool is never dropped even if the UI collector
    // isn't attached for a frame. replay=0 so re-subscribers don't re-navigate
    private val _commands = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val commands: SharedFlow<String> = _commands.asSharedFlow()

    // request navigation to a NavGraph route, e.g. 'calendar'
    fun navigateTo(route: String) {
        _commands.tryEmit(route)
    }

    // set when the assistant is handing the user to the calendar to finish adding an event
    // themselves. a latched flag rather than an event, because of the ordering: the request is
    // made before navigating, and CalendarViewModel doesn't exist until the screen composes a
    // moment later, so a SharedFlow emission would land with nobody listening and the dialog would
    // silently never open. the calendar clears it once it has acted
    private val _pendingCalendarAdd = MutableStateFlow(false)
    val pendingCalendarAdd: StateFlow<Boolean> = _pendingCalendarAdd.asStateFlow()

    fun requestCalendarAdd() {
        _pendingCalendarAdd.value = true
    }

    fun consumeCalendarAdd() {
        _pendingCalendarAdd.value = false
    }

    // routes the assistant may send the user to, keyed by friendly aliases
    companion object {
        val ROUTE_ALIASES: Map<String, String> = mapOf(
            "home" to "home",
            "workspace" to "activity",
            "activity" to "activity",
            "calendar" to "calendar",
            // same screen, but NavigateTool also latches the open-the-form flag
            "add_event" to "calendar",
            "add_meeting" to "calendar",
            "settings" to "settings",
            "myndora_ai" to "settings_ai",
            // kept so a model still holding the old name in context doesn't dead-end
            "brainbuddy_ai" to "settings_ai",
            "wellness_settings" to "settings_ai",
            "linked_devices" to "settings_linked_devices",
            "together" to "together",
            "myndora_together" to "together",
            "connections" to "together",
            "todo" to "todo",
            "todos" to "todo",
            "tasks" to "todo",
            "pomodoro" to "pomodoro",
            "focus" to "pomodoro",
            "timer" to "pomodoro",
            "care" to "care_nearby",
            "care_nearby" to "care_nearby",
            "healthcare" to "care_nearby",
            "clinics" to "care_nearby",
            "pharmacy" to "care_nearby",
        )
    }
}
