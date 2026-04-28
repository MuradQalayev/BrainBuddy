package com.muradgalayev.brainbuddy.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    val isLoggedIn: Flow<Boolean> = supabaseClient.auth.sessionStatus.map { status ->
        status is SessionStatus.Authenticated
    }

    val sessionStatus: Flow<SessionStatus> = supabaseClient.auth.sessionStatus

    fun getCurrentUserId(): String? {
        return supabaseClient.auth.currentUserOrNull()?.id
    }

    fun getCurrentUserEmail(): String? {
        return supabaseClient.auth.currentUserOrNull()?.email
    }

    fun getCurrentUserFullName(): String? {
        val meta = supabaseClient.auth.currentUserOrNull()?.userMetadata ?: return null
        val first = meta["first_name"]?.toString()?.trim('"') ?: ""
        val last = meta["last_name"]?.toString()?.trim('"') ?: ""
        return "$first $last".trim().ifEmpty { null }
    }

    suspend fun signUp(
        email: String,
        password: String,
        firstName: String,
        lastName: String,
        phone: String
    ) {
        supabaseClient.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            this.data = buildJsonObject {
                put("first_name", firstName)
                put("last_name", lastName)
                put("phone", phone)
            }
        }
    }

    suspend fun signIn(email: String, password: String) {
        supabaseClient.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() {
        supabaseClient.auth.signOut()
    }
}
