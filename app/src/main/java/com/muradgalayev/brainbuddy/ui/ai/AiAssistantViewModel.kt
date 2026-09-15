package com.muradgalayev.brainbuddy.ui.ai

import android.util.Log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.network.NetworkObserver
import com.muradgalayev.brainbuddy.data.local.AppLanguage
import com.muradgalayev.brainbuddy.data.local.AppLocale
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.data.repository.ConversationRepository
import com.muradgalayev.brainbuddy.data.health.HealthConnectManager
import com.muradgalayev.brainbuddy.domain.ai.AiClient
import com.muradgalayev.brainbuddy.domain.ai.AiNavigator
import com.muradgalayev.brainbuddy.domain.ai.AiResponse
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import com.muradgalayev.brainbuddy.domain.ai.ChatMessage
import com.muradgalayev.brainbuddy.domain.ai.ChatRole
import com.muradgalayev.brainbuddy.domain.ai.ConversationSummary
import com.muradgalayev.brainbuddy.domain.ai.conversationWindow
import com.muradgalayev.brainbuddy.domain.ai.local.LocalIntent
import com.muradgalayev.brainbuddy.domain.ai.local.LocalIntentResolver
import com.muradgalayev.brainbuddy.data.repository.AiQuotaRepository
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
import com.muradgalayev.brainbuddy.domain.scheduling.MINUTES_PER_DAY
import com.muradgalayev.brainbuddy.domain.scheduling.SleepWindow
import com.muradgalayev.brainbuddy.domain.scheduling.toEnergySignalsOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import javax.inject.Inject
import com.muradgalayev.brainbuddy.R

data class AiUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isThinking: Boolean = false,
    val error: String? = null,
    // true once today's message allowance is spent. shows the same switch-to-offline option
    // as being offline does, since from the user's side it's the same situation
    val quotaExhausted: Boolean = false,
)

private const val TAG = "AiAssistantVM"

