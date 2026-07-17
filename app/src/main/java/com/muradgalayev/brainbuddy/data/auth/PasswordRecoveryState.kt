package com.muradgalayev.brainbuddy.data.auth

import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge between MainActivity (which receives the recovery deep link) and AuthViewModel
 * (which shows the "Set new password" UI).
 *
 * Supabase's `SessionSource` sealed interface has no dedicated `PasswordRecovery` variant —
 * `resetPasswordForEmail` and OAuth deep links both surface as `SessionSource.External`.
 * So we sniff the intent URI ourselves: the recovery link Supabase generates contains
 * `type=recovery` in the URL fragment.
 */
@Singleton
class PasswordRecoveryState @Inject constructor() {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun onDeepLink(uri: Uri?) {
        if (uri == null) return
        // Supabase attaches auth tokens to the fragment (#) on redirect, but some email
        // clients/browsers rewrite the URL and move params to the query (?). Check both.
        val fragment = uri.fragment.orEmpty()
        val query = uri.query.orEmpty()
        val hay = "$fragment&$query"
        val isRecovery = hay.split('&').any { it.trim() == "type=recovery" }
        Log.d(
            "PasswordRecoveryState",
            "onDeepLink uri=$uri fragment=$fragment query=$query recovery=$isRecovery",
        )
        if (isRecovery) _active.value = true
    }

    fun clear() {
        _active.value = false
    }
}
