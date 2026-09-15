package com.muradgalayev.brainbuddy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanTest {

    @Test
    fun `only plus unlocks, anything else is free`() {
        assertEquals(Plan.Plus, Plan.fromStored("plus"))
        assertEquals(Plan.Free, Plan.fromStored("free"))
        // no row, or a value a newer server invented, must never unlock anything
        assertEquals(Plan.Free, Plan.fromStored(null))
        assertEquals(Plan.Free, Plan.fromStored("PLUS"))
        assertEquals(Plan.Free, Plan.fromStored("family"))
    }

    @Test
    fun `feature keys round trip and unknown keys highlight nothing`() {
        PlanFeature.entries.forEach { assertEquals(it, PlanFeature.fromKey(it.key)) }
        assertNull(PlanFeature.fromKey(""))
        assertNull(PlanFeature.fromKey(null))
    }
}
