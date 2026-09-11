package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.remote.SupabaseAiMessageDataSource
import com.muradgalayev.brainbuddy.data.remote.dto.AiMessageDto
import com.muradgalayev.brainbuddy.domain.ai.AiClient
import com.muradgalayev.brainbuddy.domain.ai.AiResponse
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
    private val aiClient: AiClient,
    private val backendTitle: com.muradgalayev.brainbuddy.data.ai.BackendConversationTitle,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // null means no conversation is active, and the next message will start a new one
    fun activeConversationIdFlow(): Flow<String?> {
        val userId = authRepository.getCurrentUserId() ?: return flowOf(null)
        return preferencesManager.activeConversationIdFlow(userId)
    }

    // returns the current active id, creating one if none exists yet. called right before
    // persisting the first message of a turn
    suspend fun ensureActiveConversationId(): String = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId()
            ?: return@withContext newConversationId()
        val existing = preferencesManager.activeConversationIdFlow(userId).first()
        existing ?: newConversationId().also {
            preferencesManager.setActiveConversationId(userId, it)
        }
    }

    // force-ends the current active chat. the next send mints a fresh id
    suspend fun endActiveConversation() = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId() ?: return@withContext
        preferencesManager.setActiveConversationId(userId, null)
    }

    // switch to a specific past conversation, making it active again
    suspend fun setActiveConversation(id: String) = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId() ?: return@withContext
        preferencesManager.setActiveConversationId(userId, id)
    }

    // history for the currently active conversation. empty when there's no active chat
    suspend fun loadActiveHistory(): List<ChatMessage> = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId() ?: return@withContext emptyList()
        val activeId = readActiveId(userId) ?: return@withContext emptyList()
        loadHistoryForConversation(activeId)
    }

    suspend fun loadHistoryForConversation(id: String): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            val userId = authRepository.getCurrentUserId() ?: return@withContext emptyList()
            try {
                remote.getRecentForConversation(userId, id).map { it.toDomain() }
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
            // log the real error text so we can see why the insert failed
            Log.e(TAG, "insert failed: ${e.message}", e)

            // only fall back for the specific column-missing case, where Postgrest's schema cache says the
            // column isn't there. everything else (RLS violations, network, type errors) should surface as
            // a normal failure
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

    // fetches recent messages across all conversations and groups them client-side into summaries.
    // cheap for reasonable histories, under a few hundred messages
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
                // legacy rows with no conversation_id become one implicit old conversation, so they don't vanish
                .groupBy { it.conversationId ?: LEGACY_CONVERSATION_ID }
                .map { (id, msgs) ->
                    val firstUser = msgs
                        .sortedBy { parseTs(it.createdAt) }
                        .firstOrNull { it.role.equals("USER", ignoreCase = true) && it.text.isNotBlank() }
                    val lastTs = msgs.maxOf { parseTs(it.createdAt) }
                    // prefer the title stamped on the rows, fall back to the first user message, since older chats
                    // predate title generation
                    val storedTitle = msgs.firstNotNullOfOrNull { it.title?.trim()?.ifBlank { null } }
                    ConversationSummary(
                        id = id,
                        title = storedTitle
                            ?: firstUser?.text?.take(48)?.trim()?.ifBlank { null }
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

    // generates a short title for a conversation and stamps it onto its rows, unless one already
    // exists. safe to call after every turn, it no-ops once titled. best-effort: any failure
    // leaves the first-message fallback in place
    suspend fun ensureConversationTitle(conversationId: String) = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId() ?: return@withContext
        val rows = runCatching { remote.getForConversation(userId, conversationId) }
            .getOrNull().orEmpty()
        if (rows.isEmpty()) return@withContext
        // already titled, nothing to do
        if (rows.any { !it.title.isNullOrBlank() }) return@withContext

        val firstUser = rows
            .firstOrNull { it.role.equals("USER", ignoreCase = true) && it.text.isNotBlank() }
            ?.text ?: return@withContext
        val firstAssistant = rows
            .firstOrNull { it.role.equals("ASSISTANT", ignoreCase = true) && it.text.isNotBlank() }
            ?.text

        val title = generateTitle(firstUser, firstAssistant) ?: return@withContext
        runCatching { remote.updateConversationTitle(userId, conversationId, title) }
            .onFailure { Log.w(TAG, "Failed to store conversation title: ${it.message}") }
    }

    private suspend fun generateTitle(firstUser: String, firstAssistant: String?): String? {
        val convo = buildString {
            append("User: ").append(firstUser.take(500))
            if (!firstAssistant.isNullOrBlank()) {
                append("\nAssistant: ").append(firstAssistant.take(500))
            }
        }
        val prompt = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            text = "Give this chat a very short title (3–4 words), in the same language the " +
                "user used. Reply with ONLY the title — no quotes, no trailing punctuation, " +
                "no emoji.\n\n$convo",
        )
        if (backendTitle.enabled) {
            val raw = runCatching { backendTitle.generate(prompt.text) }.getOrNull() ?: return null
            return sanitizeTitle(raw)
        }
        val response = runCatching { aiClient.send(listOf(prompt), emptyList(), TITLE_SYSTEM_PROMPT) }
            .getOrNull()
        val raw = (response as? AiResponse.Text)?.text ?: return null
        return sanitizeTitle(raw)
    }

    private fun sanitizeTitle(raw: String): String? {
        // drop any chip line the model might tack on, keep the first real line only
        var t = raw.substringBefore("[options:").trim()
        t = t.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() } ?: return null
        t = t.trim('"', '\'', '“', '”', '‘', '’').trim()
        t = t.trimEnd('.', '!', '?', ',', ':', ';').trim()
        if (t.isBlank()) return null
        return t.take(48)
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
        private const val TITLE_SYSTEM_PROMPT =
            "You generate ultra-short titles for chat conversations. Output ONLY the " +
                "title text (3–4 words), nothing else — no quotes, no punctuation, no emoji."
    }
}
