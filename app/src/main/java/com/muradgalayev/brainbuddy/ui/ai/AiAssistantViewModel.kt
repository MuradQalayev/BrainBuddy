package com.muradgalayev.brainbuddy.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.network.NetworkObserver
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.data.repository.ConversationRepository
import com.muradgalayev.brainbuddy.domain.ai.AiClient
import com.muradgalayev.brainbuddy.domain.ai.AiResponse
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import com.muradgalayev.brainbuddy.domain.ai.ChatMessage
import com.muradgalayev.brainbuddy.domain.ai.ChatRole
import com.muradgalayev.brainbuddy.domain.ai.ConversationSummary
import com.muradgalayev.brainbuddy.domain.model.AdhdProfile
import com.muradgalayev.brainbuddy.domain.model.AiTone
import com.muradgalayev.brainbuddy.domain.model.ProductiveTime
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
    private val adhdProfileRepository: AdhdProfileRepository,
    private val authRepository: AuthRepository,
    private val tools: Set<@JvmSuppressWildcards AiTool>,
    networkObserver: NetworkObserver
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    /** True when the device has a validated internet connection. */
    val isOnline: StateFlow<Boolean> = networkObserver.isOnline
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /**
     * Cached ADHD profile fed into every prompt. Seeded synchronously from the
     * repository's in-memory cache so the very first message already has context.
     * A background fetch updates it whenever the profile changes (e.g. the user
     * finishes the deep-dive survey).
     */
    private val _profile = MutableStateFlow(adhdProfileRepository.peekProfile())
    private val toolByName = tools.associateBy { it.name }

    private val _conversations = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val conversations: StateFlow<List<ConversationSummary>> = _conversations.asStateFlow()

    /** True when the active conversation exists and has at least one visible message. */
    val hasActiveChat: StateFlow<Boolean> =
        kotlinx.coroutines.flow.combine(
            conversationRepository.activeConversationIdFlow(),
            _uiState,
        ) { activeId, state ->
            activeId != null && state.messages.any {
                it.role != ChatRole.TOOL && it.text.isNotBlank()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    init {
        loadActiveHistory()
        // Only hit Supabase for the ADHD profile on the very first ViewModel
        // creation of the process. peekProfile() returns non-null after any
        // prior fetch in this process, meaning we're re-entering — no need
        // to fetch again and risk a flash while the AI card opens.
        if (adhdProfileRepository.peekProfile() == null) refreshProfile()
        refreshConversations()
        observeUserChanges()
    }

    /**
     * The AI ViewModel is scoped to the NavGraph back-stack entry and survives
     * sign-out on the same device. Without this, User A's on-screen chat would
     * still be visible to User B after they sign in. We watch `sessionStatus`
     * and reset all in-memory state the moment the auth user id flips.
     *
     * We don't care about the SessionStatus variant — only whether the current
     * user id (as reported by the repository) changed. AuthRepository.signOut
     * clears the current user, and the deep-link / password flow set it back.
     */
    private fun observeUserChanges() {
        viewModelScope.launch {
            var lastUserId: String? = authRepository.getCurrentUserId()
            authRepository.sessionStatus.collect {
                val newUserId = authRepository.getCurrentUserId()
                if (newUserId != lastUserId) {
                    lastUserId = newUserId
                    // Wipe UI-side state so no message, error, or conversation
                    // summary from the previous user leaks into this session.
                    _uiState.value = AiUiState()
                    _conversations.value = emptyList()
                    _profile.value = adhdProfileRepository.peekProfile()
                    if (newUserId != null) {
                        // Load the new user's own history + conversations.
                        loadActiveHistory()
                        refreshConversations()
                        if (adhdProfileRepository.peekProfile() == null) refreshProfile()
                    }
                }
            }
        }
    }

    private fun refreshProfile() {
        viewModelScope.launch {
            val fresh = runCatching { adhdProfileRepository.getProfile() }.getOrNull()
                ?: return@launch
            if (fresh != _profile.value) _profile.value = fresh
        }
    }

    private fun loadActiveHistory() {
        viewModelScope.launch {
            val history = conversationRepository.loadActiveHistory()
            _uiState.update { it.copy(messages = history) }
        }
    }

    fun refreshConversations() {
        viewModelScope.launch {
            _conversations.value = conversationRepository.listConversationSummaries()
        }
    }

    /**
     * End the current active chat. Doesn't delete history — just stops the UI
     * from showing it and makes the next send start a fresh conversation.
     */
    fun endChat() {
        viewModelScope.launch {
            conversationRepository.endActiveConversation()
            _uiState.update { it.copy(messages = emptyList(), error = null) }
            refreshConversations()
        }
    }

    /** Start a fresh chat immediately (ends the current if any). */
    fun startNewChat() = endChat()

    /** Switch back to a past conversation. Loads its full history into the UI. */
    fun switchToConversation(id: String) {
        viewModelScope.launch {
            conversationRepository.setActiveConversation(id)
            val history = conversationRepository.loadHistoryForConversation(id)
            _uiState.update { it.copy(messages = history, error = null) }
            refreshConversations()
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversationRepository.deleteConversation(id)
            if (_uiState.value.messages.firstOrNull()?.conversationId == id) {
                _uiState.update { it.copy(messages = emptyList()) }
            }
            refreshConversations()
        }
    }

    fun send(userText: String) {
        val trimmed = userText.trim()
        if (trimmed.isEmpty() || _uiState.value.isThinking) return

        _uiState.update { it.copy(isThinking = true, error = null) }
        viewModelScope.launch {
            // Ensure we have a conversation to stamp messages with. If the user
            // ended the previous chat, this mints a fresh one.
            val convId = conversationRepository.ensureActiveConversationId()
            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatRole.USER,
                text = trimmed,
                conversationId = convId,
            )
            appendAndPersist(userMsg)
            runAgentLoop(convId)
            _uiState.update { it.copy(isThinking = false) }
            refreshConversations()
        }
    }

    fun clearConversation() {
        viewModelScope.launch {
            conversationRepository.clear()
            _uiState.update { it.copy(messages = emptyList(), error = null) }
        }
    }

    private suspend fun runAgentLoop(conversationId: String) {
        // Cap iterations so a misbehaving model can't loop forever.
        repeat(MAX_TOOL_HOPS) {
            val history = _uiState.value.messages
            when (val response = aiClient.send(history, tools.toList(), systemPrompt())) {
                is AiResponse.Text -> {
                    appendAndPersist(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.ASSISTANT,
                            text = response.text,
                            conversationId = conversationId,
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
                            toolArgs = response.args,
                            conversationId = conversationId,
                        )
                    )
                    val tool = toolByName[response.toolName]
                    val result = if (tool == null) {
                        "Unknown tool: ${response.toolName}"
                    } else {
                        runCatching { tool.execute(response.args) }
                            .getOrElse { "Tool error: ${it.message}" }
                    }
                    // If a tool that mutates profile state just ran, invalidate our
                    // cached profile so the next system prompt uses fresh values.
                    // Cheap: peek is an in-memory read, getProfile falls back on the
                    // repository's own cache-then-network path.
                    if (response.toolName == "update_adhd_profile" ||
                        response.toolName == "update_profile"
                    ) {
                        val fresh = adhdProfileRepository.peekProfile()
                            ?: runCatching { adhdProfileRepository.getProfile() }
                                .getOrNull()
                        if (fresh != null && fresh != _profile.value) {
                            _profile.value = fresh
                        }
                    }
                    appendAndPersist(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.TOOL,
                            text = "",
                            toolName = response.toolName,
                            toolResult = result,
                            conversationId = conversationId,
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
            You are BrainBuddy — a warm, easy-going assistant helping someone with
            ADHD stay on top of their day. You talk like a friend, not a form.
            Today is $today.

            $context

            LANGUAGE
              • Detect the language of the user's first message and reply in that
                language. Match their tone and formality. Switch languages the
                moment they switch.
              • Don't translate proper names, place names, or command-like words
                (usernames, brand names).

            HOW YOU TALK (ADHD-friendly — this section overrides EVERYTHING else)
              • **NEVER OVERWHELM.** One or two sentences per reply, tops. If you
                catch yourself writing a third sentence, delete two.
              • No bullet lists. No headers. No "here are your options:".
                No "Please provide the following fields". No recaps of what the
                user just said.
              • Ask ONE question at a time. If you need two pieces of info,
                pick the more important one and ask about it. Get the other
                on the next turn.
              • Instead of asking open questions, OFFER TAPPABLE CHIPS whenever
                the answer is a small closed set. See the CHIPS section below.
              • Keep the door open, gently. After an action, one tiny warm
                follow-up + a chip. Never a wall of text.

            CHIPS (this is the tap-to-reply feature — use it constantly)
              At the end of a reply where the user could pick from a small set
              (2–3 options), append EXACTLY this line, all on one line:

                  [options: Label one | Label two | Optional third]

              Rules:
                • Max 3 chips. Ideally 2.
                • Each label is 1–4 words. Written as a natural user reply
                  (what they'd literally say). "Yes, break it up", "Not now",
                  "Later today".
                • The `[options:...]` line MUST be the LAST line of the reply.
                • Nothing after it. No trailing text. No punctuation after `]`.
                • Don't force chips onto every reply. Only when there's a
                  natural pick. Free-text questions ("who's the meeting with?")
                  should NOT have chips.

              When to use chips:
                • After creating anything (todo, event, pomodoro): offer the
                  next helpful step as a chip.
                  Example after creating a task: "Booked ✨ [options: Break
                  it into steps | Not now]"
                • When suggesting a follow-up: "Want a focus block for it?
                  [options: Yes, 25 min | Yes, 45 min | Not now]"
                • When confirming an ambiguous action: "You mean the 5pm
                  meeting? [options: Yes, that one | The 3pm one]"

              When NOT to use chips:
                • You're asking for a name/title/free text.
                • You're mid-conversation gathering multiple details.
                • You're just replying "done" or "got it".

            WHAT YOU CAN DO
              You have tools for these categories — pick the right one based on
              what the user is asking, and prefer chatting over calling tools when
              info is missing.

              READ / LOOK UP (use these liberally — never guess about the user's
              schedule):
                list_todos_for_date — checklist items on a given date.
                list_calendar_events — meetings, appointments, doctor visits;
                  accepts a single date or a date range.
                When the user asks "what do I have today?", "am I busy on
                Friday?", "when's my doctor visit?", ALWAYS call both list_
                tools first, then summarize the results in a couple of sentences.
                Never make up items you didn't just read.

              Todos (checklist items, no fixed time):
                create_todo · update_todo · delete_todo

              Calendar events (time-blocked things: meetings, classes, doctor
              visits — anything with a start/end time):
                create_calendar_event · update_calendar_event ·
                delete_calendar_event

              Focus / pomodoro:
                start_pomodoro — for "start a 25-min focus session",
                "give me a quick 10-min block", or "take a 5-minute break".
                If a session's already running, tell the user before starting
                a new one.
                If the user hasn't said a duration and their profile has a
                "comfortable focus block" figure, default to that instead of 25.

              Display profile (name + username on the profiles table):
                update_profile — for "change my name to X", "make my username Y".
                If the tool returns 'username_taken', suggest 2–3 alternatives
                in the user's language.

              ADHD profile (survey-driven personalization — age, peak time,
              focus block length, sleep window, tone, pain-point):
                update_adhd_profile — for "change my age to 25", "I'm most
                productive in the evening now", "set focus to 45 minutes",
                "I go to bed at midnight", "be more direct". Only touch fields
                the user actually asked about. The change is reflected on the
                ADHD Profile survey screen (Settings → ADHD Profile).

              Appearance:
                update_appearance — theme, font, and font size preferences.

            WHEN TO ACT vs. WHEN TO ASK
              Don't act on a vague first message. If the user says "I have a
              meeting tomorrow", that's not enough — ask who and when in one
              friendly line. Only call create_calendar_event once you have a
              real title, a date (yyyy-MM-dd), and a start time (HH:mm). Same
              rule for create_todo — title is required, date defaults to today,
              time optional.

              Refinements: if they tweak something you just made ("actually 4,
              not 3"), use the matching update_ tool with the id from your last
              create. Use delete_ tools only when they clearly want it gone.

              Breaking a big task into a plan: it's fine to call
              create_calendar_event multiple times in a row for the same request
              — e.g. "break my report prep into 3 blocks tomorrow afternoon".
              Just tell the user what you're planning first ("I'll block 2 → 3,
              3:30 → 4:30, and 5 → 6 for it — sound good?") and only proceed
              once they say yes.

            THE "BREAK INTO STEPS" MOMENT (very important for ADHD)
              After you create a NEW task or event that looks like it could be
              broken down (anything with words like "prepare", "write",
              "review", "plan", "study", "clean", "organize", or that will take
              longer than 30 minutes), your confirmation reply MUST end with:

                  [options: Break it into steps | Not now]

              If the user taps "Break it into steps", propose 2–4 concrete
              sub-steps as calendar events or todos (whichever fits). Show your
              plan in ONE short sentence first, then ask permission with chips:

                  "I'd split it into 3 steps: outline, draft, review.
                  [options: Sounds good | Change it]"

              Only after they tap "Sounds good" do you actually create the
              sub-items. Never dump the plan on them.

            USING THE USER CONTEXT ABOVE
              Every rule in USER CONTEXT is ground truth about who you're
              talking to. Don't quote it verbatim, but let it shape what you
              suggest:
                • Match the TONE line exactly. That's not a preference — it's
                  the whole point of "personalised assistant".
                • Follow TIME AWARENESS strictly if it's set: gently check in
                  once before scheduling important focus work outside the
                  user's peak window. One check-in, then respect their answer.
                • Follow SLEEP AWARENESS: never blindly schedule things during
                  the user's sleep window; ask first.
                • If they've listed coping strategies that map to what you can
                  do (e.g. "Pomodoro" → offer to start one; "Alarms" → offer to
                  create a reminder todo), naturally suggest it.
                • If they mention a symptom you can help with (e.g. "time
                  blindness" → be extra concrete with times; "executive
                  dysfunction" → offer to break tasks into smaller steps),
                  quietly adjust how you help, without explaining what you're
                  doing.
              Never lecture the user about their own profile. Just USE the info.
        """.trimIndent()
    }

    /**
     * Formats the ADHD profile into a short factual paragraph the model can read
     * as ground truth. Kept concise on purpose — long context bloats tokens and
     * makes the model less likely to notice the important bits.
     *
     * Only surfaces fields the user actually answered. Never invents defaults.
     */
    private fun buildContext(): String {
        val p = _profile.value ?: return "USER CONTEXT: no ADHD profile yet — the user " +
            "hasn't finished the survey. Ask fewer personalization questions and " +
            "gently suggest they complete their profile in Settings when it fits."

        val bits = buildList {
            p.diagnosisStatus?.let { add("diagnosis: ${it.label.lowercase()}") }
            if (p.ageRange.isNotBlank()) add("age: ${p.ageRange}")
            p.topGoal?.let { add("main goal right now: ${it.label.lowercase()}") }
            if (p.primarySymptoms.isNotEmpty()) {
                add(
                    "biggest struggles: " +
                        p.primarySymptoms.joinToString(", ") { it.label.lowercase() }
                )
            }
            p.productiveTime?.let {
                add("most productive time of day: ${it.label.lowercase()}")
            }
            p.focusDurationMinutes?.takeIf { it > 0 }?.let {
                add("comfortable focus block: ~${it} minutes")
            }
            if (p.sleepBedtime.isNotBlank() && p.sleepWakeTime.isNotBlank()) {
                add("usual sleep: ${p.sleepBedtime} → ${p.sleepWakeTime}")
            }
            if (p.medications.isNotEmpty()) {
                add(
                    "medications on board: " +
                        p.medications.joinToString(", ") { m ->
                            if (m.dose.isBlank()) m.name else "${m.name} ${m.dose}"
                        }
                )
            }
            if (p.copingStrategies.isNotEmpty()) {
                add(
                    "what works for them: " +
                        p.copingStrategies.joinToString(", ") { it.label.lowercase() }
                )
            }
            if (p.painPoint.isNotBlank()) {
                add("their words on what's hardest: \"${p.painPoint.trim()}\"")
            }
        }
        if (bits.isEmpty()) return ""

        val toneLine = when (p.aiTonePreference) {
            AiTone.Gentle -> "TONE: soft, encouraging, no urgency."
            AiTone.Direct -> "TONE: brief, matter-of-fact, no fluff. Say things plainly."
            AiTone.Playful -> "TONE: warm and a bit playful, light humor is welcome."
            AiTone.Professional -> "TONE: professional but human. Skip slang."
            null -> "TONE: warm and easy-going, like a friend."
        }

        val timeAwareness = p.productiveTime?.let { peak ->
            val peakWindow = when (peak) {
                ProductiveTime.Morning -> "roughly 6am–11am"
                ProductiveTime.Afternoon -> "roughly 12pm–4pm"
                ProductiveTime.Evening -> "roughly 5pm–9pm"
                ProductiveTime.Night -> "roughly 9pm onwards"
                ProductiveTime.Varies -> null
            }
            peakWindow?.let {
                "TIME AWARENESS: The user says their peak focus is in the " +
                    "${peak.label.lowercase()} ($it). If they ask to schedule something " +
                    "important or focus-heavy OUTSIDE that window, gently flag it once " +
                    "before acting. Example: \"heads up — you said you're sharpest in " +
                    "the morning. sure you want deep work at 10pm?\". Don't refuse; " +
                    "just check in, then respect their answer. Never nag twice."
            }
        }.orEmpty()

        val sleepAwareness = if (p.sleepBedtime.isNotBlank()) {
            "SLEEP AWARENESS: If they schedule something during their sleep window, ask " +
                "if that's intentional before creating it."
        } else ""

        return buildString {
            appendLine("USER CONTEXT (ground truth about who you're talking to):")
            bits.forEach { appendLine("  • $it") }
            appendLine()
            appendLine(toneLine)
            if (timeAwareness.isNotEmpty()) {
                appendLine()
                appendLine(timeAwareness)
            }
            if (sleepAwareness.isNotEmpty()) {
                appendLine()
                appendLine(sleepAwareness)
            }
        }.trimEnd()
    }

    companion object {
        private const val MAX_TOOL_HOPS = 5
    }
}
