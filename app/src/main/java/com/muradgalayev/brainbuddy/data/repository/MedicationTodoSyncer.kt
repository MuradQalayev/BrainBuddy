package com.muradgalayev.brainbuddy.data.repository

import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.domain.model.AdhdProfile
import com.muradgalayev.brainbuddy.domain.model.Medication
import com.muradgalayev.brainbuddy.domain.model.MedicationSlot
import com.muradgalayev.brainbuddy.domain.model.TodoItem
import kotlinx.coroutines.flow.first
import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationTodoSyncer @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val todoRepository: TodoRepository,
    private val calendarRepository: CalendarRepository,
    private val preferencesManager: com.muradgalayev.brainbuddy.data.local.PreferencesManager,
) {
    // one rebuild at a time. this is called from app start, from saving the profile, and from the
    // destination toggles, and every row it writes involves a network round-trip, so two
    // overlapping runs interleave their deletes and inserts differently every time. the symptom
    // was a twice-daily medication showing two to-dos, then one, then none, with nothing the user
    // did explaining which
    private val mutex = Mutex()

    // rewrites the medication to-dos for today and the next HORIZON_DAYS days. two things this
    // fixes over generating only today. the obvious one: a daily medication now shows up when you
    // look at tomorrow, where the Medications screen already answered that question for any date
    // and the to-do list said nothing.
    // the less obvious one, and why this was actually broken: nothing calls this on a schedule, it
    // runs when the profile is saved. generating a single day meant that after midnight the to-dos
    // were simply gone until the user next edited their medications. a rolling window survives
    // whether or not anything ran overnight.
    // past days are never touched: their rows carry whether a dose was actually taken, and
    // rebuilding them would quietly rewrite history
    suspend fun sync(profile: AdhdProfile) = mutex.withLock {
        syncLocked(profile)
    }

    private suspend fun syncLocked(profile: AdhdProfile) {
        val todayDate = LocalDate.now()
        val lastDate = todayDate.plusDays(HORIZON_DAYS - 1L)
        val doseLogs = preferencesManager.medicationDoseLogs.first()

        val wantsTodo = preferencesManager.medicationInTodo.first()
        val wantsCalendar = preferencesManager.medicationInCalendar.first()

        // clear both destinations unconditionally, before deciding what to write. turning a
        // destination off has to remove what is already there, and a clear that only ran when the
        // destination was on would strand exactly the rows the user just asked to be rid of
        todoRepository
            .getTodoItemsInRange(todayDate.toString(), lastDate.toString())
            .first()
            .filter { it.category == MEDICATION_CATEGORY }
            .forEach { todoRepository.deleteTodoItemLocal(it) }

        calendarRepository
            .getEventsInDateRange(todayDate.toString(), lastDate.toString())
            .first()
            .filter { it.id.startsWith(MEDICATION_EVENT_PREFIX) }
            .forEach { calendarRepository.deleteEventLocal(it) }

        // the list is the ground truth, not the survey answer. this used to return early unless
        // medicationStatus was Yes, which let a stale or skipped survey field silence a screen full of
        // medications the user had just typed in. if there are doses, there are doses; the
        // questionnaire is a different question
        val active = profile.medications.filter { it.name.isNotBlank() }
        if (active.isEmpty()) return
        if (!wantsTodo && !wantsCalendar) return

        for (dayOffset in 0 until HORIZON_DAYS) {
            val date = todayDate.plusDays(dayOffset.toLong())
            val dateIso = date.toString()
            for (med in active) {
                if (med.name.isBlank()) continue
                // respects each medication's own weekday schedule, so a weekdays-only prescription doesn't
                // appear on Sunday
                if (!med.isScheduledOn(date.dayOfWeek)) continue
                for (slotKey in med.slots) {
                    val slot = MedicationSlot.fromKey(slotKey) ?: continue
                    val completed = "$dateIso|${med.id}|${slot.key}" in doseLogs
                    if (wantsTodo) {
                        todoRepository.insertTodoItemLocal(buildTodo(med, slot, dateIso, completed))
                    }
                    if (wantsCalendar) {
                        calendarRepository.insertEventLocal(
                            buildEvent(med, slot, dateIso, completed)
                        )
                    }
                }
            }
        }
    }

    // erases every trace of one medication from both destinations. the rebuild in sync() should
    // already handle a deletion, since it clears the window and only writes back what still
    // exists. this exists because that reasoning only holds inside the window: rows written on an
    // earlier run, for dates now behind us or beyond the horizon, are never revisited, so a
    // deleted medication kept appearing when you scrolled. deleting is the one moment the user is
    // entitled to expect all of it to go, so this reaches wider than HORIZON_DAYS in both
    // directions and isn't conditional on the destination toggles
    suspend fun purgeMedication(medicationId: String) = mutex.withLock {
        purgeLocked(medicationId)
    }

    private suspend fun purgeLocked(medicationId: String) {
        val from = LocalDate.now().minusDays(PURGE_BACK_DAYS)
        val to = LocalDate.now().plusDays(PURGE_FORWARD_DAYS)

        todoRepository.getTodoItemsInRange(from.toString(), to.toString()).first()
            .filter { it.category == MEDICATION_CATEGORY && it.id.startsWith("med-$medicationId-") }
            .forEach { todoRepository.deleteTodoItemLocal(it) }

        val eventPrefix = "$MEDICATION_EVENT_PREFIX$medicationId-"
        calendarRepository.getEventsInDateRange(from.toString(), to.toString()).first()
            .filter { it.id.startsWith(eventPrefix) }
            .forEach { calendarRepository.deleteEventLocal(it) }
    }

    private fun buildTodo(med: Medication, slot: MedicationSlot, dateIso: String, completed: Boolean): TodoItem {
        val time = slot.defaultStartTime()
        return TodoItem(
            id = "med-${med.id}-${slot.key}-$dateIso",
            title = formatTitle(med),
            description = "",
            isCompleted = completed,
            date = dateIso,
            startTime = time,
            endTime = time,
            priority = "medium",
            attendees = 0,
            color = "#7DAA92",
            category = MEDICATION_CATEGORY,
        )
    }

    // a dose as a calendar entry. fifteen minutes rather than a point in time: a zero-length event
    // is invisible on a day view, and an hour would make four doses look like a full day of
    // appointments
    private fun buildEvent(
        med: Medication,
        slot: MedicationSlot,
        dateIso: String,
        completed: Boolean,
    ): CalendarEvent {
        val time = slot.defaultStartTime()
        val endTime = LocalTime.parse(time).plusMinutes(15).toString()
        return CalendarEvent(
            id = "$MEDICATION_EVENT_PREFIX${med.id}-${slot.key}-$dateIso",
            title = formatTitle(med),
            description = "",
            startTime = "${dateIso}T$time:00",
            endTime = "${dateIso}T$endTime:00",
            location = "",
            color = "#7DAA92",
            completed = completed,
        )
    }

    // written into the to-do and calendar rows, so it lands in whatever language the app has when
    // the window rolls forward
    private fun formatTitle(med: Medication): String {
        val dose = med.doseLabel(context.resources)
        return if (dose.isBlank()) context.getString(R.string.med_take, med.name)
        else context.getString(R.string.med_take_dose, med.name, dose)
    }

    companion object {
        const val MEDICATION_CATEGORY = "medication"

        // calendar rows have no category column, so the id carries the marker. it's what lets the
        // rebuild find its own events and leave the user's alone
        const val MEDICATION_EVENT_PREFIX = "med-evt-"

        // two weeks. long enough that the list survives a fortnight without the app being opened,
        // short enough that changing a prescription doesn't leave months of stale rows to clean up
        const val HORIZON_DAYS = 14

        // how far either side of today a purge reaches. generous on purpose: it runs once, on an
        // explicit delete, and the cost of missing a row is the user seeing a medication they removed
        const val PURGE_BACK_DAYS = 120L
        const val PURGE_FORWARD_DAYS = 120L
    }
}

private fun MedicationSlot.defaultStartTime(): String = when (this) {
    MedicationSlot.Morning -> "08:00"
    MedicationSlot.Afternoon -> "13:00"
    MedicationSlot.Evening -> "18:00"
    MedicationSlot.Night -> "22:00"
}
