package com.muradgalayev.brainbuddy.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AiMessageDto(
    val id: String,
    @SerialName("user_id")
    val userId: String,
    val role: String,
    val text: String = "",
    @SerialName("tool_name")
    val toolName: String? = null,
    @SerialName("tool_args")
    val toolArgs: String? = null,
    @SerialName("tool_result")
    val toolResult: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    @SerialName("conversation_id")
    val conversationId: String? = null,
    // short generated label for the conversation, stamped on all of its rows
    val title: String? = null,
)
