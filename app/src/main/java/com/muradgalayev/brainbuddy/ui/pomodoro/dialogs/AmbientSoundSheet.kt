package com.muradgalayev.brainbuddy.ui.pomodoro.dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.data.local.AmbientSound

private val SpotifyGreen = Color(0xFF1DB954)

// sound picker, opened from the pill inside the timer card. living in a sheet keeps the timer
// screen down to the ring and the start button, where the previous inline expanding card
// pushed those below the fold
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AmbientSoundSheet(
    selectedSound: AmbientSound?,
    onSoundSelect: (AmbientSound?) -> Unit,
    spotifyPlaylistLink: String,
    onSpotifyPlaylistLinkChange: (String) -> Unit,
    onOpenSpotifyPlaylist: () -> Unit,
    accentColor: Color,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(top = 20.dp, bottom = 28.dp),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .width(36.dp)
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                        RoundedCornerShape(999.dp),
                    )
            )

            Spacer(Modifier.height(22.dp))

            Text(
                text = "Ambient sound",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(14.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SoundChip(
                    label = "None",
                    selected = selectedSound == null,
                    accentColor = accentColor,
                    onClick = { onSoundSelect(null) },
                )
                AmbientSound.entries.forEach { sound ->
                    SoundChip(
                        label = sound.label,
                        selected = sound == selectedSound,
                        accentColor = accentColor,
                        onClick = { onSoundSelect(if (sound == selectedSound) null else sound) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Spotify playlist",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = SpotifyGreen,
            )

            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = spotifyPlaylistLink,
                onValueChange = onSpotifyPlaylistLinkChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                placeholder = { Text("https://open.spotify.com/playlist/…") },
            )

            Spacer(Modifier.height(4.dp))

            TextButton(
                onClick = onOpenSpotifyPlaylist,
                enabled = spotifyPlaylistLink.contains("open.spotify.com/playlist/"),
            ) {
                Text("Open in Spotify", color = SpotifyGreen, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SoundChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accentColor.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) accentColor else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
