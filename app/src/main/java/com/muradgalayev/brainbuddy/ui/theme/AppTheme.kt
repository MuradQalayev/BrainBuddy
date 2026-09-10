package com.muradgalayev.brainbuddy.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// selectable colour themes. the set is built around one idea: for ADHD the useful axis isn't
// 'which colour do you like' but how much stimulation you want right now. sensory needs swing,
// and the same person can need a quiet interface on an overloaded day and a warmer,
// higher-contrast one when nothing is holding their attention. so the themes are ordered along
// that axis and labelled by it, rather than being a set of arbitrary colour ways.
// rules every theme follows, because they're what keep the UI usable rather than decorated:
// - one dominant accent, one supporting accent, never more. each extra competing hue is
//   another thing to parse before you can find the button you wanted.
// - accent always means the same thing, actionable. switching theme changes hue, never meaning.
// - no red as an accent. saturated red reads as alarm and raises arousal whether or not
//   anything is wrong, so it stays reserved for genuine errors.
// - text accents are darker than graphic accents in light mode. a bright accent that looks
//   good on a ring fails contrast at label sizes, and small-text effort compounds.
// - neutrals are tinted toward the accent's temperature, so surfaces recede behind the content
//   instead of vibrating against it.
// - no saturated complementary pairs sitting adjacent, they shimmer at the boundary
enum class AppTheme(
    val id: String,
    val label: String,
    // shown under the name in the picker, says when to reach for it
    val blurb: String,
    val light: ThemePalette,
    val dark: ThemePalette,
) {
    Calm(
        id = "calm",
        label = "Calm",
        blurb = "Quietest option. For days when everything is already too much.",
        light = CalmLight,
        dark = CalmDark,
    ),
    Tide(
        id = "tide",
        label = "Tide",
        blurb = "Cool and low-arousal. Good for long stretches of focused work.",
        light = TideLight,
        dark = TideDark,
    ),
    Ember(
        id = "ember",
        label = "Ember",
        blurb = "Warm and activating. Myndora's own colours.",
        light = EmberLight,
        dark = EmberDark,
    ),
    Bloom(
        id = "bloom",
        label = "Bloom",
        blurb = "Boldest and brightest. For when nothing is holding your attention.",
        light = BloomLight,
        dark = BloomDark,
    );

    fun palette(dark: Boolean): ThemePalette = if (dark) this.dark else light

    companion object {
        val DEFAULT = Ember

        fun fromStored(raw: String?): AppTheme =
            entries.firstOrNull { it.id == raw } ?: DEFAULT
    }
}

// one theme in one brightness mode. accent and support are the two brand colours, everything
// else is the neutral ramp they sit on. plain data, so a theme reads as a list of decisions
@Immutable
data class ThemePalette(
    val accent: Color,
    val accentEnd: Color,
    val onAccent: Color,
    val accentContainer: Color,
    val onAccentContainer: Color,
    val support: Color,
    val supportEnd: Color,
    val onSupport: Color,
    val supportContainer: Color,
    val onSupportContainer: Color,
    val background: Color,
    val surface: Color,
    val containerLowest: Color,
    val containerLow: Color,
    val container: Color,
    val containerHigh: Color,
    val containerHighest: Color,
    val surfaceVariant: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val outline: Color,
    val outlineVariant: Color,
    val error: Color,
    val onError: Color,
    val errorContainer: Color,
    val onErrorContainer: Color,
)

// the two brand accents, already resolved for the active theme and brightness. exposed
// separately from ColorScheme because gradients need both ends of the ramp and Material has
// no role for 'the second half of an accent'
@Immutable
data class MyndoraAccents(
    val accent: Color,
    val accentEnd: Color,
    val support: Color,
    val supportEnd: Color,
)

// dynamic rather than static on purpose: these values change on every frame of a theme
// transition, and a static local invalidates the entire composition each time. a dynamic one
// re-composes only what actually reads an accent
val LocalMyndoraAccents = compositionLocalOf {
    AppTheme.DEFAULT.light.toAccents()
}

// a theme resolved to actual palettes, either a built-in or a generated custom one. everything
// downstream works in these terms, so a hand-picked palette is a first-class theme rather than
// a special case bolted onto the enum
@Immutable
data class ThemeSelection(
    val id: String,
    val label: String,
    val blurb: String,
    val light: ThemePalette,
    val dark: ThemePalette,
) {
    fun palette(dark: Boolean): ThemePalette = if (dark) this.dark else light

    companion object {
        const val CUSTOM_ID = "custom"

        val DEFAULT = AppTheme.DEFAULT.toSelection()

        // a theme built from the user's own two hues
        fun custom(
            accentHue: Float,
            supportHue: Float,
            vividness: Vividness,
        ) = ThemeSelection(
            id = CUSTOM_ID,
            label = "Yours",
            blurb = "Built from the two colors you picked.",
            light = customPalette(accentHue, supportHue, vividness, dark = false),
            dark = customPalette(accentHue, supportHue, vividness, dark = true),
        )
    }
}

