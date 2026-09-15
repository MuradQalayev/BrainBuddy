package com.muradgalayev.brainbuddy.ui.plan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.domain.model.Plan
import com.muradgalayev.brainbuddy.domain.model.PlanFeature
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.accessibility.speaking
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
import kotlinx.coroutines.delay

// Myndora Plus, in beta. what it costs (not decided, so N/D rather than an invented number),
// what it unlocks, and what's new in this beta. opened from Settings or from any locked
// feature, which arrives as `highlight` and is marked in the list, so the answer to 'why did
// that button bring me here' is on screen without reading everything
@Composable
fun PlanScreen(
    highlight: PlanFeature?,
    onBack: () -> Unit,
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val plan by viewModel.plan.collectAsState()
    val joining by viewModel.joining.collectAsState()
    val joinFailed by viewModel.joinFailed.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState()
    val active = plan == Plan.Plus
    val animate = animationsOn()
    val haptics = LocalHapticFeedback.current

    // the burst starts from the button you actually pressed, so both are tracked in root space and
    // the root's own corner is subtracted to get back to this Box's coordinates
    var rootCorner by remember { mutableStateOf(Offset.Zero) }
    var joinCentre by remember { mutableStateOf<Offset?>(null) }
    var burst by remember { mutableStateOf<Offset?>(null) }
    var wasActive by remember { mutableStateOf(active) }

    // only the flip is worth celebrating. arriving on a screen that was already Plus is not a
    // moment, and under reduce motion there is no burst at all
    LaunchedEffect(active) {
        if (active && !wasActive) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            if (animate) burst = joinCentre?.minus(rootCorner)
        }
        wasActive = active
    }

    // a sheet over the app rather than another settings page: the hero runs edge to edge and the
    // only way out is the X, which stays pinned while the rest scrolls under it
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { rootCorner = it.boundsInRoot().topLeft },
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            PlanHero(active = active)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 20.dp, bottom = 36.dp)
                    .navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PlanSectionLabel(stringResource(R.string.plan_included_caps))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlanFeature.entries.forEachIndexed { index, feature ->
                        StaggeredIn(index = index) {
                            FeatureRow(
                                feature = feature,
                                highlighted = feature == highlight && !active,
                                unlocked = active,
                                index = index,
                            )
                        }
                    }
                }

                PlanSectionLabel(stringResource(R.string.plan_new_caps))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    NewLine(stringResource(R.string.plan_new_free))
                    NewLine(stringResource(R.string.plan_new_price))
                    NewLine(stringResource(R.string.plan_new_rest_free))
                }

                Spacer(Modifier.height(4.dp))
                JoinButton(
                    active = active,
                    joining = joining,
                    enabled = isOnline,
                    onClick = viewModel::joinBeta,
                    modifier = Modifier.onGloballyPositioned { joinCentre = it.boundsInRoot().center },
                )
                AnimatedVisibility(visible = !active && (joinFailed || !isOnline), enter = fadeIn(), exit = fadeOut()) {
                    Text(
                        text = if (!isOnline) stringResource(R.string.plan_join_offline) else stringResource(R.string.plan_join_failed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = stringResource(R.string.plan_fine_print),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // white on the gradient, and a scrim disc so it stays legible once the hero scrolls past
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = .22f)),
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = Color.White,
                modifier = Modifier.size(21.dp),
            )
        }

        PlusCelebration(origin = burst, onFinished = { burst = null })
    }
}

// each row arrives a beat after the one above it, once, as the page settles. held flat under
// reduce motion, where the list is simply there on the first frame
@Composable
private fun StaggeredIn(index: Int, content: @Composable () -> Unit) {
    val animate = animationsOn()
    var shown by remember { mutableStateOf(!animate) }
    LaunchedEffect(Unit) {
        if (animate) {
            delay(70L * index + 60L)
            shown = true
        }
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, tween(300), label = "planRowAlpha")
    val lift by animateDpAsState(
        targetValue = if (shown) 0.dp else 16.dp,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "planRowLift",
    )
    Box(
        Modifier.graphicsLayer {
            this.alpha = alpha
            translationY = lift.toPx()
        }
    ) {
        content()
    }
}

@Composable
private fun PlanHero(active: Boolean) {
    val accents = MaterialTheme.myndoraAccents
    // two slow lights drifting behind the gradient and a sheen that crosses it now and then. both
    // are idle motion, nothing to follow, so both stop dead under reduce motion
    val drift = if (animationsOn()) {
        rememberInfiniteTransition(label = "plan_hero").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(11000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "plan_hero_drift",
        )
    } else {
        null
    }
    val sheen = rememberSheenSweep(idleMs = 4200, sweepMs = 1500)

    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 34.dp, bottomEnd = 34.dp))
            .background(Brush.linearGradient(listOf(accents.accent, accents.accentEnd)))
            .drawWithContent {
                drift?.value?.let { d ->
                    val warm = Offset(size.width * (0.16f + 0.18f * d), size.height * (0.22f + 0.16f * d))
                    val warmRadius = size.height * 0.95f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                            center = warm,
                            radius = warmRadius,
                        ),
                        radius = warmRadius,
                        center = warm,
                    )
                    val cool = Offset(size.width * (0.88f - 0.2f * d), size.height * (0.78f - 0.18f * d))
                    val coolRadius = size.height * 0.75f
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.14f), Color.Transparent),
                            center = cool,
                            radius = coolRadius,
                        ),
                        radius = coolRadius,
                        center = cool,
                    )
                }
                drawContent()
                sheen?.value?.let { drawSheen(it, strength = 0.16f) }
            }
            .padding(start = 24.dp, end = 24.dp, top = 26.dp, bottom = 30.dp),
    ) {
        Column {
            // the close button floats over this row, so leave it a lane
            Row(Modifier.padding(end = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = Color.White.copy(alpha = .22f)) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.plan_beta_caps),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = Color.White,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = active,
                    enter = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) + fadeIn(),
                ) {
                    Surface(shape = CircleShape, color = Color.White) {
                        Text(
                            text = stringResource(R.string.plan_active),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = accents.accent,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.plan_name),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                // the same silver mark that sits by your name in Settings, so joining here explains
                // what appeared there
                AnimatedVisibility(
                    visible = active,
                    enter = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow)) + fadeIn(tween(400)),
                ) {
                    PlusBadge(Modifier.padding(start = 10.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.plan_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = .88f),
            )
            Spacer(Modifier.height(18.dp))
            // the price isn't decided. N/D says so plainly instead of showing a made-up number people
            // would remember and hold the final one against
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = stringResource(R.string.plan_price_nd),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.plan_price_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = .82f),
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            Text(
                text = stringResource(R.string.plan_free_in_beta),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
            )
        }
    }
}

