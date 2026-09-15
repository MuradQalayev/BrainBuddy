package com.muradgalayev.brainbuddy.ui.pomodoro.dialogs

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FlutterDash
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
import kotlin.math.PI
import kotlin.math.sin

// one icon per locked sound, in the order the sheet lists them
private val LockedSoundIcons = listOf(
    Icons.Rounded.Park,
    Icons.Rounded.Waves,
    Icons.Rounded.LocalFireDepartment,
    Icons.Rounded.LocalCafe,
    Icons.Rounded.FlutterDash,
    Icons.Rounded.Air,
)

// where the locked sounds make their case. a window onto what's behind the lock rather than a grey
// banner: the sounds float in a row, a waveform breathes along the bottom, and the whole card is the
// button. same gradient as the Plus screen it opens, so the jump there feels like one surface.
// motion stops entirely with reduce motion on
@Composable
fun PlusSoundsCard(
    lockedCount: Int,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accents = MaterialTheme.myndoraAccents
    val phase = if (animationsOn()) {
        rememberInfiniteTransition(label = "plus_sounds").animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
            label = "wave",
        ).value
    } else {
        0f
    }

    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(accents.accent, accents.accentEnd)))
            .clickable(role = Role.Button, onClick = onUnlock),
    ) {
        // waveform along the bottom edge, faint enough that the text above never fights it
        Canvas(Modifier.matchParentSize()) {
            val bars = 30
            val slot = size.width / bars
            val barWidth = slot * .42f
            for (i in 0 until bars) {
                val swell = (sin(phase + i * .55f) + 1f) / 2f
                val h = size.height * (.10f + .26f * swell)
                drawRoundRect(
                    color = Color.White.copy(alpha = .11f),
                    topLeft = Offset(i * slot + (slot - barWidth) / 2f, size.height - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            }
        }

        Column(Modifier.padding(18.dp)) {
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

            Spacer(Modifier.height(16.dp))

            // overlapping orbs, each drifting on its own beat
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                LockedSoundIcons.forEachIndexed { index, icon ->
                    Box(
                        Modifier
                            .graphicsLayer { translationY = sin(phase + index * .9f) * 2.5.dp.toPx() }
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accents.accent)
                            .background(Color.White.copy(alpha = .18f))
                            .border(1.5.dp, Color.White.copy(alpha = .55f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.sound_unlock_title, lockedCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.sound_unlock_body),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = .88f),
            )

            Spacer(Modifier.height(16.dp))

            Row(
                Modifier
                    .clip(CircleShape)
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = accents.accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.sound_unlock_cta),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = accents.accent,
                )
            }
        }
    }
}
