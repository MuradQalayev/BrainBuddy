package com.muradgalayev.brainbuddy.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.muradgalayev.brainbuddy.data.repository.WeatherSnapshot
import com.muradgalayev.brainbuddy.ui.accessibility.animationsOn
import com.muradgalayev.brainbuddy.ui.calendar.resolveEventColor
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

// today as one wide strip: the date, the weather, and the shape of the day itself. the ribbon
// is the point. a list of times asks you to work out how much day is left, a bar with a marker
// crawling across it just shows you. time blindness isn't fixed by more numbers, it's helped
// by making time take up space. the weather lives here rather than in its own card because it
// was the least urgent thing on the screen taking up the most room
@Composable
fun HomeTodayCard(
    weather: WeatherSnapshot?,
    agenda: List<HomeAgendaItem>,
    now: LocalDateTime,
    modifier: Modifier = Modifier,
) {
    val accents = MaterialTheme.myndoraAccents
    val today = now.toLocalDate()
    val dateLabel = remember(today) { today.format(DateTimeFormatter.ofPattern("EEEE d MMM")) }

    // which block is being peeked at. survives rotation, and clears itself so the card is never
    // left holding a stale selection the user has stopped looking at
    var peekedId by rememberSaveable(today) { mutableStateOf<String?>(null) }
    val peeked = agenda.firstOrNull { it.id == peekedId }
    LaunchedEffect(peekedId) {
        if (peekedId != null) {
            delay(PeekHoldMillis)
            peekedId = null
        }
    }
    // an item that syncs away mid-peek shouldn't leave the line pointing at nothing. kept out of
    // the timer above so a background refresh doesn't restart the countdown
    LaunchedEffect(agenda) {
        if (peekedId != null && agenda.none { it.id == peekedId }) peekedId = null
    }

    HomeCard(modifier = modifier, accent = accents.accent) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    HomeSectionLabel("TODAY", accents.accent)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        weatherSubtitle(weather),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                WeatherChip(weather)
            }

            Spacer(Modifier.height(18.dp))
            DayRibbon(
                agenda = agenda,
                now = now,
                today = today,
                peekedId = peekedId,
                onPeek = { peekedId = it },
            )
            Spacer(Modifier.height(10.dp))
            // the peek takes the summary's place rather than sitting under it, so the card keeps its
            // height and tapping the bar never shoves the rest of Home down the screen
            val peekFade = if (animationsOn()) 180 else 0
            Box(Modifier.fillMaxWidth().heightIn(min = 20.dp), contentAlignment = Alignment.CenterStart) {
                AnimatedContent(
                    targetState = peeked,
                    transitionSpec = {
                        fadeIn(tween(peekFade)) togetherWith fadeOut(tween(peekFade))
                    },
                    label = "today_card_peek",
                ) { item ->
                    if (item == null) AgendaSummary(agenda) else AgendaPeek(item = item, now = now)
                }
            }
        }
    }
}

// how long a peek stays up before the card goes back to its summary
private const val PeekHoldMillis = 6_000L

