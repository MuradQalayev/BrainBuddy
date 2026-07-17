package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.remote.SupabaseAiMessageDataSource
import com.muradgalayev.brainbuddy.data.remote.dto.AiMessageDto
import com.muradgalayev.brainbuddy.domain.ai.ChatMessage
import com.muradgalayev.brainbuddy.domain.ai.ChatRole
import com.muradgalayev.brainbuddy.domain.ai.ConversationSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.time.OffsetDateTime
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val remote: SupabaseAiMessageDataSource,
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Emits the active-conversation id for the current user. Null means "no
     * conversation is active — the next message will start a new one".
     */
    fun activeConversationIdFlow(): Flow<String?> {
        val userId = authRepository.getCurrentUserId() ?: return flowOf(null)
        return preferencesManager.activeConversationIdFlow(userId)
    }

    /**
     * Return the current active id, creating one if none exists yet. Called by
     * AiAssistantViewModel right before persisting the first message of a turn.
     */
    suspend fun ensureActiveConversationId(): String = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId()
            ?: return@withContext newConversationId()
        val existing = preferencesManager.activeConversationIdFlow(userId).first()
        existing ?: newConversationId().also {
            preferencesManager.setActiveConversationId(userId, it)
        }
    }

    /** Force-end the current active chat. The next send will mint a fresh id. */
    suspend fun endActiveConversation() = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId() ?: return@withContext
        preferencesManager.setActiveConversationId(userId, null)
    }

    /** Switch to a specific past conversation (making it active again). */
    suspend fun setActiveConversation(id: String) = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId() ?: return@withContext
        preferencesManager.setActiveConversationId(userId, id)
    }

    /**
     * Load history for the currently active conversation. Returns empty when
     * there's no active chat.
     */
    suspend fun loadActiveHistory(): List<ChatMessage> = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId() ?: return@withContext emptyList()
        val activeId = readActiveId(userId) ?: return@withContext emptyList()
        loadHistoryForConversation(activeId)
    }

    suspend fun loadHistoryForConversation(id: String): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            val userId = authRepository.getCurrentUserId() ?: return@withContext emptyList()
            try {
                remote.getForConversation(userId, id).map { it.toDomain() }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load conversation $id: ${e.message}")
                emptyList()
            }
        }

    suspend fun append(message: ChatMessage) = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId() ?: return@withContext
        val conversationId = message.conversationId
            ?: run {
                Log.w(TAG, "Refusing to persist message without conversationId")
                return@withContext
            }
        try {
            remote.insert(message.toDto(userId, conversationId))
        } catch (e: Exception) {
            // Always log the real error text so we can see WHY insert failed.
            Log.e(TAG, "insert failed: ${e.message}", e)

            // Only fall back for the very specific "column missing" case. This
            // is the one where Postgrest's schema cache says the column isn't
            // there — everything else (RLS violations, network, type errors,
            // etc.) should surface as a normal failure.
            val msg = e.message.orEmpty().lowercase()
            val looksLikeMissingColumn =
                msg.contains("pgrst204") ||
                msg.contains("could not find the 'conversation_id' column") ||
                msg.contains("column \"conversation_id\" does not exist") ||
                msg.contains("column 'conversation_id' does not exist")

            if (looksLikeMissingColumn) {
                Log.e(
                    TAG,
                    "conversation_id column looks missing on ai_messages — " +
                        "retrying insert without it. Run the SQL migration!",
                )
                runCatching {
                    remote.insert(message.toDto(userId, null))
                }.onFailure {
                    Log.w(TAG, "Fallback insert also failed: ${it.message}")
                }
            }
        }
    }

    private fun ChatMessage.toDto(userId: String, conversationId: String?): AiMessageDto =
        AiMessageDto(
            id = id,
            userId = userId,
            role = role.name,
            text = text,
            toolName = toolName,
            toolArgs = toolArgs?.let { json.encodeToString(JsonObject.serializer(), it) },
            toolResult = toolResult,
            conversationId = conversationId,
        )

    /**
     * Fetch recent messages across all conversations and group them client-side
     * into summaries. Cheap for reasonable histories (< a few hundred messages).
     */
    suspend fun listConversationSummaries(): List<ConversationSummary> =
        withContext(Dispatchers.IO) {
            val userId = authRepository.getCurrentUserId() ?: return@withContext emptyList()
            val activeId = readActiveId(userId)
            val rows = try {
                remote.getAllForSummaries(userId)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch summaries: ${e.message}")
                return@withContext emptyList()
            }
            rows
                // Legacy rows with no conversation_id are treated as one
                // implicit "old conversation" so they don't just vanish.
                .groupBy { it.conversationId ?: LEGACY_CONVERSATION_ID }
                .map { (id, msgs) ->
                    val firstUser = msgs
                        .sortedBy { parseTs(it.createdAt) }
                        .firstOrNull { it.role.equals("USER", ignoreCase = true) && it.text.isNotBlank() }
                    val lastTs = msgs.maxOf { parseTs(it.createdAt) }
                    ConversationSummary(
                        id = id,
                        title = firstUser?.text?.take(48)?.trim()?.ifBlank { null }
                            ?: if (id == LEGACY_CONVERSATION_ID) "Older chats" else "New chat",
                        messageCount = msgs.count {
                            it.role.equals("USER", ignoreCase = true) ||
                                it.role.equals("ASSISTANT", ignoreCase = true)
                        },
                        lastActivityAt = lastTs,
                        isActive = id == activeId,
                    )
                }
                .sortedByDescending { it.lastActivityAt }
        }

    suspend fun deleteConversation(id: String) = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId() ?: return@withContext
        try {
            remote.deleteConversation(userId, id)
            if (readActiveId(userId) == id) {
                preferencesManager.setActiveConversationId(userId, null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to delete conversation: ${e.message}")
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId() ?: return@withContext
        try {
            remote.deleteAllForUser(userId)
            preferencesManager.setActiveConversationId(userId, null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear conversation: ${e.message}")
        }
    }

    private suspend fun readActiveId(userId: String): String? =
        preferencesManager.activeConversationIdFlow(userId).first()

    private fun newConversationId(): String = UUID.randomUUID().toString()

    private fun parseTs(iso: String?): Long =
        iso?.let { runCatching { OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() }
            ?: 0L

    private fun AiMessageDto.toDomain(): ChatMessage = ChatMessage(
        id = id,
        role = runCatching { ChatRole.valueOf(role) }.getOrDefault(ChatRole.USER),
        text = text,
        toolName = toolName,
        toolArgs = toolArgs?.let {
            runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull()
        },
        toolResult = toolResult,
        conversationId = conversationId,
    )

    companion object {
        private const val TAG = "ConversationRepository"
        private const val LEGACY_CONVERSATION_ID = "__legacy__"
    }
}
