package com.muradgalayev.brainbuddy.ui.settings.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.data.local.AppLanguage
import com.muradgalayev.brainbuddy.data.local.AppLocale
import com.muradgalayev.brainbuddy.data.local.LanguageSwitch
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// everything below names a language in that language, never in the current one: someone who
// can't read the screen they're on still has to find their own language on it

val AppLanguage.autonym: String
    get() = when (this) {
        AppLanguage.English -> "English"
        AppLanguage.Italian -> "Italiano"
    }

// the English flag is the UK's: the app's own English is British ('colour', 'personalise')
val AppLanguage.flag: String
    get() = when (this) {
        AppLanguage.English -> "\uD83C\uDDEC\uD83C\uDDE7"
        AppLanguage.Italian -> "\uD83C\uDDEE\uD83C\uDDF9"
    }

// what the switch animation says as it lands
private val AppLanguage.greeting: String
    get() = when (this) {
        AppLanguage.English -> "Hello"
        AppLanguage.Italian -> "Ciao"
    }

@Composable
fun currentAppLanguage(): AppLanguage =
    AppLanguage.fromTag(LocalConfiguration.current.locales[0].language)

// the language a picker should show as chosen. a running switch already counts, so the thumb or
// card moves the moment it's tapped instead of after the screen has changed language
@Composable
private fun selectedLanguage(): AppLanguage {
    val switch by AppLocale.switch.collectAsState()
    return switch?.to ?: currentAppLanguage()
}

// one round globe for the sign-in screen, which keeps its own fixed light palette: the orange is
// the sign-in button's. it opens the same language sheet as Settings, and the switch animation
// runs from there. the current flag rides on its corner, so the choice already made is visible
// without opening anything
@Composable
fun LanguageGlobeButton(modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val selected = selectedLanguage()
    var open by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && animationsOn()) .92f else 1f,
        animationSpec = spring(dampingRatio = .5f, stiffness = 600f),
        label = "languageGlobePress",
    )
    val label = stringResource(R.string.language_choose_cd, selected.autonym)

    Box(
        modifier = modifier
            .size(58.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                open = true
            }
            .semantics { contentDescription = label },
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .align(Alignment.Center)
                .shadow(12.dp, CircleShape, ambientColor = Color(0x22FF7A2F), spotColor = Color(0x33FF7A2F))
                .clip(CircleShape)
                .background(Color.White.copy(alpha = .95f))
                .border(1.dp, Color(0xFFF1EAE5), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Language,
                contentDescription = null,
                tint = Color(0xFFFF7A2F),
                modifier = Modifier.size(28.dp),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.White)
                .border(1.dp, Color(0xFFF1EAE5), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(
                targetState = selected,
                transitionSpec = { scaleIn(tween(220)) + fadeIn(tween(220)) togetherWith fadeOut(tween(120)) },
                label = "languageGlobeFlag",
            ) { language ->
                Text(text = language.flag, fontSize = 13.sp)
            }
        }
    }

    if (open) LanguageSheet(onDismiss = { open = false })
}

