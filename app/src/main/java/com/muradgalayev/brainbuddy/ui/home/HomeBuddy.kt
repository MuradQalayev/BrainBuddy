package com.muradgalayev.brainbuddy.ui.home

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.PI
import kotlin.math.sin

// the companion on the progress tile, a small creature that wakes up as the day goes.
// the obvious reference for a character like this is the Duolingo owl, and the obvious
// reference is the thing to avoid. that owl works by guilt: it gets sad, it gets disappointed
// in you, it dies if you stop. on an ADHD app that mechanic is actively harmful, because the
// days you most need to open the app are the days it would be saddest.
// same idea with the punishment taken out:
// - never sad, hurt or disappointed. there is no frowning pose in this file at all, and the
//   nothing-done state is asleep, which is something a creature does happily and on purpose.
// - nothing decays. mood is a pure function of what you finished today, so it can't go
//   backwards, and tomorrow it starts asleep again. no streak to break, no health to run down.
// - idle motion stays under the threshold of distraction. on a screen someone glances at forty
//   times a day a bouncing character is forty interruptions, so it breathes slowly and blinks
//   rarely, and the big reactions are saved for the full-screen view.
// - the reward is immediate and disproportionate: finishing something makes it hop the instant
//   the count changes. the gap between doing a thing and feeling anything for it is exactly
//   where ADHD motivation falls apart.
// - rememberMotionEnabled is honoured, so system-wide reduce-animations leaves a still but
//   complete character rather than a broken one
enum class BuddyMood { Resting, Stirring, Awake, Beaming }

// mood from what's done, never from what's outstanding. an empty day and an untouched day
// deliberately land on the same Resting: the creature has no way to tell the difference and
// no opinion about it either way
fun buddyMood(done: Int, total: Int): BuddyMood = when {
    total <= 0 || done <= 0 -> BuddyMood.Resting
    done >= total -> BuddyMood.Beaming
    done * 2 >= total -> BuddyMood.Awake
    else -> BuddyMood.Stirring
}

// false when the user has turned animations off system-wide
@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) > 0f
    }
}

