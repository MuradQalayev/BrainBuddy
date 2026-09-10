package com.muradgalayev.brainbuddy.ui.onboarding

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ChecklistHeader(completedCount: Int, total: Int) {
    Column {
        Text("Let's set up", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Complete your checklist below.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = .12f)) {
            Text("$completedCount of $total done", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun ChecklistStep(
    number: Int,
    title: String,
    subtitle: String?,
    completed: Boolean,
    expanded: Boolean,
    isLast: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onToggle).padding(vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(28.dp)) {
                androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                    if (completed) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                    else Text(number.toString(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(title, Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
        }
        AnimatedVisibility(expanded) {
            Column(Modifier.padding(start = 40.dp, bottom = 18.dp)) {
                subtitle?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.height(12.dp)) }
                content()
            }
        }
        if (!isLast) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = .6f))
    }
}
