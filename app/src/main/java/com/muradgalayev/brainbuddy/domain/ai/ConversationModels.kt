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
    val createdAt: Long = System.currentTimeMillis()
)
