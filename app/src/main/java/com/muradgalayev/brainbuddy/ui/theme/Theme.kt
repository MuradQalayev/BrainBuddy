package com.muradgalayev.brainbuddy.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.TextSpacing
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.data.local.FontSize
// colour schemes are built per theme in AppTheme.kt, this file only resolves which one is
// active and eases between them

// extra leading as a multiple of the style's own line height, and extra letter spacing as a
// fraction of the font size. letter spacing is in em rather than sp so it stays proportional
// as text scales: a flat +0.6sp that opens up 12sp labels nicely does almost nothing at 32sp.
// the values stop well short of WCAG 1.4.12's 0.12em test threshold on purpose. that figure is
// a 'your layout must survive this' bound, and pushing letterforms that far apart visibly
// damages OpenDyslexic and Atkinson, the faces most likely to be on screen here
private val TextSpacing.lineMultiplier: Float
    get() = when (this) {
        TextSpacing.Normal -> 1f
        TextSpacing.Relaxed -> 1.15f
        TextSpacing.Loose -> 1.3f
    }

private val TextSpacing.letterEm: Float
    get() = when (this) {
        TextSpacing.Normal -> 0f
        TextSpacing.Relaxed -> 0.02f
        TextSpacing.Loose -> 0.045f
    }

// where the line box puts its slack. Compose distributes extra leading proportionally to
// ascent and descent by default, so the gap above a line and the gap below it are unequal:
// invisible at the stock 1.5 ratio, obvious once Loose has widened it. centring makes the
// added space symmetric, which is the point of the setting. total height is unchanged, so this
// is safe at every setting, and applying it uniformly means the options differ in spacing alone
private val EvenLeading = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

// one text style at the user's chosen size and spacing. lineHeight is scaled by the font scale
// as well as by the spacing multiplier: scaling only fontSize leaves 18.4sp text sitting in a
// line box still sized for 16sp, which tightens spacing exactly when someone asks for larger
// text, which is backwards
private fun TextStyle.scaledBy(fontScale: Float, spacing: TextSpacing): TextStyle {
    // Material's styles all specify these in sp, and the guards are for a style that doesn't,
    // where the right answer is to leave the value alone rather than invent one
    if (!fontSize.isSp) return copy(lineHeightStyle = EvenLeading)

    val scaledSize = fontSize.value * fontScale
    val baseLetter = if (letterSpacing.isSp) letterSpacing.value * fontScale else 0f

    return copy(
        fontSize = scaledSize.sp,
        lineHeight = if (lineHeight.isSp) {
            (lineHeight.value * fontScale * spacing.lineMultiplier).sp
        } else {
            lineHeight
        },
        letterSpacing = (baseLetter + scaledSize * spacing.letterEm).sp,
        lineHeightStyle = EvenLeading,
    )
}

private fun getScaledTypography(
    base: Typography,
    fontSize: FontSize,
    textSpacing: TextSpacing,
): Typography {
    val scale = when (fontSize) {
        FontSize.Small -> 0.9f
        FontSize.Medium -> 1f
        FontSize.Large -> 1.15f
    }

    fun TextStyle.scaled() = scaledBy(scale, textSpacing)

    return base.copy(
        displayLarge = base.displayLarge.scaled(),
        displayMedium = base.displayMedium.scaled(),
        displaySmall = base.displaySmall.scaled(),

        headlineLarge = base.headlineLarge.scaled(),
        headlineMedium = base.headlineMedium.scaled(),
        headlineSmall = base.headlineSmall.scaled(),

        titleLarge = base.titleLarge.scaled(),
        titleMedium = base.titleMedium.scaled(),
        titleSmall = base.titleSmall.scaled(),

        bodyLarge = base.bodyLarge.scaled(),
        bodyMedium = base.bodyMedium.scaled(),
        bodySmall = base.bodySmall.scaled(),

        labelLarge = base.labelLarge.scaled(),
        labelMedium = base.labelMedium.scaled(),
        labelSmall = base.labelSmall.scaled(),
    )
}

// how long a theme change takes to cross-fade
private const val THEME_ANIM_MS = 450

@Composable
fun MyndoraTheme(
    themeMode: ThemeMode = ThemeMode.System,
    appTheme: ThemeSelection = ThemeSelection.DEFAULT,
    fontMode: FontMode = FontMode.DEFAULT,
    fontSize: FontSize,
    textSpacing: TextSpacing = TextSpacing.DEFAULT,
    // a mode overlay may temporarily promote one of the theme's supporting accents
    modeAccentKey: String? = null,
    animationsEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }

    val palette = appTheme.palette(darkTheme)
    val baseTarget = if (darkTheme) palette.toDarkColorScheme() else palette.toLightColorScheme()
    val target = baseTarget.withModeAccent(modeAccentKey, palette)

    // every colour eases to its new value rather than the whole UI cutting between two palettes on
    // one frame. for an audience disproportionately sensitive to abrupt visual change, a hard cut
    // across the entire screen is a jolt, a short ease reads as the app settling
    val colorScheme = target.animated(animationsEnabled)

    val promotedAccent = when (modeAccentKey) {
        "calm" -> palette.support
        "warm" -> palette.accentEnd
        "neutral" -> palette.onSurfaceVariant
        else -> palette.accent
    }

    val accents = MyndoraAccents(
        accent = animateThemeColor(promotedAccent, "accent", animationsEnabled),
        accentEnd = animateThemeColor(
            if (modeAccentKey == null || modeAccentKey == "theme") palette.accentEnd else promotedAccent,
            "accentEnd",
            animationsEnabled,
        ),
        support = animateThemeColor(palette.support, "support", animationsEnabled),
        supportEnd = animateThemeColor(palette.supportEnd, "supportEnd", animationsEnabled),
    )

    val baseTypography = when (fontMode) {
        FontMode.Arial -> ArialTypography
        FontMode.OpenDyslexic -> OpenDyslexicTypography
        FontMode.Atkinson -> AtkinsonTypography
    }

    val typography = getScaledTypography(baseTypography, fontSize, textSpacing)

    CompositionLocalProvider(
        LocalMyndoraAccents provides accents,
        LocalAppTheme provides appTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
        ) {
            // MaterialTheme doesn't provide LocalContentColor: Material 3 defaults it to black, and only
            // Surface, Card and Scaffold set it from their container. so on any screen whose root paints
            // itself with Modifier.background() rather than a Surface, every Text and Icon that doesn't
            // name a colour renders pure black, invisible in dark mode and correct-looking in light,
            // which is why it went unnoticed. anchoring the default to onSurface makes uncoloured content
            // follow the theme everywhere, and a Surface still overrides it locally where it should
            CompositionLocalProvider(
                LocalContentColor provides colorScheme.onSurface,
                content = content,
            )
        }
    }
}

