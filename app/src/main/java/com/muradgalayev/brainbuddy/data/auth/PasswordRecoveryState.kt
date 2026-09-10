package com.muradgalayev.brainbuddy.data.auth

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// bridge between MainActivity, which receives the recovery deep link, and AuthViewModel, which
// shows the set-new-password UI. Supabase's SessionSource has no dedicated PasswordRecovery
// variant: resetPasswordForEmail and OAuth deep links both surface as External. so we sniff
// the intent URI ourselves, since the recovery link Supabase generates carries type=recovery
@Singleton
class PasswordRecoveryState @Inject constructor() {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun onDeepLink(uri: Uri?) {
        if (uri == null) return
        // Supabase attaches auth tokens to the fragment on redirect, but some email clients and
        // browsers rewrite the URL and move params to the query, so check both
        if (hasPasswordRecoveryType(uri.fragment) || hasPasswordRecoveryType(uri.query)) {
            _active.value = true
        }
    }

    fun clear() {
        _active.value = false
    }

}

// Deliberately inspect only the non-secret `type` field. Recovery fragments also contain access
// and refresh tokens, so callers never need to copy or log the full callback URL.
internal fun hasPasswordRecoveryType(parameters: String?): Boolean = parameters
    .orEmpty()
    .removePrefix("?")
    .split('&')
    .any { parameter ->
        val parts = parameter.trim().split('=', limit = 2)
        parts.size == 2 &&
            parts[0].equals("type", ignoreCase = true) &&
            parts[1].equals("recovery", ignoreCase = true)
    }
