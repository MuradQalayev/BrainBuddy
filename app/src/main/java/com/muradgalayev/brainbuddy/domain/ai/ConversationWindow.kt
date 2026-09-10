package com.muradgalayev.brainbuddy.domain.ai

// chooses which slice of a conversation is actually sent to the model. everything the user
// typed stays on screen and in Supabase, this only decides what travels.
// without it the whole conversation went out on every turn, so a long chat grew quadratically
// in cost and eventually stopped working entirely once it outgrew the context limit. the old
// messages were paid for repeatedly and read approximately never: who the user is, their ADHD
// profile, their health context and today's date are all rebuilt fresh in the system prompt
// each turn, so the tail is only conversational thread, not memory.
// two rules decide where to cut, and the first is not negotiable:
// 1. the window starts at a USER message. an assistant tool_call and its tool result are one
//    indivisible unit sharing a tool_call_id, and cutting between them gets the request
//    rejected outright, because the surviving half references an id no longer in the payload.
//    user messages are the only points guaranteed not to be mid-exchange.
// 2. a character budget overrides the message count. twenty messages is usually small, but
//    tool results are unbounded, and one list_calendar_events over a busy week can outweigh
//    fifty ordinary replies. so the count is a ceiling, not a target

// roughly the last six to ten exchanges once tool steps are counted
const val DEFAULT_WINDOW_MESSAGES: Int = 20

// ~4k tokens at the usual four-characters-per-token rule of thumb
const val DEFAULT_WINDOW_CHARS: Int = 16_000

fun conversationWindow(
    messages: List<ChatMessage>,
    maxMessages: Int = DEFAULT_WINDOW_MESSAGES,
    maxChars: Int = DEFAULT_WINDOW_CHARS,
): List<ChatMessage> {
    if (messages.isEmpty()) return messages

    // the newest user message is the floor: it is the thing being answered, so no budget is
    // allowed to trim it away. everything after it belongs to the turn in progress
    val lastUserIndex = messages.indexOfLast { it.role == ChatRole.USER }
    // no user turn at all is not a shape this should ever see, so degrade to a plain tail rather
    // than inventing a boundary
    if (lastUserIndex < 0) return messages.takeLast(maxMessages)

    val userIndices = messages.indices.filter { messages[it].role == ChatRole.USER }

    // oldest boundary that still fits the message ceiling, then clamped to the floor. this can
    // send fewer than maxMessages: snapping forward to a user message is what keeps tool pairs
    // intact, and losing a few messages is the price of never emitting an orphaned tool result
    val countCut = messages.size - maxMessages
    var start = (userIndices.firstOrNull { it >= countCut } ?: lastUserIndex)
        .coerceAtMost(lastUserIndex)

    // then keep giving up whole exchanges until the payload fits
    while (start < lastUserIndex && charCost(messages, start) > maxChars) {
        start = userIndices.first { it > start }
    }

    return messages.drop(start)
}

// size of the window starting at `start`, counting everything that serializes
private fun charCost(messages: List<ChatMessage>, start: Int): Int {
    var total = 0
    for (i in start until messages.size) {
        val m = messages[i]
        total += m.text.length
        total += m.toolResult?.length ?: 0
        total += m.toolName?.length ?: 0
        // toString approximates the serialized args, which is all this needs: a budget, not an invoice
        total += m.toolArgs?.toString()?.length ?: 0
    }
    return total
}
