package com.muradgalayev.brainbuddy.domain.ai

import kotlinx.serialization.json.JsonObject

/**
 * Provider-agnostic AI interface. To swap Gemini for OpenAI/Claude later,
 * write a new implementation and rebind in AiModule. Nothing else changes.
 */
interface AiClient {
    suspend fun send(
        history: List<ChatMessage>,
        tools: List<AiTool>,
        systemPrompt: String
    ): AiResponse
}

sealed class AiResponse {
    /** Model wants to chat. */
    data class Text(val text: String) : AiResponse()

    /** Model wants to call a tool. */
    data class ToolCall(
        val toolName: String,
        val args: JsonObject
    ) : AiResponse()

    /** Network or parse failure. */
    data class Error(val message: String) : AiResponse()
}
