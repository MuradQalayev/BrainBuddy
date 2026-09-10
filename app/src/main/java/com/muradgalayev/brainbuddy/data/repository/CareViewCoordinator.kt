package com.muradgalayev.brainbuddy.data.repository

import com.muradgalayev.brainbuddy.domain.model.PlaceCategory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// a pending 'show these results' request from the AI to the Care Nearby screen. when the
// assistant runs find_care_nearby and opens the screen, it stashes the exact matches here so
// the screen mirrors the chat instead of listing everything
data class CareViewRequest(
    val cityId: String? = null,
    val category: PlaceCategory? = null,
    val placeIds: Set<String> = emptySet(),
)

@Singleton
class CareViewCoordinator @Inject constructor() {

    private val _request = MutableStateFlow<CareViewRequest?>(null)
    val request: StateFlow<CareViewRequest?> = _request.asStateFlow()

    fun request(cityId: String?, category: PlaceCategory?, placeIds: Set<String>) {
        _request.value = CareViewRequest(cityId, category, placeIds)
    }

    // read-and-clear, so a filter applies once rather than on every later screen visit
    fun consume(): CareViewRequest? {
        val current = _request.value
        _request.value = null
        return current
    }
}
