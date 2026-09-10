package com.muradgalayev.brainbuddy.data.auth

import java.io.IOException

// Ktor's exception message can include the complete request URL and headers. Never pass that text
// through to Compose: Authorization headers contain the user's live Supabase session token.
internal fun authErrorMessage(
    error: Throwable,
    fallback: String = "Something went wrong. Please try again.",
): String {
    if (error is IOException) {
        return "Can't reach the server. Check your internet connection."
    }

    val raw = error.message.orEmpty()
    return when {
        raw.contains("email_not_confirmed", ignoreCase = true) ||
            raw.contains("Email not confirmed", ignoreCase = true) ->
            "Please confirm your email before signing in."

        raw.contains("Invalid login", ignoreCase = true) ||
            raw.contains("invalid_credentials", ignoreCase = true) ->
            "Invalid email or password"

        raw.contains("already registered", ignoreCase = true) ||
            raw.contains("user_already_exists", ignoreCase = true) ->
            "This email is already registered"

        raw.contains("over_email_send_rate_limit", ignoreCase = true) ||
            raw.contains("email rate limit", ignoreCase = true) ->
            "Too many emails were requested. Please wait a while and try again."

        raw.contains("over_sms_send_rate_limit", ignoreCase = true) ||
            raw.contains("sms rate limit", ignoreCase = true) ->
            "Too many verification codes were requested. Please wait before trying again."

        raw.contains("phone provider", ignoreCase = true) ||
            raw.contains("sms provider", ignoreCase = true) ||
            raw.contains("sms_send_failed", ignoreCase = true) ->
            "Phone verification is not configured yet. Enable an SMS provider in Supabase."

        raw.contains("otp_expired", ignoreCase = true) ||
            raw.contains("token has expired", ignoreCase = true) ->
            "That verification code expired. Request a new one."

        raw.contains("invalid otp", ignoreCase = true) ||
            raw.contains("invalid token", ignoreCase = true) ->
            "That verification code is not correct."

        raw.contains("phone_exists", ignoreCase = true) ||
            raw.contains("phone number already", ignoreCase = true) ->
            "That phone number is already connected to another Myndora account."

        raw.contains("email_address_not_authorized", ignoreCase = true) ||
            raw.contains("Email address not authorized", ignoreCase = true) ->
            "Email delivery is not configured for this address yet."

        raw.contains("Error sending recovery email", ignoreCase = true) ||
            raw.contains("sending recovery email", ignoreCase = true) ->
            "The email service couldn't send the reset message. Please try again later."

        raw.contains("redirect", ignoreCase = true) &&
            raw.contains("not allowed", ignoreCase = true) ->
            "The password-reset callback is not allowed by the server configuration."

        raw.contains("valid email", ignoreCase = true) ->
            "Please enter a valid email address"

        raw.contains("weak_password", ignoreCase = true) ->
            "Password is too weak"

        else -> fallback
    }
}
