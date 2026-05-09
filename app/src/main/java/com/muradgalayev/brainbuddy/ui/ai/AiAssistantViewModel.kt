package com.muradgalayev.brainbuddy.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.network.NetworkObserver
import com.muradgalayev.brainbuddy.data.repository.ConversationRepository
import com.muradgalayev.brainbuddy.domain.ai.AiClient
import com.muradgalayev.brainbuddy.domain.ai.AiResponse
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import com.muradgalayev.brainbuddy.domain.ai.ChatMessage
import com.muradgalayev.brainbuddy.domain.ai.ChatRole
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

data class AiUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isThinking: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    private val aiClient: AiClient,
    private val conversationRepository: ConversationRepository,
    private val tools: Set<@JvmSuppressWildcards AiTool>,
    networkObserver: NetworkObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    /** True when the device has a validated internet connection. */
    val isOnline: StateFlow<Boolean> = networkObserver.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    private val toolByName = tools.associateBy { it.name }

    init {
        loadHistory()
    }

    private fun loadHistory() {
        viewModelScope.launch {
            val history = conversationRepository.loadHistory()
            _uiState.update { it.copy(messages = history) }
        }
    }

    fun send(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _uiState.value.isThinking) return

        val userMsg = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.USER,
            text = trimmed
        )
        appendAndPersist(userMsg)
        _uiState.update { it.copy(isThinking = true, error = null) }

        viewModelScope.launch {
            runAgentLoop()
            _uiState.update { it.copy(isThinking = false) }
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            conversationRepository.clear()
            _uiState.update { it.copy(messages = emptyList(), error = null) }
        }
    }

    private suspend fun runAgentLoop() {
        // Cap iterations so a misbehaving model can't loop forever.
        repeat(MAX_TOOL_HOPS) {
            val history = _uiState.value.messages
            when (val response = aiClient.send(history, tools.toList(), systemPrompt())) {
                is AiResponse.Text -> {
                    appendAndPersist(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.ASSISTANT,
                            text = response.text
                        )
                    )
                    return
                }
                is AiResponse.ToolCall -> {
                    // Record the call as an assistant turn so the model sees its own action.
                    appendAndPersist(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.ASSISTANT,
                            text = "",
                            toolName = response.toolName,
                            toolArgs = response.args
                        )
                    )
                    val tool = toolByName[response.toolName]
                    val result = if (tool == null) {
                        "Unknown tool: ${response.toolName}"
                    } else {
                        runCatching { tool.execute(response.args) }
                            .getOrElse { "Tool error: ${it.message}" }
                    }
                    appendAndPersist(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.TOOL,
                            text = "",
                            toolName = response.toolName,
                            toolResult = result
                        )
                    )
                    // Loop: feed result back to model for the next response.
                }
                is AiResponse.Error -> {
                    _uiState.update { it.copy(error = response.message) }
                    return
                }
            }
        }
    }

    private fun appendAndPersist(msg: ChatMessage) {
        _uiState.update { it.copy(messages = it.messages + msg) }
        viewModelScope.launch { conversationRepository.append(msg) }
    }

    private fun systemPrompt(): String {
        val today = LocalDate.now().toString()
        val context = buildContext()
        return """
            You are BrainBuddy — a warm, easy-going assistant who helps the user
            stay on top of their day. You talk like a friend, not a form.
            Today is $today.

            $context

            How you talk:
              • Casual and human. Contractions are fine. A little personality is good.
              • Short messages. One or two sentences. Never bullet lists.
              • When you need info, ask naturally — "who's the meeting with?",
                "around what time?" — not "Please provide the following fields".
              • If something seems off given what you know about the user (mood,
                schedule, recent context above), gently say so before acting.
                Example: "you said yesterday was rough — sure you want a 7am
                meeting tomorrow?". Never lecture; just check in.

            Creating events (create_todo tool):
              Don't create on a vague first message. If the user says
              "I have a meeting tomorrow", that's not enough — you don't know
              who or when. Ask first, in your own friendly words, combining
              questions where natural ("nice — who's it with, and what time?").
              Only call create_todo once you have a real title, a date
              (yyyy-MM-dd), and either a time (HH:mm) or the user explicitly
              saying it's all-day / no specific time. After it's created,
              confirm in one short line.

            Updates: if they refine something you just made ("actually 4, not 3"),
            use update_todo with the id from your last create. Use delete_todo
            only when they clearly want it gone.
        """.trimIndent()
    }

    /**
     * Extension point for richer context — mood, recent journal entries,
     * upcoming calendar density, focus stats, etc. For now this is empty;
     * later, inject the relevant repositories into this ViewModel and have
     * them contribute a short paragraph here. The system prompt picks it up
     * automatically.
     */
    private fun buildContext(): String = ""

    companion object {
        private const val MAX_TOOL_HOPS = 5
    }
}
