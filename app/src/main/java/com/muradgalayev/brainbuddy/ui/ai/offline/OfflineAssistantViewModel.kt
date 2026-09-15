package com.muradgalayev.brainbuddy.ui.ai.offline

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.network.NetworkObserver
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.PlacesRepository
import com.muradgalayev.brainbuddy.domain.ai.offline.OfflineAction
import com.muradgalayev.brainbuddy.domain.ai.offline.OfflineActionCatalog
import com.muradgalayev.brainbuddy.domain.ai.offline.OfflineActionGroup
import com.muradgalayev.brainbuddy.domain.ai.offline.OfflineActionResult
import com.muradgalayev.brainbuddy.domain.ai.offline.OfflineActionRunner
import com.muradgalayev.brainbuddy.domain.ai.offline.OfflineOption
import com.muradgalayev.brainbuddy.domain.ai.offline.OfflineSlot
import com.muradgalayev.brainbuddy.domain.ai.offline.OfflineSlotOptions
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OfflineAssistantUiState(
    val suggestions: List<OfflineAction> = emptyList(),
    val browse: List<OfflineAction> = emptyList(),
    // null while browsing, set once an action has been picked
    val selected: OfflineAction? = null,
    val values: Map<String, String> = emptyMap(),
    val dayOptions: List<OfflineOption> = emptyList(),
    val timeOptions: List<OfflineOption> = emptyList(),
    val running: Boolean = false,
    val result: OfflineActionResult? = null,
) {
    // confirm stays disabled until every required slot has an answer
    val canConfirm: Boolean
        get() = selected != null && !running &&
            selected.slots.filterNot { it.optional }.all { !values[it.key].isNullOrBlank() }
}

// drives the offline assistant: which actions to offer, what the user has filled in, and the
// result of running one. holds no chat history and talks to no model. recently-used ordering
// lives in the catalog's scoring function, the one place that decides what suggested means,
// so swapping that for an on-device model later changes nothing here
@HiltViewModel
class OfflineAssistantViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val catalog: OfflineActionCatalog,
    private val runner: OfflineActionRunner,
    private val placesRepository: PlacesRepository,
    private val adhdProfileRepository: AdhdProfileRepository,
    networkObserver: NetworkObserver,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OfflineAssistantUiState())
    val uiState: StateFlow<OfflineAssistantUiState> = _uiState.asStateFlow()

    // so the panel can announce the moment the connection is back
    // NetworkObserver.isOnline is already hot and process-wide
    val isOnline: StateFlow<Boolean> = networkObserver.isOnline

    // session-scoped: what got used most recently floats to the top next time
    private val recentActionIds = ArrayDeque<String>()

    init {
        refreshSuggestions()
    }

    private fun refreshSuggestions() {
        val careAvailable = careDataCached()
        val suggestions = catalog.suggest(
            recentActionIds = recentActionIds.toList(),
            careAvailable = careAvailable,
        )
        val browse = catalog.actions.filter { action ->
            careAvailable || action.group != OfflineActionGroup.Care
        }
        _uiState.update { it.copy(suggestions = suggestions, browse = browse) }
    }

    // the care lookup reads a disk cache that's only populated once the user has actually opened
    // Care Nearby with a connection. offering the action with an empty cache would produce 'I
    // couldn't find anything', which reads as broken rather than not yet downloaded
    private fun careDataCached(): Boolean {
        val cityId = adhdProfileRepository.peekProfile()?.cityId
            ?: placesRepository.peekCities()?.firstOrNull()?.id
            ?: return false
        return placesRepository.hasCachedPlaces(cityId)
    }

    fun select(action: OfflineAction) {
        _uiState.update {
            it.copy(
                selected = action,
                // pre-fill every default so a one-slot action is a single tap from done, and nothing is ever
                // presented blank
                values = action.slots.mapNotNull { slot ->
                    when (slot) {
                        is OfflineSlot.Choice -> slot.defaultValue?.let { v -> slot.key to v }
                        // 'Today' is right far more often than not
                        is OfflineSlot.DayPick -> slot.key to dayOptions().first().value
                        else -> null
                    }
                }.toMap(),
                dayOptions = dayOptions(),
                timeOptions = OfflineSlotOptions.times(),
                result = null,
            )
        }
    }

    fun setValue(key: String, value: String) {
        _uiState.update { state ->
            // tapping the selected chip again clears it, the only way to undo an optional answer without
            // a separate 'none' chip in every row
            val next = if (state.values[key] == value) state.values - key
            else state.values + (key to value)
            state.copy(values = next)
        }
    }

    fun back() {
        _uiState.update { it.copy(selected = null, values = emptyMap(), result = null) }
    }

    fun confirm() {
        val state = _uiState.value
        val action = state.selected ?: return
        if (state.running) return
        _uiState.update { it.copy(running = true) }
        viewModelScope.launch {
            val result = runner.run(action, state.values)
            if (result is OfflineActionResult.Done) {
                recentActionIds.remove(action.id)
                recentActionIds.addFirst(action.id)
                while (recentActionIds.size > MAX_RECENTS) recentActionIds.removeLast()
            }
            _uiState.update { it.copy(running = false, result = result) }
        }
    }

    // back to the action list after a result, with suggestions re-ranked
    fun done() {
        refreshSuggestions()
        _uiState.update { it.copy(selected = null, values = emptyMap(), result = null) }
    }

    private companion object {
        const val MAX_RECENTS = 3
    }

    private fun dayOptions() = OfflineSlotOptions.days(
        todayLabel = context.getString(com.muradgalayev.brainbuddy.R.string.common_today),
        tomorrowLabel = context.getString(com.muradgalayev.brainbuddy.R.string.common_tomorrow),
    )
}
