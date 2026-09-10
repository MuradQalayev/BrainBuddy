package com.muradgalayev.brainbuddy.ui.sharedcomponents

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun TimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val context = LocalContext.current
    val is24Hour = android.text.format.DateFormat.is24HourFormat(context)
    var totalMinutes by remember {
        mutableIntStateOf(initialHour.coerceIn(0, 23) * 60 + initialMinute.coerceIn(0, 59))
    }
    val hour = totalMinutes / 60
    val minute = totalMinutes % 60
    val formatter = remember(is24Hour) {
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a")
    }
    val presets = remember(title) {
        when {
            title.contains("bed", ignoreCase = true) -> listOf(21 * 60, 22 * 60, 23 * 60)
            title.contains("wake", ignoreCase = true) -> listOf(6 * 60, 7 * 60, 8 * 60)
            else -> listOf(9 * 60, 12 * 60, 15 * 60, 18 * 60)
        }
    }
    fun adjust(minutes: Int) {
        totalMinutes = (totalMinutes + minutes + 1440) % 1440
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier.fillMaxSize().clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp, bottomStart = 22.dp, bottomEnd = 22.dp),
                color = colors.surfaceContainerHigh,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.size(width = 42.dp, height = 4.dp)
                            .clip(CircleShape)
                            .background(colors.onSurfaceVariant.copy(alpha = .28f)),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Slide through your day or use a quick time",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(18.dp))

                    Text(
                        LocalTime.of(hour, minute).format(formatter),
                        fontSize = 48.sp,
                        lineHeight = 54.sp,
                        fontWeight = FontWeight.Black,
                        color = colors.primary,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TimeAdjustButton("Minus 15 minutes", Icons.Rounded.Remove) { adjust(-15) }
                        Slider(
                            value = totalMinutes.toFloat(),
                            onValueChange = { totalMinutes = ((it / 5f).roundToInt() * 5).coerceIn(0, 1435) },
                            valueRange = 0f..1435f,
                            modifier = Modifier.weight(1f),
                        )
                        TimeAdjustButton("Add 15 minutes", Icons.Rounded.Add) { adjust(15) }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("00", "06", "12", "18", "24").forEach {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        presets.forEach { preset ->
                            val selected = totalMinutes == preset
                            Surface(
                                modifier = Modifier.weight(1f).clickable { totalMinutes = preset },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) colors.primary else colors.surfaceContainerHighest,
                            ) {
                                Text(
                                    LocalTime.of(preset / 60, preset % 60).format(formatter),
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    color = if (selected) colors.onPrimary else colors.onSurface,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(17.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceContainerHighest),
                            elevation = null,
                        ) { Text("Cancel", color = colors.onSurface, fontWeight = FontWeight.SemiBold) }
                        Button(
                            onClick = { onConfirm(hour, minute) },
                            modifier = Modifier.weight(1.45f).height(52.dp),
                            shape = RoundedCornerShape(17.dp),
                        ) { Text("Use this time", fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeAdjustButton(
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, description, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}
