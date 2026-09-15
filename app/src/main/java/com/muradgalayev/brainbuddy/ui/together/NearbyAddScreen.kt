package com.muradgalayev.brainbuddy.ui.together

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.domain.model.ConnectionRelation
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// what the radar needs to both find and be found. Android 12 split Bluetooth out of location, and
// 13 did the same for Wi-Fi, so the list depends on the version
private fun nearbyPermissions(): Array<String> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.NEARBY_WIFI_DEVICES)
    } else {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}.toTypedArray()

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NearbyAddScreen(
    onBack: () -> Unit,
    viewModel: NearbyAddViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme

    val permissions = remember { nearbyPermissions() }
    fun hasPermissions() = permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
    var granted by remember { mutableStateOf(hasPermissions()) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        granted = hasPermissions()
    }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(permissions) }

    // visible only while the screen is actually in front of someone: backgrounding the app stops the
    // broadcast instead of leaving the name out there
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, granted) {
        if (granted && lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) viewModel.start()
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (granted) viewModel.start()
                Lifecycle.Event.ON_STOP -> viewModel.stop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stop()
        }
    }

    TogetherScaffold(title = stringResource(R.string.together_nearby_title), onBack = onBack) {
        Radar(
            people = state.people,
            sendingTo = state.sendingTo?.endpointId,
            selfName = viewModel.visibleName,
            active = granted && state.running && !state.unavailable,
            enabled = state.sendingTo == null,
            onTap = viewModel::invite,
        )

        val sending = state.sendingTo
        val (statusText, statusColor) = when {
            !granted -> stringResource(R.string.together_nearby_permission) to colors.onSurfaceVariant
            state.unavailable -> stringResource(R.string.together_nearby_unavailable) to colors.error
            sending != null -> stringResource(R.string.together_nearby_sending, sending.name) to colors.primary
            state.sentTo != null -> stringResource(R.string.together_nearby_sent, state.sentTo!!) to colors.tertiary
            state.failedTo != null -> stringResource(R.string.together_nearby_failed, state.failedTo!!) to colors.error
            state.people.isEmpty() -> stringResource(R.string.together_nearby_searching) to colors.onSurfaceVariant
            else -> stringResource(R.string.together_nearby_found) to colors.onSurface
        }
        Text(
            statusText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = statusColor,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        if (!granted) {
            Button(
                onClick = { launcher.launch(permissions) },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Text(stringResource(R.string.together_nearby_allow), fontWeight = FontWeight.Bold)
            }
        }

        TogetherCard {
            Text(
                stringResource(R.string.together_how_know),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for (relation in ConnectionRelation.entries) {
                    ChoicePill(
                        text = stringResource(relation.labelRes),
                        selected = state.relation == relation,
                        onClick = { viewModel.setRelation(relation) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.together_nearby_hint),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.together_nearby_visible, viewModel.visibleName),
                style = MaterialTheme.typography.bodySmall,
                color = colors.primary,
            )
        }
    }
}

// rings, a turning sweep and a pulse from the centre, with everyone found placed around it. with
// reduce motion on it is the same picture standing still
@Composable
private fun Radar(
    people: List<NearbyPerson>,
    sendingTo: String?,
    selfName: String,
    active: Boolean,
    enabled: Boolean,
    onTap: (NearbyPerson) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val animate = animationsOn() && active
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep = if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
            label = "radarSweep",
        ).value
    } else 0f
    val pulse = if (animate) {
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
            label = "radarPulse",
        ).value
    } else 0f

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val sizePx = with(density) { maxWidth.toPx() }
        val ring = colors.primary

        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            val c = center
            for (fraction in listOf(.34f, .67f, 1f)) {
                drawCircle(ring.copy(alpha = .16f), radius = r * fraction - 1.dp.toPx(), center = c, style = Stroke(1.dp.toPx()))
            }
            drawLine(ring.copy(alpha = .08f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1.dp.toPx())
            drawLine(ring.copy(alpha = .08f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1.dp.toPx())
            if (active) {
                rotate(sweep, pivot = c) {
                    drawCircle(
                        brush = Brush.sweepGradient(
                            0f to Color.Transparent,
                            .78f to Color.Transparent,
                            1f to ring.copy(alpha = .32f),
                            center = c,
                        ),
                        radius = r,
                        center = c,
                    )
                }
            }
            if (animate) {
                drawCircle(
                    ring.copy(alpha = .30f * (1f - pulse)),
                    radius = r * pulse,
                    center = c,
                    style = Stroke(2.dp.toPx()),
                )
            }
        }

        // you, in the middle
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(colors.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    selfName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onPrimary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.together_nearby_you),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }

        // a stable spot per person, so a list refresh doesn't shuffle everyone around the dial
        val blipPx = with(density) { 76.dp.toPx() }
        people.forEach { person ->
            val seed = abs(person.endpointId.hashCode())
            val angle = Math.toRadians((seed % 360).toDouble())
            val distance = (sizePx / 2f) * (.52f + (seed / 360 % 5) * .06f)
            val x = sizePx / 2f + (cos(angle) * distance).toFloat() - blipPx / 2f
            val y = sizePx / 2f + (sin(angle) * distance).toFloat() - blipPx / 2f
            Blip(
                person = person,
                sending = person.endpointId == sendingTo,
                enabled = enabled,
                onTap = { onTap(person) },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset { IntOffset(x.roundToInt(), y.roundToInt()) },
            )
        }
    }
}

@Composable
private fun Blip(
    person: NearbyPerson,
    sending: Boolean,
    enabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val appear by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = if (animationsOn()) spring(dampingRatio = .6f) else tween(0),
        label = "blipAppear",
    )
    Column(
        modifier = modifier
            .width(76.dp)
            .graphicsLayer {
                scaleX = appear
                scaleY = appear
                alpha = appear
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.Center) {
            TogetherAvatar(name = person.name, avatarUrl = null, size = 50.dp)
            if (sending) {
                CircularProgressIndicator(
                    modifier = Modifier.size(58.dp),
                    strokeWidth = 2.5.dp,
                    color = colors.primary,
                )
            }
        }
        Spacer(Modifier.height(3.dp))
        Text(
            person.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
