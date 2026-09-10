package com.muradgalayev.brainbuddy.data.repository

import com.muradgalayev.brainbuddy.domain.model.AdhdProfile
import com.muradgalayev.brainbuddy.domain.model.Medication
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// guards the write path for the profile row. postgrest-kt builds the columns= request parameter
// from the keys the serialised body happens to contain, and supabase-kt serialises with
// encodeDefaults = false. a field at its declared default therefore vanishes from the request
// entirely and Postgres leaves that column alone, so clearing a value silently didn't save.
// these tests pin the payload as the complete set of columns, because the failure is invisible
// at every layer above it
class AdhdProfileUpsertPayloadTest {

    @Test
    fun `deleting the last medication still writes the medication column`() {
        val emptied = AdhdProfile(userId = "u1", medications = emptyList())

        val payload = emptied.toProfileUpsertPayload()

        // the regression: an empty string is the DTO's default, so this key used to be dropped and the
        // server kept the medication the user had just deleted
        assertTrue("medication_name must be written even when empty", "medication_name" in payload)
        assertEquals("", payload["medication_name"]?.jsonPrimitive?.content)
    }

    @Test
    fun `a remaining medication is written as a json array`() {
        val profile = AdhdProfile(
            userId = "u1",
            medications = listOf(Medication(id = "m1", name = "Ritalin")),
        )

        val encoded = profile.toProfileUpsertPayload()["medication_name"]?.jsonPrimitive?.content

        assertTrue("expected a JSON array, got: $encoded", encoded.orEmpty().startsWith("["))
        assertTrue(encoded.orEmpty().contains("Ritalin"))
    }

    @Test
    fun `every other clearable answer is written at its default too`() {
        val blank = AdhdProfile(userId = "u1")

        val payload = blank.toProfileUpsertPayload()

        // a representative spread: text, list and nullable-number columns all had the same bug
        listOf("pain_point", "primary_symptoms", "top_goals", "focus_duration_minutes", "city_id")
            .forEach { column ->
                assertTrue("$column must be written even when cleared", column in payload)
            }
    }

    @Test
    fun `server owned columns are left out`() {
        val payload = AdhdProfile(userId = "u1").toProfileUpsertPayload()

        // ours is always null, and sending it would overwrite the timestamp Postgres maintains
        assertFalse("updated_at must not be sent", "updated_at" in payload)
    }

    @Test
    fun `the user id is always present so the upsert can resolve its conflict target`() {
        val payload = AdhdProfile(userId = "u1").toProfileUpsertPayload()

        assertEquals("u1", payload["user_id"]?.jsonPrimitive?.content)
    }
}
