package com.muradgalayev.brainbuddy.ui.activity

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.R
import java.time.LocalTime

// ambient background

// a soft wash behind the Workspace that shifts with the time of day, generated from the
// theme's own accents rather than an image. photography lives on the hero cards, where a scrim
// can guarantee contrast over a known area; the page background sits behind all the small text
// on the screen, so it stays a gradient. it also tracks the clock, which quietly tells a
// time-blind user roughly when they are. null under Simplify mode, which exists to cut
// stimulation and so wins
@Composable
fun rememberTimeOfDayWash(simplified: Boolean): Brush? {
    if (simplified) return null

    val scheme = MaterialTheme.colorScheme
    val dark = isSystemInDarkTheme()
    val hour = remember { LocalTime.now().hour }

    // which accent leads, and how strongly it tints the background
    val (tint, strength) = when (hour) {
        in 5..8 -> scheme.primary to 0.16f          // dawn, warm orange
        in 9..11 -> scheme.tertiary to 0.11f        // morning, sage, alert but calm
        in 12..16 -> scheme.secondary to 0.09f      // afternoon, cool blue, lowest tint
        in 17..20 -> scheme.primary to 0.14f        // evening, warm again, winding down
        else -> scheme.secondary to 0.12f           // night, cool and dim
    }

    // dark mode needs a lighter hand: the same alpha over a near-black surface reads as a muddy
    // colour cast rather than a wash
    val alpha = if (dark) strength * 0.65f else strength

    return Brush.verticalGradient(
        colors = listOf(
            lerp(scheme.background, tint, alpha),
            lerp(scheme.background, tint, alpha * 0.35f),
            scheme.background,
        ),
    )
}

// 'right now' hero

enum class HeroPage { Now, Today, Week }

// the one card that answers 'what do I do right now?'. replaces a vertical stack of
// equal-weight destination cards as the screen's opening move: a menu asks the user to choose,
// this states the answer and offers a single action, which is the difference between starting
// and stalling when executive function is the bottleneck.
// swipeable across Now / Today / This week. the hero is the pager, not the action cards below
// it, since hiding primary actions off-screen horizontally makes them easy to forget
@Composable
fun RightNowHero(
    state: WorkspaceUiState,
    onStartFocus: () -> Unit,
    onOpenTodos: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = HeroPage.entries
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scheme = MaterialTheme.colorScheme

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
        ) { index ->
            when (pages[index]) {
                HeroPage.Now -> NowCard(
                    state = state,
                    onStartFocus = onStartFocus,
                    onOpenTodos = onOpenTodos,
                )
                HeroPage.Today -> SummaryCard(
                    photo = R.drawable.wellness_movement_suggestion,
                    simplified = state.isSimplified,
                    eyebrow = "TODAY",
                    headline = todayHeadline(state),
                    stats = listOf(
                        "${state.todayCompletedTasks}/${state.todayTotalTasks}" to "tasks done",
                        "${state.todayFocusMinutes}m" to "focused",
                    ),
                    progress = state.todoProgress,
                )
                HeroPage.Week -> SummaryCard(
                    photo = R.drawable.wellness_walk_suggestion,
                    simplified = state.isSimplified,
                    eyebrow = "LAST 7 DAYS",
                    headline = weekHeadline(state),
                    stats = listOf(
                        "${state.weekCompletedTasks}" to "tasks done",
                        "${state.weekFocusMinutes}m" to "focused",
                    ),
                    progress = null,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            pages.forEachIndexed { index, _ ->
                val selected = pagerState.currentPage == index
                val width by animateFloatAsState(
                    targetValue = if (selected) 18f else 6f,
                    animationSpec = tween(220),
                    label = "hero_dot_$index",
                )
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .height(6.dp)
                        .width(width.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) scheme.primary
                            else scheme.onSurfaceVariant.copy(alpha = 0.25f)
                        ),
                )
            }
        }
    }
}

