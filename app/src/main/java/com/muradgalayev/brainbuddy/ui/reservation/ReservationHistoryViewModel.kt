package com.muradgalayev.brainbuddy.ui.reservation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ReservationHistoryViewModel @Inject constructor(
    calendarRepository: CalendarRepository,
) : ViewModel() {
    val appointments = calendarRepository.getAllEvents()
        .map { events ->
            events.filter { it.description.startsWith("Reservation request") }
                .sortedByDescending { it.startTime }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
