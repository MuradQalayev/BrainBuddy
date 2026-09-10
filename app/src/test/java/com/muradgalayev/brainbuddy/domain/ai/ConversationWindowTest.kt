package com.muradgalayev.brainbuddy.domain.ai

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

// the invariant these all defend: a window never begins mid-exchange. a tool message whose
// assistant tool_call was trimmed away carries a tool_call_id pointing at nothing, and the
// provider rejects the entire request, so the failure isn't a degraded answer, it's the
// assistant going silent
class ConversationWindowTest {

    private var counter = 0

    private fun user(text: String = "hello") =
        ChatMessage(id = "u${counter++}", role = ChatRole.USER, text = text)

    private fun assistant(text: String = "hi") =
        ChatMessage(id = "a${counter++}", role = ChatRole.ASSISTANT, text = text)

    private fun toolCall(name: String = "create_todo") = ChatMessage(
        id = "c${counter++}",
        role = ChatRole.ASSISTANT,
        text = "",
        toolName = name,
        toolArgs = buildJsonObject { put("title", "x") },
    )

    private fun toolResult(name: String = "create_todo", result: String = "done") = ChatMessage(
        id = "t${counter++}",
        role = ChatRole.TOOL,
        text = "",
        toolName = name,
        toolResult = result,
    )

    // one complete exchange: ask, call a tool, get the result, reply
    private fun exchange() = listOf(user(), toolCall(), toolResult(), assistant())

    private fun List<ChatMessage>.startsMidExchange(): Boolean =
        firstOrNull()?.role != ChatRole.USER

    @Test
    fun `short conversation is sent whole`() {
        val messages = exchange()
        assertEquals(messages, conversationWindow(messages))
    }

    @Test
    fun `empty conversation stays empty`() {
        assertTrue(conversationWindow(emptyList()).isEmpty())
    }

    @Test
    fun `long conversation is trimmed to roughly the cap`() {
        val messages = (1..20).flatMap { exchange() } // 80 messages
        val window = conversationWindow(messages)

        assertTrue("window should be trimmed", window.size < messages.size)
        assertTrue("window should not exceed the cap", window.size <= DEFAULT_WINDOW_MESSAGES)
        assertEquals("newest message must survive", messages.last(), window.last())
    }

    @Test
    fun `window never starts on an orphaned tool result`() {
        // sweep every conversation length: the naive takeLast lands mid-exchange for three out of
        // every four of these
        for (exchanges in 1..30) {
            val messages = (1..exchanges).flatMap { exchange() }
            val window = conversationWindow(messages)
            assertTrue(
                "length $exchanges started mid-exchange with ${window.first().role}",
                !window.startsMidExchange(),
            )
        }
    }

    @Test
    fun `every tool result in the window keeps its preceding call`() {
        val messages = (1..30).flatMap { exchange() }
        val window = conversationWindow(messages)

        window.forEachIndexed { i, msg ->
            if (msg.role == ChatRole.TOOL) {
                assertTrue("tool result at $i has no preceding call", i > 0)
                val prev = window[i - 1]
                assertEquals(ChatRole.ASSISTANT, prev.role)
                assertEquals(msg.toolName, prev.toolName)
            }
        }
    }

    @Test
    fun `a huge tool result shrinks the window below the message cap`() {
        val fat = "x".repeat(DEFAULT_WINDOW_CHARS)
        val messages = listOf(
            user(), toolCall(), toolResult(result = fat), assistant(),
            user(), toolCall(), toolResult(result = fat), assistant(),
            user("what now?"),
        )
        val window = conversationWindow(messages)

        assertTrue("char budget should have trimmed further", window.size < messages.size)
        assertTrue(!window.startsMidExchange())
        assertEquals(messages.last(), window.last())
    }

    @Test
    fun `the newest user message survives a budget that cannot be met`() {
        // a single exchange far over budget: nothing can be given up without losing the question being
        // answered, so the floor holds
        val fat = "x".repeat(DEFAULT_WINDOW_CHARS * 3)
        val messages = listOf(user("please help"), toolCall(), toolResult(result = fat))
        val window = conversationWindow(messages)

        assertTrue(window.isNotEmpty())
        assertEquals(ChatRole.USER, window.first().role)
        assertEquals("please help", window.first().text)
    }

    @Test
    fun `mid-turn history ending in a tool result still starts clean`() {
        // what runAgentLoop actually sends on hops 2+: the tail is a tool result, not a user message
        val messages = (1..15).flatMap { exchange() } + listOf(user("and this?"), toolCall(), toolResult())
        val window = conversationWindow(messages)

        assertEquals(ChatRole.USER, window.first().role)
        assertEquals(ChatRole.TOOL, window.last().role)
    }

    @Test
    fun `a conversation with no user message degrades to a plain tail`() {
        val messages = (1..30).map { assistant("orphan $it") }
        val window = conversationWindow(messages)

        assertEquals(DEFAULT_WINDOW_MESSAGES, window.size)
        assertSame(messages.last(), window.last())
    }
}
