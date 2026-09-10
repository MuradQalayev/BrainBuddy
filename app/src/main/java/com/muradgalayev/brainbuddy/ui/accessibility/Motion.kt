package com.muradgalayev.brainbuddy.ui.accessibility

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

// whether the app is allowed to move. provided once at the root from the stored preference, so
// no component has to reach for DataStore to know whether to animate.
// a zero-duration animation still arrives at the right value, it just gets there on the next
// frame. that's the property that makes this safe to thread through existing code: turning
// motion off can never leave a component stuck mid-transition, showing a half-faded card or a
// ring frozen at 40%.
// static rather than dynamic because it changes at most a handful of times in a session, and
// when it does, every animated thing on screen genuinely does need to re-read it
val LocalAnimationsEnabled = staticCompositionLocalOf { true }

// shorthand for the common if (animationsOn()) ... else ... at a call site
@Composable
@ReadOnlyComposable
fun animationsOn(): Boolean = LocalAnimationsEnabled.current