fun AppTheme.toSelection() = ThemeSelection(
    id = id,
    label = label,
    blurb = blurb,
    light = light,
    dark = dark,
)

// the active theme itself, for the rare component that needs a palette other than the one
// currently applied: the nav bar is a dark pill in both brightness modes, so it reads from
// the theme's dark palette whatever the app is set to
val LocalAppTheme = staticCompositionLocalOf { ThemeSelection.DEFAULT }

fun ThemePalette.toAccents() = MyndoraAccents(
    accent = accent,
    accentEnd = accentEnd,
    support = support,
    supportEnd = supportEnd,
)

fun ThemePalette.toLightColorScheme(): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = accentContainer,
    onPrimaryContainer = onAccentContainer,
    secondary = support,
    onSecondary = onSupport,
    secondaryContainer = supportContainer,
    onSecondaryContainer = onSupportContainer,
    tertiary = support,
    onTertiary = onSupport,
    tertiaryContainer = supportContainer,
    onTertiaryContainer = onSupportContainer,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    background = background,
    onBackground = onSurface,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceContainerLowest = containerLowest,
    surfaceContainerLow = containerLow,
    surfaceContainer = container,
    surfaceContainerHigh = containerHigh,
    surfaceContainerHighest = containerHighest,
    outline = outline,
    outlineVariant = outlineVariant,
)

fun ThemePalette.toDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = onAccent,
    primaryContainer = accentContainer,
    onPrimaryContainer = onAccentContainer,
    secondary = support,
    onSecondary = onSupport,
    secondaryContainer = supportContainer,
    onSecondaryContainer = onSupportContainer,
    tertiary = support,
    onTertiary = onSupport,
    tertiaryContainer = supportContainer,
    onTertiaryContainer = onSupportContainer,
    error = error,
    onError = onError,
    errorContainer = errorContainer,
    onErrorContainer = onErrorContainer,
    background = background,
    onBackground = onSurface,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    surfaceContainerLowest = containerLowest,
    surfaceContainerLow = containerLow,
    surfaceContainer = container,
    surfaceContainerHigh = containerHigh,
    surfaceContainerHighest = containerHighest,
    outline = outline,
    outlineVariant = outlineVariant,
    scrim = Color.Black,
)

// error colours are shared across every theme on purpose. 'something went wrong' has to look
// identical everywhere, or the one signal that must cut through becomes theme-dependent
private val ErrorLight = Color(0xFFDC2626)
private val OnErrorLight = Color(0xFFFFFFFF)
private val ErrorContainerLight = Color(0xFFFEE2E2)
private val OnErrorContainerLight = Color(0xFF7F1D1D)

private val ErrorDark = Color(0xFFF87171)
private val OnErrorDark = Color(0xFF450A0A)
private val ErrorContainerDark = Color(0xFF7F1D1D)
private val OnErrorContainerDark = Color(0xFFFEE2E2)

// Calm, lowest stimulation. deliberately the least colourful thing here: a slate blue that is
// clearly a colour, so buttons still read as buttons, without ever raising its voice
private val CalmLight = ThemePalette(
    accent = Color(0xFF475569), accentEnd = Color(0xFF64748B), onAccent = Color(0xFFFFFFFF),
    accentContainer = Color(0xFFE2E8F0), onAccentContainer = Color(0xFF1E293B),
    support = Color(0xFF0F766E), supportEnd = Color(0xFF14B8A6), onSupport = Color(0xFFFFFFFF),
    supportContainer = Color(0xFFCCFBF1), onSupportContainer = Color(0xFF134E4A),
    background = Color(0xFFF8FAFC), surface = Color(0xFFFFFFFF),
    containerLowest = Color(0xFFFFFFFF), containerLow = Color(0xFFF8FAFC),
    container = Color(0xFFF1F5F9), containerHigh = Color(0xFFE8EDF3),
    containerHighest = Color(0xFFE2E8F0), surfaceVariant = Color(0xFFEEF2F6),
    onSurface = Color(0xFF0F172A), onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1), outlineVariant = Color(0xFFE2E8F0),
    error = ErrorLight, onError = OnErrorLight,
    errorContainer = ErrorContainerLight, onErrorContainer = OnErrorContainerLight,
)

