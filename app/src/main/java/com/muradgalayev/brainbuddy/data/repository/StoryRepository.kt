package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

// one published story, already merged with whether this user has seen it
data class Story(
    val id: String,
    val imageUrl: String,
    // the word under the circle in the tray
    val title: String,
    val caption: String?,
    // Optional Instagram-style link sticker. Only normalized http(s) URLs reach the UI.
    val linkUrl: String?,
    val linkLabel: String?,
    val seen: Boolean,
)

@Serializable
private data class StoryDto(
    val id: String,
    @SerialName("image_url") val imageUrl: String,
    val title: String? = null,
    val caption: String? = null,
    @SerialName("link_url") val linkUrl: String? = null,
    @SerialName("link_label") val linkLabel: String? = null,
)

@Serializable
private data class StoryViewDto(
    @SerialName("user_id") val userId: String,
    @SerialName("story_id") val storyId: String,
)

// reads the story feed and records what's been seen. there's no write path for stories on
// purpose: they're published from the Supabase dashboard, and the table has no insert policy
@Singleton
class StoryRepository @Inject constructor(
    private val supabase: SupabaseClient,
    private val authRepository: AuthRepository,
) {

    // the live stories, oldest first, each marked seen or not. oldest first because these are read
    // like a sequence rather than a feed, story 1 then story 2 the way the sender wrote them, and
    // newest-first would show the punchline before the setup.
    // returns empty on any failure. a story is a nice-to-have, and a broken network must leave the
    // home screen exactly as it was rather than show an error about content nobody asked for
    suspend fun loadStories(): List<Story> = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId() ?: return@withContext emptyList()
        try {
            // RLS already filters to live rows, so the app doesn't re-state the date logic and the two
            // can't drift apart
            val stories = supabase.from(STORIES)
                .select {
                    order("published_at", Order.ASCENDING)
                    order("sort_order", Order.ASCENDING)
                }
                .decodeList<StoryDto>()
            if (stories.isEmpty()) return@withContext emptyList()

            val seenIds = runCatching {
                supabase.from(STORY_VIEWS)
                    .select { filter { eq("user_id", userId) } }
                    .decodeList<StoryViewDto>()
                    .map { it.storyId }
                    .toSet()
            }.getOrElse {
                // losing the seen-set costs a re-read of something already read, losing the stories costs the
                // whole feature, so neither is fatal
                Log.w(TAG, "Couldn't load story views (${it::class.java.simpleName})")
                emptySet()
            }

            stories.map {
                Story(
                    id = it.id,
                    imageUrl = it.imageUrl,
                    // a circle with no word under it reads as a broken avatar, so an untitled story borrows the
                    // app's name rather than nothing
                    title = it.title?.takeIf { t -> t.isNotBlank() } ?: "Myndora",
                    caption = it.caption?.takeIf { c -> c.isNotBlank() },
                    linkUrl = safeStoryLink(it.linkUrl),
                    linkLabel = it.linkLabel?.trim()?.takeIf { label -> label.isNotEmpty() },
                    seen = it.id in seenIds,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't load stories (${e::class.java.simpleName})")
            emptyList()
        }
    }

    // records that the user reached this story. best-effort and fire-and-forget: the worst
    // outcome of a failure is the indicator staying lit, which is a far smaller problem than an
    // error toast over a motivational poster
    suspend fun markSeen(storyId: String) = withContext(Dispatchers.IO) {
        val userId = authRepository.getCurrentUserId() ?: return@withContext
        runCatching {
            // the primary key makes a repeat view a no-op rather than a duplicate
            supabase.from(STORY_VIEWS).upsert(
                StoryViewDto(userId = userId, storyId = storyId)
            )
        }.onFailure { Log.w(TAG, "Couldn't record story view (${it::class.java.simpleName})") }
        Unit
    }

    private companion object {
        const val TAG = "StoryRepository"
        const val STORIES = "stories"
        const val STORY_VIEWS = "story_views"
    }
}

// Treat links as untrusted content even though stories currently come from the dashboard. This
// helper is deliberately JVM-only so its scheme/host boundary can be covered by unit tests.
internal fun safeStoryLink(raw: String?): String? {
    val value = raw?.trim()?.takeIf { it.isNotEmpty() && it.length <= 2_048 } ?: return null
    val parsed = runCatching { java.net.URI(value) }.getOrNull() ?: return null
    if (parsed.scheme?.lowercase() !in setOf("http", "https")) return null
    if (parsed.host.isNullOrBlank() || parsed.userInfo != null) return null
    return value
}
