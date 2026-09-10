package com.muradgalayev.brainbuddy.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents

// the shared look of the home tiles. three things do the work, and they're worth naming because
// they're what separate a screen of coloured rectangles from a screen that looks made on purpose:
// - a hairline, not a shadow. a 1dp outline reads crisply at any brightness, where a drop shadow
//   turns to grey mud in dark mode, and it's what the rest of the app already does.
// - a tint, not a fill. each tile carries a barely-there wash of its own accent, falling off
//   towards the bottom as if lit from above. flat surfaceContainer everywhere makes a layout
//   feel like a spreadsheet.
// - nested radii. inner elements curve less than the tile holding them; matching radii at
//   different sizes is the detail that makes corners look wrong without anyone saying why
val HomeCardShape = RoundedCornerShape(28.dp)
val HomeInnerShape = RoundedCornerShape(18.dp)
val HomePillShape = RoundedCornerShape(999.dp)

@Composable
fun homeCardBorder(): Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)

@Composable
private fun isDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < .5f

// lit from the top in the tile's own accent. deliberately almost invisible
@Composable
fun homeCardWash(accent: Color): Brush = Brush.verticalGradient(
    listOf(accent.copy(alpha = if (isDarkTheme()) .10f else .055f), Color.Transparent)
)

// a home tile. handles the surface, the hairline, the wash and, when it's tappable, the
// press-in, so no card has to remember to do those four things the same way as its neighbours
@Composable
fun HomeCard(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.myndoraAccents.accent,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .985f else 1f,
        animationSpec = spring(dampingRatio = .6f),
        label = "home_card_press",
    )

    Surface(
        modifier = modifier
            .then(if (onClick != null) Modifier.scale(scale) else Modifier)
            .border(1.dp, homeCardBorder(), HomeCardShape),
        shape = HomeCardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(homeCardWash(accent))
                .then(
                    if (onClick != null) {
                        Modifier.clickable(interaction, indication = null, onClick = onClick)
                    } else {
                        Modifier
                    }
                ),
            content = content,
        )
    }
}

// small, tracked, upper-case. the quiet half of a heading pair
@Composable
fun HomeSectionLabel(text: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = color,
        letterSpacing = 1.3.sp,
        maxLines = 1,
    )
}

// fixed-width digits. a countdown re-rendering every few seconds shifts sideways as 1s and 4s
// swap places, and the eye reads that twitch as the layout being unstable. tabular figures pin it
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
