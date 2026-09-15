package com.muradgalayev.brainbuddy.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.domain.model.AppMode
import com.muradgalayev.brainbuddy.domain.model.ModeStatus
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.accessibility.speaking
import com.muradgalayev.brainbuddy.ui.modes.modeAccentColor
import com.muradgalayev.brainbuddy.ui.modes.modeIcon
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// the mode tile: what's running, and every other option stacked under it. the list is always
// open rather than behind a chevron, hiding it cost two taps to switch. names only, each with a
// small settings button straight into that mode's editor: the line saying what a mode changed
// was more text than anyone reads on a home screen.
// the tile therefore grows by a row per mode. that's the accepted cost of this shape: it's a
// tile the user can turn off or resize, and someone with eight modes has said by building them
// that switching matters to them.
// the modes come from the user's own list rather than a fixed enum. 'no mode' is offered as an
// explicit choice, because without it there's no way to stop applying one short of finding a
// mode that overrides nothing
@Composable
fun HomeModeTile(
    modes: List<AppMode>,
    activeMode: AppMode?,
    status: ModeStatus?,
    showIntro: Boolean,
    onDismissIntro: () -> Unit,
    onSelect: (String?) -> Unit,
    onManage: () -> Unit,
    onEdit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val animate = animationsOn()
    val colorDuration = if (animate) 420 else 0
    val controlDuration = if (animate) 240 else 0
    val accent by animateColorAsState(
        targetValue = modeAccentColor(activeMode?.accent),
        animationSpec = tween(colorDuration),
        label = "mode_accent",
    )

    val title = activeMode?.name ?: stringResource(R.string.home_no_mode)
    val modeDescription = stringResource(R.string.home_mode_cd, title)

    HomeCard(modifier = modifier, accent = accent) {
        Column(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 11.dp)) {
                // the label is what tells a new user 'Work' is a mode and not a heading
                HomeSectionLabel(stringResource(R.string.home_mode_caps), accent)
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = modeDescription },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusLed(color = accent)
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f)) { RollingTitle(title = title, accent = accent) }
                    if (activeMode != null) {
                        ModeSettingsButton(name = activeMode.name, onClick = { onEdit(activeMode.id) })
                    }
                }
                // why it's on and when it stops, the two things people wonder about a mode they didn't
                // expect. one short line, not the old 'what it changes' summary
                status?.let {
                    Text(
                        text = it.label(),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 24.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = showIntro,
                enter = fadeIn(tween(controlDuration)) + expandVertically(tween(controlDuration)),
                exit = fadeOut(tween(controlDuration)) + shrinkVertically(tween(controlDuration)),
            ) {
                ModesIntro(
                    accent = accent,
                    onSetUp = {
                        onDismissIntro()
                        onManage()
                    },
                    onDismiss = onDismissIntro,
                )
            }

            Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                // only the modes you aren't in, the one you are in is the line above
                modes.filter { it.id != activeMode?.id }.forEach { option ->
                    ModeOptionRow(
                        icon = { tint ->
                            Icon(modeIcon(option.icon), null, tint = tint, modifier = Modifier.size(15.dp))
                        },
                        title = option.name,
                        onClick = { onSelect(option.id) },
                        onSettings = { onEdit(option.id) },
                    )
                }
                if (activeMode != null) {
                    ModeOptionRow(
                        icon = { tint ->
                            Icon(Icons.Rounded.Tune, null, tint = tint, modifier = Modifier.size(15.dp))
                        },
                        title = stringResource(R.string.home_no_mode),
                        onClick = { onSelect(null) },
                    )
                }
                ModeOptionRow(
                    icon = { tint ->
                        Icon(Icons.Rounded.Tune, null, tint = tint, modifier = Modifier.size(15.dp))
                    },
                    title = if (modes.isEmpty()) stringResource(R.string.home_create_mode) else stringResource(R.string.home_manage_modes),
                    onClick = onManage,
                )
            }
        }
    }
}