// the day as a bar: blocks for what's booked, a marker for where you are in it, and the clock
// riding along with the marker, so 'where am I' and 'what time is it' are one glance not two
@Composable
private fun DayRibbon(
    agenda: List<HomeAgendaItem>,
    now: LocalDateTime,
    today: LocalDate,
    peekedId: String?,
    onPeek: (String?) -> Unit,
) {
    val accents = MaterialTheme.myndoraAccents
    val onSurface = MaterialTheme.colorScheme.onSurface
    val track = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .13f)
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .3f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = .6f)
    val surface = MaterialTheme.colorScheme.surfaceContainer

    // slides rather than jumps, so a glance away and back doesn't lose where the day was
    val animatedNow by animateFloatAsState(
        targetValue = ribbonFraction(now.toLocalTime()),
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "ribbon_now",
    )
    val clock = remember(now.hour, now.minute) { now.format(DateTimeFormatter.ofPattern("HH:mm")) }

    val blocks = remember(agenda, today) { ribbonBlocks(agenda, today) }
    val ribbonLabel = remember(agenda, today) { ribbonDescription(agenda, today) }

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val width = maxWidth
        val pillWidth = 52.dp

        Column {
            // the clock pill rides above the marker, clamped so it never hangs off an edge. tapping it
            // peeks at whatever you're in or heading for, so the likeliest question doesn't require
            // finding a 7dp block first
            Box(
                Modifier
                    .offset(x = markerOffset(animatedNow, width, pillWidth))
                    .width(pillWidth)
                    .clip(HomePillShape)
                    .background(onSurface)
                    .clickable(enabled = agenda.isNotEmpty()) {
                        val next = selectNextUp(agenda, now)?.id
                        onPeek(if (next == peekedId) null else next)
                    }
                    .padding(vertical = 3.dp)
                    .semantics { contentDescription = "Now $clock. Show what's on now." },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    clock,
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    fontWeight = FontWeight.Bold,
                    color = surface,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.height(6.dp))

            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(22.dp)
                    .semantics { contentDescription = ribbonLabel }
                    .pointerInput(blocks) {
                        if (blocks.isEmpty()) return@pointerInput
                        detectTapGestures { offset ->
                            val width = size.width.toFloat()
                            if (width <= 0f) return@detectTapGestures
                            onPeek(
                                pickRibbonBlock(
                                    blocks = blocks,
                                    fraction = offset.x / width,
                                    // a 15-minute event is a few dp wide, without slack the bar would only answer a precise hit
                                    tolerance = TapSlack.toPx() / width,
                                    current = peekedId,
                                )?.id,
                            )
                        }
                    },
            ) {
                val trackHeight = 12.dp.toPx()
                val top = (size.height - trackHeight) / 2f
                val radius = CornerRadius(trackHeight / 2f)
                drawRoundRect(track, Offset(0f, top), Size(size.width, trackHeight), radius)

                // quarter-day ticks, drawn under the blocks so a busy day hides them
                listOf(0f, 1f / 3f, 2f / 3f, 1f).forEach { fraction ->
                    val x = (fraction * size.width).coerceIn(0.5.dp.toPx(), size.width - 0.5.dp.toPx())
                    drawLine(
                        color = tickColor,
                        start = Offset(x, top + 3.dp.toPx()),
                        end = Offset(x, top + trackHeight - 3.dp.toPx()),
                        strokeWidth = 1.dp.toPx(),
                    )
                }

                val laneGap = 2.dp.toPx()
                blocks.forEach { block ->
                    val base = if (block.isTask) accents.accent else resolveEventColor(block.colorKey).accent
                    val left = (block.from * size.width)
                    val blockWidth = ((block.to - block.from) * size.width).coerceAtLeast(trackHeight)

                    // only a cluster that actually collides splits into lanes, the rest of the day keeps the
                    // full-height bar, so one double-booking doesn't stripe the whole ribbon
                    val laneHeight = if (block.lanes > 1) {
                        (trackHeight - laneGap * (block.lanes - 1)) / block.lanes
                    } else {
                        trackHeight
                    }
                    val laneTop = top + block.lane * (laneHeight + laneGap)

                    val alpha = when {
                        block.completed -> .28f
                        // peeking dims everything else, so the answer to 'which one is that' is on the bar as well as
                        // in the line underneath
                        peekedId != null && block.id != peekedId -> .35f
                        else -> 1f
                    }
                    drawRoundRect(
                        // top-lit like the cards, so the blocks read as objects on the track rather than holes cut in it
                        brush = Brush.verticalGradient(
                            listOf(base.copy(alpha = alpha), base.copy(alpha = alpha * .72f)),
                            startY = laneTop,
                            endY = laneTop + laneHeight,
                        ),
                        topLeft = Offset(left.coerceAtMost(size.width - blockWidth), laneTop),
                        size = Size(blockWidth, laneHeight),
                        cornerRadius = CornerRadius(laneHeight / 2f),
                    )
                }

                val x = animatedNow * size.width
                val markerWidth = 3.dp.toPx()
                val clamped = (x - markerWidth / 2f).coerceIn(0f, size.width - markerWidth)
                // a soft halo lifts the marker off whatever colour it happens to cross
                drawRoundRect(
                    color = surface.copy(alpha = .85f),
                    topLeft = Offset(clamped - 2.dp.toPx(), 0f),
                    size = Size(markerWidth + 4.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
                drawRoundRect(
                    color = onSurface,
                    topLeft = Offset(clamped, 0f),
                    size = Size(markerWidth, size.height),
                    cornerRadius = CornerRadius(markerWidth / 2f),
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(Modifier.fillMaxWidth()) {
                listOf("06", "12", "18", "00").forEachIndexed { index, hour ->
                    Text(
                        hour,
                        style = MaterialTheme.typography.labelSmall.tabular(),
                        color = labelColor,
                    )
                    if (index < 3) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

// keeps the clock pill centred on the marker without letting it leave the card
private fun markerOffset(fraction: Float, width: Dp, pillWidth: Dp): Dp {
    val centred = width * fraction - pillWidth / 2
    return centred.coerceIn(0.dp, (width - pillWidth).coerceAtLeast(0.dp))
}

// how far off a block a tap can land and still count
private val TapSlack = 12.dp

// one tapped thing, in the space the summary row was using. one line and read-only on purpose:
// it answers what that block is, when, and how long until, and stops there. anything that
// opens a screen belongs to a deliberate second tap, not to a glance
@Composable
private fun AgendaPeek(item: HomeAgendaItem, now: LocalDateTime) {
    val accents = MaterialTheme.myndoraAccents
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val dot = if (item.kind == AgendaKind.Task) accents.accent else resolveEventColor(item.colorKey).accent
    val place = item.location.takeIf { it.isNotBlank() }

    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(7.dp).clip(HomePillShape).background(dot))
        Text(
            if (place == null) item.title else "${item.title} · $place",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            // an untimed task has no range worth printing, 'Anytime today' already says it
            if (item.start == null) {
                countdownLabel(item, now)
            } else {
                "${timeRangeLabel(item)} · ${countdownLabel(item, now)}"
            },
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = muted,
            maxLines = 1,
        )
    }
}

// what the bar says to a screen reader, which otherwise sees an empty Canvas
private fun ribbonDescription(agenda: List<HomeAgendaItem>, today: LocalDate): String {
    val timed = agenda
        .filter { it.start?.toLocalDate() == today }
        .sortedBy { it.start }
    if (timed.isEmpty()) return "Day ribbon. Nothing scheduled today."
    val spoken = timed.take(RibbonSpokenItems)
        .joinToString(". ") { "${it.title}, ${timeRangeLabel(it)}" }
    val rest = timed.size - RibbonSpokenItems
    val tail = if (rest > 0) ". And $rest more." else "."
    return "Day ribbon. $spoken$tail"
}

// reading out a whole day is worse than a summary, the rest is counted
private const val RibbonSpokenItems = 4

// dotted keys rather than a sentence: faster to scan, and it colour-matches the bar
@Composable
private fun AgendaSummary(agenda: List<HomeAgendaItem>) {
    val accents = MaterialTheme.myndoraAccents
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val open = agenda.filterNot { it.completed }
    val events = open.count { it.kind == AgendaKind.Event }
    val tasks = open.count { it.kind == AgendaKind.Task }
    val done = agenda.count { it.completed }

    if (agenda.isEmpty()) {
        Text(
            "Nothing on today",
            style = MaterialTheme.typography.labelMedium,
            color = muted,
            maxLines = 1,
        )
        return
    }
    if (open.isEmpty()) {
        Text(
            "All clear — everything's done",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = accents.support,
            maxLines = 1,
        )
        return
    }

    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
        if (events > 0) SummaryKey(events, if (events == 1) "event" else "events", resolveEventColor(null).accent)
        if (tasks > 0) SummaryKey(tasks, if (tasks == 1) "task" else "tasks", accents.accent)
        if (done > 0) SummaryKey(done, "done", muted.copy(alpha = .5f))
    }
}

@Composable
private fun SummaryKey(count: Int, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(7.dp).clip(HomePillShape).background(color))
        Text(
            "$count $label",
            style = MaterialTheme.typography.labelMedium.tabular(),
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun WeatherChip(weather: WeatherSnapshot?) {
    val dark = MaterialTheme.colorScheme.background.luminance() < .5f
    val sunny = weather?.weatherCode?.let { it == 0 || it == 1 } == true
    val gradient = when {
        dark -> listOf(Color(0xFF2C4152), Color(0xFF1D2934))
        sunny -> listOf(Color(0xFFFFD67A), Color(0xFFFFA86B))
        else -> listOf(Color(0xFFA9C9DF), Color(0xFF759DBA))
    }
    val ink = if (dark) Color.White else Color(0xFF25313A)

    Row(
        Modifier
            .clip(HomeInnerShape)
            .background(Brush.linearGradient(gradient))
            .border(1.dp, Color.White.copy(alpha = if (dark) .10f else .26f), HomeInnerShape)
            .padding(start = 12.dp, end = 14.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (sunny) Icons.Rounded.WbSunny else Icons.Rounded.Cloud,
            null,
            tint = if (sunny) Color(0xFFFFF3BF) else Color.White.copy(alpha = .92f),
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(9.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                weather?.let { "${it.temperatureC.toInt()}°" } ?: "—°",
                style = MaterialTheme.typography.titleMedium.tabular(),
                fontWeight = FontWeight.Bold,
                color = ink,
                maxLines = 1,
            )
            if (weather != null) {
                Text(
                    "H ${weather.highC.toInt()}  L ${weather.lowC.toInt()}",
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = ink.copy(alpha = .78f),
                    letterSpacing = .3.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun weatherSubtitle(weather: WeatherSnapshot?): String {
    weather ?: return "Set your location for weather"
    return "${weather.city} · ${weatherDescription(weather.weatherCode)}"
}

internal fun weatherDescription(code: Int): String = when (code) {
    0 -> "Clear sky"
    1, 2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Foggy"
    in 51..67, in 80..82 -> "Rain"
    in 71..77, 85, 86 -> "Snow"
    in 95..99 -> "Thunderstorm"
    else -> "Current weather"
}
