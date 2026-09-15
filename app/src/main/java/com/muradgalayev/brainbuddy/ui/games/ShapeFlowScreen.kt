package com.muradgalayev.brainbuddy.ui.games

import androidx.annotation.RequiresApi
import android.os.VibratorManager
import android.os.Build
import com.muradgalayev.brainbuddy.ui.utils.contentDescription
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.accessibility.speaking
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
import kotlinx.coroutines.delay
import kotlin.math.min
import kotlin.random.Random
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

private const val GAME_PREFERENCES = "shape_flow_game"
private const val BEST_SCORE_KEY = "best_score"

private enum class ShapeGameMode { Memory, FreePlay }
private enum class ShapeGamePhase { Ready, Showing, Input, Success, Lost }
private enum class SequenceSpeed(val activeMillis: Long, val gapMillis: Long) {
    Calm(activeMillis = 520L, gapMillis = 190L),
    Quick(activeMillis = 300L, gapMillis = 110L),
}

private enum class ShapeKind { Circle, Square, Trapezoid, Triangle, Squircle, Diamond }

private data class PlayShape(
    @androidx.annotation.StringRes val nameRes: Int,
    val color: Color,
    val kind: ShapeKind,
)

private val playShapes = listOf(
    PlayShape(R.string.shape_purple_circle, Color(0xFF7651C9), ShapeKind.Circle),
    PlayShape(R.string.shape_blue_square, Color(0xFF4F8BC9), ShapeKind.Square),
    PlayShape(R.string.shape_golden_trapezoid, Color(0xFFD9AA35), ShapeKind.Trapezoid),
    PlayShape(R.string.shape_green_triangle, Color(0xFF4FA973), ShapeKind.Triangle),
    PlayShape(R.string.shape_rose_squircle, Color(0xFFD95572), ShapeKind.Squircle),
    PlayShape(R.string.shape_orange_diamond, Color(0xFFE0772D), ShapeKind.Diamond),
)

