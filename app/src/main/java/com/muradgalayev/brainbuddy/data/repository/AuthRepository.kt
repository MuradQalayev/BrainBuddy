package com.muradgalayev.brainbuddy.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.muradgalayev.brainbuddy.data.contacts.sha256Phone
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.providers.Google
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.storage.storage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.days

@Serializable
data class ProfileDto(
    val id: String,
    val email: String? = null,

    @SerialName("display_name")
    val displayName: String? = null,

    val username: String? = null,

    @SerialName("avatar_url")
    val avatarUrl: String? = null,

    @SerialName("linked_google_email")
    val linkedGoogleEmail: String? = null,

    // backend-generated pseudonym, see supabase_masked_id.sql. nullable only so a profile cached
    // before this column existed still decodes, the column itself is NOT NULL
    @SerialName("masked_id")
    val maskedId: String? = null,
)

// patch-only DTO for updating just the Google Calendar link column
@Serializable
private data class LinkedGoogleEmailPatch(
    @SerialName("linked_google_email")
    val linkedGoogleEmail: String?
)

@Serializable
private data class HealthConnectionPatch(
    @SerialName("health_connect_linked") val linked: Boolean,
    @SerialName("health_connected_at") val connectedAt: String?,
    @SerialName("health_last_synced_at") val lastSyncedAt: String?,
    @SerialName("health_data_types") val dataTypes: List<String>,
)

// standalone read DTO for linked_google_email. ProfileDto needs id present, and a targeted
// SELECT of one column doesn't return it, so decoding into ProfileDto fails
@Serializable
private data class LinkedGoogleEmailRow(
    @SerialName("linked_google_email")
    val linkedGoogleEmail: String? = null
)

@Serializable
data class ProfileUpdateDto(
    @SerialName("display_name")
    val displayName: String,

    val username: String
)

@Serializable
data class AvatarUpdateDto(
    @SerialName("avatar_url")
    val avatarUrl: String,
)

private const val AVATAR_BUCKET = "profile_photos"

