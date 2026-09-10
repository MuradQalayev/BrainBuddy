package com.muradgalayev.brainbuddy.data.auth

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

        val message = authErrorMessage(error)

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
            authErrorMessage(Exception("over_email_send_rate_limit")),
        )
    }

    @Test
    fun `sms provider setup error is safe and actionable`() {
        assertEquals(
            "Phone verification is not configured yet. Enable an SMS provider in Supabase.",
            authErrorMessage(Exception("sms provider is not enabled Authorization: Bearer secret")),
        )
    }

    @Test
    fun `invalid phone OTP does not expose the transport exception`() {
        assertEquals(
            "That verification code is not correct.",
            authErrorMessage(Exception("invalid otp")),
        )
    }

    @Test
    fun `unknown auth failure uses the safe fallback`() {
        assertEquals(
            "Safe fallback",
            authErrorMessage(
                Exception("URL: https://project.supabase.co Headers: Bearer secret"),
                fallback = "Safe fallback",
            ),
        )
    }
}