/** A short, sensory reset inspired by the shape fidget reference. */
@Composable
fun ShapeFlowScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences(GAME_PREFERENCES, Context.MODE_PRIVATE)
    }
    val motionEnabled = animationsOn()

    var mode by rememberSaveable { mutableStateOf(ShapeGameMode.Memory) }
    var speed by rememberSaveable { mutableStateOf(SequenceSpeed.Calm) }
    var phase by rememberSaveable { mutableStateOf(ShapeGamePhase.Ready) }
    var hapticsEnabled by rememberSaveable { mutableStateOf(true) }
    var bestScore by rememberSaveable { mutableIntStateOf(preferences.getInt(BEST_SCORE_KEY, 0)) }
    var sequence by remember { mutableStateOf(emptyList<Int>()) }
    var playerIndex by remember { mutableIntStateOf(0) }
    var activeShape by remember { mutableIntStateOf(-1) }
    var freePlayTaps by rememberSaveable { mutableIntStateOf(0) }
    var playbackVersion by remember { mutableIntStateOf(0) }
    var flashVersion by remember { mutableIntStateOf(0) }
    var nextRoundVersion by remember { mutableIntStateOf(0) }

    fun rememberBest(score: Int) {
        if (score <= bestScore) return
        bestScore = score
        preferences.edit().putInt(BEST_SCORE_KEY, score).apply()
    }

    fun beginMemoryGame() {
        sequence = listOf(Random.nextInt(playShapes.size))
        playerIndex = 0
        activeShape = -1
        phase = ShapeGamePhase.Showing
        playbackVersion += 1
    }

    fun changeMode(newMode: ShapeGameMode) {
        mode = newMode
        sequence = emptyList()
        playerIndex = 0
        activeShape = -1
        freePlayTaps = 0
        phase = ShapeGamePhase.Ready
    }

    LaunchedEffect(playbackVersion, mode, speed) {
        if (playbackVersion == 0 || mode != ShapeGameMode.Memory || phase != ShapeGamePhase.Showing) {
            return@LaunchedEffect
        }
        activeShape = -1
        delay(420L)
        sequence.forEach { shapeIndex ->
            activeShape = shapeIndex
            if (hapticsEnabled) vibrateForShape(context, shapeIndex)
            delay(speed.activeMillis)
            activeShape = -1
            delay(speed.gapMillis)
        }
        playerIndex = 0
        phase = ShapeGamePhase.Input
    }

    LaunchedEffect(flashVersion) {
        if (flashVersion == 0) return@LaunchedEffect
        delay(170L)
        if (phase != ShapeGamePhase.Showing) activeShape = -1
    }

    LaunchedEffect(nextRoundVersion, mode) {
        if (nextRoundVersion == 0 || mode != ShapeGameMode.Memory || phase != ShapeGamePhase.Success) {
            return@LaunchedEffect
        }
        delay(720L)
        sequence = sequence + Random.nextInt(playShapes.size)
        playerIndex = 0
        phase = ShapeGamePhase.Showing
        playbackVersion += 1
    }

    fun tapShape(index: Int) {
        if (mode == ShapeGameMode.FreePlay) {
            activeShape = index
            flashVersion += 1
            freePlayTaps += 1
            if (hapticsEnabled) vibrateForShape(context, index)
            return
        }
        if (phase != ShapeGamePhase.Input) return

        activeShape = index
        flashVersion += 1
        if (hapticsEnabled) vibrateForShape(context, index)

        if (sequence.getOrNull(playerIndex) != index) {
            phase = ShapeGamePhase.Lost
            return
        }
        if (playerIndex == sequence.lastIndex) {
            rememberBest(sequence.size)
            phase = ShapeGamePhase.Success
            nextRoundVersion += 1
        } else {
            playerIndex += 1
        }
    }

    val instruction = when {
        mode == ShapeGameMode.FreePlay && freePlayTaps == 0 ->
            stringResource(R.string.shape_tap_any)
        mode == ShapeGameMode.FreePlay ->
            if (freePlayTaps == 1) stringResource(R.string.shape_one_tap)
            else stringResource(R.string.shape_n_taps, freePlayTaps)
        phase == ShapeGamePhase.Ready -> stringResource(R.string.shape_watch_glow)
        phase == ShapeGamePhase.Showing -> stringResource(R.string.shape_watch_carefully)
        phase == ShapeGamePhase.Input -> stringResource(R.string.shape_your_turn_of, playerIndex + 1, sequence.size)
        phase == ShapeGamePhase.Success -> stringResource(R.string.shape_beautiful)
        else -> stringResource(R.string.shape_almost)
    }
    val boardEnabled = mode == ShapeGameMode.FreePlay || phase == ShapeGamePhase.Input

    val accents = MaterialTheme.myndoraAccents
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Barely-there colour in the page background connects this detail screen to the soft
        // washes used by Home without competing with the six game colours.
        Canvas(Modifier.matchParentSize()) {
            drawCircle(
                color = accents.accent.copy(alpha = .045f),
                radius = size.width * .48f,
                center = Offset(size.width * 1.02f, size.height * .04f),
            )
            drawCircle(
                color = accents.support.copy(alpha = .035f),
                radius = size.width * .42f,
                center = Offset(-size.width * .10f, size.height * .72f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp),
        ) {
            GameHeader(
                onBack = onBack,
                hapticsEnabled = hapticsEnabled,
                onToggleHaptics = { hapticsEnabled = !hapticsEnabled },
            )

            Spacer(Modifier.height(20.dp))
            GameModeSelector(
                selected = mode,
                onSelect = ::changeMode,
            )

            Spacer(Modifier.height(14.dp))
            GameArena(
                mode = mode,
                phase = phase,
                round = sequence.size,
                bestScore = bestScore,
                freePlayTaps = freePlayTaps,
                playerIndex = playerIndex,
                instruction = instruction,
                activeShape = activeShape,
                enabled = boardEnabled,
                motionEnabled = motionEnabled,
                onShapeTap = ::tapShape,
            )

            Spacer(Modifier.height(14.dp))
            if (mode == ShapeGameMode.Memory) {
                MemoryControlDock(
                    speed = speed,
                    phase = phase,
                    onSpeedChange = { speed = it },
                    onAction = ::beginMemoryGame,
                )
            } else {
                FreePlayControlDock(
                    taps = freePlayTaps,
                    onClear = { freePlayTaps = 0 },
                )
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = if (hapticsEnabled) stringResource(R.string.shape_haptics_on)
                else stringResource(R.string.shape_haptics_off),
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun GameHeader(
    onBack: () -> Unit,
    hapticsEnabled: Boolean,
    onToggleHaptics: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onBack,
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(21.dp),
                )
            }
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.shape_mindful_play),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.35.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.nav_shape_flow),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.shape_tiny_reset),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        Surface(
            onClick = onToggleHaptics,
            shape = RoundedCornerShape(16.dp),
            color = if (hapticsEnabled) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainer,
            contentColor = if (hapticsEnabled) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Vibration,
                    contentDescription = if (hapticsEnabled) stringResource(R.string.shape_turn_haptics_off) else stringResource(R.string.shape_turn_haptics_on),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = if (hapticsEnabled) stringResource(R.string.common_on) else stringResource(R.string.common_off),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun GameModeSelector(
    selected: ShapeGameMode,
    onSelect: (ShapeGameMode) -> Unit,
) {
    val selectorShape = RoundedCornerShape(20.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
                shape = selectorShape,
            ),
        shape = selectorShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Row(Modifier.padding(5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            ModeButton(
                label = stringResource(R.string.shape_memory),
                icon = Icons.Rounded.Psychology,
                selected = selected == ShapeGameMode.Memory,
                onClick = { onSelect(ShapeGameMode.Memory) },
                modifier = Modifier.weight(1f),
            )
            ModeButton(
                label = stringResource(R.string.shape_fidget),
                icon = Icons.Rounded.TouchApp,
                selected = selected == ShapeGameMode.FreePlay,
                onClick = { onSelect(ShapeGameMode.FreePlay) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ModeButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = tween(if (animationsOn()) 220 else 0),
        label = "gameModeColor",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun GameArena(
    mode: ShapeGameMode,
    phase: ShapeGamePhase,
    round: Int,
    bestScore: Int,
    freePlayTaps: Int,
    playerIndex: Int,
    instruction: String,
    activeShape: Int,
    enabled: Boolean,
    motionEnabled: Boolean,
    onShapeTap: (Int) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val accents = MaterialTheme.myndoraAccents
    val arenaShape = RoundedCornerShape(32.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, colors.outlineVariant.copy(alpha = .62f), arenaShape),
        shape = arenaShape,
        color = colors.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(
                            accents.accent.copy(alpha = .075f),
                            Color.Transparent,
                            accents.support.copy(alpha = .035f),
                        ),
                    ),
                )
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PhasePill(mode = mode, phase = phase)
                Spacer(Modifier.weight(1f))
                ArenaMetric(
                    label = if (mode == ShapeGameMode.Memory) stringResource(R.string.shape_round) else stringResource(R.string.shape_taps_caps),
                    value = if (mode == ShapeGameMode.Memory) round.toString() else freePlayTaps.toString(),
                )
                if (mode == ShapeGameMode.Memory) {
                    Spacer(Modifier.width(7.dp))
                    ArenaMetric(label = stringResource(R.string.shape_best), value = bestScore.toString())
                }
            }

            Spacer(Modifier.height(14.dp))
            AnimatedContent(
                targetState = instruction,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                transitionSpec = { fadeIn(tween(170)) togetherWith fadeOut(tween(100)) },
                label = "gameInstruction",
            ) { text ->
                Text(
                    text = text,
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                    textAlign = TextAlign.Start,
                )
            }
            Spacer(Modifier.height(10.dp))
            SequenceTrack(
                mode = mode,
                phase = phase,
                length = round,
                playerIndex = playerIndex,
            )
            Spacer(Modifier.height(15.dp))
            ShapeGrid(
                activeShape = activeShape,
                enabled = enabled,
                motionEnabled = motionEnabled,
                onShapeTap = onShapeTap,
            )
        }
    }
}

@Composable
private fun PhasePill(mode: ShapeGameMode, phase: ShapeGamePhase) {
    val colors = MaterialTheme.colorScheme
    val (label, accent) = if (mode == ShapeGameMode.FreePlay) {
        stringResource(R.string.shape_free_play) to MaterialTheme.myndoraAccents.support
    } else {
        when (phase) {
            ShapeGamePhase.Ready -> stringResource(R.string.shape_ready) to colors.onSurfaceVariant
            ShapeGamePhase.Showing -> stringResource(R.string.shape_watch) to MaterialTheme.myndoraAccents.accentEnd
            ShapeGamePhase.Input -> stringResource(R.string.shape_your_turn) to MaterialTheme.myndoraAccents.support
            ShapeGamePhase.Success -> stringResource(R.string.shape_nice) to colors.primary
            ShapeGamePhase.Lost -> stringResource(R.string.common_try_again) to colors.error
        }
    }
    Surface(
        shape = CircleShape,
        color = colors.surface.copy(alpha = .72f),
        border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = .45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(7.dp).background(accent, CircleShape))
            Spacer(Modifier.width(7.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ArenaMetric(label: String, value: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .45f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = .9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun SequenceTrack(
    mode: ShapeGameMode,
    phase: ShapeGamePhase,
    length: Int,
    playerIndex: Int,
) {
    if (mode == ShapeGameMode.FreePlay) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            playShapes.forEach { shape ->
                Box(Modifier.size(5.dp).background(shape.color.copy(alpha = .72f), CircleShape))
            }
        }
        return
    }

    val visibleSegments = length.coerceAtLeast(4).coerceAtMost(8)
    val completed = when (phase) {
        ShapeGamePhase.Success -> length
        ShapeGamePhase.Input -> playerIndex
        else -> 0
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(visibleSegments) { index ->
            val filled = index < completed
            val exists = index < length
            val color = when {
                phase == ShapeGamePhase.Lost && exists -> MaterialTheme.colorScheme.error.copy(alpha = .55f)
                filled -> MaterialTheme.colorScheme.primary
                exists -> MaterialTheme.colorScheme.primary.copy(alpha = .24f)
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f)
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .background(color, CircleShape),
            )
        }
        if (length > visibleSegments) {
            Text(
                text = "+${length - visibleSegments}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ShapeGrid(
    activeShape: Int,
    enabled: Boolean,
    motionEnabled: Boolean,
    onShapeTap: (Int) -> Unit,
) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val accents = MaterialTheme.myndoraAccents
    val gridShape = RoundedCornerShape(25.dp)
    val gridBrush = if (dark) {
        Brush.linearGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                lerp(MaterialTheme.colorScheme.surfaceContainer, accents.accent, .10f),
            ),
        )
    } else {
        Brush.linearGradient(
            listOf(
                lerp(Color.White, MaterialTheme.colorScheme.primaryContainer, .38f),
                lerp(Color.White, MaterialTheme.colorScheme.secondaryContainer, .24f),
            ),
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
            .clip(gridShape)
            .background(gridBrush)
            .border(1.dp, Color.White.copy(alpha = if (dark) .08f else .62f), gridShape)
            .padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(3) { row ->
            Row(
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(2) { column ->
                    val index = row * 2 + column
                    ShapeTile(
                        shape = playShapes[index],
                        active = activeShape == index,
                        enabled = enabled,
                        motionEnabled = motionEnabled,
                        onClick = { onShapeTap(index) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun ShapeTile(
    shape: PlayShape,
    active: Boolean,
    enabled: Boolean,
    motionEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            active -> 1.08f
            pressed -> .95f
            else -> 1f
        },
        animationSpec = tween(if (motionEnabled) 130 else 0, easing = FastOutSlowInEasing),
        label = "shapeScale",
    )
    val color by animateColorAsState(
        targetValue = if (active) lerp(shape.color, Color.White, .34f) else shape.color,
        animationSpec = tween(if (motionEnabled) 120 else 0),
        label = "shapeGlow",
    )
    Box(
        modifier = modifier
            .contentDescription(
                if (active) stringResource(R.string.shape_glowing, stringResource(shape.nameRes))
                else stringResource(shape.nameRes)
            )
            .clip(RoundedCornerShape(22.dp))
            .background(
                if (active) Color.White.copy(alpha = .16f)
                else Color.Transparent,
            )
            .border(
                width = if (active) 1.dp else 0.dp,
                color = Color.White.copy(alpha = .34f),
                shape = RoundedCornerShape(22.dp),
            )
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = if (enabled || active) 1f else .82f
                },
        ) {
            if (active) drawPlayShape(shape.kind, shape.color.copy(alpha = .22f), 1.12f)
            drawPlayShape(shape.kind, color, .88f)
        }
    }
}

@Composable
private fun MemoryControlDock(
    speed: SequenceSpeed,
    phase: ShapeGamePhase,
    onSpeedChange: (SequenceSpeed) -> Unit,
    onAction: () -> Unit,
) {
    val waiting = phase == ShapeGamePhase.Showing || phase == ShapeGamePhase.Success
    val dockShape = RoundedCornerShape(28.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
                dockShape,
            ),
        shape = dockShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Column(Modifier.padding(15.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.shape_pace),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (speed == SequenceSpeed.Calm) stringResource(R.string.shape_easy_follow) else stringResource(R.string.shape_little_challenge),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                SpeedChoice(
                    label = stringResource(R.string.theme_calm),
                    selected = speed == SequenceSpeed.Calm,
                    enabled = !waiting,
                    onClick = { onSpeedChange(SequenceSpeed.Calm) },
                )
                Spacer(Modifier.width(7.dp))
                SpeedChoice(
                    label = stringResource(R.string.shape_quick),
                    selected = speed == SequenceSpeed.Quick,
                    enabled = !waiting,
                    onClick = { onSpeedChange(SequenceSpeed.Quick) },
                )
            }
            Spacer(Modifier.height(14.dp))
            PrimaryGameButton(
                label = when (phase) {
                    ShapeGamePhase.Ready -> stringResource(R.string.shape_start_memory)
                    ShapeGamePhase.Lost -> stringResource(R.string.common_try_again)
                    ShapeGamePhase.Input -> stringResource(R.string.shape_restart)
                    ShapeGamePhase.Showing -> stringResource(R.string.shape_watch_pattern)
                    ShapeGamePhase.Success -> stringResource(R.string.shape_next_round)
                },
                icon = if (phase == ShapeGamePhase.Input || phase == ShapeGamePhase.Lost) {
                    Icons.Rounded.Refresh
                } else {
                    Icons.Rounded.PlayArrow
                },
                enabled = !waiting,
                onClick = onAction,
            )
        }
    }
}

@Composable
private fun PrimaryGameButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) .985f else 1f,
        animationSpec = tween(if (animationsOn()) 100 else 0),
        label = "gameButtonPress",
    )
    val accents = MaterialTheme.myndoraAccents
    val brush = if (enabled) {
        Brush.horizontalGradient(listOf(accents.accent, accents.accentEnd))
    } else {
        Brush.horizontalGradient(
            listOf(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .scale(scale)
            .clip(RoundedCornerShape(19.dp))
            .background(brush)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(21.dp),
            tint = if (enabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FreePlayControlDock(
    taps: Int,
    onClear: () -> Unit,
) {
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f), shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 17.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.shape_no_score),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = stringResource(R.string.shape_tap_feels_good),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (taps > 0) {
                Spacer(Modifier.width(12.dp))
                Surface(
                    onClick = onClear,
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Text(
                        text = stringResource(R.string.common_reset),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SpeedChoice(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainer,
        contentColor = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Compact Workspace entry point for the game. */
@Composable
fun ShapeFlowEntryCard(
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val cardScale by animateFloatAsState(
        targetValue = if (pressed) .985f else 1f,
        animationSpec = tween(if (animationsOn()) 100 else 0),
        label = "shapeFlowCardPress",
    )
    val accents = MaterialTheme.myndoraAccents
    val cardShape = RoundedCornerShape(28.dp)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 148.dp)
            .scale(cardScale)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f),
                cardShape,
            ),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            accents.accent.copy(alpha = .11f),
                            Color.Transparent,
                            accents.support.copy(alpha = .06f),
                        ),
                    ),
                )
                .drawBehind {
                    drawCircle(
                        color = accents.accentEnd.copy(alpha = .07f),
                        radius = size.minDimension * .62f,
                        center = Offset(size.width * .98f, size.height * .08f),
                    )
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = speaking(stringResource(R.string.shape_game_cd), onClick),
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.shape_mindful_play),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.25.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.nav_shape_flow),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.shape_card_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(11.dp))
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = .72f),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant.copy(alpha = .4f),
                        ),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.shape_card_meta),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(14.dp))
                MiniShapeBoard()
            }
        }
    }
}

