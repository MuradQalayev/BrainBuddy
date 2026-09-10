package com.muradgalayev.brainbuddy.domain.model

// Myndora Together: mutual connections between users, and the scopes each side chooses to
// open up. the sharing model is deliberately one-directional per scope. if your partner grants
// you CALENDAR you can put an event on their calendar and they'll see it, and you still see
// nothing else of theirs. both sides granting the same scope is two independent grants.
// AVAILABILITY is the one scope that shares a shape rather than content: the hours you're
// already spoken for, with nothing attached to them. it exists so someone scheduling for you
// can avoid your 12:00 without ever learning what your 12:00 is

// how the sender labels the person they're adding
enum class ConnectionRelation(val label: String) {
    FRIEND("Friend"),
    FAMILY("Family"),
    PARTNER("Partner"),
    OTHER("Someone else");

    companion object {
        fun fromRemote(value: String?): ConnectionRelation =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
    }
}

// a single thing a connection may do with your data
enum class ShareScope(val label: String, val description: String) {
    CALENDAR(
        "Calendar",
        "They can add events to your calendar. You'll see them; they only ever see the ones they added.",
    ),
    AVAILABILITY(
        "Free times",
        "They see which hours you're already busy — the times only, never what's in them. " +
            "Used to suggest when to put things.",
    ),
    TODOS(
        "To-dos",
        "They can add tasks to your list. They only ever see the ones they added.",
    ),
    WELLNESS(
        "Wellness summary",
        "They can see your step, sleep and heart-rate summary. Read-only.",
    ),
    FOCUS(
        "Focus together",
        "They can invite you to focus at the same time, and see that you're in a " +
            "session and how long is left — never what you're working on.",
    );

    companion object {
        fun fromRemote(value: String?): ShareScope? =
            entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

// whether the sender wrote their own question or had Myndora generate a code
enum class ChallengeKind { QUESTION, CODE }

// a live connection, with both permission directions already resolved so the UI can render a
// profile without further queries. grantedByMe is what I opened up to them, grantedToMe is
// what they opened up to me
data class Connection(
    val connectionId: String,
    val userId: String,
    val displayName: String?,
    val username: String?,
    val avatarUrl: String?,
    val relation: ConnectionRelation,
    val grantedByMe: Set<ShareScope> = emptySet(),
    val grantedToMe: Set<ShareScope> = emptySet(),
    val connectedAt: String? = null,
) {
    // best available human label. falls back through username to a generic name
    val name: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: "Myndora user"

    fun canGrant(scope: ShareScope): Boolean = scope in grantedByMe
    fun canUse(scope: ShareScope): Boolean = scope in grantedToMe
}

// a request waiting for me to answer its challenge
data class IncomingRequest(
    val id: String,
    val requesterId: String,
    val displayName: String?,
    val username: String?,
    val avatarUrl: String?,
    val relation: ConnectionRelation,
    val challengeKind: ChallengeKind,
    // null for CODE challenges, where the UI shows a generic code prompt instead
    val challengeQuestion: String?,
    val attemptsLeft: Int,
    val locked: Boolean,
) {
    val name: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: "Myndora user"

    // what to show above the answer field
    val prompt: String
        get() = challengeQuestion?.takeIf { it.isNotBlank() }
            ?: "Enter the code $name gave you"
}

// a request I sent that hasn't been answered yet
data class OutgoingRequest(
    val id: String,
    val addresseeId: String,
    val displayName: String?,
    val username: String?,
    val avatarUrl: String?,
    val relation: ConnectionRelation,
    val locked: Boolean,
) {
    val name: String
        get() = displayName?.takeIf { it.isNotBlank() }
            ?: username?.takeIf { it.isNotBlank() }
            ?: "Myndora user"
}

// outcome of answering a challenge
sealed interface ChallengeResult {
    data object Accepted : ChallengeResult
    data class Wrong(val attemptsLeft: Int) : ChallengeResult
    data object Locked : ChallengeResult
}

// a freshly minted invite, shown once to its creator so they can share it
data class InviteLink(
    val token: String,
    // the myndora://connect deep link to send
    val url: String,
    val expiresAt: String? = null,
)

// A verified Myndora account matched to a phone number that exists in the caller's local address
// book. The local contact name/number never comes from or returns to Supabase.
data class MyndoraContact(
    val userId: String,
    val contactName: String,
    val phoneNumber: String,
    val avatarUrl: String? = null,
    val isConnected: Boolean = false,
)

// what a tapped invite link resolves to, before the user commits to accepting
data class InvitePreview(
    val inviterId: String? = null,
    val name: String = "A Myndora user",
    val username: String? = null,
    val avatarUrl: String? = null,
    val relation: ConnectionRelation = ConnectionRelation.OTHER,
    val valid: Boolean = false,
    // why it can't be used, when valid is false
    val reason: String? = null,
)
