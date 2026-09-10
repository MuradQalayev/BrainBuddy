package com.muradgalayev.brainbuddy.ui.pomodoro

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.data.repository.FocusPhase
import com.muradgalayev.brainbuddy.data.repository.FocusSession
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.accessibility.speaking
import kotlinx.coroutines.delay

// what the Pomodoro screen becomes while a shared session owns the clock. it shows the time
// and nothing that changes it, and that distinction is the point: the problem was never a
// second countdown, it was a second set of controls. a Reset here would silently desync a block
// the other person is still sitting in, and they'd have no way to know. read-only, this is the
// same information with none of the danger.
// the halo behind the ring breathes only during focus. on a break the point is to stop looking
@Composable
fun SharedSessionPanel(
    session: FocusSession,
    onOpenRoom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val animate = animationsOn()
    val onBreak = session.phase == FocusPhase.BREAK
    val totalSeconds = (if (onBreak) session.breakMinutes else session.focusMinutes) * 60

    var remaining by remember(session.id, session.phase, session.secondsRemaining) {
        mutableIntStateOf(session.secondsRemaining)
    }
    LaunchedEffect(session.id, session.phase, session.secondsRemaining) {
        while (remaining > 0) {
            delay(1_000)
            remaining -= 1
        }
    }

    val progress by animateFloatAsState(
        targetValue = if (totalSeconds <= 0) 0f else 1f - remaining.toFloat() / totalSeconds,
        animationSpec = tween(if (animate) 600 else 0, easing = FastOutSlowInEasing),
        label = "shared_progress",
    )

    val halo = if (animate && !onBreak) {
        rememberInfiniteTransition(label = "shared_halo").animateFloat(
            initialValue = 0.97f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(
                animation = tween(3200, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "shared_halo_scale",
        ).value
    } else 1f

    val accent = if (onBreak) colors.tertiary else colors.primary

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(contentAlignment = Alignment.Center) {
            // a soft bloom rather than a hard second ring: it gives the number presence without competing
            // with it for the eye
            Box(
                Modifier
                    .size(250.dp)
                    .scale(halo)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(accent.copy(alpha = 0.16f), accent.copy(alpha = 0f)),
                        )
                    )
            )

            Canvas(Modifier.size(196.dp)) {
                val stroke = 10.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(
                    color = accent.copy(alpha = 0.16f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                if (progress > 0f) {
                    drawArc(
                        color = accent,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.coerceIn(0f, 1f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round),
                    )
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (onBreak) Icons.Rounded.Coffee else Icons.Rounded.Group,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "%d:%02d".format(remaining / 60, remaining % 60),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = if (onBreak) "On a break together" else "Focusing together",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = session.hostName.take(1).uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (session.joinedCount > 2) {
                    "${session.hostName} +${session.joinedCount - 2}"
                } else {
                    session.hostName
                },
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = speaking("Open the focus room", onOpenRoom),
            modifier = Modifier.fillMaxWidth().height(54.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text("Open the focus room", fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(10.dp))
        Text(
            // says why the controls are missing. without it, the absence reads as something broken rather
            // than something deliberate
            text = "Controls live in the room so you both stay in step",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant.copy(alpha = 0.8f),
        )
    }
}