@Composable
private fun MiniShapeBoard() {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val background = if (dark) MaterialTheme.colorScheme.surfaceContainerHigh
    else lerp(Color.White, MaterialTheme.colorScheme.primaryContainer, .40f)
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier = Modifier
            .size(width = 96.dp, height = 112.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, Color.White.copy(alpha = if (dark) .08f else .64f), shape)
            .padding(9.dp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cellWidth = size.width / 2f
            val cellHeight = size.height / 3f
            playShapes.forEachIndexed { index, shape ->
                val column = index % 2
                val row = index / 2
                translate(left = column * cellWidth, top = row * cellHeight) {
                    drawPlayShape(
                        kind = shape.kind,
                        color = shape.color,
                        scale = .68f,
                        areaWidth = cellWidth,
                        areaHeight = cellHeight,
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawPlayShape(
    kind: ShapeKind,
    color: Color,
    scale: Float,
    areaWidth: Float = size.width,
    areaHeight: Float = size.height,
) {
    val diameter = min(areaWidth, areaHeight) * scale
    val left = (areaWidth - diameter) / 2f
    val top = (areaHeight - diameter) / 2f
    val right = left + diameter
    val bottom = top + diameter

    when (kind) {
        ShapeKind.Circle -> drawCircle(
            color = color,
            radius = diameter / 2f,
            center = androidx.compose.ui.geometry.Offset(areaWidth / 2f, areaHeight / 2f),
        )
        ShapeKind.Square -> drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(diameter, diameter),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(diameter * .09f),
        )
        ShapeKind.Squircle -> drawRoundRect(
            color = color,
            topLeft = androidx.compose.ui.geometry.Offset(left, top),
            size = androidx.compose.ui.geometry.Size(diameter, diameter),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(diameter * .30f),
        )
        ShapeKind.Trapezoid -> drawPath(
            path = Path().apply {
                moveTo(left + diameter * .10f, top + diameter * .16f)
                lineTo(right - diameter * .10f, top + diameter * .16f)
                lineTo(right - diameter * .24f, bottom - diameter * .12f)
                lineTo(left + diameter * .24f, bottom - diameter * .12f)
                close()
            },
            color = color,
        )
        ShapeKind.Triangle -> drawPath(
            path = Path().apply {
                moveTo(areaWidth / 2f, top + diameter * .06f)
                lineTo(right - diameter * .05f, bottom - diameter * .08f)
                lineTo(left + diameter * .05f, bottom - diameter * .08f)
                close()
            },
            color = color,
        )
        ShapeKind.Diamond -> drawPath(
            path = Path().apply {
                moveTo(areaWidth / 2f, top + diameter * .03f)
                lineTo(right - diameter * .03f, areaHeight / 2f)
                lineTo(areaWidth / 2f, bottom - diameter * .03f)
                lineTo(left + diameter * .03f, areaHeight / 2f)
                close()
            },
            color = color,
        )
    }
}

// every shape has its own rhythm, so the memory game can be played by feel: count the pulses
// and their length, not just how strong they are. strength alone was what told them apart before,
// and on phones without amplitude control that made all six the same buzz. every pattern fits
// inside Quick mode's 300ms glow, so the next shape's never starts on top of it
private val ShapeRhythms: List<Pair<LongArray, IntArray>> = listOf(
    // circle: one round thump
    longArrayOf(0, 70) to intArrayOf(0, 170),
    // square: two even knocks
    longArrayOf(0, 35, 70, 35) to intArrayOf(0, 210, 0, 210),
    // trapezoid: short, then long
    longArrayOf(0, 25, 60, 95) to intArrayOf(0, 140, 0, 230),
    // triangle: three quick ticks
    longArrayOf(0, 18, 45, 18, 45, 18) to intArrayOf(0, 235, 0, 235, 0, 235),
    // squircle: a soft swell that grows
    longArrayOf(0, 30, 30, 30, 30, 30) to intArrayOf(0, 45, 95, 145, 200, 255),
    // diamond: long, short, short
    longArrayOf(0, 90, 50, 20, 40, 20) to intArrayOf(0, 225, 0, 200, 0, 200),
)

private fun vibrateForShape(context: Context, index: Int) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (!vibrator.hasVibrator()) return
    val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        shapePrimitives(vibrator, index)
    } else {
        null
    } ?: ShapeRhythms[index.coerceIn(0, ShapeRhythms.lastIndex)].let { (timings, amplitudes) ->
        VibrationEffect.createWaveform(timings, amplitudes, -1)
    }
    vibrator.vibrate(effect)
}

// the same six rhythms built from the phone's own haptic primitives, which feel crisper than a
// raw motor buzz where the hardware has them. null when it doesn't, and the waveform takes over
@RequiresApi(Build.VERSION_CODES.S)
private fun shapePrimitives(vibrator: Vibrator, index: Int): VibrationEffect? {
    val needed = when (index) {
        0 -> intArrayOf(VibrationEffect.Composition.PRIMITIVE_THUD)
        1 -> intArrayOf(VibrationEffect.Composition.PRIMITIVE_CLICK)
        2 -> intArrayOf(VibrationEffect.Composition.PRIMITIVE_TICK, VibrationEffect.Composition.PRIMITIVE_CLICK)
        3 -> intArrayOf(VibrationEffect.Composition.PRIMITIVE_TICK)
        4 -> intArrayOf(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, VibrationEffect.Composition.PRIMITIVE_QUICK_FALL)
        else -> intArrayOf(VibrationEffect.Composition.PRIMITIVE_CLICK, VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
    }
    if (!vibrator.areAllPrimitivesSupported(*needed)) return null
    val composition = VibrationEffect.startComposition()
    when (index) {
        0 -> composition.addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, .9f)
        1 -> composition
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 70)
        2 -> composition
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, .5f)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 60)
        3 -> composition
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1f)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1f, 45)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 1f, 45)
        4 -> composition
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, .75f)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_FALL, .6f)
        else -> composition
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, .8f, 55)
            .addPrimitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, .8f, 40)
    }
    return composition.compose()
}