// one of the modes you could switch to. the row switches, the gear edits
@Composable
private fun ModeOptionRow(
    icon: @Composable (Color) -> Unit,
    title: String,
    onClick: () -> Unit,
    onSettings: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HomeInnerShape)
            .clickable(onClick = speaking(title, onClick))
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(colors.onSurfaceVariant.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            icon(colors.onSurfaceVariant)
        }
        Spacer(Modifier.width(11.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onSettings != null) {
            ModeSettingsButton(name = title, onClick = onSettings)
        }
    }
}

@Composable
private fun ModeStatus.label(): String = when (this) {
    ModeStatus.Manual -> stringResource(R.string.home_mode_manual)
    ModeStatus.AllDay -> stringResource(R.string.home_mode_all_day)
    is ModeStatus.Until -> stringResource(
        R.string.home_mode_until,
        time.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")),
    )
}

// shown once, until dismissed or a mode is picked. a new account has modes but nothing running,
// and without a sentence of explanation the tile is a list of names with no reason to tap one
@Composable
private fun ModesIntro(accent: Color, onSetUp: () -> Unit, onDismiss: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
            .fillMaxWidth()
            .clip(HomeInnerShape)
            .background(accent.copy(alpha = 0.10f))
            .padding(12.dp),
    ) {
        Text(
            text = stringResource(R.string.home_modes_intro, stringResource(R.string.mode_work)),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntroPill(stringResource(R.string.home_modes_set_up), accent, filled = true, onClick = onSetUp)
            IntroPill(stringResource(R.string.common_got_it), accent, filled = false, onClick = onDismiss)
        }
    }
}

@Composable
private fun IntroPill(label: String, accent: Color, filled: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (filled) Color.White else accent,
        modifier = Modifier
            .clip(HomePillShape)
            .background(if (filled) accent else accent.copy(alpha = 0.14f))
            .clickable(onClick = speaking(label, onClick))
            .padding(horizontal = 14.dp, vertical = 7.dp),
    )
}

@Composable
private fun ModeSettingsButton(name: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val label = stringResource(R.string.mode_settings_cd, name)
    Box(
        Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = speaking(label, onClick))
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.Settings, null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
    }
}

// the mode's name, rolling. the row is a fixed height and clipped so the two names pass each
// other inside it, without which the strip would grow and shrink by a line height on every
// switch, exactly the kind of layout twitch this app spends its effort avoiding
@Composable
private fun RollingTitle(title: String, accent: Color) {
    // read outside the transition lambda: that lambda isn't composable, so the setting has to be
    // resolved here and captured
    val animate = animationsOn()
    Box(Modifier.height(26.dp).clip(HomePillShape), contentAlignment = Alignment.CenterStart) {
        AnimatedContent(
            targetState = title,
            transitionSpec = {
                if (!animate) {
                    fadeIn(tween(0)).togetherWith(fadeOut(tween(0)))
                } else {
                    val slide = tween<IntOffset>(durationMillis = 260, easing = FastOutSlowInEasing)
                    (slideInVertically(slide) { height -> height } + fadeIn(tween(220)))
                        .togetherWith(
                            slideOutVertically(slide) { height -> -height } + fadeOut(tween(160))
                        )
                }
            },
            label = "mode_name",
        ) { current ->
            Text(
                text = current,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// slow, shallow pulse, a status light idling rather than an alert. held still under reduce
// motion, where a permanent pulse is precisely what the setting exists to stop
@Composable
private fun StatusLed(color: Color) {
    val glow: State<Float>? = if (animationsOn()) {
        val transition = rememberInfiniteTransition(label = "mode_led")
        transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2400, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "mode_led_glow",
        )
    } else {
        null
    }

    Box(Modifier.size(14.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.18f * (glow?.value ?: 1f)))
        )
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = glow?.value ?: 1f))
        )
    }
}
