package com.muradgalayev.brainbuddy.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// how much of the bottom the floating nav bar covers. content scrolls underneath the pill, so a
// scrolling screen adds this to the end of its padding to keep its last item reachable
val LocalNavBarInset = compositionLocalOf { 0.dp }

// for screens that don't scroll under the bar yet: they keep ending above it
@Composable
internal fun AboveNavBar(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(bottom = LocalNavBarInset.current)) { content() }
}
