package com.muradgalayev.brainbuddy.data.ai

import com.muradgalayev.brainbuddy.domain.ai.AiClient
import com.muradgalayev.brainbuddy.domain.ai.AiResponse
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import com.muradgalayev.brainbuddy.domain.ai.ChatMessage
import com.muradgalayev.brainbuddy.domain.ai.ChatRole

// wraps the real AiClient and scrubs the user's direct identifiers from every outbound request.
// bound in AiModule as the only AiClient the graph exposes, so changing provider, or adding a
// second one later, can't accidentally route around it. that's the point of doing it here
// rather than in the ViewModel: there is one door out to a third party, and this is it.
// still required even though the provider is EU-hosted: keeping the data in the EU answers
// where it goes, not what goes, and a direct identifier in a prompt is a disclosure either way.
// redaction is outbound-only, so the UI and the persisted history keep the user's real words
class RedactingAiClient(
    private val delegate: AiClient,
    private val redactor: PiiRedactor,
) : AiClient {

    override suspend fun send(
        history: List<ChatMessage>,
        tools: List<AiTool>,
        systemPrompt: String,
    ): AiResponse = delegate.send(
        history = history.map(::redact),
        tools = tools,
        // the system prompt carries the ADHD profile, whose free-text pain_point is written by the
        // user and routinely contains their own name
        systemPrompt = redactor.redactKnownIdentifiers(systemPrompt),
    )

    private fun redact(message: ChatMessage): ChatMessage = message.copy(
        text = when (message.role) {
            // only what the user typed gets the generic email and phone sweep. tool results carry clinic
            // contact details that the contact tool needs verbatim
            ChatRole.USER -> redactor.redactUserText(message.text)
            else -> redactor.redactKnownIdentifiers(message.text)
        },
        toolArgs = message.toolArgs?.let(redactor::redactJson),
        toolResult = message.toolResult?.let(redactor::redactKnownIdentifiers),
    )
}
