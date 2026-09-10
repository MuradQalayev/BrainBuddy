package com.muradgalayev.brainbuddy.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.data.local.FontMode

private val OpenDyslexicFontFamily = FontFamily(
    Font(R.font.open_dyslexic_regular, FontWeight.Normal),
    Font(R.font.open_dyslexic_bold, FontWeight.Bold),
)

// Arimo, shown to the user as Arial. Arial itself is a Monotype licence we can't ship and
// Android has never carried it. Arimo (Apache-2.0) is its metric-compatible twin: same widths,
// same letterforms, so a layout designed against Arial lays out identically
private val ArialFontFamily = FontFamily(
    Font(R.font.arimo_regular, FontWeight.Normal),
    Font(R.font.arimo_bold, FontWeight.Bold),
)

// Atkinson Hyperlegible (Braille Institute, SIL OFL). drawn specifically for low vision: it
// exaggerates the differences between shapes that usually collide, I/l/1, O/0, b/d, rather
// than merely enlarging them
private val AtkinsonFontFamily = FontFamily(
    Font(R.font.atkinson_hyperlegible_regular, FontWeight.Normal),
    Font(R.font.atkinson_hyperlegible_bold, FontWeight.Bold),
)

private fun Typography.withFontFamily(fontFamily: FontFamily) = copy(
    displayLarge = displayLarge.copy(fontFamily = fontFamily),
    displayMedium = displayMedium.copy(fontFamily = fontFamily),
    displaySmall = displaySmall.copy(fontFamily = fontFamily),
    headlineLarge = headlineLarge.copy(fontFamily = fontFamily),
    headlineMedium = headlineMedium.copy(fontFamily = fontFamily),
    headlineSmall = headlineSmall.copy(fontFamily = fontFamily),
    titleLarge = titleLarge.copy(fontFamily = fontFamily),
    titleMedium = titleMedium.copy(fontFamily = fontFamily),
    titleSmall = titleSmall.copy(fontFamily = fontFamily),
    bodyLarge = bodyLarge.copy(fontFamily = fontFamily),
    bodyMedium = bodyMedium.copy(fontFamily = fontFamily),
    bodySmall = bodySmall.copy(fontFamily = fontFamily),
    labelLarge = labelLarge.copy(fontFamily = fontFamily),
    labelMedium = labelMedium.copy(fontFamily = fontFamily),
    labelSmall = labelSmall.copy(fontFamily = fontFamily),
)

// every option shares these sizes, weights and spacing, only the family changes, so switching
// typeface never reflows a screen. font size is a separate setting
private val BaseTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 40.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        lineHeight = 32.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.25.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)

// default. familiar, neutral, and the widest character coverage of the three
val ArialTypography = BaseTypography.withFontFamily(ArialFontFamily)

// weighted bottoms give each letter a distinct orientation, so b/d/p/q stop swapping
val OpenDyslexicTypography = BaseTypography.withFontFamily(OpenDyslexicFontFamily)

// built for low vision, unambiguous letterforms rather than just larger ones
val AtkinsonTypography = BaseTypography.withFontFamily(AtkinsonFontFamily)

// the typeface behind a FontMode. exposed for the one thing MaterialTheme.typography can't do:
// showing a font the user hasn't picked yet. a chooser that lists Atkinson in the currently
// active font is asking people to choose blind
fun fontFamilyOf(mode: FontMode): FontFamily = when (mode) {
    FontMode.Arial -> ArialFontFamily
    FontMode.OpenDyslexic -> OpenDyslexicFontFamily
    FontMode.Atkinson -> AtkinsonFontFamily
}
