package com.muradgalayev.brainbuddy.ui.plan

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.accessibility.speaking

// the silver ramp the Plus mark is made of. fixed rather than theme-derived: it has to read as
// the same piece of metal on a white card, a dark card and the accent gradient of the hero
private val SilverHigh = Color(0xFFFCFCFD)
private val SilverLight = Color(0xFFE7EAEF)
private val SilverMid = Color(0xFFC3C9D2)
private val SilverDeep = Color(0xFF98A0AD)
private val SilverInk = Color(0xFF39404E)

// brushed metal: a light face with a darker lower-right edge, so the pill has a direction
fun silverBrush(): Brush = Brush.linearGradient(
    0f to SilverLight,
    0.28f to SilverHigh,
    0.52f to SilverMid,
    0.78f to SilverLight,
    1f to SilverDeep,
)

private fun silverEdge(): Brush = Brush.linearGradient(
    listOf(Color.White.copy(alpha = 0.95f), SilverDeep.copy(alpha = 0.8f)),
)

// the small silver Plus mark. sits beside a name, in the hero, anywhere membership is worth
// stating quietly. the sheen crosses it every few seconds and is still the rest of the time,
// which is what keeps it a mark and not a flashing badge
@Composable
fun PlusBadge(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val label = stringResource(R.string.plan_badge_plus)
    val description = stringResource(R.string.plan_badge_cd)
    val sheen = rememberSheenSweep()
    val shape = RoundedCornerShape(50)

    Row(
        modifier = modifier
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(role = Role.Button, onClick = speaking(description, onClick))
                } else {
                    Modifier
                }
            )
            .background(silverBrush(), shape)
            .border(0.8.dp, silverEdge(), shape)
            .drawWithContent {
                drawContent()
                sheen?.value?.let { drawSheen(it) }
            }
            .padding(horizontal = 7.dp, vertical = 2.dp)
            .semantics(mergeDescendants = true) { contentDescription = description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.AutoAwesome,
            contentDescription = null,
            tint = SilverInk,
            modifier = Modifier.size(9.dp),
        )
        Spacer(Modifier.width(3.dp))
        Text(
            text = label,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 1.1.sp,
            color = SilverInk,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

// mostly parked, then one quick pass across. null under reduce motion, where silver is just
// silver. shared with the hero and the join button so every Plus surface catches the light the
// same way
@Composable
internal fun rememberSheenSweep(idleMs: Int = 3300, sweepMs: Int = 1300): State<Float>? {
    if (!animationsOn()) return null
    val transition = rememberInfiniteTransition(label = "plus_sheen")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = idleMs + sweepMs
                0f at 0 using LinearEasing
                0f at idleMs using LinearEasing
                1f at idleMs + sweepMs using LinearEasing
            },
            repeatMode = RepeatMode.Restart,
        ),
        label = "plus_sheen_progress",
    )
}

// a diagonal highlight band crossing left to right. the clip above it keeps it inside the pill
internal fun DrawScope.drawSheen(progress: Float, strength: Float = 0.85f) {
    if (progress <= 0f || progress >= 1f) return
    val band = size.width * 0.5f
    val x = -band + progress * (size.width + band * 2)
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(Color.Transparent, Color.White.copy(alpha = strength), Color.Transparent),
            start = Offset(x, 0f),
            end = Offset(x + band, size.height),
        ),
    )
}
