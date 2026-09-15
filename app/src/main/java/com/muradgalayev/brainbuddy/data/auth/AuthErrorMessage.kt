package com.muradgalayev.brainbuddy.data.auth

import androidx.annotation.StringRes
import java.io.IOException
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.ui.utils.UiText
import com.muradgalayev.brainbuddy.ui.utils.uiText

// Ktor's exception message can include the complete request URL and headers. Never pass that text
// through to Compose: Authorization headers contain the user's live Supabase session token.
internal fun authErrorMessage(
    error: Throwable,
    @StringRes fallback: Int = R.string.auth_error_generic,
): UiText = uiText(authErrorRes(error, fallback))

@StringRes
private fun authErrorRes(error: Throwable, @StringRes fallback: Int): Int {
    if (error is IOException) {
        return R.string.auth_error_unreachable
    }

    val raw = error.message.orEmpty()
    return when {
        raw.contains("email_not_confirmed", ignoreCase = true) ||
            raw.contains("Email not confirmed", ignoreCase = true) ->
            R.string.auth_error_confirm_email

        raw.contains("Invalid login", ignoreCase = true) ||
            raw.contains("invalid_credentials", ignoreCase = true) ->
            R.string.auth_error_invalid_credentials

        raw.contains("already registered", ignoreCase = true) ||
            raw.contains("user_already_exists", ignoreCase = true) ->
            R.string.auth_error_already_registered

        raw.contains("over_email_send_rate_limit", ignoreCase = true) ||
            raw.contains("email rate limit", ignoreCase = true) ->
            R.string.auth_error_email_rate

        raw.contains("over_sms_send_rate_limit", ignoreCase = true) ||
            raw.contains("sms rate limit", ignoreCase = true) ->
            R.string.auth_error_sms_rate

        raw.contains("phone provider", ignoreCase = true) ||
            raw.contains("sms provider", ignoreCase = true) ||
            raw.contains("sms_send_failed", ignoreCase = true) ->
            R.string.auth_error_sms_not_configured

        raw.contains("otp_expired", ignoreCase = true) ||
            raw.contains("token has expired", ignoreCase = true) ->
            R.string.auth_error_code_expired

        raw.contains("invalid otp", ignoreCase = true) ||
            raw.contains("invalid token", ignoreCase = true) ->
            R.string.auth_error_code_wrong

        raw.contains("phone_exists", ignoreCase = true) ||
            raw.contains("phone number already", ignoreCase = true) ->
            R.string.auth_error_phone_taken

        raw.contains("email_address_not_authorized", ignoreCase = true) ||
            raw.contains("Email address not authorized", ignoreCase = true) ->
            R.string.auth_error_email_not_authorized

        raw.contains("Error sending recovery email", ignoreCase = true) ||
            raw.contains("sending recovery email", ignoreCase = true) ->
            R.string.auth_error_reset_send_failed

        raw.contains("redirect", ignoreCase = true) &&
            raw.contains("not allowed", ignoreCase = true) ->
            R.string.auth_error_redirect_not_allowed

        raw.contains("valid email", ignoreCase = true) ->
            R.string.auth_error_invalid_email_address

        raw.contains("weak_password", ignoreCase = true) ->
            R.string.auth_error_weak_password

        else -> fallback
    }
}
