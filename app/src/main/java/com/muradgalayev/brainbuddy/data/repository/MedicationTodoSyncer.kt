package com.muradgalayev.brainbuddy.data.repository

import com.muradgalayev.brainbuddy.domain.model.AdhdProfile
import com.muradgalayev.brainbuddy.domain.model.Medication
import com.muradgalayev.brainbuddy.domain.model.MedicationSlot
import com.muradgalayev.brainbuddy.domain.model.MedicationStatus
import com.muradgalayev.brainbuddy.domain.model.TodoItem
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MedicationTodoSyncer @Inject constructor(
    private val todoRepository: TodoRepository,
) {
    suspend fun sync(profile: AdhdProfile) {
        val today = LocalDate.now().toString()

        val existing = todoRepository.getTodoItemsByDate(today).first()
            .filter { it.category == MEDICATION_CATEGORY }
        existing.forEach { todoRepository.deleteTodoItem(it) }

        if (profile.medicationStatus != MedicationStatus.Yes) return

        for (med in profile.medications) {
            if (med.name.isBlank()) continue
            for (slotKey in med.slots) {
                val slot = MedicationSlot.fromKey(slotKey) ?: continue
                todoRepository.insertTodoItem(buildTodo(med, slot, today))
            }
        }
    }

    private fun buildTodo(med: Medication, slot: MedicationSlot, dateIso: String): TodoItem {
        val time = slot.defaultStartTime()
        return TodoItem(
            id = "med-${med.id}-${slot.key}-$dateIso",
            title = formatTitle(med),
            description = "",
            isCompleted = false,
            date = dateIso,
            startTime = time,
            endTime = time,
            priority = "medium",
            attendees = 0,
            color = "#7DAA92",
            category = MEDICATION_CATEGORY,
        )
    }

    private fun formatTitle(med: Medication): String =
        if (med.dose.isBlank()) "Take ${med.name}" else "Take ${med.name} ${med.dose}"

    companion object { const val MEDICATION_CATEGORY = "medication" }
}

private fun MedicationSlot.defaultStartTime(): String = when (this) {
    MedicationSlot.Morning -> "08:00"
    MedicationSlot.Afternoon -> "13:00"
    MedicationSlot.Evening -> "18:00"
    MedicationSlot.Night -> "22:00"
}
