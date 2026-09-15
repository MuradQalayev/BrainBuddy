package com.muradgalayev.brainbuddy.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Brightness4
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.ui.settings.AppearancePreferencesViewModel
import com.muradgalayev.brainbuddy.ui.settings.PillOption
import com.muradgalayev.brainbuddy.ui.settings.components.LanguageCards
import com.muradgalayev.brainbuddy.ui.settings.components.ThemePicker
import com.muradgalayev.brainbuddy.ui.theme.fontFamilyOf
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// first screen after sign-up: how Myndora should look and read. it comes before the ADHD
// questionnaire rather than after it for two reasons. the questionnaire is the longest stretch
// of reading in the app, so it should already be in the typeface and size the person can
// actually read comfortably. and it asks for personal detail, so being handed control of the
// surface first, on something with no wrong answers, is a gentler opening.
// every control writes the real setting immediately, so this screen is its own preview: the
// page you're looking at is the app you're choosing
@Composable
fun AppearanceSetupScreen(
    onContinue: () -> Unit,
    viewModel: AppearancePreferencesViewModel = hiltViewModel(),
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val fontMode by viewModel.fontMode.collectAsState()
    val fontSize by viewModel.fontSize.collectAsState()
    val appTheme by viewModel.appTheme.collectAsState()
    val customThemeSpec by viewModel.customThemeSpec.collectAsState()

    val colors = MaterialTheme.colorScheme

    // Do not let an automatically scheduled Work/Weekend mode mask or reject the choices on this
    // screen. The user's mode selection itself is untouched and resumes after Continue.
    DisposableEffect(Unit) {
        viewModel.setAppearanceSetupActive(true)
        onDispose { viewModel.setAppearanceSetupActive(false) }
    }

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 56.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.setup_make_it_yours),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.setup_appearance_intro),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }

            // language before anything else: every card below reads in whatever is picked here
            SetupCard(
                title = stringResource(R.string.language_title),
                blurb = stringResource(R.string.language_setup_blurb),
            ) {
                LanguageCards()
            }

            // brightness first: the colour preview below has to resolve light or dark before it can show
            // anything truthful
            SetupCard(
                title = stringResource(R.string.setup_light_or_dark),
                blurb = stringResource(R.string.setup_light_or_dark_blurb),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PillOption(
                        label = stringResource(R.string.common_light),
                        icon = Icons.Outlined.LightMode,
                        selected = themeMode == ThemeMode.Light,
                        onClick = { viewModel.setThemeMode(ThemeMode.Light) },
                        modifier = Modifier.weight(1f),
                    )
                    PillOption(
                        label = stringResource(R.string.common_dark),
                        icon = Icons.Outlined.DarkMode,
                        selected = themeMode == ThemeMode.Dark,
                        onClick = { viewModel.setThemeMode(ThemeMode.Dark) },
                        modifier = Modifier.weight(1f),
                    )
                    PillOption(
                        label = stringResource(R.string.common_auto),
                        icon = Icons.Outlined.Brightness4,
                        selected = themeMode == ThemeMode.System,
                        onClick = { viewModel.setThemeMode(ThemeMode.System) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // the same picker Settings uses, custom palette builder and all. someone who wants their own
            // two colours on day one shouldn't have to finish onboarding and go hunting for the setting
            ThemePicker(
                selected = appTheme,
                customSpec = customThemeSpec,
                themeMode = themeMode,
                onSelectBuiltIn = { viewModel.setAppTheme(it) },
                onCustomChange = { viewModel.setCustomTheme(it) },
            )

            SetupCard(
                title = stringResource(R.string.setup_typeface),
                blurb = stringResource(R.string.setup_typeface_blurb),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    FontOption(
                        mode = FontMode.Arial,
                        name = "Arial",
                        blurb = stringResource(R.string.setup_font_arial_blurb),
                        selected = fontMode == FontMode.Arial,
                        onClick = { viewModel.setFontMode(FontMode.Arial) },
                    )
                    FontOption(
                        mode = FontMode.Atkinson,
                        name = "Atkinson Hyperlegible",
                        blurb = stringResource(R.string.setup_font_atkinson_blurb),
                        selected = fontMode == FontMode.Atkinson,
                        onClick = { viewModel.setFontMode(FontMode.Atkinson) },
                    )
                    FontOption(
                        mode = FontMode.OpenDyslexic,
                        name = "OpenDyslexic",
                        blurb = stringResource(R.string.setup_font_dyslexic_blurb),
                        selected = fontMode == FontMode.OpenDyslexic,
                        onClick = { viewModel.setFontMode(FontMode.OpenDyslexic) },
                    )
                }
            }

            SetupCard(
                title = stringResource(R.string.setup_text_size),
                blurb = stringResource(R.string.setup_text_size_blurb),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    PillOption(
                        label = stringResource(R.string.common_small),
                        selected = fontSize == FontSize.Small,
                        onClick = { viewModel.setFontSize(FontSize.Small) },
                        modifier = Modifier.weight(1f),
                    )
                    PillOption(
                        label = stringResource(R.string.common_medium),
                        selected = fontSize == FontSize.Medium,
                        onClick = { viewModel.setFontSize(FontSize.Medium) },
                        modifier = Modifier.weight(1f),
                    )
                    PillOption(
                        label = stringResource(R.string.common_large),
                        selected = fontSize == FontSize.Large,
                        onClick = { viewModel.setFontSize(FontSize.Large) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text(
                    text = stringResource(R.string.common_continue),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// section shell: a title, a line of why, and whatever controls belong to it
@Composable
private fun SetupCard(
    title: String,
    blurb: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            content()
        }
    }
}

// one typeface, rendered in itself. sizes here are fixed rather than taken from the type
// scale: this row is a specimen of the font, and letting the size setting move it too would
// confuse two separate choices
@Composable
private fun FontOption(
    mode: FontMode,
    name: String,
    blurb: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val family = fontFamilyOf(mode)

    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.6f),
        animationSpec = tween(220),
        label = "fontOptionBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = tween(220),
        label = "fontOptionBorderWidth",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = if (selected) colors.primaryContainer.copy(alpha = 0.35f)
        else colors.surfaceContainerHigh,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Aa",
                    fontFamily = family,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
            }

            Spacer(Modifier.width(13.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    fontFamily = family,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = blurb,
                    fontFamily = family,
                    fontSize = 12.sp,
                    color = colors.onSurfaceVariant,
                )
            }

            Spacer(Modifier.width(10.dp))

            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(if (selected) colors.primary else colors.surfaceContainerHighest)
                    .border(
                        width = if (selected) 0.dp else 1.5.dp,
                        color = colors.onSurfaceVariant.copy(alpha = 0.35f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.common_selected),
                        tint = colors.onPrimary,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}
