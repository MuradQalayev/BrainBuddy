package com.muradgalayev.brainbuddy.domain.ai.offline

import android.util.Log
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton
import com.muradgalayev.brainbuddy.R

// executes a filled-in OfflineAction by calling the very same AiTool the online assistant would
// have called. no model in this path and no network call: the user has already made every
// decision a model would have made, so all that's left is assembling the arguments. that's the
// whole trick behind offline mode, since the expensive part of an assistant is choosing the
// tool and its arguments, and a short list of taps chooses them exactly
@Singleton
class OfflineActionRunner @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val tools: Set<@JvmSuppressWildcards AiTool>,
) {
    private val toolByName by lazy { tools.associateBy { it.name } }

    // values maps slot key to what the user picked, already resolved to ISO dates, HH:mm times and
    // enum names. blank entries are dropped so an untouched optional slot doesn't overwrite
    // anything with an empty string
    suspend fun run(action: OfflineAction, values: Map<String, String>): OfflineActionResult {
        val missing = action.slots
            .filterNot { it.optional }
            .filter { values[it.key].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            return OfflineActionResult.Failed(
                context.getString(R.string.offline_still_need, missing.joinToString { it.label.lowercase() })
            )
        }

        val tool = toolByName[action.toolName]
            ?: return OfflineActionResult.Failed(context.getString(R.string.offline_unavailable))

        val args = buildArgs(action, values)
        return runCatching { tool.execute(args) }
            .fold(
                onSuccess = { message ->
                    // tools report their own failures as a plain string starting with 'Failed' rather than
                    // throwing, so surface those as failures too, or a confirmation screen would claim success
                    if (message.startsWith("Failed", ignoreCase = true)) {
                        OfflineActionResult.Failed(message.removePrefix("Failed:").trim())
                    } else {
                        OfflineActionResult.Done(action.confirmation ?: message, queuedForSync = action.syncs)
                    }
                },
                onFailure = {
                    Log.w(TAG, "offline action ${action.id} failed", it)
                    OfflineActionResult.Failed(
                        it.message ?: context.getString(R.string.offline_failed_generic)
                    )
                },
            )
    }

    private fun buildArgs(action: OfflineAction, values: Map<String, String>): JsonObject =
        buildJsonObject {
            action.fixedArgs.forEach { (key, raw) -> putTyped(key, raw) }
            action.slots.forEach { slot ->
                val raw = values[slot.key]?.trim()
                if (!raw.isNullOrBlank()) putTyped(slot.key, raw)
            }
        }

    // slot values travel as strings because that's what a chip carries, but the tool schemas
    // declare integers and booleans. coerce on the way in so focus_duration_minutes arrives as 25
    // and not '25': several tools read those with toIntOrNull and would silently drop a quoted number
    private fun kotlinx.serialization.json.JsonObjectBuilder.putTyped(key: String, raw: String) {
        val asLong = raw.toLongOrNull()
        when {
            asLong != null -> put(key, asLong)
            raw.equals("true", ignoreCase = true) -> put(key, true)
            raw.equals("false", ignoreCase = true) -> put(key, false)
            else -> put(key, raw)
        }
    }

    private companion object {
        const val TAG = "OfflineActionRunner"
    }
}