// the two languages as big cards, for onboarding and the Settings sheet. onPick defaults to
// switching straight away; the sheet overrides it to get itself out of the way first
@Composable
fun LanguageCards(
    modifier: Modifier = Modifier,
    selected: AppLanguage = selectedLanguage(),
    onPick: ((AppLanguage) -> Unit)? = null,
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppLanguage.entries.forEach { language ->
            LanguageCard(
                language = language,
                selected = language == selected,
                onClick = {
                    if (language != selected) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        if (onPick != null) onPick(language) else AppLocale.requestSwitch(context, language)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LanguageCard(
    language: AppLanguage,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val animate = animationsOn()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && animate) .96f else 1f,
        animationSpec = spring(dampingRatio = .55f, stiffness = 600f),
        label = "languageCardScale",
    )
    val container by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        animationSpec = tween(260),
        label = "languageCardColor",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.outlineVariant.copy(alpha = .5f),
        animationSpec = tween(260),
        label = "languageCardBorder",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = tween(260),
        label = "languageCardBorderWidth",
    )
    val badgeColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.outlineVariant.copy(alpha = .6f),
        animationSpec = tween(260),
        label = "languageBadge",
    )

    Surface(
        selected = selected,
        onClick = onClick,
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
        },
        shape = RoundedCornerShape(22.dp),
        color = container,
        border = BorderStroke(borderWidth, borderColor),
        interactionSource = interaction,
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // the flag in a ring that takes the theme colour when this one is chosen
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(colors.surface)
                        .border(2.dp, badgeColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = language.flag, fontSize = 24.sp)
                }
                Spacer(Modifier.weight(1f))
                AnimatedVisibility(
                    visible = selected,
                    enter = scaleIn(spring(dampingRatio = .5f, stiffness = 500f)) + fadeIn(),
                    exit = scaleOut(tween(160)) + fadeOut(tween(160)),
                ) {
                    Box(
                        modifier = Modifier.size(24.dp).clip(CircleShape).background(colors.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = colors.onPrimary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = language.autonym,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) colors.onPrimaryContainer else colors.onSurface,
            )
        }
    }
}

// Settings' way in. a sheet rather than a dialog so it can hold the same cards as onboarding.
// the pick is shown on the card first, then the sheet slides away before the switch covers the
// screen: the sheet is its own window and would otherwise sit on top of the transition
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme
    var picked by remember { mutableStateOf<AppLanguage?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainerLow,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 28.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .background(colors.onSurfaceVariant.copy(alpha = .25f), RoundedCornerShape(999.dp)),
            )
            Spacer(Modifier.height(22.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Language,
                        contentDescription = null,
                        tint = colors.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = stringResource(R.string.language_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.language_dialog_body),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(20.dp))
            LanguageCards(
                selected = picked ?: selectedLanguage(),
                onPick = { language ->
                    picked = language
                    scope.launch {
                        // long enough to see the card take the selection
                        delay(180)
                        sheetState.hide()
                    }.invokeOnCompletion {
                        onDismiss()
                        AppLocale.requestSwitch(context, language)
                    }
                },
            )
        }
    }
}

// the switch itself, drawn over the whole app from MainActivity. paced so it can actually be
// read: the old language's flag and greeting come up, hold, the flag flips over while the new
// greeting rises in letter by letter, that holds while the locale changes underneath, and only
// then does the app fade back in. about three seconds end to end.
// the screen underneath is never recreated, so scroll position and half-typed text survive.
// with reduce motion on it's a short plain cross-fade: no flip, no rising letters, no spinning ring
@Composable
fun LanguageSwitchOverlay() {
    val switch by AppLocale.switch.collectAsState()
    val active = switch ?: return
    SwitchCurtain(active)
}