@HiltViewModel
class AiAssistantViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val aiClient: AiClient,
    private val localIntentResolver: LocalIntentResolver,
    private val aiQuotaRepository: AiQuotaRepository,
    private val conversationRepository: ConversationRepository,
    private val adhdProfileRepository: AdhdProfileRepository,
    private val authRepository: AuthRepository,
    preferencesManager: PreferencesManager,
    private val healthConnectManager: HealthConnectManager,
    private val tools: Set<@JvmSuppressWildcards AiTool>,
    private val aiNavigator: AiNavigator,
    private val networkObserver: NetworkObserver,
) : ViewModel() {
    val spokenResponsesEnabled: StateFlow<Boolean> = preferencesManager.aiSpokenResponses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val chatReadAloudEnabled: StateFlow<Boolean> = preferencesManager.aiChatReadAloud
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
    val selectedVoiceName: StateFlow<String?> = preferencesManager.aiVoiceName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    private val healthPersonalizationEnabled: StateFlow<Boolean> =
        preferencesManager.aiHealthPersonalization
            .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    // ephemeral (calendar) mode: the calendar's AI button runs a throwaway chat that never
    // reaches history, and survives closing/reopening the card on the same screen so a
    // half-typed session doesn't reset. persisted mode is the default
    private var ephemeral = false
    private var calendarPrimed = false
    private var ephemeralConvId = ""

    // a message spoken while the previous turn was still running, see send()
    private var queuedMessage: String? = null

    // navigation requests from the navigate/export tools, collected by NavGraph
    val navigationCommands = aiNavigator.commands

    // passed straight through from NetworkObserver, which is already hot and process-wide.
    // re-wrapping it in a WhileSubscribed hurts here: this VM is activity-scoped, so the nav-bar
    // button holds one permanent subscription and never gets the refresh navigating would give
    val isOnline: StateFlow<Boolean> = networkObserver.isOnline

    // cached profile fed into every prompt. seeded from the repository's in-memory cache so the
    // first message already has context, then refreshed in the background when it changes
    private val _profile = MutableStateFlow(adhdProfileRepository.peekProfile())
    private val toolByName = tools.associateBy { it.name }

    // the backend pseudonym stamped into every system prompt, the only user id that reaches the
    // model. while null the prompt just omits it, we never fall back to the real auth id
    private val _maskedId = MutableStateFlow(authRepository.getMaskedUserId())

    // AI stays locked until the survey is done: nav-bar button disabled, account screen nags.
    // seeded from the in-memory profile cache, refreshed on demand
    private val _surveyCompleted = MutableStateFlow(
        adhdProfileRepository.peekProfile()?.surveyCompleted == true
    )
    val surveyCompleted: StateFlow<Boolean> = _surveyCompleted.asStateFlow()

    // re-reads the survey flag from cache/network, call when re-entering a screen
    fun refreshSurveyCompleted() {
        viewModelScope.launch {
            // three-state on purpose: null means we couldn't resolve it (offline, session not restored)
            // and that must never lock the assistant. only a definite false does
            val done = runCatching { adhdProfileRepository.surveyCompletedOrNull() }
                .getOrNull() ?: return@launch
            if (done != _surveyCompleted.value) _surveyCompleted.value = done
            // the assistant reads survey answers out of _profile, and for a new user this VM was built
            // before any profile existed. pull the fresh one as soon as the survey is done, or the AI
            // keeps saying 'no profile yet'. saveProfile() updates the peek synchronously, so this is
            // cheap and catches a re-done survey too
            if (done && adhdProfileRepository.peekProfile() != _profile.value) {
                refreshProfile()
            }
        }
    }

    private val _conversations = MutableStateFlow<List<ConversationSummary>>(emptyList())
    val conversations: StateFlow<List<ConversationSummary>> = _conversations.asStateFlow()

    // true when the active conversation exists and has at least one visible message
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
        // only hit Supabase on the first VM creation of the process. a non-null peek means we are
        // re-entering, so refetching would just risk a flash as the AI card opens
        if (adhdProfileRepository.peekProfile() == null) refreshProfile()
        refreshConversations()
        refreshSurveyCompleted()
        refreshMaskedId()
        observeUserChanges()
    }

    // no-op when the cache already has one
    private fun refreshMaskedId() {
        if (_maskedId.value != null) return
        viewModelScope.launch {
            val masked = runCatching { authRepository.ensureMaskedUserId() }.getOrNull()
            if (masked != _maskedId.value) _maskedId.value = masked
        }
    }

    // this VM is scoped to the nav back-stack entry and survives sign-out on the same device,
    // so without this user A's chat would still be on screen for user B. watch sessionStatus and
    // wipe in-memory state whenever the auth user id flips
    private fun observeUserChanges() {
        viewModelScope.launch {
            var lastUserId: String? = authRepository.getCurrentUserId()
            authRepository.sessionStatus.collect {
                val newUserId = authRepository.getCurrentUserId()
                if (newUserId != lastUserId) {
                    lastUserId = newUserId
                    // so no message, error or summary from the previous user leaks in
                    _uiState.value = AiUiState()
                    _conversations.value = emptyList()
                    _profile.value = adhdProfileRepository.peekProfile()
                    // drop the old pseudonym before anything can build a prompt with it. peekProfile() checks
                    // the id and clears a stale cache, so this reads null until the new user's row lands
                    _maskedId.value = authRepository.getMaskedUserId()
                    if (newUserId != null) {
                        // the refresh below leaves the flag alone when it can't resolve, so clear it first, or the
                        // previous user's 'completed' unlocks the assistant for whoever signs in offline
                        _surveyCompleted.value =
                            adhdProfileRepository.peekProfile()?.surveyCompleted == true
                        // load the new user's own history and conversations
                        loadActiveHistory()
                        refreshConversations()
                        refreshSurveyCompleted()
                        refreshMaskedId()
                        if (adhdProfileRepository.peekProfile() == null) refreshProfile()
                    } else {
                        _surveyCompleted.value = false
                    }
                }
            }
        }
    }

    fun refreshProfile() {
        // saveProfile() writes this cache synchronously right after the survey submits, so it is
        // already populated on the next recomposition
        adhdProfileRepository.peekProfile()?.let {
            if (it != _profile.value) _profile.value = it
        }
        viewModelScope.launch {
            val fresh = runCatching { adhdProfileRepository.getProfile() }.getOrNull()
                ?: return@launch
            if (fresh != _profile.value) _profile.value = fresh
        }
    }

    private fun loadActiveHistory() {
        viewModelScope.launch {
            val history = conversationRepository.loadActiveHistory()
            // the calendar's ephemeral chat must never adopt the persisted global conversation
            if (ephemeral) return@launch
            _uiState.update { it.copy(messages = history) }
        }
    }

    fun refreshConversations() {
        viewModelScope.launch {
            _conversations.value = conversationRepository.listConversationSummaries()

        }
    }

    // ends the current chat. doesn't delete anything, just stops showing it and makes the
    // next send start fresh
    fun endChat() {
        viewModelScope.launch {
            conversationRepository.endActiveConversation()
            _uiState.update { it.copy(messages = emptyList(), error = null) }
            refreshConversations()
        }
    }

    fun startNewChat() = endChat()

    // primes the calendar's AI button with a throwaway chat that never reaches history.
    // seeds the greeting only the first time it opens in this screen session, so reopening
    // the card keeps whatever the user already typed
    fun primeCalendarEventPrompt() {
        if (_uiState.value.isThinking) return
        ephemeral = true
        // already have an in-flight ephemeral session, leave it alone
        if (calendarPrimed) return
        calendarPrimed = true
        ephemeralConvId = UUID.randomUUID().toString()
        val greeting = ChatMessage(
            id = UUID.randomUUID().toString(),
            role = ChatRole.ASSISTANT,
            text = context.getString(R.string.ai_calendar_prime),
            conversationId = ephemeralConvId,
        )
        _uiState.update { it.copy(messages = listOf(greeting), error = null) }
    }

    // switch back to a past conversation and load its history into the UI
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
        if (trimmed.isEmpty()) return

        // speaking while the last turn is still in flight used to throw the words away silently.
        // in a chat box you'd at least see it didn't send; by voice there is nothing to see, so it
        // just looked like it ignored you. hold it and run it when the turn finishes
        if (_uiState.value.isThinking) {
            queuedMessage = trimmed
            return
        }

        // ask the system directly rather than trusting the observed flow. this gates an outbound
        // request, so it should go on the freshest answer, not one up to a debounce old
        if (!networkObserver.currentlyOnline()) {
            _uiState.update {
                it.copy(
                    error = context.getString(R.string.ai_offline_error),
                )
            }
            return
        }

        // nav chips are deterministic UI actions, not prompts, so a wellness-access request can't
        // be misread as calendar work
        // the English labels stay matched as well as the localised ones: older chats, and a model that
        // ignored the label it was given, still land on the right screen
        val chip = trimmed.lowercase()
        when {
            chip == "open wellness settings" || chip == "enable wellness personalization" ||
                chip == context.getString(R.string.ai_chip_open_wellness).lowercase() -> {
                aiNavigator.navigateTo("settings_ai")
                return
            }
            chip == "connect health connect" || chip == "open linked devices" ||
                chip == context.getString(R.string.ai_chip_connect_health).lowercase() -> {
                aiNavigator.navigateTo("settings_linked_devices")
                return
            }
        }

        // handled before anything reaches the model: a typeface swap is a lookup, and paying ~8,700
        // tokens of profile and tool schemas to move one enum is wrong twice over, once in cost and
        // once in shipping the user's medication list to a third party to change a font.
        // falls through to the model whenever it isn't sure. see LocalIntentResolver
        localIntentResolver.resolve(trimmed)?.let { intent ->
            runLocalIntent(trimmed, intent)
            return
        }

        // ephemeral calendar chat: run the loop in memory only, no history, titles or summaries
        if (ephemeral) {
            _uiState.update { it.copy(isThinking = true, error = null) }
            viewModelScope.launch {
                try {
                    appendAndPersist(
                        ChatMessage(
                            id = UUID.randomUUID().toString(),
                            role = ChatRole.USER,
                            text = trimmed,
                            conversationId = ephemeralConvId,
                        )
                    )
                    runAgentLoop(ephemeralConvId)
                } catch (e: Exception) {
                    Log.w(TAG, "Voice turn failed", e)
                    _uiState.update { it.copy(error = turnFailedMessage(e)) }
                } finally {
                    // unconditional. isThinking stuck at true makes send() drop every later message without a
                    // word, so one failed turn used to kill the assistant until the app restarted
                    _uiState.update { it.copy(isThinking = false) }
                    drainQueuedMessage()
                }
            }
            return
        }

        // captured before we append, so we know whether to ask for a title afterwards
        val isFirstTurn = _uiState.value.messages.none { it.role == ChatRole.USER }

        _uiState.update { it.copy(isThinking = true, error = null) }
        viewModelScope.launch {
            try {
                // mints a fresh conversation if the user ended the previous one
                val convId = conversationRepository.ensureActiveConversationId()
                val userMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = ChatRole.USER,
                    text = trimmed,
                    conversationId = convId,
                )
                appendAndPersist(userMsg)
                runAgentLoop(convId)
                refreshConversations()

                // title the conversation off the first exchange, best-effort and off the critical path
                if (isFirstTurn) {
                    launch {
                        runCatching {
                            conversationRepository.ensureConversationTitle(convId)
                            refreshConversations()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Turn failed", e)
                _uiState.update { it.copy(error = turnFailedMessage(e)) }
            } finally {
                _uiState.update { it.copy(isThinking = false) }
                drainQueuedMessage()
            }
        }
    }

    // runs whatever was said while the last turn was still in flight
    private fun drainQueuedMessage() {
        val next = queuedMessage ?: return
        queuedMessage = null
        send(next)
    }

    // runs a locally-recognised command and writes it to history as if the assistant had done it,
    // because from the user's side it did. what we don't record is a tool-call/tool-result pair:
    // there was no model call, and inventing one would put a fabricated action into the transcript
    // we later send upstream. no quota is claimed either, this spends nothing at the provider
    private fun runLocalIntent(userText: String, intent: LocalIntent) {
        _uiState.update { it.copy(isThinking = true, error = null) }
        viewModelScope.launch {
            try {
                val convId = if (ephemeral) {
                    ephemeralConvId
                } else {
                    conversationRepository.ensureActiveConversationId()
                }
                appendAndPersist(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = ChatRole.USER,
                        text = userText,
                        conversationId = convId,
                    )
                )

                val tool = toolByName[intent.toolName]
                val result = if (tool == null) {
                    // the catalog names a tool that isn't bound. a canned success would tell the user something
                    // happened when nothing did
                    Log.w(TAG, "Local intent names unknown tool ${intent.toolName}")
                    null
                } else {
                    runCatching { tool.execute(intent.args) }
                        .onFailure { Log.w(TAG, "Local intent failed", it) }
                        .getOrNull()
                }

                appendAndPersist(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = ChatRole.ASSISTANT,
                        // a tool can refuse for reasons the resolver can't know (appearance is locked while a mode
                        // is active) and it says so better than a canned line would
                        text = when {
                            result == null -> context.getString(R.string.ai_local_failed)
                            result.startsWith("Failed") -> result
                            else -> intent.reply
                        },
                        conversationId = convId,
                    )
                )

                if (!ephemeral) refreshConversations()
            } catch (e: Exception) {
                Log.w(TAG, "Local intent turn failed", e)
                _uiState.update { it.copy(error = turnFailedMessage(e)) }
            } finally {
                _uiState.update { it.copy(isThinking = false) }
                drainQueuedMessage()
            }
        }
    }

    private fun turnFailedMessage(e: Exception): String =
        context.getString(R.string.ai_turn_failed, e.message ?: context.getString(R.string.common_no_details))

    fun clearConversation() {
        viewModelScope.launch {
            conversationRepository.clear()
            _uiState.update { it.copy(messages = emptyList(), error = null) }
        }
    }

    // runs the model until it produces text, and makes sure the user hears something either way.
    // burning through MAX_TOOL_HOPS used to return with no message and no error set, so the
    // screen just went quiet: a glitch in the chat window, total silence in the voice assistant
    private suspend fun runAgentLoop(conversationId: String) {
        // one claim per user message, not per request. the loop can fire several requests for one
        // message when the model calls tools, and charging for its internal steps would make the
        // allowance impossible to predict.
        // sits here because both entry paths, normal chat and ephemeral calendar, come through here
        val quota = aiQuotaRepository.consume()
        if (!quota.allowed) {
            _uiState.update {
                it.copy(
                    quotaExhausted = true,
                    error = context.getString(R.string.ai_quota_exhausted, quota.dailyLimit),
                )
            }
            return
        }
        // clears itself when the allowance rolls over: the user can always try again, and the
        // server's answer decides, not a cached flag
        if (_uiState.value.quotaExhausted) {
            _uiState.update { it.copy(quotaExhausted = false) }
        }

        // cap iterations so a misbehaving model can't loop forever
        repeat(MAX_TOOL_HOPS) {
            // only the tail travels, see conversationWindow(). the full list stays in uiState for the
            // screen and in Supabase for history
            val history = conversationWindow(_uiState.value.messages)
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
                    // record the call as an assistant turn so the model sees its own action
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
                    // a tool that mutates profile state just ran, so drop the cached copy before the next system
                    // prompt. cheap: peek is in-memory and getProfile has its own cache-then-network path
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
                    // feed the result back and go round again
                }
                is AiResponse.Error -> {
                    _uiState.update { it.copy(error = response.message) }
                    return
                }
            }
        }

        // hop limit hit: the model kept calling tools and never spoke. the work did happen, so say
        // so rather than leaving dead silence
        appendAndPersist(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                role = ChatRole.ASSISTANT,
                text = context.getString(R.string.ai_hop_limit),
                conversationId = conversationId,
            )
        )
    }

    // assistant replies that arrived during this session and haven't been typed out on screen yet.
    // the card's own bookkeeping can't do this: it is disposed when the sheet closes, so reopening
    // the assistant made the last answer type itself out again as if it had just arrived. only
    // messages created here land in the set, and history is loaded straight into state rather than
    // appended, so reopening a past conversation shows every answer whole
    private val pendingReveal = mutableSetOf<String>()

    fun shouldAnimateReveal(messageId: String): Boolean = messageId in pendingReveal

    fun markRevealed(messageId: String) {
        pendingReveal.remove(messageId)
    }

    private fun appendAndPersist(msg: ChatMessage) {
        if (msg.role == ChatRole.ASSISTANT) pendingReveal.add(msg.id)
        _uiState.update { it.copy(messages = it.messages + msg) }
        // ephemeral chats stay in memory. runCatching because this is fire-and-forget: an uncaught
        // throw in a viewModelScope child takes the app down with it, and failing to write history
        // must never cost the user their answer
        if (!ephemeral) viewModelScope.launch {
            runCatching { conversationRepository.append(msg) }
                .onFailure { Log.w(TAG, "Persisting message failed", it) }
        }
    }

    // the language Myndora is showing right now, named for the model. read per prompt rather than
    // once, so switching language in Settings changes the very next reply
    private fun appLanguageName(): String = when (AppLocale.current(context)) {
        AppLanguage.English -> "English"
        AppLanguage.Italian -> "Italian"
    }

    private fun systemPrompt(): String {
        val today = LocalDate.now().toString()
        val context = buildContext() + buildHealthContext()
        return """
            You are Myndora — a warm, easy-going assistant helping someone with
            ADHD stay on top of their day. You talk like a friend, not a form.
            Today is $today.

            ${buildIdentityContext()}

            $context

            LANGUAGE
              • The app is set to ${appLanguageName()}. Reply in ${appLanguageName()},
                chip labels included, unless the user writes to you in another
                language: then reply in theirs, and switch the moment they switch.
                Match their tone and formality.
              • Don't translate proper names, place names, or command-like words
                (usernames, brand names).

            WHAT YOU'RE FOR
              You help with this person's day inside Myndora: to-dos, calendar,
              focus timers, routines, their ADHD profile, the app's own settings,
              and finding care nearby. That is the whole job.
              Myndora itself is squarely in scope, so questions about how it works
              — signing in, the language it speaks, Myndora Plus, Shape Flow,
              where a setting lives — get a real answer, never the it-isn't-what-
              I'm-for line. You are the one person who should know this app.
              If they ask about anything else — history, geography, maths, news,
              sport, code, celebrities, medical or legal advice, or anything you
              would need the internet for — do NOT answer it, even when you are
              sure you know. Say in one short line that it isn't what you're for,
              then offer something you can actually do. No preamble, no apology,
              no "as an AI". Say it in the user's TONE, like everything else.
              Example: "not my thing — I'm your day, not a search engine."
              [options: Add a to-do | What's on today]
              Never state a fact about the world. You cannot browse, and you have
              no reliable knowledge of anything current.
              Two things that are NOT off-topic, because they're about them and
              not about the world:
                • Anything already in USER CONTEXT — that's theirs. Answer freely.
                • Feeling stuck, overwhelmed or unmotivated. Stay warm and brief,
                  help them take one small step, and never diagnose.

            HOW YOU TALK (ADHD-friendly — governs LENGTH and SHAPE of every reply)
              This section decides how MUCH you say. The TONE line in USER CONTEXT
              decides how you SOUND, and it is not a suggestion: the user chose it
              themselves, as the last question of their setup survey, when asked how
              they wanted you to talk to them. It is the one thing they explicitly
              asked of you, so it governs every reply — confirmations, refusals,
              errors, one-word answers.
              The two never conflict: a direct reply and a playful reply are both one
              short sentence, they just don't sound the same. Never let brevity
              flatten the voice into the same neutral assistant for everyone — the
              tone is the personalisation.
              • **NEVER OVERWHELM. BE SHORT.** This is the #1 rule — you're talking
                to someone with ADHD, and long replies lose them. Aim for ONE short
                sentence; two is the absolute maximum, and only when truly needed.
                Keep each sentence tight — no filler, no preamble, no wind-up. If a
                reply runs past 15 words (excluding the options line), cut it down
                before sending. Use the fewest words that still answer clearly.
              • No bullet lists. No headers. No "here are your options:".
                No "Please provide the following fields". No recaps of what the
                user just said.
              • **ASK AS LITTLE AS POSSIBLE.** At most ONE question in a reply, and
                only when you genuinely cannot act without the answer. If a sensible
                default exists, take it and say what you did — they can correct you in
                three words, and that is far cheaper than being interviewed. Being
                asked a string of questions is exactly what makes someone with ADHD
                abandon the task they came to do.
                Ask: the title of a thing that has none, the day of something you
                cannot place.
                Don't ask: where it is, whether there's a link, what colour it should
                be, whether they want a reminder, whether they'd like to break it up.
                None of those stop you acting.
              • Instead of asking open questions, OFFER TAPPABLE CHIPS whenever
                the answer is a small closed set. See the CHIPS section below.
              • Keep the door open, gently. After an action, one tiny warm
                follow-up + a chip. Never a wall of text.
              • Prefer a tappable chip over making the user type yes/no, repeat a
                phrase, or manually find a screen. If 2–3 useful next actions exist,
                show them as chips and keep the sentence before them very short.

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
                • When asking WHICH of a few things they mean — even if the
                  value they'll give next is free text. Choosing the field is a
                  closed set, so offer it as chips instead of making them type
                  the word. Examples:
                    "Which one — your name or your username?
                     [options: Name | Username]"
                    "What should I change? [options: Name | Username | Both]"
                  Only the ACTUAL new value ("what should it say?") is typed.

              When NOT to use chips:
                • You're asking for the actual free-text VALUE (the new name,
                  the task title, what the meeting is about). Note: this is only
                  about typing the value — if you're first asking WHICH field or
                  offering a small set of picks, still use chips.
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

              SOMEONE ELSE'S CALENDAR (you cannot do this)
                create_calendar_event ALWAYS writes to this user's own calendar.
                There is no tool that writes to anyone else's. So if they ask to
                add something to a friend's, partner's or parent's calendar —
                "add a meeting to Ali's calendar", "put this on my mum's" — do
                NOT call create_calendar_event. Creating it on their own calendar
                and saying "done" is the worst possible answer: it looks like it
                worked and the other person never gets it.
                Instead: say in one short line that they need to add it
                themselves, then call navigate_to_screen with add_event, which
                opens the calendar with the form ready and the "Also add to" row
                where they pick the person.
                Never say you added it. You didn't.

              WHEN SHOULD THIS GO? (never answer this yourself)
                suggest_time — call it whenever the user wants something scheduled
                  but didn't say a time, or asks when they should do something.
                  You do NOT know when to put things. USER CONTEXT tells you they
                  are a morning person; it does not tell you their last eight gym
                  sessions were at 18:30, what is already on Thursday, that they
                  slept five hours, or that a 07:00 dinner is absurd. This tool
                  knows all of that and you cannot.
                  Flow: suggest_time → offer the first option WITH its reason in
                  one short sentence → chips to accept or ask for another → only
                  after they agree, call create_calendar_event with the date and
                  start_time it gave you.
                  Never invent a time and never quietly pick one to skip a step.
                  The reason is the point: "your usual gym slot" is why they tap.

              Todos (checklist items, no fixed time):
                create_todo · update_todo · delete_todo

              Calendar events (time-blocked things: meetings, classes, doctor
              visits — anything with a start/end time):
                create_calendar_event · update_calendar_event ·
                delete_calendar_event · split_calendar_even
                An event carries a title, date, start/end time, and OPTIONAL
                location (a room or address), link (a URL — Zoom/Meet/doc/map),
                notes, and color. Fill those in when the user mentioned them, and
                otherwise leave them empty and say nothing. Do NOT ask for a place
                or a link: the event is perfectly usable without either, and asking
                turned every single 'add a meeting' into a two-question interview.

              Splitting an event into stations (split_calendar_event):
                Break a block into smaller ordered steps — e.g. "Math prep 90m"
                → Review 20m, Break 5m, Practice 45m, Recap 20m. Use it when the
                user asks to "split", "break into steps/stations", or "make a
                pomodoro plan" for an event, OR proactively offer it after
                creating a long focus-type block (see the BREAK INTO STEPS note).
                You need the event id (from create_calendar_event or
                list_calendar_events) and a stations list; each station has a
                title, minutes, and kind ('focus' or 'break'). Propose the plan
                in ONE sentence and get a yes (chips) BEFORE calling the tool.
                It replaces the whole plan, so to add/edit one station, read the
                current plan and send the complete updated list.

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
                update_appearance — theme, font, font size, and text spacing preferences.

              Navigation (moving around the app):
                navigate_to_screen — for "go to the calendar", "open settings",
                "take me to pomodoro", "show me care nearby". Just move them —
                a one-line confirmation is enough, no chips needed.

              Password and signing in:
                Myndora never shows or sets a password in the app. Settings →
                Password & sign-in emails them a link to choose one, and it works
                for Google accounts too — that adds email sign-in without
                unlinking Google. So for "I forgot my password", "change my
                password", "how do I sign in on my laptop", say in one line that
                the link comes by email, then navigate_to_screen with password,
                which opens that sheet for them. You cannot read, set or reset a
                password yourself, and you must never ask them to type one to you.

              Language:
                The app speaks English and Italian. Settings → Language switches
                it, and everything changes in place, including what I say next.
                For "parla italiano", "change the app to Italian", "can you speak
                English": if they want the whole APP switched, navigate_to_screen
                with language and let them pick. If they just want THIS
                conversation in another language, don't navigate — simply reply in
                it, per the LANGUAGE rules above. Asking you to speak Italian is
                not the same as asking to change the app.

              Shape Flow (the focus game):
                A short calming game in Myndora: shapes flow past and they tap
                the ones that match. Built for the fidgety, can't-start moments —
                a couple of minutes of it as a way in, or as a real break between
                focus blocks. navigate_to_screen with game opens it. Offer it when
                someone says they're restless, stuck, or can't start, alongside a
                pomodoro — never instead of dealing with what they actually asked.

              Myndora Plus (the paid plan):
                Everything above is free. Plus adds the Workspace assistant
                (voice, and on-device when offline), Calendar AI, Health Connect,
                and building your own colour theme. It is in beta: free to join
                for now, the final price isn't decided, and nothing is charged.
                navigate_to_screen with myndora_plus opens it. Say what it
                unlocks plainly if asked, never push it, and never quote a price
                or a date — you don't know them. If a tool fails because
                something is Plus-only, say which feature it belongs to and offer
                to open that screen.

              Google Calendar sync:
                export_calendar_to_google — for "sync my calendar to Google",
                "push my events to Google Calendar". If no Google account is
                linked, the tool opens Settings so they can connect — tell them
                that plainly and to ask again once connected.

              Notifications (reminders, daily summary, focus nudges, breaks):
                update_notification_settings — for "remind me the day before too",
                "turn off the morning summary", "nudge me to focus at 9am",
                "stop the break alerts". Only touch the fields they mention.
                Lead times are day_before / min_30_before / at_start, set per
                category (to-dos vs calendar). Confirm what changed in one line.

              Healthcare / care nearby (curated clinics, dentists, pharmacies,
              ASL, support groups in the user's city):
                find_care_nearby — for "find me a dentist", "any pharmacies
                near me?", "where's the nearest hospital?". 'specialist' covers
                dentists. Each result comes back with its opening hours and the
                current time. When the user cares about opening hours ("one
                that's open now", "the closest open dentist"), READ the hours +
                current time yourself and reply with ONLY the closest place
                that's actually open — not the whole list. Pass limit=1 when
                they want just one. If none of the returned places are open,
                say so and offer the nearest one with its next opening time.
                Summarize in a sentence; set open_screen=true when they want to
                see the full map/list.

                contact — for "call it", "call the dentist", "email them",
                "open their website". Pass action (call|email|website) and the
                value (phone/email/URL) from the find_care_nearby result you
                just read. If several places match, confirm which one first with
                chips before contacting. It opens the dialer/email draft/browser
                — it never calls or sends anything on its own.

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
              This is worth offering, and worth offering RARELY. Once per
              conversation, for something genuinely big — a task that is clearly
              several hours, or one they've already said they're stuck on. A
              confirmation that ends in a question every single time stops reading
              as help and starts reading as nagging, which is the opposite of the
              point. When it does earn its place, end that reply with:

                  [options: Break it into steps | Not now]

              For everything else, confirm in a few words and stop. "Added ✨" is a
              complete reply.

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
                • Match the TONE line exactly, including its "Sounds like"
                  example — copy its register, punctuation and emoji policy, not
                  its words. That's not a preference; it's the whole point of
                  "personalised assistant", and it applies to every reply
                  including refusals, errors and one-word confirmations.
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

    // tells the model who it's talking to in pseudonymous terms, and what to do when it hits a
    // redaction. without it the model reads the withheld-name marker as a gap to fill and starts
    // asking the user to retype their name, which is the one thing this exists to avoid
    private fun buildIdentityContext(): String {
        val ref = _maskedId.value
        val refLine = if (ref != null) "You know them only as user $ref. " else ""
        return """
            WHO YOU'RE TALKING TO
              ${refLine}You do not have their name, username, email, or phone, and you
              never need them — every tool acts on the signed-in user automatically.
              Text like "[user's name withheld]" is a privacy placeholder, not
              something missing. Don't mention it, don't ask them to fill it in, and
              don't ask for personal details you weren't given. If they ask what
              their name or email is, say it's on their Settings screen rather than
              guessing. Greet them without a name ("hey" beats "hey there, [name]").
        """.trimIndent()
    }

    // formats the profile into a short factual paragraph the model can treat as ground truth.
    // short on purpose, long context bloats tokens and buries the important bits.
    // only includes fields the user actually answered
    private fun buildContext(): String {
        val p = _profile.value ?: return "USER CONTEXT: no ADHD profile yet — the user " +
            "hasn't finished the survey. Ask fewer personalization questions and " +
            "gently suggest they complete their profile in Settings when it fits."

        val bits = buildList {
            p.diagnosisStatus?.let { add("diagnosis: ${it.label.lowercase()}") }
            if (p.ageRange.isNotBlank()) add("age: ${p.ageRange}")
            if (p.topGoals.isNotEmpty()) {
                add(
                    "working on right now: " +
                        p.topGoals.joinToString(", ") { it.label.lowercase() }
                )
            }
            if (p.primarySymptoms.isNotEmpty()) {
                add(
                    "biggest struggles: " +
                        p.primarySymptoms.joinToString(", ") { it.label.lowercase() }
                )
            }
            p.productiveTime?.let {
                add("most productive time of day: ${it.label.lowercase()}")
            }
            // how to speak to this person, which matters more to them than any fact about them.
            // rejection-sensitive dysphoria is common in ADHD, so this is an instruction not a preference
            p.nudgeTone?.let { add("wants reminders to sound: ${it.label.lowercase()}") }
            p.missedTaskResponse?.let {
                add("when they miss something, they want you to: ${it.label.lowercase()}")
            }
            p.checkInCeiling?.let {
                add("hard ceiling on check-ins — never exceed this: ${it.label.lowercase()}")
            }
            p.chronotype?.let { add("chronotype: ${it.label.lowercase()}") }
            p.planChangeImpact?.let { add("when plans change unexpectedly: ${it.label.lowercase()}") }
            p.captureNeed?.let { add("needs to write things down: ${it.label.lowercase()}") }
            if (p.coOccurring.isNotEmpty()) {
                add(
                    "also applies to them: " +
                        p.coOccurring.joinToString(", ") { it.label.lowercase() }
                )
            }
            if (p.pastStrategies.isNotEmpty()) {
                add(
                    "already tried (don't re-suggest these as if new): " +
                        p.pastStrategies.joinToString(", ") { it.label.lowercase() }
                )
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
                            if (m.doseLabel.isBlank()) m.name else "${m.name} ${m.doseLabel}"
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

        // each tone carries a worked example, because the length rules squeeze every reply into one
        // short sentence and four adjectives all produce the same sentence at that length. the
        // example is what separates them: punctuation, emoji policy and stance survive being cut to
        // fifteen words where an adjective doesn't
        val toneLine = when (p.aiTonePreference) {
            AiTone.Gentle -> """
                TONE: soft and reassuring, no urgency, never commanding. Offer rather
                than instruct ("want me to…" not "do this"). A gentle emoji is fine.
                Nothing is ever late or missed — it's just not done yet.
                Sounds like: "no rush — want me to move it to tomorrow?"
            """.trimIndent()
            AiTone.Direct -> """
                TONE: plain and factual. No emoji, no exclamation marks, no softening,
                no "great!" or "sure thing". State what happened or ask the one thing
                you need. Shortest tone of the four.
                Sounds like: "Moved to 18:00."
            """.trimIndent()
            AiTone.Playful -> """
                TONE: light and a bit cheeky. Contractions, lowercase openers, one
                emoji when it lands. Never jokey about something they're struggling
                with — playful about the task, warm about the person.
                Sounds like: "done ✌️ 6pm gym, past-you would be proud"
            """.trimIndent()
            AiTone.Professional -> """
                TONE: neutral and competent. Complete sentences, correct punctuation,
                no emoji, no slang, no exclamation marks. Courteous, not warm.
                Sounds like: "Scheduled for 18:00. Anything else for today?"
            """.trimIndent()
            null -> """
                TONE: warm and easy-going, like a friend who has your back.
                Contractions are fine, an emoji occasionally.
                Sounds like: "sorted — gym at 6 👍"
            """.trimIndent()
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

    private fun buildHealthContext(): String {
        val health = healthConnectManager.state.value
        if (!health.connected) {
            return """

                WELLNESS ACCESS: Health Connect is not connected. If the user asks
                about steps, sleep, calories, exercise, heart rate, or wellness-based
                planning, do not guess and do not open Calendar. Say briefly that they
                need to connect it, then end with:
                [options: ${context.getString(R.string.ai_chip_connect_health)} | ${context.getString(R.string.common_not_now)}]
                Use those two labels exactly as written, in any language.
            """.trimIndent()
        }
        if (!healthPersonalizationEnabled.value) {
            return """

                WELLNESS ACCESS: Health Connect is connected, but the user has not
                allowed AI wellness personalization. You cannot access or quote their
                health values. If they ask about steps, sleep, calories, exercise,
                heart rate, or wellness-based planning, say: "Oops — enable wellness
                personalization first." Then end with:
                [options: ${context.getString(R.string.ai_chip_open_wellness)} | ${context.getString(R.string.common_not_now)}]
                Use those two labels exactly as written, in any language.
                Never use Calendar as a fallback for a wellness question.
            """.trimIndent()
        }

        val facts = buildList {
            health.todaySteps?.let { add("steps today: $it") }
            health.lastSleepHours?.let { add("last sleep duration: ${"%.1f".format(it)} hours") }
            health.exerciseMinutesThisWeek?.let { add("exercise in the last 7 days: $it minutes") }
            health.caloriesBurnedToday?.let { add("calories burned today: $it kcal") }
            health.restingHeartRateBpm?.let { add("latest resting heart rate: $it bpm") }
            // averages, not the last sample, or the model reads a post-stairs spike as the resting rate
            health.todayAverageHeartRateBpm?.let {
                add("average heart rate today: $it bpm (${health.todayHeartRateSamples} readings)")
            }
            health.weekAverageHeartRateBpm?.let {
                add("average heart rate over the last 7 days: $it bpm")
            }
        }
        if (facts.isEmpty()) return ""

        // the same number the calendar's chips use, from the same function, so the two can never
        // disagree about whether today was heavy
        val fatigue = health.toEnergySignalsOrNull()?.fatigue(wakingDayProgress())

        return buildString {
            appendLine()
            appendLine("CONSENTED WELLNESS CONTEXT (a recent summary, not a diagnosis):")
            facts.forEach { appendLine("  • $it") }
            appendLine("Use this only to gently personalize workload, breaks, timing, and focus suggestions.")
            appendLine("Never diagnose, infer a condition, claim causation, or present medical advice.")
            appendLine("Do not mention these values unless they are directly useful to the user's request.")
            appendLine("If a value seems concerning, avoid interpreting it and suggest professional help only when appropriate.")

            // same threshold as TimeSuggestionEngine's EnergyAware rule
            if (fatigue != null && fatigue >= HEAVY_DAY_FATIGUE) {
                appendLine()
                appendLine("TODAY HAS BEEN HEAVY")
                appendLine("  Their sleep and movement together say today has taken a lot")
                appendLine("  out of them. Before you schedule anything demanding — gym,")
                appendLine("  a run, a long focus block, a deep-work session — say so ONCE")
                appendLine("  in half a sentence and offer the easier option, then do")
                appendLine("  whatever they answer.")
                appendLine("  Example: \"you've been on your feet all day — still up for the")
                appendLine("  gym, or move it to tomorrow?\"")
                appendLine("  [options: Still on | Tomorrow instead]")
                appendLine("  Rules: never refuse, never lecture, never repeat it in the")
                appendLine("  same conversation, and never mention the numbers unless they")
                appendLine("  ask. Say nothing at all for easy things — a to-do, dinner,")
                appendLine("  a reminder. This is about effort, not about their day.")
            }
        }
    }

    // fraction of the waking day gone, 0f..1f. steps are cumulative, so 3000 by 09:00 is a brisk
    // morning and 3000 by 21:00 is a slow day. mirrors DayContext.dayProgress so the assistant
    // and the calendar read the day the same way
    private fun wakingDayProgress(): Float {
        val profile = _profile.value
        val sleep = SleepWindow.parse(profile?.sleepBedtime, profile?.sleepWakeTime)
        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute
        val awakeLength = Math.floorMod(sleep.bedMinutes - sleep.wakeMinutes, MINUTES_PER_DAY)
            .takeIf { it > 0 } ?: MINUTES_PER_DAY
        val elapsed = Math.floorMod(nowMinutes - sleep.wakeMinutes, MINUTES_PER_DAY)
        return (elapsed.toFloat() / awakeLength).coerceIn(0f, 1f)
    }

    companion object {
        private const val MAX_TOOL_HOPS = 5

        // where a heavy day starts. matches the EnergyAware threshold in TimeSuggestionEngine so the
        // chips and the assistant can't say different things about the same day
        private const val HEAVY_DAY_FATIGUE = 0.35f
    }
}
