package com.muradgalayev.brainbuddy.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.core.graphics.ColorUtils
import com.muradgalayev.brainbuddy.R

// how saturated a custom theme's accents are. three steps rather than a free slider because
// saturation is the control that decides how stimulating the result is, and that's the one
// thing worth being deliberate about. a continuous slider invites picking the most intense
// value by default, whereas naming the steps makes Soft an obvious, equally valid choice
enum class Vividness(val id: String, @androidx.annotation.StringRes val labelRes: Int, val saturation: Float) {
    Soft("soft", R.string.vivid_soft, 0.40f),
    Balanced("balanced", R.string.vivid_balanced, 0.58f),
    Vivid("vivid", R.string.vivid_vivid, 0.76f);

    companion object {
        val DEFAULT = Balanced
        fun fromStored(raw: String?): Vividness =
            entries.firstOrNull { it.id == raw } ?: DEFAULT
    }
}

// a user-built theme: two hues and how vivid to render them. stored rather than the generated
// colours themselves, so an improvement to the generator reaches themes people already made
// instead of leaving them frozen on old output
@androidx.compose.runtime.Immutable
data class CustomThemeSpec(
    val accentHue: Float = DEFAULT_ACCENT_HUE,
    val supportHue: Float = DEFAULT_SUPPORT_HUE,
    val vividness: Vividness = Vividness.DEFAULT,
)

// where the editor starts: Myndora's own orange, paired with a teal
const val DEFAULT_ACCENT_HUE = 24f
const val DEFAULT_SUPPORT_HUE = 174f

// contrast the accent must clear against the surface it sits on, as text. WCAG AA
private const val MIN_TEXT_CONTRAST = 4.5f

// builds a full palette from two hues. only hue is chosen by the user, lightness and
// saturation are derived, because those are what decide whether the result is readable, and a
// picker that lets you produce an unreadable interface isn't giving you a choice, it's giving
// you a trap. the accent is darkened (or lightened in dark mode) until it clears AA against
// the surface it will sit on, so every colour on the wheel yields something usable.
// neutrals are pulled slightly toward the accent's hue for the same reason the built-ins do
// it: surfaces then recede behind the content rather than vibrating against it
fun customPalette(
    accentHue: Float,
    supportHue: Float,
    vividness: Vividness,
    dark: Boolean,
): ThemePalette = if (dark) {
    darkCustomPalette(accentHue, supportHue, vividness.saturation)
} else {
    lightCustomPalette(accentHue, supportHue, vividness.saturation)
}

private fun lightCustomPalette(accentHue: Float, supportHue: Float, sat: Float): ThemePalette {
    val surface = Color.White
    val accent = hsl(accentHue, sat.coerceAtLeast(0.45f), 0.44f).darkenUntilReadableOn(surface)
    val support = hsl(supportHue, sat.coerceAtLeast(0.45f), 0.44f).darkenUntilReadableOn(surface)

    // neutral ramp: the accent's hue at a trace of saturation. enough to agree with the accent,
    // not enough to read as a colour in its own right
    val n = { l: Float, s: Float -> hsl(accentHue, s, l) }

    return ThemePalette(
        accent = accent,
        accentEnd = hsl(accentHue, sat.coerceAtLeast(0.5f), 0.57f),
        onAccent = Color.White,
        accentContainer = hsl(accentHue, (sat * 0.75f).coerceAtMost(0.55f), 0.92f),
        onAccentContainer = hsl(accentHue, sat, 0.22f),
        support = support,
        supportEnd = hsl(supportHue, sat.coerceAtLeast(0.5f), 0.57f),
        onSupport = Color.White,
        supportContainer = hsl(supportHue, (sat * 0.75f).coerceAtMost(0.55f), 0.92f),
        onSupportContainer = hsl(supportHue, sat, 0.22f),
        background = n(0.985f, 0.030f),
        surface = surface,
        containerLowest = surface,
        containerLow = n(0.985f, 0.030f),
        container = n(0.960f, 0.040f),
        containerHigh = n(0.935f, 0.045f),
        containerHighest = n(0.905f, 0.050f),
        surfaceVariant = n(0.950f, 0.040f),
        onSurface = n(0.100f, 0.140f),
        onSurfaceVariant = n(0.450f, 0.080f),
        outline = n(0.840f, 0.060f),
        outlineVariant = n(0.910f, 0.045f),
        error = Color(0xFFDC2626),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFEE2E2),
        onErrorContainer = Color(0xFF7F1D1D),
    )
}

