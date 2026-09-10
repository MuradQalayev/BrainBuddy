package com.muradgalayev.brainbuddy.data.mapper

import com.muradgalayev.brainbuddy.data.local.entity.CalendarEventEntity
import com.muradgalayev.brainbuddy.data.local.entity.TodoItemEntity
import com.muradgalayev.brainbuddy.data.remote.dto.CalendarEventDto
import com.muradgalayev.brainbuddy.data.remote.dto.TodoItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// authorship round-tripping for shared calendar events and to-dos. user_id is whose list a row
// belongs to, created_by is who wrote it, and the whole Together privacy model keys off that
// difference: RLS lets a connection reach only rows where created_by = auth.uid().
// so a mapper that drops or rewrites created_by doesn't cause a visible bug, it silently cuts
// someone off from the row they created, or worse hands the DB a null the INSERT policy
// rejects. these tests exist because that already happened once in the to-do path
class TogetherAuthorshipTest {

    private val me = "user-me"
    private val partner = "user-partner"

    // calendar

    @Test
    fun `event I created for myself reports no other author`() {
        val entity = CalendarEventEntity(
            id = "e1",
            userId = me,
            title = "Dentist",
            startTime = "2026-08-10T09:00:00",
            endTime = "2026-08-10T10:00:00",
            createdBy = me,
        )

        assertNull(entity.toDomain().createdByOther)
    }

    @Test
    fun `event a connection put on my calendar names them as the author`() {
        val entity = CalendarEventEntity(
            id = "e1",
            userId = me,
            title = "Dentist",
            startTime = "2026-08-10T09:00:00",
            endTime = "2026-08-10T10:00:00",
            createdBy = partner,
        )

        assertEquals(partner, entity.toDomain().createdByOther)
    }

    @Test
    fun `editing a connection's event does not steal its authorship`() {
        // The owner opens an event their partner added and saves a change. If the
        // round-trip stamped created_by with the editor, the partner would lose
        // access to the event they created.
        val original = CalendarEventEntity(
            id = "e1",
            userId = me,
            title = "Dentist",
            startTime = "2026-08-10T09:00:00",
            endTime = "2026-08-10T10:00:00",
            createdBy = partner,
        )

        val edited = original.toDomain().copy(title = "Dentist (moved)").toEntity(me)

        assertEquals(partner, edited.createdBy)
        assertEquals("Dentist (moved)", edited.title)
    }

    @Test
    fun `my own event is stamped with my id on save`() {
        val entity = CalendarEventEntity(
            id = "e1",
            userId = me,
            title = "Gym",
            startTime = "2026-08-10T09:00:00",
            endTime = "2026-08-10T10:00:00",
            createdBy = me,
        )

        assertEquals(me, entity.toDomain().toEntity(me).createdBy)
    }

    @Test
    fun `event pushed to the server always carries an author`() {
        // The connection INSERT policy requires created_by = auth.uid(); a null here
        // is rejected by the server rather than defaulting to anything sensible.
        val legacyRow = CalendarEventEntity(
            id = "e1",
            userId = me,
            title = "Old event from before this column existed",
            startTime = "2026-08-10T09:00:00",
            endTime = "2026-08-10T10:00:00",
            createdBy = null,
        )

        assertEquals(me, legacyRow.toDto(me).createdBy)
    }

    @Test
    fun `event arriving from the server keeps its author through the cache`() {
        val dto = CalendarEventDto(
            id = "e1",
            userId = me,
            title = "Dentist",
            startTime = "2026-08-10T09:00:00",
            endTime = "2026-08-10T10:00:00",
            createdBy = partner,
        )

        assertEquals(partner, dto.toEntity().createdBy)
    }

    // ── To-dos ──

    @Test
    fun `task a connection added to my list names them as the author`() {
        val entity = TodoItemEntity(
            id = "t1",
            userId = me,
            title = "Pick up prescription",
            startTime = "09:00",
            endTime = "10:00",
            createdBy = partner,
        )

        assertEquals(partner, entity.toDomain().createdByOther)
    }

    @Test
    fun `task I created for myself reports no other author`() {
        val entity = TodoItemEntity(
            id = "t1",
            userId = me,
            title = "Laundry",
            startTime = "09:00",
            endTime = "10:00",
            createdBy = me,
        )

        assertNull(entity.toDomain().createdByOther)
    }

    @Test
    fun `editing a connection's task does not steal its authorship`() {
        // the regression: TodoItem carried no authorship, so ticking off or editing a task your partner
        // added rewrote created_by to you and dropped them from their own row
        val original = TodoItemEntity(
            id = "t1",
            userId = me,
            title = "Pick up prescription",
            startTime = "09:00",
            endTime = "10:00",
            createdBy = partner,
        )

        val edited = original.toDomain().copy(isCompleted = true).toEntity(me)

        assertEquals(partner, edited.createdBy)
        assertEquals(true, edited.isCompleted)
    }

    @Test
    fun `task pushed to the server always carries an author`() {
        val legacyRow = TodoItemEntity(
            id = "t1",
            userId = me,
            title = "Old task",
            startTime = "09:00",
            endTime = "10:00",
            createdBy = null,
        )

        assertEquals(me, legacyRow.toDto(me).createdBy)
    }

    @Test
    fun `task arriving from the server keeps its author through the cache`() {
        val dto = TodoItemDto(
            id = "t1",
            userId = me,
            title = "Pick up prescription",
            date = "2026-08-10",
            startTime = "09:00",
            endTime = "10:00",
            createdBy = partner,
        )

        assertEquals(partner, dto.toEntity().createdBy)
    }
}
