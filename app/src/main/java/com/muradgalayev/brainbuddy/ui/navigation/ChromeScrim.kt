package com.muradgalayev.brainbuddy.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue

// a screen's overlay can't draw past the NavHost's insets, so the strips around the nav pill and under
// the status bar stayed undimmed. overlays report their dim here and the graph paints those strips.
// one NavGraph, one value, so a plain holder is enough
internal object ChromeScrim {
    var alpha by mutableFloatStateOf(0f)
}

// call next to the overlay's visibility flag, not inside it, so the strips fade out with the overlay
// instead of waiting for its exit animation to finish
@Composable
fun DimAppChrome(alpha: Float) {
    DisposableEffect(alpha) {
        ChromeScrim.alpha = alpha
        onDispose { ChromeScrim.alpha = 0f }
    }
}
