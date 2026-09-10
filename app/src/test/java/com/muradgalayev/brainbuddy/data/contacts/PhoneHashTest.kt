package com.muradgalayev.brainbuddy.data.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PhoneHashTest {

    @Test
    fun `phone hash matches standard lowercase SHA-256 used by Postgres`() {
        assertEquals(
            "78f00e1ca317fb2af02c89b17b8b07382ff70336aba0997ad1f3ef3645b4e682",
            sha256Phone("+393331234567"),
        )
    }

    @Test
    fun `different normalized numbers do not share a hash`() {
        assertNotEquals(sha256Phone("+393331234567"), sha256Phone("+393331234568"))
    }
}
