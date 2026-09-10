package com.muradgalayev.brainbuddy.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Diversity3
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.muradgalayev.brainbuddy.domain.model.ShareScope

// back arrow, title and scrolling body, matching the settings sub-pages
@Composable
fun TogetherScaffold(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, top = 12.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.onSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            trailing?.invoke()
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
    }
}

// gradient explainer card, same treatment as the settings detail heroes
@Composable
fun TogetherHero(
    title: String,
    description: String,
    icon: ImageVector = Icons.Rounded.Diversity3,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(
                        colors.primary.copy(alpha = .24f),
                        colors.surfaceContainer,
                        colors.primaryContainer.copy(alpha = .40f),
                    ),
                ),
                RoundedCornerShape(26.dp),
            )
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(colors.surface.copy(alpha = .72f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.primary, modifier = Modifier.size(29.dp))
        }
        Spacer(Modifier.width(15.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun TogetherSectionHeader(title: String, accent: Color = MaterialTheme.colorScheme.primary) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// avatar with initials fallback, since most Myndora users have no photo set. ring draws a
// tinted halo around the face and is how a list of people gets its rhythm: the relation is
// legible before you read a single word. null keeps the plain circle for the places that stack
// avatars, where rings would just add noise
@Composable
fun TogetherAvatar(
    name: String,
    avatarUrl: String?,
    size: Dp = 46.dp,
    ring: Color? = null,
) {
    val colors = MaterialTheme.colorScheme
    val initials = name.split(" ", ".", "_", "-")
        .mapNotNull { it.firstOrNull()?.uppercase() }
        .take(2)
        .joinToString("")
        .ifBlank { "?" }
    val tint = ring ?: colors.primary
    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (ring != null) {
                    Modifier
                        .border(1.5.dp, ring.copy(alpha = .45f), CircleShape)
                        .padding(3.dp)
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(tint.copy(alpha = .14f)),
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(avatarUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = name,
                    // crop, not the default fit: avatars are rarely square, and letting a portrait letterbox
                    // inside a circle looks like a rendering fault
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
            } else {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = tint,
                )
            }
        }
    }
}

// rounded container used for every card on these screens. accent washes the card with a trace
// of a colour and outlines it in the same hue, deliberately faint: enough to tell two cards
// apart while scrolling past, not enough to turn a list of people into a colour chart
@Composable
fun TogetherCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    accent: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(22.dp)
    val background = if (accent == null) {
        SolidColor(colors.surfaceContainer)
    } else {
        Brush.linearGradient(
            listOf(
                lerp(colors.surfaceContainer, accent, .13f),
                lerp(colors.surfaceContainer, accent, .03f),
            ),
        )
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .then(
                if (accent != null) {
                    Modifier.border(1.dp, accent.copy(alpha = .16f), shape)
                } else {
                    Modifier
                }
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        content = content,
    )
}

// one permission switch. busy shows a spinner in place of the switch while the change is in
// flight, so a slow network can't be mistaken for the toggle failing
@Composable
fun ScopeToggleRow(
    scope: ShareScope,
    granted: Boolean,
    busy: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                scope.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                scope.description,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(12.dp))
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        } else {
            Switch(checked = granted, onCheckedChange = onChange)
        }
    }
}
