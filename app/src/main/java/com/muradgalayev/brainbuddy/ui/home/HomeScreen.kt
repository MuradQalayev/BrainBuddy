package com.muradgalayev.brainbuddy.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.muradgalayev.brainbuddy.ui.calendar.rememberCalendarPalette
import com.muradgalayev.brainbuddy.ui.components.AiPromptCard
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        TodayDateCard(modifier = Modifier.fillMaxWidth(0.62f))
        Spacer(modifier = Modifier.height(24.dp))
        AiPromptCard(embedded = true)
    }
}

@Composable
private fun TodayDateCard(modifier: Modifier = Modifier) {
    val palette = rememberCalendarPalette()
    val today = remember { LocalDate.now() }
    val weekday = remember(today) {
        today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    }
    val month = remember(today) {
        today.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }
    val dayNumber = remember(today) { today.dayOfMonth.toString().padStart(2, '0') }
    val year = remember(today) { today.year.toString() }

    Surface(
        modifier = modifier
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(24.dp),
                ambientColor = palette.lavender.copy(alpha = 0.18f),
                spotColor = palette.lavender.copy(alpha = 0.22f)
            ),
        shape = RoundedCornerShape(24.dp),
        color = palette.cardBg,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(palette.lavenderSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.CalendarToday,
                    contentDescription = null,
                    tint = palette.lavender,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "$dayNumber $month $year",
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.ink,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(
                    text = weekday,
                    style = MaterialTheme.typography.labelMedium,
                    color = palette.lavender,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