private val CalmDark = ThemePalette(
    accent = Color(0xFF94A3B8), accentEnd = Color(0xFFCBD5E1), onAccent = Color(0xFF0F172A),
    accentContainer = Color(0xFF334155), onAccentContainer = Color(0xFFE2E8F0),
    support = Color(0xFF5EEAD4), supportEnd = Color(0xFF99F6E4), onSupport = Color(0xFF042F2E),
    supportContainer = Color(0xFF115E59), onSupportContainer = Color(0xFFCCFBF1),
    background = Color(0xFF0B0F14), surface = Color(0xFF131A22),
    containerLowest = Color(0xFF070A0E), containerLow = Color(0xFF0F151C),
    container = Color(0xFF131A22), containerHigh = Color(0xFF1E262F),
    containerHighest = Color(0xFF29323D), surfaceVariant = Color(0xFF1E262F),
    onSurface = Color(0xFFF1F5F9), onSurfaceVariant = Color(0xFF94A3B8),
    outline = Color(0xFF475569), outlineVariant = Color(0xFF1E262F),
    error = ErrorDark, onError = OnErrorDark,
    errorContainer = ErrorContainerDark, onErrorContainer = OnErrorContainerDark,
)

// Tide, cool and focus-oriented
private val TideLight = ThemePalette(
    accent = Color(0xFF0369A1), accentEnd = Color(0xFF0EA5E9), onAccent = Color(0xFFFFFFFF),
    accentContainer = Color(0xFFE0F2FE), onAccentContainer = Color(0xFF0C4A6E),
    support = Color(0xFF0D9488), supportEnd = Color(0xFF14B8A6), onSupport = Color(0xFFFFFFFF),
    supportContainer = Color(0xFFCCFBF1), onSupportContainer = Color(0xFF134E4A),
    background = Color(0xFFF7FAFC), surface = Color(0xFFFFFFFF),
    containerLowest = Color(0xFFFFFFFF), containerLow = Color(0xFFF7FAFC),
    container = Color(0xFFEFF6FA), containerHigh = Color(0xFFE3EDF4),
    containerHighest = Color(0xFFD6E3EC), surfaceVariant = Color(0xFFEAF2F7),
    onSurface = Color(0xFF0C1A24), onSurfaceVariant = Color(0xFF5A7286),
    outline = Color(0xFFC3D4E0), outlineVariant = Color(0xFFE3EDF4),
    error = ErrorLight, onError = OnErrorLight,
    errorContainer = ErrorContainerLight, onErrorContainer = OnErrorContainerLight,
)

private val TideDark = ThemePalette(
    accent = Color(0xFF38BDF8), accentEnd = Color(0xFF7DD3FC), onAccent = Color(0xFF082F49),
    accentContainer = Color(0xFF075985), onAccentContainer = Color(0xFFE0F2FE),
    support = Color(0xFF2DD4BF), supportEnd = Color(0xFF5EEAD4), onSupport = Color(0xFF042F2E),
    supportContainer = Color(0xFF115E59), onSupportContainer = Color(0xFFCCFBF1),
    background = Color(0xFF08121A), surface = Color(0xFF0F1D27),
    containerLowest = Color(0xFF050C12), containerLow = Color(0xFF0B1721),
    container = Color(0xFF0F1D27), containerHigh = Color(0xFF172833),
    containerHighest = Color(0xFF22343F), surfaceVariant = Color(0xFF172833),
    onSurface = Color(0xFFF0F7FB), onSurfaceVariant = Color(0xFF8FA9BA),
    outline = Color(0xFF3E5768), outlineVariant = Color(0xFF172833),
    error = ErrorDark, onError = OnErrorDark,
    errorContainer = ErrorContainerDark, onErrorContainer = OnErrorContainerDark,
)

