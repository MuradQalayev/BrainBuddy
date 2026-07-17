package com.muradgalayev.brainbuddy.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.PlacesRepository
import com.muradgalayev.brainbuddy.domain.model.City
import com.muradgalayev.brainbuddy.domain.model.Place
import com.muradgalayev.brainbuddy.domain.model.PlaceCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CareNearbyState(
    val isLoading: Boolean = true,
    val cities: List<City> = emptyList(),
    val selectedCityId: String? = null,
    val places: List<Place> = emptyList(),
    val activeCategories: Set<PlaceCategory> = emptySet(),
    val medicationName: String = "",
    val errorMessage: String? = null,
)

@HiltViewModel
class CareNearbyViewModel @Inject constructor(
    private val placesRepository: PlacesRepository,
    private val adhdProfileRepository: AdhdProfileRepository,
) : ViewModel() {

    // Synchronous initial state — if caches are warm, the screen paints
    // fully populated on its first frame (no loading flash).
    private val _state = MutableStateFlow(buildInitialState())
    val state: StateFlow<CareNearbyState> = _state.asStateFlow()

    init {
        // If we couldn't satisfy the screen from cache, hit the network.
        // Otherwise we trust the cached snapshot until the user pulls Refresh.
        if (_state.value.cities.isEmpty()) {
            load(forceRefresh = false)
        }
    }

    private fun buildInitialState(): CareNearbyState {
        val cachedCities = placesRepository.peekCities() ?: return CareNearbyState(isLoading = true)
        val cachedProfile = adhdProfileRepository.peekProfile()
        val initialCity = cachedProfile?.cityId?.takeIf { id -> cachedCities.any { it.id == id } }
            ?: cachedCities.firstOrNull()?.id
        val cachedPlaces = initialCity?.let { placesRepository.peekPlaces(it) }.orEmpty()
        return CareNearbyState(
            isLoading = false,
            cities = cachedCities,
            selectedCityId = initialCity,
            places = cachedPlaces,
            medicationName = formatMeds(cachedProfile?.medications),
        )
    }

    private fun formatMeds(meds: List<com.muradgalayev.brainbuddy.domain.model.Medication>?): String {
        if (meds.isNullOrEmpty()) return ""
        return meds.filter { it.name.isNotBlank() }.joinToString(", ") { m ->
            if (m.dose.isBlank()) m.name else "${m.name} ${m.dose}"
        }
    }

    /** User-triggered refresh: skips the cache and re-hits Supabase. */
    fun refresh() {
        placesRepository.invalidateCache()
        load(forceRefresh = true)
    }

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            // Only show the spinner when we'd otherwise show empty content.
            val showSpinner = forceRefresh ||
                (_state.value.cities.isEmpty() && !placesRepository.hasCachedCities())
            if (showSpinner) {
                _state.update { it.copy(isLoading = true, errorMessage = null) }
            } else {
                _state.update { it.copy(errorMessage = null) }
            }

            // Cities and the ADHD profile live in different Supabase tables and don't
            // depend on each other — run them in parallel so this screen isn't stuck
            // behind two sequential round-trips.
            val citiesDeferred = async { placesRepository.listCities(forceRefresh) }
            val profileDeferred = async { adhdProfileRepository.getProfile() }
            val citiesResult = citiesDeferred.await()
            val profile = profileDeferred.await()

            citiesResult
                .onSuccess { cities ->
                    val initialCity = _state.value.selectedCityId?.takeIf { id -> cities.any { it.id == id } }
                        ?: profile?.cityId?.takeIf { id -> cities.any { it.id == id } }
                        ?: cities.firstOrNull()?.id

                    _state.update {
                        it.copy(
                            cities = cities,
                            selectedCityId = initialCity,
                            medicationName = formatMeds(profile?.medications),
                            errorMessage = null,
                        )
                    }
                    if (initialCity != null) {
                        loadPlaces(initialCity, forceRefresh)
                    } else {
                        _state.update { it.copy(isLoading = false, places = emptyList()) }
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            medicationName = formatMeds(profile?.medications),
                            errorMessage = e.message ?: "Couldn't load cities",
                        )
                    }
                }
        }
    }

    fun selectCity(cityId: String) {
        if (cityId == _state.value.selectedCityId) return
        // Use cached places for the new city if we have them, so the list
        // swaps instantly without a spinner.
        val cachedPlaces = placesRepository.peekPlaces(cityId)
        _state.update {
            it.copy(
                selectedCityId = cityId,
                places = cachedPlaces.orEmpty(),
            )
        }
        loadPlaces(cityId, forceRefresh = false)
    }

    fun toggleCategory(category: PlaceCategory) {
        _state.update {
            val newSet = if (category in it.activeCategories) it.activeCategories - category
            else it.activeCategories + category
            it.copy(activeCategories = newSet)
        }
    }

    fun clearCategories() {
        _state.update { it.copy(activeCategories = emptySet()) }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun loadPlaces(cityId: String, forceRefresh: Boolean) {
        val showSpinner = forceRefresh ||
            (!placesRepository.hasCachedPlaces(cityId) && _state.value.places.isEmpty())
        if (showSpinner) {
            _state.update { it.copy(isLoading = true) }
        }
        viewModelScope.launch {
            placesRepository.listPlacesInCity(cityId, forceRefresh)
                .onSuccess { places ->
                    if (cityId == _state.value.selectedCityId) {
                        _state.update {
                            it.copy(places = places, isLoading = false, errorMessage = null)
                        }
                    }
                }
                .onFailure { e ->
                    if (cityId == _state.value.selectedCityId) {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = e.message ?: "Couldn't load places",
                            )
                        }
                    }
                }
        }
    }
}
