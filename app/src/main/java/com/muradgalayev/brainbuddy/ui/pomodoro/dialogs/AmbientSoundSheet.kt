package com.muradgalayev.brainbuddy.ui.pomodoro.dialogs

import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
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
import com.muradgalayev.brainbuddy.data.local.SoundDownload
import com.muradgalayev.brainbuddy.R

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
    downloads: Map<AmbientSound, SoundDownload> = emptyMap(),
    onRetry: (AmbientSound) -> Unit = {},
    plusActive: Boolean = true,
    onUnlock: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState()
    val selectedStatus = selectedSound?.let { downloads[it] }

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
                text = stringResource(R.string.sound_ambient),
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
                    label = stringResource(R.string.common_none),
                    selected = selectedSound == null,
                    accentColor = accentColor,
                    onClick = { onSoundSelect(null) },
                )
                AmbientSound.entries.forEach { sound ->
                    val status = downloads[sound]
                    // locked sounds stay in the row with a lock, so what Plus adds is visible, not hidden
                    val locked = sound.plus && !plusActive
                    SoundChip(
                        label = stringResource(sound.labelRes),
                        selected = sound == selectedSound,
                        accentColor = accentColor,
                        // only the picked sound shows its download, the rest would be noise
                        status = status.takeIf { sound == selectedSound },
                        locked = locked,
                        onClick = {
                            when {
                                locked -> onUnlock()
                                // tapping a failed pick retries rather than switching the sound off
                                sound == selectedSound && status == SoundDownload.Failed -> onRetry(sound)
                                sound == selectedSound -> onSoundSelect(null)
                                else -> onSoundSelect(sound)
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = stringResource(
                    if (selectedStatus == SoundDownload.Failed) R.string.sound_download_failed
                    else R.string.sound_offline_hint
                ),
                style = MaterialTheme.typography.bodySmall,
                color = if (selectedStatus == SoundDownload.Failed) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!plusActive) {
                Spacer(Modifier.height(16.dp))
                PlusSoundsCard(
                    lockedCount = AmbientSound.entries.count { it.plus },
                    onUnlock = onUnlock,
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.sound_spotify),
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
                Text(stringResource(R.string.sound_open_spotify), color = SpotifyGreen, fontWeight = FontWeight.SemiBold)
            }

            // CC0 needs no credit, the author asks for one anyway
            Text(
                text = stringResource(R.string.sound_credit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
private fun SoundChip(
    label: String,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
    status: SoundDownload? = null,
    locked: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (selected) accentColor.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (locked) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = stringResource(R.string.plan_locked_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(13.dp),
                )
                Spacer(Modifier.width(6.dp))
            }
            if (status is SoundDownload.Downloading) {
                val progress = status.progress
                if (progress != null) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = accentColor,
                        trackColor = accentColor.copy(alpha = 0.2f),
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = accentColor,
                    )
                }
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = when {
                    status == SoundDownload.Failed -> MaterialTheme.colorScheme.error
                    selected -> accentColor
                    locked -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