private fun darkCustomPalette(accentHue: Float, supportHue: Float, sat: Float): ThemePalette {
    val n = { l: Float, s: Float -> hsl(accentHue, s, l) }
    val surface = n(0.090f, 0.110f)

    val accent = hsl(accentHue, sat.coerceAtLeast(0.5f), 0.66f).lightenUntilReadableOn(surface)
    val support = hsl(supportHue, sat.coerceAtLeast(0.5f), 0.66f).lightenUntilReadableOn(surface)

    return ThemePalette(
        accent = accent,
        accentEnd = hsl(accentHue, (sat * 0.85f).coerceAtLeast(0.4f), 0.78f),
        onAccent = hsl(accentHue, 0.60f, 0.12f),
        accentContainer = hsl(accentHue, sat, 0.30f),
        onAccentContainer = hsl(accentHue, 0.35f, 0.90f),
        support = support,
        supportEnd = hsl(supportHue, (sat * 0.85f).coerceAtLeast(0.4f), 0.78f),
        onSupport = hsl(supportHue, 0.60f, 0.12f),
        supportContainer = hsl(supportHue, sat, 0.30f),
        onSupportContainer = hsl(supportHue, 0.35f, 0.90f),
        background = n(0.055f, 0.120f),
        surface = surface,
        containerLowest = n(0.035f, 0.130f),
        containerLow = n(0.070f, 0.115f),
        container = surface,
        containerHigh = n(0.140f, 0.100f),
        containerHighest = n(0.190f, 0.090f),
        surfaceVariant = n(0.140f, 0.100f),
        onSurface = n(0.970f, 0.050f),
        onSurfaceVariant = n(0.660f, 0.080f),
        outline = n(0.350f, 0.080f),
        outlineVariant = n(0.140f, 0.100f),
        error = Color(0xFFF87171),
        onError = Color(0xFF450A0A),
        errorContainer = Color(0xFF7F1D1D),
        onErrorContainer = Color(0xFFFEE2E2),
    )
}

// walks the colour darker in small steps until it clears AA against the surface
private fun Color.darkenUntilReadableOn(surface: Color): Color = adjustUntilReadable(surface, -0.02f)

// walks the colour lighter until it clears AA against the surface
private fun Color.lightenUntilReadableOn(surface: Color): Color = adjustUntilReadable(surface, 0.02f)

private fun Color.adjustUntilReadable(surface: Color, step: Float): Color {
    val hsl = toHsl()
    var lightness = hsl[2]
    var candidate = this
    // bounded: some hues can't reach the target before running out of range, and a loop that
    // insists would spin. whatever the closest reachable value is, is what we use
    repeat(40) {
        if (contrastRatio(candidate, surface) >= MIN_TEXT_CONTRAST) return candidate
        lightness = (lightness + step).coerceIn(0f, 1f)
        candidate = hsl(hsl[0], hsl[1], lightness)
    }
    return candidate
}

private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun Color.toHsl(): FloatArray {
    val out = FloatArray(3)
    ColorUtils.colorToHSL(toArgbInt(), out)
    return out
}

private fun Color.toArgbInt(): Int = android.graphics.Color.argb(
    (alpha * 255).toInt(),
    (red * 255).toInt(),
    (green * 255).toInt(),
    (blue * 255).toInt(),
)

// hue in degrees, saturation and lightness as 0..1
internal fun hsl(hue: Float, saturation: Float, lightness: Float): Color = Color(
    ColorUtils.HSLToColor(
        floatArrayOf(
            ((hue % 360f) + 360f) % 360f,
            saturation.coerceIn(0f, 1f),
            lightness.coerceIn(0f, 1f),
        )
    )
)

// public alias of hsl() for UI that paints a hue directly, like the picker strip
fun hslColor(hue: Float, saturation: Float, lightness: Float): Color = hsl(hue, saturation, lightness)
