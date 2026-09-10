package com.muradgalayev.brainbuddy.ui.together

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PhoneVerificationTest {

    @Test
    fun `formatted international phone is normalized to E164`() {
        assertEquals("+393331234567", normalizeVerificationPhone(" +39 (333) 123-4567 "))
    }

    @Test
    fun `country code is mandatory`() {
        assertNull(normalizeVerificationPhone("333 123 4567"))
    }

    @Test
    fun `letters are not silently accepted as a phone number`() {
        assertNull(normalizeVerificationPhone("+39 hello 3331234567"))
    }

    @Test
    fun `impossible E164 lengths are rejected`() {
        assertNull(normalizeVerificationPhone("+123"))
        assertNull(normalizeVerificationPhone("+1234567890123456"))
    }
}