// Ember, Myndora's own, warm
private val EmberLight = ThemePalette(
    accent = Color(0xFFEA580C), accentEnd = Color(0xFFF97316), onAccent = Color(0xFFFFFFFF),
    accentContainer = Color(0xFFFFEDD5), onAccentContainer = Color(0xFF7C2D12),
    support = Color(0xFF0D9488), supportEnd = Color(0xFF14B8A6), onSupport = Color(0xFFFFFFFF),
    supportContainer = Color(0xFFCCFBF1), onSupportContainer = Color(0xFF134E4A),
    background = Color(0xFFFAFAF9), surface = Color(0xFFFFFFFF),
    containerLowest = Color(0xFFFFFFFF), containerLow = Color(0xFFFAFAF9),
    container = Color(0xFFF5F5F4), containerHigh = Color(0xFFEEEDEB),
    containerHighest = Color(0xFFE7E5E4), surfaceVariant = Color(0xFFF0EFED),
    onSurface = Color(0xFF1C1917), onSurfaceVariant = Color(0xFF78716C),
    outline = Color(0xFFD6D3D1), outlineVariant = Color(0xFFE7E5E4),
    error = ErrorLight, onError = OnErrorLight,
    errorContainer = ErrorContainerLight, onErrorContainer = OnErrorContainerLight,
)

private val EmberDark = ThemePalette(
    accent = Color(0xFFFB923C), accentEnd = Color(0xFFFDBA74), onAccent = Color(0xFF431407),
    accentContainer = Color(0xFF9A3412), onAccentContainer = Color(0xFFFFEDD5),
    support = Color(0xFF2DD4BF), supportEnd = Color(0xFF5EEAD4), onSupport = Color(0xFF042F2E),
    supportContainer = Color(0xFF115E59), onSupportContainer = Color(0xFFCCFBF1),
    background = Color(0xFF12100E), surface = Color(0xFF1C1917),
    containerLowest = Color(0xFF0C0A09), containerLow = Color(0xFF171513),
    container = Color(0xFF1C1917), containerHigh = Color(0xFF292524),
    containerHighest = Color(0xFF35302E), surfaceVariant = Color(0xFF292524),
    onSurface = Color(0xFFFAFAF9), onSurfaceVariant = Color(0xFFA8A29E),
    outline = Color(0xFF57534E), outlineVariant = Color(0xFF292524),
    error = ErrorDark, onError = OnErrorDark,
    errorContainer = ErrorContainerDark, onErrorContainer = OnErrorContainerDark,
)

// Bloom, highest stimulation. the violet and pink are close enough on the wheel to stay
// harmonious, a violet/lime pairing at this saturation would shimmer where the two meet
private val BloomLight = ThemePalette(
    accent = Color(0xFF7C3AED), accentEnd = Color(0xFFA855F7), onAccent = Color(0xFFFFFFFF),
    accentContainer = Color(0xFFEDE9FE), onAccentContainer = Color(0xFF4C1D95),
    support = Color(0xFFDB2777), supportEnd = Color(0xFFEC4899), onSupport = Color(0xFFFFFFFF),
    supportContainer = Color(0xFFFCE7F3), onSupportContainer = Color(0xFF831843),
    background = Color(0xFFFAF9FC), surface = Color(0xFFFFFFFF),
    containerLowest = Color(0xFFFFFFFF), containerLow = Color(0xFFFAF9FC),
    container = Color(0xFFF4F2F8), containerHigh = Color(0xFFEAE7F1),
    containerHighest = Color(0xFFDEDAE9), surfaceVariant = Color(0xFFF0EEF6),
    onSurface = Color(0xFF1B1725), onSurfaceVariant = Color(0xFF6F6885),
    outline = Color(0xFFCEC8DD), outlineVariant = Color(0xFFEAE7F1),
    error = ErrorLight, onError = OnErrorLight,
    errorContainer = ErrorContainerLight, onErrorContainer = OnErrorContainerLight,
)

private val BloomDark = ThemePalette(
    accent = Color(0xFFA78BFA), accentEnd = Color(0xFFC4B5FD), onAccent = Color(0xFF2E1065),
    accentContainer = Color(0xFF5B21B6), onAccentContainer = Color(0xFFEDE9FE),
    support = Color(0xFFF472B6), supportEnd = Color(0xFFF9A8D4), onSupport = Color(0xFF500724),
    supportContainer = Color(0xFF9D174D), onSupportContainer = Color(0xFFFCE7F3),
    background = Color(0xFF100D18), surface = Color(0xFF191524),
    containerLowest = Color(0xFF0A0811), containerLow = Color(0xFF14111E),
    container = Color(0xFF191524), containerHigh = Color(0xFF241F33),
    containerHighest = Color(0xFF302941), surfaceVariant = Color(0xFF241F33),
    onSurface = Color(0xFFF7F5FC), onSurfaceVariant = Color(0xFFA79DBE),
    outline = Color(0xFF554C6B), outlineVariant = Color(0xFF241F33),
    error = ErrorDark, onError = OnErrorDark,
    errorContainer = ErrorContainerDark, onErrorContainer = OnErrorContainerDark,
)
