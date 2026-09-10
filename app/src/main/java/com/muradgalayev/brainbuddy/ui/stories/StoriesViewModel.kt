package com.muradgalayev.brainbuddy.ui.stories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.repository.Story
import com.muradgalayev.brainbuddy.data.repository.StoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StoriesUiState(
    val stories: List<Story> = emptyList(),
    // true while the first load is in flight and we have nothing cached yet
    val loading: Boolean = false,
) {
    val hasStories: Boolean get() = stories.isNotEmpty()

    // drives the 'there's something new' look of the pull indicator
    val hasUnseen: Boolean get() = stories.any { !it.seen }

    // where opening should start: the first unseen story, or the beginning when everything has
    // been read. resuming at the first unseen one is what people already expect from every stories
    // UI they've used, and dropping them back at story one after they'd read three would read as
    // the app losing their place
    val startIndex: Int
        get() = stories.indexOfFirst { !it.seen }.takeIf { it >= 0 } ?: 0
}

@HiltViewModel
class StoriesViewModel @Inject constructor(
    private val storyRepository: StoryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoriesUiState())
    val uiState: StateFlow<StoriesUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    // reloads the feed. cheap and quiet, the home screen calls this on resume so a story published
    // while the app was backgrounded lights the indicator without the user doing anything
    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = it.stories.isEmpty()) }
            val stories = storyRepository.loadStories()
            _uiState.update { current ->
                // equality-guarded: an unchanged feed must not restart the indicator's animation on every resume
                if (current.stories == stories) current.copy(loading = false)
                else current.copy(stories = stories, loading = false)
            }
        }
    }

    // marks a story read, locally first. the local update is what stops the progress ring
    // flickering back to unseen while the network call is still going, and the server write is
    // best-effort behind it
    fun markSeen(storyId: String) {
        val alreadySeen = _uiState.value.stories.firstOrNull { it.id == storyId }?.seen == true
        if (alreadySeen) return

        _uiState.update { state ->
            state.copy(
                stories = state.stories.map {
                    if (it.id == storyId) it.copy(seen = true) else it
                }
            )
        }
        viewModelScope.launch { storyRepository.markSeen(storyId) }
    }
}