@Composable
private fun SwitchCurtain(active: LanguageSwitch) {
    val context = LocalContext.current
    val animate = animationsOn()
    val colors = MaterialTheme.colorScheme
    val current by rememberUpdatedState(currentAppLanguage())
    // a pre-33 recreate lands here mid-switch, already applied: start covered and just reveal
    val cover = remember { Animatable(if (active.applied) 1f else 0f) }
    // fills once round over the whole wait, so the ring closes just as the new greeting settles
    val ring = remember { Animatable(if (active.applied || !animate) 1f else 0f) }
    var showTarget by remember { mutableStateOf(active.applied) }

    LaunchedEffect(Unit) {
        if (!active.applied) {
            if (animate) launch { ring.animateTo(1f, tween(2_100, easing = FastOutSlowInEasing)) }
            cover.animateTo(1f, tween(if (animate) 480 else 150, easing = FastOutSlowInEasing))
            // long enough to register 'Hello' as the language being left
            delay(if (animate) 650 else 0)
            showTarget = true
            // the flip and the letters take ~700ms; hold past them so 'Ciao' sits still for a beat
            delay(if (animate) 1_000 else 150)
            AppLocale.commitSwitch(context)
        }
        // the configuration change arrives a frame or two after the locale is set. wait for it, but
        // never leave someone stuck behind the curtain if it doesn't come
        withTimeoutOrNull(2_000) { snapshotFlow { current }.first { it == active.to } }
        delay(if (animate) 450 else 60)
        cover.animateTo(0f, tween(if (animate) 650 else 150, easing = FastOutSlowInEasing))
        AppLocale.finishSwitch()
    }

    val progress = cover.value
    val shown = if (showTarget) active.to else active.from
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = progress }
            .background(colors.background)
            // swallows every touch while it's up, nothing should be tappable mid-switch
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = shown.autonym
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors.primary.copy(alpha = .16f), Color.Transparent),
                    center = center,
                    radius = size.minDimension * .75f,
                ),
                radius = size.minDimension * .75f,
                center = center,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer {
                if (animate) {
                    val lift = 1f - progress
                    scaleX = .92f + .08f * progress
                    scaleY = .92f + .08f * progress
                    translationY = lift * 36.dp.toPx()
                }
            },
        ) {
            SwitchRing(
                from = active.from,
                to = active.to,
                flipped = showTarget,
                progress = ring.value,
                animate = animate,
            )
            Spacer(Modifier.height(32.dp))
            AnimatedContent(
                targetState = shown,
                transitionSpec = {
                    // the old word lifts away as a whole, the new one builds itself letter by letter
                    if (animate) {
                        fadeIn(tween(1)) togetherWith
                            (slideOutVertically(tween(380, easing = FastOutSlowInEasing)) { -it / 2 } +
                                fadeOut(tween(300))) using SizeTransform(clip = false)
                    } else {
                        fadeIn(tween(150)) togetherWith fadeOut(tween(150))
                    }
                },
                label = "languageGreeting",
            ) { language ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // the language being left is already on screen when the curtain rises, so only the
                    // arriving one gets the letter-by-letter entrance
                    RisingWord(
                        text = language.greeting,
                        animate = animate && language == active.to && !active.applied,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = language.autonym,
                        style = MaterialTheme.typography.titleMedium,
                        letterSpacing = 1.sp,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// each letter floats up into place a little after the one before it
@Composable
private fun RisingWord(text: String, animate: Boolean) {
    val colors = MaterialTheme.colorScheme
    Row {
        text.forEachIndexed { index, letter ->
            val rise = remember(text) { Animatable(if (animate) 0f else 1f) }
            LaunchedEffect(text) {
                if (animate) {
                    delay(120L + index * 70L)
                    rise.animateTo(1f, spring(dampingRatio = .55f, stiffness = 260f))
                }
            }
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = colors.onBackground,
                modifier = Modifier.graphicsLayer {
                    val r = rise.value
                    alpha = r.coerceIn(0f, 1f)
                    translationY = (1f - r) * 28.dp.toPx()
                    val s = .7f + .3f * r
                    scaleX = s
                    scaleY = s
                },
            )
        }
    }
}

// the flag of the language on screen in a tonal disc, inside a ring that fills as the switch
// goes through. when it turns over, the disc flips like a card and comes down showing the other
// flag
@Composable
private fun SwitchRing(
    from: AppLanguage,
    to: AppLanguage,
    flipped: Boolean,
    progress: Float,
    animate: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    val turn by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = if (animate) tween(700, easing = FastOutSlowInEasing) else tween(0),
        label = "languageFlip",
    )
    Box(Modifier.size(120.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = colors.primary.copy(alpha = .14f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke),
            )
            // from twelve o'clock, clockwise. a round cap on a closed ring leaves a nub, so it goes
            // butt once full
            drawArc(
                color = colors.primary,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = if (progress < 1f) StrokeCap.Round else StrokeCap.Butt),
            )
        }
        // past halfway the back face is showing, so it's drawn un-mirrored with the new flag
        val backFace = turn > 90f
        Box(
            modifier = Modifier
                .size(90.dp)
                .graphicsLayer {
                    rotationY = if (backFace) turn - 180f else turn
                    cameraDistance = 14f * density
                }
                .clip(CircleShape)
                .background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = if (backFace) to.flag else from.flag, fontSize = 46.sp)
        }
    }
}
