package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

// tells the backend where to reach this device. registration is idempotent and cheap, so it
// runs on every launch as well as on token rotation. the alternative, register once and trust
// it, quietly stops working after a reinstall or a Google-initiated rotation, and the symptom
// is 'push just doesn't arrive for some users', which is close to undiagnosable from outside
@Singleton
class DeviceTokenRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
) {

    // fetches the current FCM token and claims it for the signed-in user
    suspend fun registerCurrentDevice() {
        val token = currentToken() ?: return
        register(token)
    }

    suspend fun register(token: String) = withContext(Dispatchers.IO) {
        // no user, so nothing to attach it to. sign-in calls this again
        if (authRepository.getCurrentUserId() == null) return@withContext
        runCatching {
            supabase.postgrest.rpc(
                "claim_device_token",
                buildJsonObject {
                    put("p_token", token)
                    put("p_platform", "android")
                },
            )
        }.onFailure { Log.w(TAG, "Couldn't register device token: ${it.message}") }
        Unit
    }

    // drops this device's token on sign-out. without it the next person to sign in on a shared
    // phone keeps receiving the previous account's invites until their own registration overwrites
    // the row
    suspend fun unregisterCurrentDevice() = withContext(Dispatchers.IO) {
        val token = currentToken() ?: return@withContext
        runCatching {
            supabase.postgrest.from("device_tokens").delete {
                filter { eq("token", token) }
            }
        }.onFailure { Log.w(TAG, "Couldn't unregister device token: ${it.message}") }
        Unit
    }

    private suspend fun currentToken(): String? = runCatching {
        suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    cont.resume(task.result?.takeIf { task.isSuccessful })
                }
        }
    }.getOrNull()

    private companion object {
        const val TAG = "DeviceTokenRepo"
    }
}
