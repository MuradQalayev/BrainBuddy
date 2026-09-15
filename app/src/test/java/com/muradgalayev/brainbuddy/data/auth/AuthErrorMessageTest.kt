package com.muradgalayev.brainbuddy.data.auth

import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.ui.utils.resolve
import com.muradgalayev.brainbuddy.testing.TestStrings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AuthErrorMessageTest {

    @Test
    fun `recovery server failure never exposes request headers or bearer token`() {
        val secret = "secret-session-token"
        val error = Exception(
            "unexpected_failure: Error sending recovery email\n" +
                "URL: https://project.supabase.co/auth/v1/recover\n" +
                "Headers: Authorization=[Bearer $secret]",
        )

        val message = authErrorMessage(error).resolve(TestStrings.en)

        assertEquals(
            "The email service couldn't send the reset message. Please try again later.",
            message,
        )
        assertFalse(message.contains(secret))
        assertFalse(message.contains("Authorization"))
        assertFalse(message.contains("supabase.co"))
    }

    @Test
    fun `email rate limit gets a useful action instead of server diagnostics`() {
        assertEquals(
            "Too many emails were requested. Please wait a while and try again.",
            authErrorMessage(Exception("over_email_send_rate_limit")).resolve(TestStrings.en),
        )
    }

    @Test
    fun `sms provider setup error is safe and actionable`() {
        assertEquals(
            "Phone verification is not configured yet. Enable an SMS provider in Supabase.",
            authErrorMessage(Exception("sms provider is not enabled Authorization: Bearer secret")).resolve(TestStrings.en),
        )
    }

    @Test
    fun `invalid phone OTP does not expose the transport exception`() {
        assertEquals(
            "That verification code is not correct.",
            authErrorMessage(Exception("invalid otp")).resolve(TestStrings.en),
        )
    }

    @Test
    fun `unknown auth failure uses the safe fallback`() {
        assertEquals(
            "Couldn't send the password email. Please try again.",
            authErrorMessage(
                Exception("URL: https://project.supabase.co Headers: Bearer secret"),
                fallback = R.string.settings_password_email_failed,
            ).resolve(TestStrings.en),
        )
    }
}
