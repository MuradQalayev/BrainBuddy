package com.muradgalayev.brainbuddy.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    val email: String? = null,

    @SerialName("display_name")
    val displayName: String? = null,

    val username: String? = null,

    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

@Serializable
data class ProfileUpdateDto(
    @SerialName("display_name")
    val displayName: String,

    val username: String
)

@Serializable
data class ProfileInsertDto(
    val id: String,
    val email: String? = null,

    @SerialName("display_name")
    val displayName: String? = null,

    val username: String? = null,

    @SerialName("avatar_url")
    val avatarUrl: String? = null
)

@Singleton
class AuthRepository @Inject constructor(
    private val supabaseClient: SupabaseClient
) {
    @Volatile private var cachedProfile: ProfileDto? = null

    /** Synchronous peek for instant UI prefill. Returns null if no current user. */
    fun peekProfile(): ProfileDto? {
        val currentId = getCurrentUserId() ?: return null
        val cached = cachedProfile ?: return null
        return if (cached.id == currentId) cached else {
            cachedProfile = null
            null
        }
    }

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

    /**
     * Display name resolution order: explicit `display_name` > Google's `full_name`/`name`
     * > "first_name last_name". Returns null if none are set.
     */
    fun getCurrentUserFullName(): String? {
        val meta = supabaseClient.auth.currentUserOrNull()?.userMetadata ?: return null
        meta.stringField("display_name")?.let { return it }
        meta.stringField("full_name")?.let { return it }
        meta.stringField("name")?.let { return it }
        val first = meta.stringField("first_name").orEmpty()
        val last = meta.stringField("last_name").orEmpty()
        return "$first $last".trim().ifEmpty { null }
    }

    fun getCurrentUserUsername(): String? {
        val meta = supabaseClient.auth.currentUserOrNull()?.userMetadata ?: return null
        return meta.stringField("username")
    }

    /**
     * Avatar URL — prefer the explicit `avatar_url` field; fall back to Google's `picture`
     * claim that Supabase forwards from the OAuth identity.
     */
    fun getCurrentUserAvatarUrl(): String? {
        val meta = supabaseClient.auth.currentUserOrNull()?.userMetadata ?: return null
        return meta.stringField("avatar_url") ?: meta.stringField("picture")
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

    suspend fun signInWithGoogle() {
        supabaseClient.auth.signInWith(Google)
    }

    suspend fun signOut() {
        supabaseClient.auth.signOut()
        cachedProfile = null
    }

    /**
     * Persist edited profile fields to Supabase auth user_metadata. The Supabase API
     * merges the provided keys into existing metadata, so other fields (first_name,
     * phone, avatar_url, …) are preserved.
     */
    /**
     * Update the profile row for the current user. If no row exists yet
     * (Supabase doesn't auto-create profiles unless you have a trigger),
     * fall through to an insert so the caller never sees "List is empty.".
     */
    suspend fun updateProfile(displayName: String, username: String): ProfileDto {
        val userId = getCurrentUserId() ?: error("No logged-in user")

        val updated = supabaseClient
            .from("profiles")
            .update(
                ProfileUpdateDto(
                    displayName = displayName,
                    username = username
                )
            ) {
                filter { eq("id", userId) }
                select()
            }
            .decodeList<ProfileDto>()
            .firstOrNull()

        if (updated != null) {
            cachedProfile = updated
            return updated
        }

        // No row updated — insert one and return that.
        val inserted = supabaseClient
            .from("profiles")
            .insert(
                ProfileInsertDto(
                    id = userId,
                    email = getCurrentUserEmail(),
                    displayName = displayName,
                    username = username,
                    avatarUrl = getCurrentUserAvatarUrl(),
                )
            ) { select() }
            .decodeList<ProfileDto>()
            .firstOrNull()
            ?: error("profile insert returned no row")

        cachedProfile = inserted
        return inserted
    }

    /**
     * Returns null when no profile row exists yet for this user — callers
     * should treat that as "first-time login" rather than an exception.
     * Use [ensureProfileExists] when you need a guaranteed row.
     */
    suspend fun getProfile(): ProfileDto? {
        val userId = getCurrentUserId() ?: return null

        return supabaseClient
            .from("profiles")
            .select {
                filter { eq("id", userId) }
                limit(1)
            }
            .decodeList<ProfileDto>()
            .firstOrNull()
            ?.also { cachedProfile = it }
    }

    suspend fun ensureProfileExists(): ProfileDto {
        val userId = getCurrentUserId() ?: error("No logged-in user")

        getProfile()?.let { return it }

        val profile = ProfileInsertDto(
            id = userId,
            email = getCurrentUserEmail(),
            displayName = getCurrentUserFullName(),
            username = getCurrentUserUsername(),
            avatarUrl = getCurrentUserAvatarUrl()
        )

        return supabaseClient
            .from("profiles")
            .insert(profile) { select() }
            .decodeList<ProfileDto>()
            .firstOrNull()
            ?.also { cachedProfile = it }
            ?: error("profile insert returned no row")
    }

    /**
     * True if no other profile already uses this username. Case-insensitive,
     * ignores the current user's own row.
     */
    suspend fun isUsernameAvailable(username: String): Boolean {
        val trimmed = username.trim().lowercase()
        if (trimmed.isEmpty()) return false
        val currentUserId = getCurrentUserId()

        return try {
            val matches = supabaseClient
                .from("profiles")
                .select(Columns.list("id", "username")) {
                    filter { ilike("username", trimmed) }
                    limit(2)
                }
                .decodeList<ProfileDto>()

            matches.none { it.id != currentUserId && it.username?.lowercase() == trimmed }
        } catch (e: Exception) {
            // Don't block the user on a transient failure — assume available.
            true
        }
    }

    private fun Map<String, JsonElement>.stringField(key: String): String? {
        val element = this[key] ?: return null
        if (element !is JsonPrimitive) return null
        if (!element.isString) return null
        val value = element.content.trim()
        return value.ifEmpty { null }
    }
}
