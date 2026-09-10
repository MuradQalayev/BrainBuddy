package com.muradgalayev.brainbuddy.domain.ai

import kotlinx.serialization.json.JsonObject

// provider-agnostic AI interface. to change provider, write a new implementation and rebind in
// AiModule, nothing else changes. the choice is constrained though: see the binding in AiModule
// for why the provider has to be EU-hosted rather than merely capable
interface AiClient {
    suspend fun send(
        history: List<ChatMessage>,
        tools: List<AiTool>,
        systemPrompt: String
    ): AiResponse
}

sealed class AiResponse {
    // model wants to chat
    data class Text(val text: String) : AiResponse()

    // model wants to call a tool
    data class ToolCall(
        val toolName: String,
        val args: JsonObject
    ) : AiResponse()

    // network or parse failure
    data class Error(val message: String) : AiResponse()
}
