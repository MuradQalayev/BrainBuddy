package com.muradgalayev.brainbuddy.ui.pomodoro

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.muradgalayev.brainbuddy.ui.accessibility.speaking
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

// lengths worth one tap. anything else is what the timer's own picker is for
private val DURATIONS = listOf(15, 25, 45, 50, 60)

// setting up a shared session: how long, and with whom. tapping a name used to create the room
// instantly at whatever length the timer happened to be showing, which made the most
// consequential part of the invite, the length the other person is agreeing to, an invisible
// side effect of unrelated state. two decisions, stated, then one deliberate Create.
// connections who haven't granted Focus together are listed and greyed rather than hidden:
// leaving them out looked like the app had lost them, and gave no hint there's a switch
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateFocusRoomSheet(
    people: List<FocusCandidate>,
    defaultMinutes: Int,
    sending: Boolean,
    onCreate: (userIds: List<String>, minutes: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colors = MaterialTheme.colorScheme

    var minutes by remember {
        mutableIntStateOf(DURATIONS.minByOrNull { kotlin.math.abs(it - defaultMinutes) } ?: 25)
    }
    var selected by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .navigationBarsPadding(),
        ) {
            Text(
                text = stringResource(R.string.together_scope_focus),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.focus_both_tap),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )

            Spacer(Modifier.height(22.dp))
            SectionLabel(stringResource(R.string.focus_how_long))
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DURATIONS.forEach { value ->
                    DurationChip(
                        minutes = value,
                        selected = value == minutes,
                        onClick = { minutes = value },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            SectionLabel(stringResource(R.string.focus_with_who))
            Spacer(Modifier.height(6.dp))

            if (people.isEmpty()) {
                Text(
                    text = stringResource(R.string.focus_add_someone_first),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            }

            people.forEach { person ->
                PersonRow(
                    person = person,
                    selected = person.userId == selected,
                    onClick = { selected = if (selected == person.userId) null else person.userId },
                )
            }

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = speaking(stringResource(R.string.focus_create_room)) {
                    selected?.let { onCreate(listOf(it), minutes) }
                },
                // enabled only once both decisions are made. a Create that can fail on a missing choice teaches
                // people to distrust the button
                enabled = selected != null && !sending,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = if (sending) stringResource(R.string.focus_creating) else stringResource(R.string.focus_create_room),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun DurationChip(minutes: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) colors.primary else colors.surfaceContainerHigh
            )
            .clickable(onClick = speaking(stringResource(R.string.focus_n_minutes, minutes), onClick))
            .padding(horizontal = 18.dp, vertical = 11.dp),
    ) {
        Text(
            text = stringResource(R.string.common_minutes_short, minutes),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) colors.onPrimary else colors.onSurface,
        )
    }
}

@Composable
private fun PersonRow(
    person: FocusCandidate,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val enabled = person.allowed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) colors.primary.copy(alpha = 0.12f) else colors.surface
            )
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) colors.primary else colors.surface,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable(enabled = enabled, onClick = speaking(person.name, onClick))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.primary.copy(alpha = if (enabled) 0.22f else 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            if (person.avatarUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(person.avatarUrl).crossfade(true).build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                )
            } else {
                Text(
                    text = person.name.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface.copy(alpha = if (enabled) 1f else 0.45f),
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = person.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = colors.onSurface.copy(alpha = if (enabled) 1f else 0.45f),
            )
            if (!enabled) {
                Text(
                    text = stringResource(R.string.focus_not_enabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
