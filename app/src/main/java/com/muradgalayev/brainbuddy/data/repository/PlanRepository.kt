package com.muradgalayev.brainbuddy.data.repository

import android.content.Context
import android.util.Log
import com.muradgalayev.brainbuddy.domain.model.Plan
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
private data class PlanRowDto(val plan: String)

// which plan this account is on. the server owns the answer: public.user_plans can only be
// written by join_plus_beta() and, later, billing, so the app reads and never writes it.
// the last answer is kept per account on the device, so a Plus user's buttons are unlocked on
// the first frame of a cold start instead of flashing locked until the network replies
@Singleton
class PlanRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _plan = MutableStateFlow(cachedPlanFor(authRepository.getCurrentOrCachedUserId()))
    val plan: StateFlow<Plan> = _plan.asStateFlow()

    // from SyncCoordinator. a failed read keeps what we had: being offline mustn't lock someone out
    // of what they already have
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId() ?: return@withContext
        runCatching {
            supabase.from(TABLE)
                .select { filter { eq("user_id", userId) } }
                .decodeList<PlanRowDto>()
                .firstOrNull()
        }.onSuccess { row -> remember(userId, Plan.fromStored(row?.plan)) }
            .onFailure { Log.w(TAG, "Plan refresh failed, keeping ${_plan.value}: ${it.message}") }
    }

    // the beta's free join. the function upgrades the caller server-side and returns the result
    suspend fun joinBeta(): Result<Plan> = withContext(Dispatchers.IO) {
        runCatching {
            val userId = authRepository.getCurrentUserId() ?: error("Not signed in")
            val row = supabase.postgrest
                .rpc("join_plus_beta")
                .decodeList<PlanRowDto>()
                .firstOrNull()
            Plan.fromStored(row?.plan).also { remember(userId, it) }
        }.onFailure { Log.w(TAG, "Joining the beta failed: ${it.message}") }
    }

    fun clearForSignOut() {
        prefs.edit().clear().apply()
        _plan.value = Plan.Free
    }

    private fun remember(userId: String, plan: Plan) {
        prefs.edit().putString(KEY_USER, userId).putString(KEY_PLAN, plan.name).apply()
        _plan.value = plan
    }

    // only trusted for the account it was saved for, so a switch of accounts starts at Free
    private fun cachedPlanFor(userId: String?): Plan {
        if (userId == null || prefs.getString(KEY_USER, null) != userId) return Plan.Free
        return if (prefs.getString(KEY_PLAN, null) == Plan.Plus.name) Plan.Plus else Plan.Free
    }

    private companion object {
        const val TAG = "PlanRepository"
        const val TABLE = "user_plans"
        const val PREFS = "plan_cache"
        const val KEY_USER = "user_id"
        const val KEY_PLAN = "plan"
    }
}
