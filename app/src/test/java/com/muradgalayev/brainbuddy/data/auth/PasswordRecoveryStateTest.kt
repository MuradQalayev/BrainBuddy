package com.muradgalayev.brainbuddy.data.auth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordRecoveryStateTest {

    @Test
    fun `finds recovery type among implicit-flow fragment fields`() {
        assertTrue(
            hasPasswordRecoveryType(
                "access_token=secret&refresh_token=also-secret&type=recovery",
            ),
        )
    }

    @Test
    fun `accepts a recovery type moved into the query by a browser`() {
        assertTrue(hasPasswordRecoveryType("?code=abc&type=RECOVERY"))
    }

    @Test
    fun `does not confuse a normal oauth callback with recovery`() {
        assertFalse(hasPasswordRecoveryType("access_token=secret&type=signup"))
        assertFalse(hasPasswordRecoveryType("code=abc"))
        assertFalse(hasPasswordRecoveryType(null))
    }
}
