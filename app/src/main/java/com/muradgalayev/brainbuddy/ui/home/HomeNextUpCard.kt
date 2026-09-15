package com.muradgalayev.brainbuddy.ui.home

import com.muradgalayev.brainbuddy.ui.utils.resolve
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.ui.calendar.resolveEventColor
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// the one thing that's next, and the button that starts it. deciding what to do is its own
// task, and the one that most often doesn't happen, so this card refuses to be a list: whatever
// is running or nearest gets the whole card, with starting it reduced to a single tap.
// everything else stays on the Calendar and Todo tabs, where it can be dealt with deliberately
@Composable
fun HomeNextUpCard(
    item: HomeAgendaItem?,
    now: LocalDateTime,
    onStart: (HomeAgendaItem) -> Unit,
    onDone: (HomeAgendaItem) -> Unit,
    onSnooze: (HomeAgendaItem) -> Unit,
    modifier: Modifier = Modifier,
    // while the motion tip is asking, the empty card reports where it is and where its breathing
    // bolt is, so the tip can hang off it and ring the bolt. off by default, because these fire
    // on every frame the page scrolls
    reportMotionAnchor: Boolean = false,
    onMotionCard: (Rect) -> Unit = {},
    onMotionSpot: (Rect) -> Unit = {},
) {
    val accents = MaterialTheme.myndoraAccents
    val haptics = LocalHapticFeedback.current

    if (item == null) {
        HomeCard(
            modifier = if (reportMotionAnchor) {
                modifier.onGloballyPositioned { onMotionCard(it.boundsInRoot()) }
            } else {
                modifier
            },
            accent = accents.accent,
        ) {
            EmptyNextUp(onBoltBounds = if (reportMotionAnchor) onMotionSpot else null)
        }
        return
    }

    val accent = if (item.kind == AgendaKind.Task) accents.accent else resolveEventColor(item.colorKey).accent
    val running = item.start != null && item.end != null &&
        !item.start.isAfter(now) && item.end.isAfter(now)
    val clock = remember(item.start) {
        item.start?.format(DateTimeFormatter.ofPattern("HH:mm"))
    }

    HomeCard(modifier = modifier, accent = accent) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TintedIcon(
                    icon = if (item.kind == AgendaKind.Task) Icons.Rounded.TaskAlt else Icons.Rounded.CalendarMonth,
                    accent = accent,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    if (running) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LiveDot(accent)
                            Spacer(Modifier.width(7.dp))
                            HomeSectionLabel(stringResource(R.string.home_happening_now), accent)
                        }
                    } else {
                        HomeSectionLabel(stringResource(R.string.home_next_up_caps), accent)
                    }
                    if (clock != null) {
                        Text(
                            clock,
                            style = MaterialTheme.typography.labelMedium.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                CountdownPill(item = item, now = now, accent = accent)
            }

            Spacer(Modifier.height(12.dp))
            Text(
                item.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 27.sp,
            )

            if (running) {
                Spacer(Modifier.height(12.dp))
                ElapsedBar(item = item, now = now, accent = accent)
            }

            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StartButton(
                    accent = accent,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStart(item)
                    },
                )
                GhostAction(Icons.Rounded.Check, stringResource(R.string.home_mark_done)) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDone(item)
                }
                if (item.kind == AgendaKind.Task) {
                    GhostAction(Icons.Rounded.Snooze, stringResource(R.string.home_snooze_30)) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onSnooze(item)
                    }
                }
            }
        }
    }
}

// only the countdown swaps, so the title underneath never flickers as the minutes tick
@Composable
private fun CountdownPill(item: HomeAgendaItem, now: LocalDateTime, accent: Color) {
    Box(
        Modifier
            .clip(HomePillShape)
            .background(accent.copy(alpha = .13f))
            .border(1.dp, accent.copy(alpha = .22f), HomePillShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        AnimatedContent(
            targetState = countdownLabel(item, now).resolve(),
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "next_up_countdown",
        ) { label ->
            Text(
                label,
                style = MaterialTheme.typography.labelLarge.tabular(),
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TintedIcon(icon: ImageVector, accent: Color) {
    Box(
        Modifier
            .size(40.dp)
            .clip(HomeInnerShape)
            .background(accent.copy(alpha = .14f))
            .border(1.dp, accent.copy(alpha = .18f), HomeInnerShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = accent, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun EmptyNextUp(onBoltBounds: ((Rect) -> Unit)? = null) {
    val accents = MaterialTheme.myndoraAccents
    Row(
        Modifier.fillMaxWidth().padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // a free moment still looks alive: the bolt breathes and a ring drifts out from it
        Box(
            contentAlignment = Alignment.Center,
            modifier = if (onBoltBounds != null) {
                Modifier.onGloballyPositioned { onBoltBounds(it.boundsInRoot()) }
            } else {
                Modifier
            },
        ) {
            PulseRing(accents.accent, HomeInnerShape, Modifier.matchParentSize(), periodMs = 2600)
            Box(Modifier.breathing()) {
                TintedIcon(Icons.Rounded.Bolt, accents.accent)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                stringResource(R.string.home_nothing_next),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                stringResource(R.string.home_nothing_next_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// the 'on air' dot beside Happening now
@Composable
private fun LiveDot(accent: Color) {
    Box(Modifier.size(8.dp), contentAlignment = Alignment.Center) {
        PulseRing(accent, CircleShape, Modifier.matchParentSize(), grow = 1.4f, periodMs = 1400)
        Box(Modifier.size(8.dp).clip(CircleShape).background(accent))
    }
}

// how much of the thing you're inside has gone, the bar empties as the time does
@Composable
private fun ElapsedBar(item: HomeAgendaItem, now: LocalDateTime, accent: Color) {
    val start = item.start ?: return
    val end = item.end ?: return
    val total = Duration.between(start, end).toMillis().coerceAtLeast(1L)
    val gone = Duration.between(start, now).toMillis().coerceIn(0L, total)
    val progress by animateFloatAsState(
        targetValue = 1f - gone.toFloat() / total.toFloat(),
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "next_up_elapsed",
    )
    Box(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(HomePillShape)
            .background(accent.copy(alpha = .14f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(8.dp)
                .clip(HomePillShape)
                .background(Brush.horizontalGradient(listOf(accent.copy(alpha = .75f), accent))),
        )
    }
}

@Composable
private fun StartButton(accent: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .96f else 1f,
        animationSpec = spring(dampingRatio = .55f),
        label = "start_press",
    )
    Row(
        modifier
            .scale(scale)
            .height(52.dp)
            .clip(HomeInnerShape)
            // lighter at the top edge, the same top-lit logic as the cards, which is what keeps a flat
            // fill from looking like a coloured rectangle
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = .92f), accent)))
            .clickable(interaction, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(21.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            stringResource(R.string.home_start_focus),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            letterSpacing = .2.sp,
        )
    }
}

// outlined rather than filled, the secondary actions shouldn't compete with Start
@Composable
private fun GhostAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .93f else 1f,
        animationSpec = spring(dampingRatio = .55f),
        label = "ghost_press",
    )
    Box(
        Modifier
            .scale(scale)
            .size(52.dp)
            .clip(HomeInnerShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = .6f))
            .border(1.dp, homeCardBorder(), HomeInnerShape)
            .clickable(interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
    }
}
