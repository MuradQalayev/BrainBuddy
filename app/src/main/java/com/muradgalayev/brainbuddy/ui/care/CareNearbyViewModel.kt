package com.muradgalayev.brainbuddy.ui.care

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.location.LocationCityResolver
import com.muradgalayev.brainbuddy.data.location.LocationFixOutcome
import com.muradgalayev.brainbuddy.data.network.NetworkObserver
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.CareViewCoordinator
import com.muradgalayev.brainbuddy.data.repository.CareViewRequest
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
    // device location once resolved, powers the map blue dot and the distance sorting
    val userLat: Double? = null,
    val userLng: Double? = null,
    val locationDenied: Boolean = false,
    // when the AI opened this screen with a filter, the exact places to show. empty = no AI filter
    val pinnedPlaceIds: Set<String> = emptySet(),
)

@HiltViewModel
class CareNearbyViewModel @Inject constructor(
    private val placesRepository: PlacesRepository,
    private val adhdProfileRepository: AdhdProfileRepository,
    private val locationResolver: LocationCityResolver,
    careViewCoordinator: CareViewCoordinator,
    networkObserver: NetworkObserver,
) : ViewModel() {

    // NetworkObserver.isOnline is already hot and process-wide
    val isOnline: StateFlow<Boolean> = networkObserver.isOnline

    // a pending 'show these results' request from the AI, applied once. consumed here so a later
    // unrelated visit to this screen isn't re-filtered
    private var pendingRequest: CareViewRequest? = careViewCoordinator.consume()

    // synchronous initial state: with warm caches the screen paints populated on its first frame
    private val _state = MutableStateFlow(buildInitialState())
    val state: StateFlow<CareNearbyState> = _state.asStateFlow()

    init {
        // if we couldn't satisfy the screen from cache, hit the network. otherwise trust the cached
        // snapshot until the user pulls Refresh
        val initialCityId = _state.value.selectedCityId
        if (_state.value.cities.isEmpty() ||
            (initialCityId != null && !placesRepository.hasCachedPlaces(initialCityId))
        ) {
            load(forceRefresh = false)
        }
    }

    private fun buildInitialState(): CareNearbyState {
        val cachedCities = placesRepository.peekCities() ?: return CareNearbyState(isLoading = true)
        val cachedProfile = adhdProfileRepository.peekProfile()
        val pending = pendingRequest
        val initialCity = cachedProfile?.cityId?.takeIf { id -> cachedCities.any { it.id == id } }
        val cachedPlaces = initialCity?.let { placesRepository.peekPlaces(it) }.orEmpty()
        val state = CareNearbyState(
            isLoading = initialCity != null && !placesRepository.hasCachedPlaces(initialCity),
            cities = cachedCities,
            selectedCityId = initialCity,
            places = cachedPlaces,
            medicationName = formatMeds(cachedProfile?.medications),
            activeCategories = pending?.category?.let { setOf(it) } ?: emptySet(),
            pinnedPlaceIds = pending?.placeIds ?: emptySet(),
        )
        // applied, so don't re-apply on a later network load or refresh
        if (pending != null) pendingRequest = null
        return state
    }

    private fun formatMeds(meds: List<com.muradgalayev.brainbuddy.domain.model.Medication>?): String {
        if (meds.isNullOrEmpty()) return ""
        return meds.filter { it.name.isNotBlank() }.joinToString(", ") { m ->
            if (m.doseLabel.isBlank()) m.name else "${m.name} ${m.doseLabel}"
        }
    }

    // user-triggered refresh: skips the cache and re-hits Supabase
    fun refresh() {
        load(forceRefresh = true)
    }

    private fun load(forceRefresh: Boolean) {
        viewModelScope.launch {
            // only show the spinner when we'd otherwise show empty content
            val showSpinner = forceRefresh ||
                (_state.value.cities.isEmpty() && !placesRepository.hasCachedCities())
            if (showSpinner) {
                _state.update { it.copy(isLoading = true, errorMessage = null) }
            } else {
                _state.update { it.copy(errorMessage = null) }
            }

            // cities and the ADHD profile live in different tables and don't depend on each other, so run
            // them in parallel rather than stacking two sequential round-trips
            val citiesDeferred = async { placesRepository.listCities(forceRefresh) }
            val profileDeferred = async { adhdProfileRepository.getProfile() }
            val citiesResult = citiesDeferred.await()
            val profile = profileDeferred.await()

            citiesResult
                .onSuccess { cities ->
                    val pending = pendingRequest
                    // healthcare is scoped to the city saved in the user's profile, and can't be changed here
                    val initialCity = profile?.cityId?.takeIf { id -> cities.any { it.id == id } }

                    _state.update {
                        it.copy(
                            cities = cities,
                            selectedCityId = initialCity,
                            medicationName = formatMeds(profile?.medications),
                            activeCategories = pending?.category?.let { c -> setOf(c) }
                                ?: it.activeCategories,
                            pinnedPlaceIds = pending?.placeIds ?: it.pinnedPlaceIds,
                            errorMessage = null,
                        )
                    }
                    if (pending != null) pendingRequest = null
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

    fun toggleCategory(category: PlaceCategory) {
        _state.update {
            val newSet = if (category in it.activeCategories) it.activeCategories - category
            else it.activeCategories + category
            // the user is driving the filter now, so drop any AI pin and let their choice win
            it.copy(activeCategories = newSet, pinnedPlaceIds = emptySet())
        }
    }

    fun clearCategories() {
        _state.update { it.copy(activeCategories = emptySet(), pinnedPlaceIds = emptySet()) }
    }

    // drops just the AI-applied result filter, keeping the category chips as they are
    fun clearAiFilter() {
        _state.update { it.copy(pinnedPlaceIds = emptySet()) }
    }

    fun dismissError() {
        _state.update { it.copy(errorMessage = null) }
    }

    // true if the app already holds location permission, so no prompt is needed
    fun hasLocationPermission(): Boolean = locationResolver.hasLocationPermission()

    // resolves the device location so the map can show the user and the list can be ordered by
    // distance. call after permission is granted. silent on failure, the screen just stays in
    // city-list mode
    fun requestUserLocation() {
        viewModelScope.launch {
            when (val outcome = locationResolver.currentCoordinates()) {
                is LocationFixOutcome.Located -> _state.update {
                    it.copy(userLat = outcome.lat, userLng = outcome.lng, locationDenied = false)
                }
                LocationFixOutcome.PermissionMissing -> _state.update {
                    it.copy(locationDenied = true)
                }
                else -> Unit
            }
        }
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