@Composable
private fun animateThemeColor(target: Color, label: String, animationsEnabled: Boolean): Color =
    animateColorAsState(
        targetValue = target,
        animationSpec = tween(if (animationsEnabled) THEME_ANIM_MS else 0),
        label = label,
    ).value

// eases every role of the scheme toward its target. roles are animated individually because
// ColorScheme is a plain holder with no interpolation of its own
@Composable
private fun ColorScheme.animated(animationsEnabled: Boolean): ColorScheme = copy(
    primary = animateThemeColor(primary, "primary", animationsEnabled),
    onPrimary = animateThemeColor(onPrimary, "onPrimary", animationsEnabled),
    primaryContainer = animateThemeColor(primaryContainer, "primaryContainer", animationsEnabled),
    onPrimaryContainer = animateThemeColor(onPrimaryContainer, "onPrimaryContainer", animationsEnabled),
    secondary = animateThemeColor(secondary, "secondary", animationsEnabled),
    onSecondary = animateThemeColor(onSecondary, "onSecondary", animationsEnabled),
    secondaryContainer = animateThemeColor(secondaryContainer, "secondaryContainer", animationsEnabled),
    onSecondaryContainer = animateThemeColor(onSecondaryContainer, "onSecondaryContainer", animationsEnabled),
    tertiary = animateThemeColor(tertiary, "tertiary", animationsEnabled),
    onTertiary = animateThemeColor(onTertiary, "onTertiary", animationsEnabled),
    tertiaryContainer = animateThemeColor(tertiaryContainer, "tertiaryContainer", animationsEnabled),
    onTertiaryContainer = animateThemeColor(onTertiaryContainer, "onTertiaryContainer", animationsEnabled),
    error = animateThemeColor(error, "error", animationsEnabled),
    onError = animateThemeColor(onError, "onError", animationsEnabled),
    errorContainer = animateThemeColor(errorContainer, "errorContainer", animationsEnabled),
    onErrorContainer = animateThemeColor(onErrorContainer, "onErrorContainer", animationsEnabled),
    background = animateThemeColor(background, "background", animationsEnabled),
    onBackground = animateThemeColor(onBackground, "onBackground", animationsEnabled),
    surface = animateThemeColor(surface, "surface", animationsEnabled),
    onSurface = animateThemeColor(onSurface, "onSurface", animationsEnabled),
    surfaceVariant = animateThemeColor(surfaceVariant, "surfaceVariant", animationsEnabled),
    onSurfaceVariant = animateThemeColor(onSurfaceVariant, "onSurfaceVariant", animationsEnabled),
    surfaceContainerLowest = animateThemeColor(surfaceContainerLowest, "containerLowest", animationsEnabled),
    surfaceContainerLow = animateThemeColor(surfaceContainerLow, "containerLow", animationsEnabled),
    surfaceContainer = animateThemeColor(surfaceContainer, "container", animationsEnabled),
    surfaceContainerHigh = animateThemeColor(surfaceContainerHigh, "containerHigh", animationsEnabled),
    surfaceContainerHighest = animateThemeColor(surfaceContainerHighest, "containerHighest", animationsEnabled),
    outline = animateThemeColor(outline, "outline", animationsEnabled),
    outlineVariant = animateThemeColor(outlineVariant, "outlineVariant", animationsEnabled),
)

// promotes a theme-native colour without replacing the user's stored theme
private fun ColorScheme.withModeAccent(key: String?, palette: ThemePalette): ColorScheme = when (key) {
    "calm" -> copy(
        primary = palette.support,
        onPrimary = palette.onSupport,
        primaryContainer = palette.supportContainer,
        onPrimaryContainer = palette.onSupportContainer,
    )
    "warm" -> copy(primary = palette.accentEnd, onPrimary = palette.onAccent)
    "neutral" -> copy(
        primary = palette.onSurfaceVariant,
        onPrimary = palette.surface,
        primaryContainer = palette.surfaceVariant,
        onPrimaryContainer = palette.onSurface,
    )
    else -> this
}

// the active theme's two brand accents, for gradients Material has no role for
val MaterialTheme.myndoraAccents: MyndoraAccents
    @Composable get() = LocalMyndoraAccents.current
