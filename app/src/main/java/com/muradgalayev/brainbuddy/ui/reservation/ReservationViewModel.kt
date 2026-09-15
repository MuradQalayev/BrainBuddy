package com.muradgalayev.brainbuddy.ui.reservation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.SavedStateHandle
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.PlacesRepository
import com.muradgalayev.brainbuddy.data.network.NetworkObserver
import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import com.muradgalayev.brainbuddy.domain.model.Place
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import com.muradgalayev.brainbuddy.R

data class ReservationState(
    val loading: Boolean = true,
    val cityName: String = "",
    val places: List<Place> = emptyList(),
    val selectedPlaceId: String? = null,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
    val isOnline: Boolean = true,
)

@HiltViewModel
class ReservationViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val placesRepository: PlacesRepository,
    private val profileRepository: AdhdProfileRepository,
    private val calendarRepository: CalendarRepository,
    private val networkObserver: NetworkObserver,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val requestedPlaceId = savedStateHandle.get<String>("placeId").orEmpty()
    private val _state = MutableStateFlow(ReservationState())
    val state: StateFlow<ReservationState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            networkObserver.isOnline.collect { online ->
                _state.update { it.copy(isOnline = online) }
            }
        }
        load()
    }

    private fun load() = viewModelScope.launch {
        val profile = profileRepository.getProfile()
        val cities = placesRepository.listCities().getOrNull().orEmpty()
        val city = cities.firstOrNull { it.id == profile?.cityId }
        if (city == null) {
            _state.update { it.copy(loading = false, error = context.getString(R.string.res_choose_city_first)) }
            return@launch
        }
        val places = placesRepository.listPlacesInCity(city.id).getOrElse { emptyList() }
        _state.update {
            it.copy(
                loading = false,
                cityName = city.name,
                places = places,
                selectedPlaceId = requestedPlaceId
                    .takeIf { requested -> places.any { it.id == requested } }
                    ?: places.firstOrNull()?.id,
                error = if (places.isEmpty()) context.getString(R.string.res_no_places, city.name) else null,
            )
        }
    }

    fun selectPlace(id: String) = _state.update { it.copy(selectedPlaceId = id, saved = false) }
    fun setDate(value: LocalDate) = _state.update { it.copy(date = value, saved = false) }
    fun setTime(value: LocalTime) = _state.update { it.copy(time = value, saved = false) }

    fun saveAppointment() {
        val snapshot = _state.value
        if (!networkObserver.currentlyOnline()) {
            _state.update {
                it.copy(isOnline = false, error = context.getString(R.string.res_connect_internet))
            }
            return
        }
        val place = snapshot.places.firstOrNull { it.id == snapshot.selectedPlaceId } ?: return
        val date = snapshot.date ?: return
        val time = snapshot.time ?: return
        if (snapshot.saving) return
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val appointmentResult = runCatching {
                val start = LocalDateTime.of(date, time)
                calendarRepository.insertEvent(
                    CalendarEvent(
                        id = UUID.randomUUID().toString(),
                        title = context.getString(R.string.res_appointment_at, place.name),
                        description = context.getString(R.string.res_request_description, place.name) +
                            listOf(place.phone, place.email).filter { it.isNotBlank() }.joinToString(" · "),
                        startTime = start.toString(),
                        endTime = start.plusMinutes(30).toString(),
                        location = place.address,
                        color = "#7FA3C9",
                        link = place.website,
                    )
                )
            }
            appointmentResult.onFailure { error ->
                _state.update { it.copy(saving = false, error = error.message ?: context.getString(R.string.res_save_failed)) }
                return@launch
            }

            _state.update { it.copy(saving = false, saved = true) }
        }
    }

}
