package com.muradgalayev.brainbuddy.domain.ai

import kotlinx.serialization.json.JsonObject

enum class ChatRole { USER, ASSISTANT, TOOL }

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val text: String,
    val toolName: String? = null,
    val toolArgs: JsonObject? = null,
    val toolResult: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Nullable to keep legacy rows loadable — new messages always have one. */
    val conversationId: String? = null,
)

/**
 * A compact summary of a past conversation, built by grouping messages by
 * [ChatMessage.conversationId]. Used in the history sheet.
 */
data class ConversationSummary(
    val id: String,
    val title: String,
    val messageCount: Int,
    val lastActivityAt: Long,
    val isActive: Boolean,
)
