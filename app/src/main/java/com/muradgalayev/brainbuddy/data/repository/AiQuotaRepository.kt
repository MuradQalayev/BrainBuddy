package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

// the outcome of asking permission to send one assistant message. used and dailyLimit come back
// from the server on both answers, so the UI can say '3 left today' without keeping its own
// count, which would drift the moment the user has a second device
data class QuotaDecision(
    val allowed: Boolean,
    val used: Int,
    val dailyLimit: Int,
) {
    val remaining: Int get() = (dailyLimit - used).coerceAtLeast(0)
}

@Serializable
private data class QuotaRowDto(
    val allowed: Boolean,
    val used: Int,
    @SerialName("daily_limit") val dailyLimit: Int,
)

// daily message allowance, counted and enforced in Postgres. the app deliberately holds no copy
// of the limit: consume_ai_message() owns the number and returns it, because a limit the client
// knows is a limit the client can argue with
@Singleton
class AiQuotaRepository @Inject constructor(
    private val supabase: SupabaseClient,
) {

    // claims one message from today's allowance. fails open: if Supabase is unreachable or the call
    // throws, this returns allowed, because a hiccup in our own backend must not take the assistant
    // down with it. the exposure is bounded, since reaching the model needs a working network
    // anyway, so nobody can sit offline and mine free messages. failing closed would trade a rare
    // billing overrun for a common outage, which is the worse of the two
    suspend fun consume(): QuotaDecision = withContext(Dispatchers.IO) {
        try {
            val row = supabase.postgrest
                .rpc("consume_ai_message")
                .decodeAs<List<QuotaRowDto>>()
                .firstOrNull()
            if (row == null) {
                Log.w(TAG, "consume_ai_message returned no row — allowing")
                return@withContext QuotaDecision(allowed = true, used = 0, dailyLimit = 0)
            }
            QuotaDecision(row.allowed, row.used, row.dailyLimit)
        } catch (e: Exception) {
            Log.w(TAG, "Quota check failed — allowing this turn", e)
            QuotaDecision(allowed = true, used = 0, dailyLimit = 0)
        }
    }

    private companion object {
        const val TAG = "AiQuotaRepository"
    }
}
