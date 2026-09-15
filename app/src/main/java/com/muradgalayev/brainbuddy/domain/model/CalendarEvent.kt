package com.muradgalayev.brainbuddy.domain.model

// calendar event, independent from TodoItem. startTime and endTime are ISO-8601 local datetime
// strings, and the date of an event is implicit in startTime
data class CalendarEvent(
    val id: String,
    val title: String,
    val description: String,
    val startTime: String,
    val endTime: String,
    val location: String,
    val color: String,
    val link: String = "",
    // explicitly ticked off. all-subtasks-done is derived separately, not stored here
    val completed: Boolean = false,
    // author's user id when someone else put this event here through Together, null for events the
    // owner created themselves
    val createdByOther: String? = null,
)

// a medical booking made through Care nearby. there's no column for it: a booking is spotted by
// its description, which opens with res_request_description in whichever language it was made in.
// a new translation of that string needs its opening words added here
val CalendarEvent.isReservation: Boolean
    get() = RESERVATION_MARKERS.any { description.startsWith(it) }

private val RESERVATION_MARKERS = listOf("Reservation request", "Richiesta di prenotazione")
