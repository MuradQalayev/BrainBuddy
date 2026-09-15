package com.muradgalayev.brainbuddy.ui.utils

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.muradgalayev.brainbuddy.R

// user-facing text held by a view model. view models outlive a language switch, so a String
// resolved in one would stay in the old language; this is resolved where it's drawn instead
sealed interface UiText {
    data class Res(@StringRes val id: Int, val args: List<Any> = emptyList()) : UiText

    // text that is already final: a name, a server message, something the user typed
    data class Raw(val value: String) : UiText

    // 'Anna, Marco and Sofia', with the conjunction in the app's language
    data class Names(val names: List<String>) : UiText
}

fun uiText(@StringRes id: Int, vararg args: Any): UiText = UiText.Res(id, args.toList())

fun String.asUiText(): UiText = UiText.Raw(this)

// lookup is the raw resource text for an id. taking it as a function rather than a Context is
// what lets unit tests resolve against the real strings.xml files without Robolectric
fun UiText.resolve(lookup: (Int) -> String): String = when (this) {
    is UiText.Raw -> value
    is UiText.Names ->
        if (names.size <= 1) names.firstOrNull().orEmpty()
        else lookup(R.string.list_and).format(names.dropLast(1).joinToString(", "), names.last())
    is UiText.Res ->
        // no args means no formatting, the same as Resources.getString(id): a literal % survives
        if (args.isEmpty()) lookup(id)
        else lookup(id).format(*args.map { if (it is UiText) it.resolve(lookup) else it }.toTypedArray())
}

fun UiText.resolve(context: Context): String = resolve(context::getString)

@Composable
fun UiText.resolve(): String {
    // read so a locale change recomposes this, even where the text itself didn't change
    LocalConfiguration.current
    return resolve(LocalContext.current)
}