// overshoots just past its size and settles: a tick landing, the one place on this page where a
// spring would have been right if the stagger delay allowed one
private val PopEasing = CubicBezierEasing(0.2f, 1.5f, 0.4f, 1f)

@Composable
private fun PlanSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.3.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun FeatureRow(feature: PlanFeature, highlighted: Boolean, unlocked: Boolean, index: Int) {
    val colors = MaterialTheme.colorScheme
    val accent = MaterialTheme.myndoraAccents.accent
    val border by animateColorAsState(
        targetValue = if (highlighted) accent else colors.outlineVariant.copy(alpha = .5f),
        animationSpec = tween(260),
        label = "planFeatureBorder",
    )
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (highlighted) accent.copy(alpha = .08f) else colors.surfaceContainer,
        border = BorderStroke(if (highlighted) 1.5.dp else 1.dp, border),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(feature.icon(), null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(feature.titleRes()),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                Text(
                    text = stringResource(feature.bodyRes()),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
            // staggered so unlocking reads top to bottom, like the list is being ticked off
            AnimatedVisibility(
                visible = unlocked,
                enter = scaleIn(
                    animationSpec = tween(420, delayMillis = 90 * index, easing = PopEasing),
                    initialScale = 0.4f,
                ) + fadeIn(tween(200, delayMillis = 90 * index)),
            ) {
                Row {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun NewLine(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.myndoraAccents.accent),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun JoinButton(
    active: Boolean,
    joining: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = MaterialTheme.myndoraAccents
    val label = if (active) stringResource(R.string.plan_joined) else stringResource(R.string.plan_join)
    val clickable = !active && !joining && enabled
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && animationsOn()) 0.965f else 1f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow),
        label = "planJoinPress",
    )
    // the sheen is the offer catching the light. once you're in there's nothing left to sell
    val sheen = if (clickable) rememberSheenSweep(idleMs = 2600, sweepMs = 1100) else null

    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (active) Brush.linearGradient(listOf(accents.accent.copy(alpha = .14f), accents.accent.copy(alpha = .14f)))
                else Brush.linearGradient(
                    listOf(accents.accent, accents.accentEnd).map { if (enabled) it else it.copy(alpha = .45f) },
                ),
            )
            .drawWithContent {
                drawContent()
                sheen?.value?.let { drawSheen(it, strength = 0.3f) }
            }
            .clickable(
                enabled = clickable,
                interactionSource = interaction,
                indication = LocalIndication.current,
                role = Role.Button,
                onClick = speaking(label, onClick),
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = Triple(active, joining, label),
            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
            label = "planJoin",
        ) { (isActive, isJoining, text) ->
            when {
                isJoining -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isActive) {
                        Icon(Icons.Rounded.Check, null, tint = accents.accent, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) accents.accent else Color.White,
                    )
                }
            }
        }
    }
}

private fun PlanFeature.icon(): ImageVector = when (this) {
    PlanFeature.WorkspaceAi -> Icons.Rounded.Mic
    PlanFeature.CalendarAi -> Icons.Rounded.CalendarMonth
    PlanFeature.HealthConnect -> Icons.Rounded.Favorite
    PlanFeature.CustomColors -> Icons.Rounded.Palette
    PlanFeature.FocusTogether -> Icons.Rounded.Group
    PlanFeature.AmbientSounds -> Icons.Rounded.GraphicEq
}

private fun PlanFeature.titleRes(): Int = when (this) {
    PlanFeature.WorkspaceAi -> R.string.plan_feature_workspace_ai
    PlanFeature.CalendarAi -> R.string.plan_feature_calendar_ai
    PlanFeature.HealthConnect -> R.string.plan_feature_health
    PlanFeature.CustomColors -> R.string.plan_feature_colors
    PlanFeature.FocusTogether -> R.string.plan_feature_focus_together
    PlanFeature.AmbientSounds -> R.string.plan_feature_sounds
}

private fun PlanFeature.bodyRes(): Int = when (this) {
    PlanFeature.WorkspaceAi -> R.string.plan_feature_workspace_ai_body
    PlanFeature.CalendarAi -> R.string.plan_feature_calendar_ai_body
    PlanFeature.HealthConnect -> R.string.plan_feature_health_body
    PlanFeature.CustomColors -> R.string.plan_feature_colors_body
    PlanFeature.FocusTogether -> R.string.plan_feature_focus_together_body
    PlanFeature.AmbientSounds -> R.string.plan_feature_sounds_body
}