// lively is the full-screen treatment: bigger breathing, sleep z's, sparkles when beaming.
// the tile passes false and stays close to still
@Composable
fun HomeBuddy(
    done: Int,
    total: Int,
    bodyStart: Color,
    bodyEnd: Color,
    modifier: Modifier = Modifier,
    lively: Boolean = false,
) {
    val mood = buddyMood(done, total)
    val motion = rememberMotionEnabled()
    val measurer = rememberTextMeasurer()

    // ink that stays legible whatever accent the user's theme lands on
    val ink = if (bodyStart.luminance() < .42f) Color(0xFFF4F3FF) else Color(0xFF1B1B2F)

    // how awake it looks. continuous rather than stepped, so a win in the middle of the day eases
    // the whole face open instead of snapping between two drawings
    val wakeTarget = when (mood) {
        BuddyMood.Resting -> 0f
        BuddyMood.Stirring -> .55f
        BuddyMood.Awake -> .85f
        BuddyMood.Beaming -> 1f
    }
    val wake by animateFloatAsState(
        targetValue = wakeTarget,
        animationSpec = tween(900, easing = FastOutSlowInEasing),
        label = "buddy_wake",
    )
    val joy by animateFloatAsState(
        targetValue = if (mood == BuddyMood.Beaming) 1f else wakeTarget * .7f,
        animationSpec = tween(700, easing = FastOutSlowInEasing),
        label = "buddy_joy",
    )

    val transition = rememberInfiniteTransition(label = "buddy_idle")
    val breathRaw by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        // slower asleep than awake. a sleeping thing that breathes at waking pace looks anxious
        animationSpec = infiniteRepeatable(
            tween(if (lively) 3600 else 4600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "buddy_breath",
    )
    val blinkRaw by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 5400
                1f at 0
                1f at 4900
                .05f at 5020
                1f at 5160
            },
            RepeatMode.Restart,
        ),
        label = "buddy_blink",
    )
    val sparkleRaw by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart),
        label = "buddy_sparkle",
    )
    val breath = if (motion) breathRaw else .5f
    val blink = if (motion) blinkRaw else 1f
    val sparkle = if (motion) sparkleRaw else .35f

    // the payoff. fires the moment the count goes up and nowhere else, not on first composition,
    // so returning to the tab doesn't fake a celebration you didn't earn
    val hop = remember { Animatable(0f) }
    var lastDone by remember { mutableIntStateOf(done) }
    LaunchedEffect(done) {
        if (done > lastDone && motion) {
            hop.snapTo(0f)
            hop.animateTo(1f, tween(620, easing = LinearEasing))
        }
        lastDone = done
    }

    Canvas(modifier) {
        val unit = minOf(size.width, size.height)
        val halfWidth = unit * .30f
        val halfHeight = unit * .255f
        // sits lower when asleep, the whole body settles towards the ground
        val centreY = size.height * .56f + halfHeight * (.12f - .12f * wake)
        val centreX = size.width / 2f

        val bounce = sin(hop.value * PI).toFloat()
        val lift = bounce * unit * .085f
        val breathe = (breath - .5f) * 2f      // -1..1
        val stretchY = 1f + .075f * bounce + .018f * breathe * (.4f + .6f * wake)
        val stretchX = 1f - .06f * bounce - .014f * breathe * (.4f + .6f * wake)
        // asleep it's a flatter puddle, awake it stands up
        val poseX = 1.10f - .10f * wake
        val poseY = .86f + .14f * wake

        val bodyHalfWidth = halfWidth * stretchX * poseX
        val bodyHalfHeight = halfHeight * stretchY * poseY
        val bodyCentre = Offset(centreX, centreY - lift)

        // contact shadow. shrinks as it leaves the ground, which is most of what sells a jump
        drawOval(
            color = ink.copy(alpha = .10f * (1f - bounce * .6f)),
            topLeft = Offset(
                centreX - bodyHalfWidth * (.92f - bounce * .18f),
                centreY + bodyHalfHeight * .84f,
            ),
            size = Size(
                bodyHalfWidth * 2f * (.92f - bounce * .18f),
                bodyHalfHeight * .30f,
            ),
        )

        drawAntenna(
            centre = bodyCentre,
            halfWidth = bodyHalfWidth,
            halfHeight = bodyHalfHeight,
            unit = unit,
            sway = breathe * (2.5f + 6f * wake) + bounce * 14f,
            colour = bodyEnd,
            wake = wake,
        )

        val body = blobPath(bodyCentre, bodyHalfWidth, bodyHalfHeight)
        drawPath(
            path = body,
            brush = Brush.verticalGradient(
                colors = listOf(bodyStart, bodyEnd),
                startY = bodyCentre.y - bodyHalfHeight,
                endY = bodyCentre.y + bodyHalfHeight,
            ),
        )
        // light from above-left, clipped to the body so it reads as a surface rather than a sticker
        // sitting on top of one
        clipPath(body) {
            drawOval(
                color = Color.White.copy(alpha = .20f),
                topLeft = Offset(
                    bodyCentre.x - bodyHalfWidth * .86f,
                    bodyCentre.y - bodyHalfHeight * 1.02f,
                ),
                size = Size(bodyHalfWidth * 1.25f, bodyHalfHeight * .78f),
            )
        }

        drawFace(
            centre = bodyCentre,
            halfWidth = bodyHalfWidth,
            halfHeight = bodyHalfHeight,
            wake = wake,
            joy = joy,
            blink = blink,
            ink = ink,
            unit = unit,
        )

        if (lively && wake < .35f) {
            drawSleepZs(
                measurer = measurer,
                centre = bodyCentre,
                halfWidth = bodyHalfWidth,
                halfHeight = bodyHalfHeight,
                unit = unit,
                phase = sparkle,
                colour = ink.copy(alpha = .55f),
            )
        }

        if (lively && mood == BuddyMood.Beaming) {
            drawJoySparkles(bodyCentre, bodyHalfWidth, bodyHalfHeight, unit, sparkle, bodyEnd)
        }
    }
}

