package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.muradgalayev.brainbuddy.data.remote.SupabaseAiMessageDataSource
import com.muradgalayev.brainbuddy.data.remote.dto.AiMessageDto
import com.muradgalayev.brainbuddy.domain.ai.ChatMessage
import com.muradgalayev.brainbuddy.domain.ai.ChatRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConversationRepository @Inject constructor(
    private val remote: SupabaseAiMessageDataSource,
    private val authRepository: AuthRepository
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun loadHistory(): List<ChatMessage> = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId() ?: return@withContext emptyList()
        try {
            remote.getRecent(userId).map { it.toDomain() }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load conversation: ${e.message}")
            emptyList()
        }
    }

    suspend fun append(message: ChatMessage) = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId() ?: return@withContext
        try {
            remote.insert(message.toDto(userId))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save message: ${e.message}")
        }
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId() ?: return@withContext
        try {
            remote.deleteAllForUser(userId)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to clear conversation: ${e.message}")
        }
    }

    private fun ChatMessage.toDto(userId: String): AiMessageDto = AiMessageDto(
        id = id,
        userId = userId,
        role = role.name,
        text = text,
        toolName = toolName,
        toolArgs = toolArgs?.let { json.encodeToString(JsonObject.serializer(), it) },
        toolResult = toolResult
    )

    private fun AiMessageDto.toDomain(): ChatMessage = ChatMessage(
        id = id,
        role = runCatching { ChatRole.valueOf(role) }.getOrDefault(ChatRole.USER),
        text = text,
        toolName = toolName,
        toolArgs = toolArgs?.let {
            runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull()
        },
        toolResult = toolResult
    )

    companion object {
        private const val TAG = "ConversationRepository"
    }
}
