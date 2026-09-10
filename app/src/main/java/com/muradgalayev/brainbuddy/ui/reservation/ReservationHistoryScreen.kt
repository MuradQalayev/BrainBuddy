package com.muradgalayev.brainbuddy.ui.reservation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.domain.model.CalendarEvent
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
fun ReservationHistoryScreen(
    onBack: () -> Unit,
    viewModel: ReservationHistoryViewModel = hiltViewModel(),
) {
    val appointments by viewModel.appointments.collectAsState()
    val now = LocalDateTime.now()
    val upcoming = appointments.filter { runCatching { LocalDateTime.parse(it.startTime) >= now }.getOrDefault(false) }
    val past = appointments.filterNot { it in upcoming }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Column(Modifier.padding(start = 6.dp)) {
                Text("Your appointments", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text("Reservation history", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (appointments.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(Modifier.size(72.dp), CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary) }
                    }
                    Text("No appointments yet", Modifier.padding(top = 16.dp), fontWeight = FontWeight.Bold)
                    Text("Appointments you add will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (upcoming.isNotEmpty()) {
                    item { HistoryLabel("Upcoming", upcoming.size) }
                    items(upcoming, key = { it.id }) { AppointmentCard(it, true) }
                }
                if (past.isNotEmpty()) {
                    item { Spacer(Modifier.size(8.dp)); HistoryLabel("Past", past.size) }
                    items(past, key = { it.id }) { AppointmentCard(it, false) }
                }
            }
        }
    }
}

@Composable
private fun HistoryLabel(title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Surface(Modifier.padding(start = 8.dp), CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(count.toString(), Modifier.padding(horizontal = 9.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AppointmentCard(event: CalendarEvent, upcoming: Boolean) {
    val dateTime = runCatching { LocalDateTime.parse(event.startTime) }.getOrNull()
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (upcoming) MaterialTheme.colorScheme.primaryContainer.copy(alpha = .55f)
        else MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(Modifier.size(54.dp), RoundedCornerShape(17.dp), color = MaterialTheme.colorScheme.surface) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(dateTime?.format(DateTimeFormatter.ofPattern("MMM"))?.uppercase().orEmpty(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(dateTime?.dayOfMonth?.toString().orEmpty(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
                }
            }
            Column(Modifier.weight(1f).padding(start = 14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(event.title, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(dateTime?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "", Modifier.padding(start = 5.dp), style = MaterialTheme.typography.bodySmall)
                }
                if (event.location.isNotBlank()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.LocationOn, null, Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(event.location, Modifier.padding(start = 5.dp), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                }
            }
            Text(if (upcoming) "Planned" else "Past", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