// a soft egg, wider at the bottom. four cubics, a plain circle reads as a ball not a body
private fun blobPath(centre: Offset, halfWidth: Float, halfHeight: Float): Path = Path().apply {
    val k = .5523f
    moveTo(centre.x, centre.y - halfHeight)
    cubicTo(
        centre.x + halfWidth * k * 1.18f, centre.y - halfHeight,
        centre.x + halfWidth, centre.y - halfHeight * k * .92f,
        centre.x + halfWidth, centre.y,
    )
    cubicTo(
        centre.x + halfWidth, centre.y + halfHeight * k * 1.06f,
        centre.x + halfWidth * k * .88f, centre.y + halfHeight,
        centre.x, centre.y + halfHeight,
    )
    cubicTo(
        centre.x - halfWidth * k * .88f, centre.y + halfHeight,
        centre.x - halfWidth, centre.y + halfHeight * k * 1.06f,
        centre.x - halfWidth, centre.y,
    )
    cubicTo(
        centre.x - halfWidth, centre.y - halfHeight * k * .92f,
        centre.x - halfWidth * k * 1.18f, centre.y - halfHeight,
        centre.x, centre.y - halfHeight,
    )
    close()
}

// a stalk and a lit bulb. droops while it sleeps, stands up and glows as it wakes
private fun DrawScope.drawAntenna(
    centre: Offset,
    halfWidth: Float,
    halfHeight: Float,
    unit: Float,
    sway: Float,
    colour: Color,
    wake: Float,
) {
    val root = Offset(centre.x + halfWidth * .10f, centre.y - halfHeight * .96f)
    val length = halfHeight * (.34f + .34f * wake)
    val tip = Offset(root.x + sway * unit * .0035f, root.y - length)
    val stalk = Path().apply {
        moveTo(root.x, root.y)
        quadraticBezierTo(
            root.x - length * .22f + sway * unit * .0018f,
            root.y - length * .55f,
            tip.x, tip.y,
        )
    }
    drawPath(stalk, colour.copy(alpha = .85f), style = Stroke(width = unit * .014f, cap = StrokeCap.Round))
    val bulb = unit * (.021f + .009f * wake)
    drawCircle(colour.copy(alpha = .28f * wake), radius = bulb * 2.4f, center = tip)
    drawCircle(colour, radius = bulb, center = tip)
}

