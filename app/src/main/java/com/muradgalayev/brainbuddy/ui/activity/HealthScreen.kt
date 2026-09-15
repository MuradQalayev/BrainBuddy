package com.muradgalayev.brainbuddy.ui.activity

import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.data.health.HealthConnectUiState
import com.muradgalayev.brainbuddy.domain.model.Medication
import java.time.LocalDate

private val MedicationAccent = Color(0xFF18A9D1)
private val HealthAccent = Color(0xFFFF4B6E)

// health: two doors, and nothing else. the two halves of health in this app have almost
// nothing in common. medication is something you do, several times a day on a schedule you
// set, and forgetting whether you did it is one of the most common and most consequential
// ADHD failures there is. Health Connect is something you read: numbers a watch collected
// while you weren't thinking about them.
// stacking them in one scrolling feed, as this page used to, meant the thing you act on was
// buried among the things you merely look at. so this page's job is to be a fork in the road,
// with each card carrying the one number that says whether you need to go in at all
@Composable
fun HealthScreen(
    onBack: () -> Unit,
    onMedications: () -> Unit,
    onHealthConnect: () -> Unit,
    viewModel: WorkspaceViewModel = hiltViewModel(),
) {
    val healthState by viewModel.healthState.collectAsState()
    val medications by viewModel.medications.collectAsState()
    val doseLogs by viewModel.medicationDoseLogs.collectAsState()

    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        viewModel.refreshHealth()
    }

    val today = remember { LocalDate.now() }
    val scheduled = remember(medications, today) {
        medications.filter { it.isScheduledOn(today.dayOfWeek) }.sumOf { it.slots.distinct().size }
    }
    val taken = remember(doseLogs, today, scheduled) {
        doseLogs.count { it.startsWith("$today|") }.coerceAtMost(scheduled)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WellnessBackButton(onBack)
            Text(
                stringResource(R.string.widget_health),
                Modifier.weight(1f).padding(start = 14.dp),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(HealthAccent.copy(alpha = .14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Favorite,
                    contentDescription = null,
                    tint = HealthAccent,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        Spacer(Modifier.height(22.dp))

        HealthDoor(
            accent = MedicationAccent,
            icon = { Icon(Icons.Rounded.Medication, null, tint = MedicationAccent, modifier = Modifier.size(22.dp)) },
            title = stringResource(R.string.ws_medications),
            line = when {
                medications.isEmpty() -> stringResource(R.string.health_add_meds)
                scheduled == 0 -> stringResource(R.string.health_nothing_today)
                taken == scheduled -> stringResource(R.string.health_all_taken, scheduled)
                else -> stringResource(R.string.health_taken_of, taken, scheduled)
            },
            onClick = onMedications,
        ) {
            if (scheduled > 0) DoseDots(taken = taken, total = scheduled)
        }

        Spacer(Modifier.height(12.dp))

        HealthDoor(
            accent = HealthAccent,
            icon = { Icon(Icons.Rounded.Favorite, null, tint = HealthAccent, modifier = Modifier.size(20.dp)) },
            title = stringResource(R.string.settings_health_connect),
            line = healthSummaryLine(healthState),
            onClick = onHealthConnect,
        ) {
            if (healthState.connected) MiniRings(healthState)
        }
    }
}

// one number, chosen so the card answers 'do I need to open this?' on its own
@Composable
private fun healthSummaryLine(state: HealthConnectUiState): String {
    if (!state.connected) return stringResource(R.string.health_connect_line)
    val steps = state.todaySteps?.let { stringResource(R.string.health_steps_count, "%,d".format(it.toInt())) }
    val sleep = state.lastSleepHours?.let { stringResource(R.string.health_sleep_hours, "%.1f".format(it)) }
    return listOfNotNull(steps, sleep).joinToString(" · ").ifEmpty { stringResource(R.string.health_no_readings) }
}

@Composable
private fun HealthDoor(
    accent: Color,
    icon: @Composable () -> Unit,
    title: String,
    line: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        onClick = onClick,
    ) {
        Column(
            Modifier
                .background(
                    Brush.verticalGradient(listOf(accent.copy(alpha = .10f), Color.Transparent))
                )
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(accent.copy(alpha = .14f)),
                    contentAlignment = Alignment.Center,
                ) { icon() }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(14.dp))
            trailing()
        }
    }
}

// today's doses as pips, filled for taken and hollow for still due. a count says '2 of 3',
// the pips say it without reading, which is the difference between glancing and parsing
@Composable
private fun DoseDots(taken: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total.coerceAtMost(12)) { index ->
            Box(
                Modifier
                    .size(width = if (index < taken) 22.dp else 22.dp, height = 6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(
                        if (index < taken) MedicationAccent
                        else MedicationAccent.copy(alpha = .18f)
                    )
            )
        }
    }
}

// the three activity rings at glance size, same colours as the full page they open
@Composable
private fun MiniRings(state: HealthConnectUiState) {
    val values = listOf(
        ((state.todaySteps ?: 0).toFloat() / 8_000f).coerceIn(0f, 1f),
        ((state.exerciseMinutesThisWeek ?: 0).toFloat() / 30f).coerceIn(0f, 1f),
        ((state.caloriesBurnedToday ?: 0).toFloat() / 500f).coerceIn(0f, 1f),
    )
    val colors = listOf(Color(0xFFFF2D55), Color(0xFF22C733), Color(0xFF00AEEA))

    Canvas(Modifier.size(46.dp)) {
        values.forEachIndexed { index, progress ->
            val inset = index * 7.dp.toPx()
            val arcSize = androidx.compose.ui.geometry.Size(
                size.width - inset * 2,
                size.height - inset * 2,
            )
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(
                colors[index].copy(alpha = .16f), -90f, 360f, false,
                topLeft = topLeft, size = arcSize,
                style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
            )
            if (progress > 0f) {
                drawArc(
                    colors[index], -90f, progress * 360f, false,
                    topLeft = topLeft, size = arcSize,
                    style = Stroke(4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }
    }
}

// doses scheduled for a date, for callers that need the same count this page shows
fun scheduledDoseCount(medications: List<Medication>, date: LocalDate): Int =
    medications.filter { it.isScheduledOn(date.dayOfWeek) }.sumOf { it.slots.distinct().size }
