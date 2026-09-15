package com.muradgalayev.brainbuddy.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

// semantics blocks aren't composable, so a string resource can't be read inside one. these take
// the text as an argument instead, which is evaluated where the modifier chain is built

fun Modifier.contentDescription(text: String): Modifier = semantics { contentDescription = text }

fun Modifier.stateDescription(text: String): Modifier = semantics { stateDescription = text }
