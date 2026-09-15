package com.muradgalayev.brainbuddy.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents

// the half beside Focus. one tap to your QR, and the scanner is a tap away inside it
@Composable
fun HomeQrTile(
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.myndoraAccents.accent

    HomeCard(modifier = modifier, accent = accent, onClick = onOpen) {
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(50.dp)) {
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(HomeInnerShape)
                        .background(accent.copy(alpha = .14f))
                        .border(1.dp, accent.copy(alpha = .18f), HomeInnerShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.QrCode2, null, tint = accent, modifier = Modifier.size(26.dp))
                }
                // the scanner mark on the corner: this tile shows a code and reads one
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(accent)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.QrCodeScanner, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                HomeSectionLabel(stringResource(R.string.home_nearby_caps), accent)
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.home_nearby_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
