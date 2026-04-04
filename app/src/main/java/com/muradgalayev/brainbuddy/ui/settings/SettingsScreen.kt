package com.muradgalayev.brainbuddy.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.ui.components.ProfileHeaderCard
import com.muradgalayev.brainbuddy.ui.settings.components.QuickAccessSection
import com.muradgalayev.brainbuddy.ui.settings.components.AppearanceRow
import com.muradgalayev.brainbuddy.ui.settings.components.FocusModeSection
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val fontMode by viewModel.fontMode.collectAsState()
    val enabledNavItems by viewModel.enabledNavItems.collectAsState()
    val focusModeEnabled by viewModel.focusModeEnabled.collectAsState()
    var appearanceOpen by rememberSaveable { mutableStateOf(false) }
    var quickAccessOpen by rememberSaveable { mutableStateOf(false) }
    var pomodoroOpen by rememberSaveable { mutableStateOf(false) }
    val fontSize by viewModel.fontSize.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        if (appearanceOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.15f))
                    .zIndex(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { appearanceOpen = false }
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp)
                .zIndex(if (appearanceOpen) 2f else 0f)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(28.dp))

            //profile view

            val isLoggedIn = false
            val userName = if (isLoggedIn) "Murad Galayev" else "Welcome"
            val userSubtitle = if (isLoggedIn) "murad@example.com" else "Sign in to sync your data"

            ProfileHeaderCard(
                name = userName,
                subtitle = userSubtitle,
                isLoggedIn = isLoggedIn,
                onClick = {
                    // later: navigate to login/profile
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // --- Appearance ---
            AppearanceRow(
                themeMode = themeMode,
                fontMode = fontMode,
                fontSize = fontSize,
                expanded = appearanceOpen,
                onToggle = { appearanceOpen = !appearanceOpen },
                onThemeChange = {
                    viewModel.setThemeMode(it)
                },
                onFontChange = {
                    viewModel.setFontMode(it)
                },
                onFontSizeChange = {
                    viewModel.setFontSize(it)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            QuickAccessSection(
                enabledRoutes = enabledNavItems,
                expanded = quickAccessOpen,
                onToggleExpanded = { quickAccessOpen = !quickAccessOpen },
                onToggle = { route, enabled -> viewModel.toggleNavItem(route, enabled) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            FocusModeSection(
                enabled = focusModeEnabled,
                expanded = pomodoroOpen,
                onToggleExpanded = { pomodoroOpen = !pomodoroOpen },
                onToggle = { viewModel.toggleFocusMode(it) }
            )
        }
    }
}


@Composable
fun SectionLabel(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun PillOption(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    val bg = if (selected)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHighest

    val fg = if (selected)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = fg
            )
        }
    }
}