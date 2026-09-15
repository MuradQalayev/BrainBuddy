package com.muradgalayev.brainbuddy.ui.calendar

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.components.AiSparkleIcon
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// header: back button, calendar logo, title, mode toggle, AI
@Composable
fun CalendarHeader(
    mode: CalendarMode,
    onModeChange: (CalendarMode) -> Unit,
    onBackClick: () -> Unit = {},
    onAiClick: () -> Unit = {},
    aiEnabled: Boolean = true,
    // Myndora Plus not active: still tappable (it opens the plan), marked with a lock
    aiLocked: Boolean = false,
    // reports the AI button's bounds in root coordinates, so the intro animation can fly its
    // round shape straight into the logo
    onAiButtonPositioned: (Rect) -> Unit = {},
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBackClick, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.together_scope_calendar),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        ModeToggleButton(currentMode = mode, onModeChange = onModeChange)
        Spacer(modifier = Modifier.width(8.dp))
        AiPillButton(
            enabled = aiEnabled,
            locked = aiLocked,
            onClick = onAiClick,
            onPositioned = onAiButtonPositioned,
        )
    }
}

// compact AI pill. sits next to the mode toggle as a peer control rather than the 44dp badge
// it used to be, same height as the toggle, so the header reads as one row of controls instead
// of a title with a bubble stuck on the end.
// the sparkle breathes slowly: it's the only thing marking this as the assistant rather than
// another filter. under reduce motion it holds still, since the icon and the border already
// carry that meaning and a permanent pulse is exactly the idle movement the setting is for.
// the connection deliberately doesn't show here at all. the pill used to mute its colours and
// relabel itself Offline, which on a flaky network made it look like it was breaking and
// recovering every few seconds, and was never true anyway since the assistant still works
// offline. losing the connection now changes one thing: a switch appears inside the assistant,
// once the user has opened it and is in a position to care. see OfflineSwitchBar
@Composable
private fun AiPillButton(
    enabled: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
    onPositioned: (Rect) -> Unit,
) {
    // reduce motion stops this outright rather than shortening it. the usual 'if (animationsOn())
    // 320 else 0' trick works because a zero-duration animation still lands on its target, but
    // this one repeats forever and reverses, so at zero duration it would arrive and turn around
    // every frame, which is a flicker rather than stillness. the transition is only created while
    // it is allowed to run; when it isn't, there is nothing composed to animate
    // a locked pill doesn't breathe: the pulse says 'the assistant is here', which it isn't yet
    val pulses = enabled && !locked && animationsOn()
    val sparkleScale: State<Float>? = if (pulses) {
        val motion = rememberInfiniteTransition(label = "calendar_ai_presence")
        motion.animateFloat(
            initialValue = 1f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(1500),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "calendar_ai_sparkle_scale",
        )
    } else {
        null
    }
    val accent = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        // survey not done, so the feature genuinely isn't available yet. this is the only state that
        // dims the pill, because it's the only one where tapping has nothing to offer
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .32f)
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .height(CONTROL_HEIGHT)
            // bounds feed the intro animation that flies this shape into the logo
            .onGloballyPositioned { onPositioned(it.boundsInRoot()) },
        shape = RoundedCornerShape(percent = 50),
        color = Color.Transparent,
        border = BorderStroke(1.dp, accent.copy(alpha = if (enabled) .35f else .18f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primaryContainer
                                .copy(alpha = if (enabled) .70f else .20f),
                            MaterialTheme.colorScheme.secondaryContainer
                                .copy(alpha = if (enabled) .45f else .12f),
                        ),
                    ),
                )
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            AiSparkleIcon(
                color = accent,
                modifier = Modifier
                    .size(15.dp)
                    // graphicsLayer, not Modifier.scale: reading the infinite transition inside this lambda keeps
                    // it in the draw phase, so the pulse invalidates a layer instead of recomposing the whole
                    // header sixty times a second for as long as the calendar is open
                    .graphicsLayer {
                        val s = sparkleScale?.value ?: 1f
                        scaleX = s
                        scaleY = s
                    },
            )
            Text(
                text = "AI",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = accent,
            )
            if (locked && enabled) {
                Icon(
                    androidx.compose.material.icons.Icons.Rounded.Lock,
                    contentDescription = stringResource(com.muradgalayev.brainbuddy.R.string.plan_locked_cd),
                    tint = accent,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

// shared height so the mode toggle and the AI pill line up as one row of controls
private val CONTROL_HEIGHT = 34.dp

// a single button showing the mode you are currently in, and tapping swaps it. replaces the
// old two-segment M/W pill. showing the current state rather than the destination is the
// honest reading: the label always describes what's on screen, so nothing has to be inferred
// from which half looks highlighted. the label cross-fades and the icon rotates so the tap
// clearly did something, which a static swap wouldn't convey
@Composable
fun ModeToggleButton(
    currentMode: CalendarMode,
    onModeChange: (CalendarMode) -> Unit,
) {
    val isMonthly = currentMode == CalendarMode.Monthly
    val iconRotation by animateFloatAsState(
        targetValue = if (isMonthly) 0f else 180f,
        animationSpec = tween(280),
        label = "modeIconRotation",
    )

    Surface(
        onClick = {
            onModeChange(if (isMonthly) CalendarMode.Weekly else CalendarMode.Monthly)
        },
        modifier = Modifier.height(CONTROL_HEIGHT),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 12.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AnimatedContent(
                targetState = isMonthly,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                label = "modeLabel",
            ) { monthly ->
                Text(
                    text = if (monthly) stringResource(R.string.cal_month) else stringResource(R.string.cal_week),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Icon(
                imageVector = Icons.Rounded.UnfoldMore,
                contentDescription = if (isMonthly) stringResource(R.string.cal_switch_week) else stringResource(R.string.cal_switch_month),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(16.dp)
                    .rotate(iconRotation),
            )
        }
    }
}

// month navigator
@Composable
fun MonthNavigator(
    palette: CalendarPalette,
    currentMonth: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // large month and year title
        Text(
            text = "${currentMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${currentMonth.year}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = palette.ink,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Rounded.ChevronLeft,
                contentDescription = stringResource(R.string.common_previous),
                tint = palette.muted
            )
        }
        IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = stringResource(R.string.common_next),
                tint = palette.muted
            )
        }
    }
}

// day of week header
@Composable
fun DayOfWeekHeader(firstDayOfWeek: DayOfWeek) {
    val daysOfWeek = remember(firstDayOfWeek) {
        val days = DayOfWeek.entries.toMutableList()
        val index = days.indexOf(firstDayOfWeek)
        days.subList(index, days.size) + days.subList(0, index)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        daysOfWeek.forEach { day ->
            Text(
                text = day.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    .uppercase()
                    .take(2),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                letterSpacing = 0.5.sp
            )
        }
    }
}
