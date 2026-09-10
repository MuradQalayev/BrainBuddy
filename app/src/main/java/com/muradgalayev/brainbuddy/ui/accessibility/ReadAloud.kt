package com.muradgalayev.brainbuddy.ui.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import com.muradgalayev.brainbuddy.data.local.ReadAloudSpeaker

// the app-wide speak-what-I-tap hook. deliberately a plain (String?) -> Unit rather than the
// speaker itself, so a composable can take it without depending on anything about
// text-to-speech, and so previews and tests get a no-op for free
val LocalReadAloud = staticCompositionLocalOf<(String?) -> Unit> { {} }

// wraps an existing click so it speaks the label first. used at the point a component already
// knows its own label, which is why the feature works without a global touch interceptor or a
// hit-test against the semantics tree, neither of which Compose exposes publicly. it also
// means we never announce a scroll that merely started on top of a button
@Composable
@ReadOnlyComposable
fun speaking(label: String?, onClick: () -> Unit): () -> Unit {
    val speak = LocalReadAloud.current
    return {
        speak(label)
        onClick()
    }
}

// same, for the (T) -> Unit click handlers that carry a value
@Composable
@ReadOnlyComposable
fun <T> speakingWith(label: (T) -> String?, onClick: (T) -> Unit): (T) -> Unit {
    val speak = LocalReadAloud.current
    return { value ->
        speak(label(value))
        onClick(value)
    }
}

// bridges the injected ReadAloudSpeaker into LocalReadAloud
fun readAloudHandler(speaker: ReadAloudSpeaker): (String?) -> Unit = speaker::speak