// eyes and mouth. the closed-eye arc and the open eye cross-fade rather than switch, so waking
// up is a continuous movement. there is no unhappy mouth in here, the curve only ever varies
// between a small content one and a wide one
private fun DrawScope.drawFace(
    centre: Offset,
    halfWidth: Float,
    halfHeight: Float,
    wake: Float,
    joy: Float,
    blink: Float,
    ink: Color,
    unit: Float,
) {
    val eyeGap = halfWidth * .40f
    val eyeY = centre.y - halfHeight * .10f
    val eyeRadius = halfHeight * .17f
    val open = (wake.coerceIn(0f, 1f) * blink).coerceIn(0f, 1f)

    listOf(-1f, 1f).forEach { side ->
        val eyeX = centre.x + side * eyeGap

        // closed lid: a shallow arc, strongest when asleep or mid-blink
        val closed = 1f - open
        if (closed > .01f) {
            drawArc(
                color = ink.copy(alpha = .82f * closed),
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(eyeX - eyeRadius * 1.15f, eyeY - eyeRadius * .75f),
                size = Size(eyeRadius * 2.3f, eyeRadius * 1.5f),
                style = Stroke(width = unit * .012f, cap = StrokeCap.Round),
            )
        }
        if (open > .01f) {
            drawOval(
                color = ink.copy(alpha = .88f * open),
                topLeft = Offset(eyeX - eyeRadius, eyeY - eyeRadius * open),
                size = Size(eyeRadius * 2f, eyeRadius * 2f * open),
            )
            drawCircle(
                color = Color.White.copy(alpha = .85f * open),
                radius = eyeRadius * .34f * open,
                center = Offset(eyeX - eyeRadius * .30f, eyeY - eyeRadius * .34f * open),
            )
        }

        // blush, and only ever as an addition: it fades in with joy, it never turns pale
        if (joy > .3f) {
            drawCircle(
                color = Color(0xFFFF8FA3).copy(alpha = .30f * (joy - .3f) / .7f),
                radius = eyeRadius * .78f,
                center = Offset(eyeX + side * eyeRadius * .95f, eyeY + eyeRadius * 1.5f),
            )
        }
    }

    // mouth: content when asleep, wide open when beaming. never inverted
    val mouthWidth = halfWidth * (.20f + .16f * joy)
    val mouthY = centre.y + halfHeight * .30f
    val depth = halfHeight * (.10f + .30f * joy)
    val mouth = Path().apply {
        moveTo(centre.x - mouthWidth, mouthY)
        quadraticBezierTo(centre.x, mouthY + depth, centre.x + mouthWidth, mouthY)
    }
    drawPath(
        path = mouth,
        color = ink.copy(alpha = .80f),
        style = Stroke(width = unit * .015f, cap = StrokeCap.Round),
    )
}

// three z's drifting up and out. only in the full view, the tile is too small to hold them
private fun DrawScope.drawSleepZs(
    measurer: androidx.compose.ui.text.TextMeasurer,
    centre: Offset,
    halfWidth: Float,
    halfHeight: Float,
    unit: Float,
    phase: Float,
    colour: Color,
) {
    repeat(3) { index ->
        val local = ((phase + index * .33f) % 1f)
        val rise = local * halfHeight * 1.5f
        // fades in and back out, so none of them pops out of existence mid-air
        val alpha = (sin(local * PI).toFloat()).coerceIn(0f, 1f)
        val scale = .6f + local * .7f
        val style = TextStyle(
            color = colour.copy(alpha = colour.alpha * alpha),
            fontSize = (unit * .075f * scale).toSp(),
            fontWeight = FontWeight.Bold,
        )
        val laid = measurer.measure("z", style)
        drawText(
            textLayoutResult = laid,
            topLeft = Offset(
                centre.x + halfWidth * (.55f + local * .35f),
                centre.y - halfHeight * 1.15f - rise,
            ),
        )
    }
}

// four-point sparkles around a finished day. slow rotation, no bursts, nothing strobing
private fun DrawScope.drawJoySparkles(
    centre: Offset,
    halfWidth: Float,
    halfHeight: Float,
    unit: Float,
    phase: Float,
    colour: Color,
) {
    val spots = listOf(
        Offset(-1.25f, -.85f) to 0f,
        Offset(1.30f, -.60f) to .35f,
        Offset(-1.05f, .45f) to .62f,
        Offset(1.15f, .55f) to .84f,
    )
    spots.forEach { (spot, offsetPhase) ->
        val local = (phase + offsetPhase) % 1f
        val pulse = sin(local * PI).toFloat()
        if (pulse <= .02f) return@forEach
        val at = Offset(centre.x + spot.x * halfWidth, centre.y + spot.y * halfHeight)
        val arm = unit * .028f * (.5f + .5f * pulse)
        val paint = colour.copy(alpha = .75f * pulse)
        rotate(degrees = local * 60f, pivot = at) {
            drawLine(paint, Offset(at.x - arm, at.y), Offset(at.x + arm, at.y), unit * .006f, StrokeCap.Round)
            drawLine(paint, Offset(at.x, at.y - arm), Offset(at.x, at.y + arm), unit * .006f, StrokeCap.Round)
        }
    }
}