// thrown when a username collides with an existing row in profiles
class UsernameTakenException(username: String) :
    Exception("Username '$username' is already taken")

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
    private val supabaseClient: SupabaseClient,
    @ApplicationContext context: Context
) {
    // disk-backed so the last-known profile survives process death. read synchronously at
    // construction so a cold start seeds the name and avatar immediately instead of flashing
    // 'Welcome' until the network fetch lands
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PROFILE_PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile private var cachedProfileField: ProfileDto? =
        prefs.getString(KEY_CACHED_PROFILE, null)?.let {
            runCatching { Json.decodeFromString(ProfileDto.serializer(), it) }.getOrNull()
        }

    // mirrored to disk on every write so it outlives the process. assigning null clears the
    // persisted copy too, e.g. on sign-out
    private var cachedProfile: ProfileDto?
        get() = cachedProfileField
        set(value) {
            cachedProfileField = value
            if (value == null) {
                prefs.edit().remove(KEY_CACHED_PROFILE).apply()
            } else {
                prefs.edit()
                    .putString(KEY_CACHED_PROFILE, Json.encodeToString(ProfileDto.serializer(), value))
                    .apply()
            }
        }

    private val profileRevalidatedThisProcess = AtomicBoolean(false)

    // true exactly once per process. gates a single stale-while-revalidate refetch: the UI seeds
    // instantly from the disk cache, then the first screen to take this token refreshes in the
    // background to pick up edits made on another device, without the seed flashing 'Welcome'
    fun consumeProfileRevalidationToken(): Boolean =
        profileRevalidatedThisProcess.compareAndSet(false, true)

    // synchronous peek for instant prefill, backed by the disk cache so it survives process
    // death. on a cold start Supabase restores its session asynchronously, so getCurrentUserId is
    // briefly null while the ViewModel is being built, and in that window we return the
    // last-known profile optimistically (it's cleared on sign-out, so it can only belong to the
    // session being restored). once a user id is available we enforce it matches
    fun peekProfile(): ProfileDto? {
        val cached = cachedProfile ?: return null
        val currentId = getCurrentUserId() ?: return cached
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

    // identity for local-first repositories. Supabase restores its session asynchronously after
    // process start, while the last authenticated profile is already on disk. the cache is
    // cleared on sign-out, so using its id to open that user's Room rows offline is safe
    fun getCurrentOrCachedUserId(): String? =
        getCurrentUserId() ?: cachedProfile?.id

    // the pseudonym, the only identifier we may hand to a third-party AI provider. served from
    // the disk cache so it's there synchronously on a cold start, and null only until the first
    // profile fetch of a brand-new install, which callers treat as 'send no reference at all'.
    // never substitute getCurrentUserId here: that's the auth subject and pairs directly with
    // the email in auth.users, which is what pseudonymisation exists to keep off other people's
    // infrastructure
    fun getMaskedUserId(): String? = peekProfile()?.maskedId?.takeIf { it.isNotBlank() }

    // fetches the profile row when the cache has no masked id yet: first run after install, or a
    // cache written before the column existed. still null if there's no row or we're offline
    suspend fun ensureMaskedUserId(): String? {
        getMaskedUserId()?.let { return it }
        runCatching { getProfile() }
        return getMaskedUserId()
    }

    fun getCurrentUserEmail(): String? {
        return supabaseClient.auth.currentUserOrNull()?.email
    }

    // display name order: explicit display_name, then Google's full_name or name, then
    // 'first_name last_name'. null if none are set
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

    // Legacy profile phone captured at signup. It is editable metadata and must never be used as
    // proof that this account owns a number; contact discovery uses getVerifiedPhone() instead.
    fun getCurrentUserPhone(): String? {
        getVerifiedPhone()?.let { return it }
        val meta = supabaseClient.auth.currentUserOrNull()?.userMetadata ?: return null
        return meta.stringField("phone")?.takeIf { it.isNotBlank() }
    }

    // Profile-only metadata. Changing this does not make an account discoverable by contacts.
    suspend fun updateUserPhone(phone: String) {
        supabaseClient.auth.updateUser {
            data = buildJsonObject { put("phone", phone) }
        }
    }

    // Supabase sets phoneConfirmedAt only after a successful SMS OTP. This is the sole phone
    // identity accepted by Myndora contact discovery, for email and Google-authenticated users.
    fun getVerifiedPhone(): String? {
        val user = supabaseClient.auth.currentUserOrNull() ?: return null
        if (user.phoneConfirmedAt == null) return null
        return user.phone?.takeIf { it.isNotBlank() }
    }

    // Starts a signed-in user's phone-change flow. Supabase sends the OTP through the SMS provider
    // configured in the project; this does not create a second account.
    suspend fun requestPhoneVerification(phone: String) {
        // Reserve only the one-way hash in an app-owned table. The migration deliberately never
        // writes to or indexes Supabase's managed auth.users table.
        supabaseClient.postgrest.rpc(
            "prepare_phone_verification",
            buildJsonObject { put("p_phone_hash", sha256Phone(phone)) },
        )
        supabaseClient.auth.updateUser { this.phone = phone }
    }

    suspend fun verifyPhoneChange(phone: String, code: String) {
        val expectedUserId = getCurrentUserId() ?: error("No logged-in user")
        val expectedPhoneHash = sha256Phone(phone)

        supabaseClient.auth.verifyPhoneOtp(
            type = OtpType.Phone.PHONE_CHANGE,
            phone = phone,
            token = code,
        )
        // Refresh the locally persisted session so getVerifiedPhone() changes immediately.
        supabaseClient.auth.retrieveUserForCurrentSession(updateSession = true)

        // Never accept an OTP response that changed the authenticated identity or did not attach
        // this exact verified number to the existing email/Google account.
        val refreshed = supabaseClient.auth.currentUserOrNull()
        val verifiedHash = refreshed?.phone?.let(::sha256Phone)
        if (
            refreshed?.id != expectedUserId ||
            refreshed.phoneConfirmedAt == null ||
            verifiedHash != expectedPhoneHash
        ) {
            supabaseClient.auth.clearSession()
            throw SecurityException("Phone verification identity check failed")
        }

        // The reservation is only a short-lived concurrency guard. Verification has already
        // succeeded, so a cleanup failure must not make the UI claim the number was rejected.
        runCatching {
            supabaseClient.postgrest.rpc(
                "complete_phone_verification",
                buildJsonObject { put("p_phone_hash", expectedPhoneHash) },
            )
        }
    }

    // uploads a new avatar to the profile_photos bucket, saves its URL onto the profiles row and
    // user metadata, and returns it
    suspend fun uploadAvatar(bytes: ByteArray, extension: String): String {
        val userId = getCurrentUserId() ?: error("No logged-in user")
        val path = "$userId/${UUID.randomUUID()}.$extension"

        supabaseClient.storage.from(AVATAR_BUCKET).upload(path, bytes) { upsert = true }
        // private bucket, so a long-lived signed URL rather than a public one: the photo isn't
        // world-readable but still loads without re-signing on every view
        val url = supabaseClient.storage.from(AVATAR_BUCKET)
            .createSignedUrl(path, expiresIn = 3650.days)

        // persist to the profiles row so it survives reinstalls and other devices
        runCatching {
            supabaseClient.from("profiles")
                .update(AvatarUpdateDto(avatarUrl = url)) { filter { eq("id", userId) } }
        }
        // and into metadata so getCurrentUserAvatarUrl() reflects it immediately
        runCatching {
            supabaseClient.auth.updateUser { data = buildJsonObject { put("avatar_url", url) } }
        }
        cachedProfile = cachedProfile?.copy(avatarUrl = url)
        return url
    }

    // prefer the explicit avatar_url field, fall back to Google's picture claim that Supabase
    // forwards from the OAuth identity
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

    // sends a Supabase password-reset email. the link comes back into the app through the
    // brainbuddy://auth-callback deep link, and Supabase attaches type=recovery to the fragment
    // so we can tell it apart from a normal login
    suspend fun sendPasswordReset(email: String) {
        supabaseClient.auth.resetPasswordForEmail(
            email = email,
            redirectUrl = "brainbuddy://auth-callback",
        )
    }

    // only works while the current session is in Supabase's recovery state
    suspend fun updatePassword(newPassword: String) {
        supabaseClient.auth.updateUser {
            password = newPassword
        }
    }

    suspend fun signOut() {
        // revoke remotely when reachable. Supabase does this before clearing its persisted session,
        // so network failure is deliberately swallowed
        runCatching { supabaseClient.auth.signOut() }
        runCatching { supabaseClient.auth.clearSession() }
        cachedProfile = null
        clearPendingProfileUpdate()
    }

    // persists edited profile fields to auth user_metadata. Supabase merges the provided keys,
    // so first_name, phone, avatar_url and the rest are preserved
    // updates the profile row for the current user. Supabase doesn't auto-create profiles
    // without a trigger, so fall through to an insert and the caller never sees 'List is empty.'
    suspend fun updateProfile(displayName: String, username: String): ProfileDto {
        val userId = getCurrentOrCachedUserId() ?: error("No logged-in user")
        val optimistic = (cachedProfile ?: ProfileDto(id = userId)).copy(
            displayName = displayName,
            username = username,
        )

        // a restored local identity is enough to accept the edit. Supabase requests need a live
        // session, so queue this immediately when starting offline
        if (getCurrentUserId() == null) {
            cachedProfile = optimistic
            queueProfileUpdate(displayName, username)
            return optimistic
        }

        val updated = try {
            translateUsernameConflict(username) {
            supabaseClient
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
            }
        } catch (e: UsernameTakenException) {
            throw e
        } catch (e: Exception) {
            cachedProfile = optimistic
            queueProfileUpdate(displayName, username)
            return optimistic
        }

        if (updated != null) {
            cachedProfile = updated
            clearPendingProfileUpdate()
            return updated
        }

        // no row updated, insert one and return that
        val inserted = translateUsernameConflict(username) {
            supabaseClient
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
        } ?: error("profile insert returned no row")

        cachedProfile = inserted
        clearPendingProfileUpdate()
        return inserted
    }

    // retries an edit accepted while offline. safe to call on every reconnect
    suspend fun syncPendingProfileUpdate() {
        val displayName = prefs.getString(KEY_PENDING_DISPLAY_NAME, null) ?: return
        val username = prefs.getString(KEY_PENDING_USERNAME, null) ?: return
        if (getCurrentUserId() == null) return
        updateProfile(displayName, username)
    }

    fun hasPendingProfileUpdate(): Boolean =
        prefs.contains(KEY_PENDING_DISPLAY_NAME) && prefs.contains(KEY_PENDING_USERNAME)

    private fun queueProfileUpdate(displayName: String, username: String) {
        prefs.edit()
            .putString(KEY_PENDING_DISPLAY_NAME, displayName)
            .putString(KEY_PENDING_USERNAME, username)
            .apply()
    }

    private fun clearPendingProfileUpdate() {
        prefs.edit().remove(KEY_PENDING_DISPLAY_NAME).remove(KEY_PENDING_USERNAME).apply()
    }

    // Postgres 23505 (unique_violation) is what the DB-side unique index on LOWER(username)
    // raises for a duplicate. surface it typed so the UI can say 'username is already taken'
    // instead of showing a raw REST error
    private inline fun <T> translateUsernameConflict(username: String, block: () -> T): T {
        return try {
            block()
        } catch (e: Exception) {
            val msg = e.message.orEmpty()
            val isConflict = msg.contains("23505") ||
                msg.contains("duplicate key", ignoreCase = true) ||
                msg.contains("profiles_username", ignoreCase = true)
            if (isConflict) throw UsernameTakenException(username) else throw e
        }
    }

    // null when no profile row exists yet, which callers should read as first-time login rather
    // than an error. use ensureProfileExists() when you need a guaranteed row
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

    // case-insensitive, ignoring the user's own row. uses the is_username_available RPC
    // (SECURITY DEFINER) so RLS on profiles doesn't hide other users' rows. falls back to a
    // direct SELECT if the RPC isn't installed, which is only accurate under a permissive
    // policy: the unique index is the real source of truth
    suspend fun isUsernameAvailable(username: String): Boolean {
        val trimmed = username.trim()
        if (trimmed.isEmpty()) return false

        // preferred path, needs the SQL below applied in Supabase
        val rpcResult = runCatching {
            supabaseClient.postgrest
                .rpc(
                    "is_username_available",
                    buildJsonObject { put("candidate", JsonPrimitive(trimmed)) },
                )
                .decodeAs<Boolean>()
        }
        if (rpcResult.isSuccess) return rpcResult.getOrNull() ?: true

        // fallback direct query. under strict RLS this can return false negatives, saying available
        // for a name someone else already holds. the unique index still enforces it on save
        val currentUserId = getCurrentUserId()
        val lower = trimmed.lowercase()
        return try {
            val matches = supabaseClient
                .from("profiles")
                .select(Columns.list("id", "username")) {
                    filter { ilike("username", trimmed) }
                    limit(2)
                }
                .decodeList<ProfileDto>()
            matches.none { it.id != currentUserId && it.username?.lowercase() == lower }
        } catch (_: Exception) {
            true
        }
    }

    // null when there is no session, no profile row, or the column is null
    suspend fun getLinkedGoogleEmail(): String? {
        val userId = getCurrentUserId() ?: return null
        return runCatching {
            supabaseClient
                .from("profiles")
                .select(Columns.list("linked_google_email")) {
                    filter { eq("id", userId) }
                    limit(1)
                }
                .decodeList<LinkedGoogleEmailRow>()
                .firstOrNull()
                ?.linkedGoogleEmail
        }.onFailure {
            android.util.Log.w("AuthRepository", "getLinkedGoogleEmail failed: ${it.message}")
        }.getOrNull()
    }

    // pass null to disconnect. silently no-ops when there is no session
    suspend fun setLinkedGoogleEmail(email: String?) {
        val userId = getCurrentUserId() ?: return
        supabaseClient
            .from("profiles")
            .update(LinkedGoogleEmailPatch(linkedGoogleEmail = email)) {
                filter { eq("id", userId) }
            }
    }

    // connection metadata only, raw health measurements never enter profiles
    suspend fun setHealthConnection(
        linked: Boolean,
        connectedAt: String?,
        lastSyncedAt: String?,
        dataTypes: List<String>,
    ) {
        val userId = getCurrentUserId() ?: return
        supabaseClient.from("profiles").update(
            HealthConnectionPatch(linked, connectedAt, lastSyncedAt, dataTypes),
        ) {
            filter { eq("id", userId) }
        }
    }

    private fun Map<String, JsonElement>.stringField(key: String): String? {
        val element = this[key] ?: return null
        if (element !is JsonPrimitive) return null
        if (!element.isString) return null
        val value = element.content.trim()
        return value.ifEmpty { null }
    }

    private companion object {
        const val PROFILE_PREFS_NAME = "auth_profile_cache"
        const val KEY_CACHED_PROFILE = "cached_profile"
        const val KEY_PENDING_DISPLAY_NAME = "pending_display_name"
        const val KEY_PENDING_USERNAME = "pending_username"
    }
}