private fun todayHeadline(state: WorkspaceUiState): String = when {
    state.todayTotalTasks == 0 -> "Nothing on the list yet"
    state.todayCompletedTasks == state.todayTotalTasks -> "All clear. Nice."
    state.todayCompletedTasks == 0 -> "Fresh page"
    else -> "You're moving"
}

private fun weekHeadline(state: WorkspaceUiState): String = when {
    state.weekCompletedTasks == 0 && state.weekFocusMinutes == 0 -> "A quiet week so far"
    state.weekCompletedTasks == 0 -> "Focus time is adding up"
    else -> "Steady week"
}

// page one, the actual answer. one thing, one button. three states in priority order:
// something running now, something coming up, or nothing at all. the empty state still offers
// an action, because 'nothing scheduled' plus no next step is where an ADHD user drifts
@Composable
private fun NowCard(
    state: WorkspaceUiState,
    onStartFocus: () -> Unit,
    onOpenTodos: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val hasItem = state.nextUpTitle != null

    HeroSurface(
        photo = R.drawable.workspace_focus_hero,
        simplified = state.isSimplified,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (state.nextUpIsNow) "RIGHT NOW" else "UP NEXT",
                    color = scheme.primary,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
                // says which list this came from. the hero draws on the calendar and the to-do list together,
                // and without this a to-do reads as a meeting
                if (hasItem) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (state.nextUpIsEvent) "· Calendar" else "· To-do",
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            if (hasItem) {
                Text(
                    text = state.nextUpTitle.orEmpty(),
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                state.nextUpWhen?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = it,
                        color = scheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                Text(
                    text = "Nothing scheduled",
                    color = scheme.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "A short focus block is an easy way to start.",
                    color = scheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroButton(
                    label = if (state.pomodoroIsActive) "Back to focus" else "Start focus",
                    icon = Icons.Rounded.PlayArrow,
                    filled = true,
                    onClick = onStartFocus,
                )
                if (hasItem) {
                    HeroButton(
                        label = "See list",
                        icon = Icons.Rounded.CheckCircle,
                        filled = false,
                        onClick = onOpenTodos,
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    @DrawableRes photo: Int?,
    simplified: Boolean,
    eyebrow: String,
    headline: String,
    stats: List<Pair<String, String>>,
    progress: Float?,
) {
    val scheme = MaterialTheme.colorScheme
    HeroSurface(photo = photo, simplified = simplified) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = eyebrow,
                color = scheme.primary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = headline,
                color = scheme.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                stats.forEach { (value, label) ->
                    Column {
                        Text(
                            text = value,
                            color = scheme.onSurface,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = label,
                            color = scheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
            if (progress != null) {
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(scheme.primary.copy(alpha = 0.15f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(scheme.primary),
                    )
                }
            }
        }
    }
}

// shared shell so all three pages sit at identical size and elevation.
// the photo is deliberately buried under a heavy scrim: a photograph can't guarantee a
// contrast ratio on its own, and this app's readers are the least able to absorb the cost of
// low-contrast text, so the image reads as texture behind a near-solid surface rather than as
// a background you look at. the scrim is denser on the left, where every page puts its text.
// photos are dropped entirely under Simplify mode
@Composable
private fun HeroSurface(
    @DrawableRes photo: Int? = null,
    simplified: Boolean = false,
    content: @Composable () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val showPhoto = photo != null && !simplified

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(196.dp),
        shape = RoundedCornerShape(26.dp),
        color = scheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (showPhoto) {
                Image(
                    painter = painterResource(photo),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0.0f to scheme.surface.copy(alpha = 0.94f),
                                0.55f to scheme.surface.copy(alpha = 0.86f),
                                1.0f to scheme.surface.copy(alpha = 0.62f),
                            ),
                        ),
                )
            }
            content()
        }
    }
}

@Composable
private fun HeroButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    filled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val bg = if (filled) scheme.primary else Color.Transparent
    val fg = if (filled) scheme.onPrimary else scheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        border = if (filled) null
        else androidx.compose.foundation.BorderStroke(1.dp, scheme.outline.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = label,
                color = fg,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
