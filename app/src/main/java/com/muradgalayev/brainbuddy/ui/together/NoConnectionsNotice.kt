package com.muradgalayev.brainbuddy.ui.together

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// shown when the user picks 'for someone' but has nobody connected yet. the audience switcher
// used to be hidden outright in that case, which quietly made the feature invisible to exactly
// the people who hadn't discovered it, and you can't want a capability you've never seen.
// showing the switch and explaining the one missing prerequisite turns a dead end into the
// shortest path to fixing it. deliberately not an error: nothing has gone wrong
@Composable
fun NoConnectionsNotice(
    // what's being created, 'event' or 'task'. keeps the copy specific
    itemNoun: String,
    accent: Color,
    ink: Color,
    muted: Color,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.10f), accent.copy(alpha = 0.04f))
                )
            )
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.PersonAdd,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(17.dp),
                )
            }

            Spacer(Modifier.width(11.dp))

            Text(
                text = "Nobody connected yet",
                color = ink,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        Spacer(Modifier.height(9.dp))

        Text(
            text = "Connect with someone first, then you can send this $itemNoun " +
                "straight to them.",
            color = muted,
            fontSize = 12.5.sp,
            lineHeight = 17.sp,
        )

        Spacer(Modifier.height(13.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(11.dp))
                .background(accent)
                .clickable(onClick = onConnect)
                .padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Add someone",
                color = Color.White,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(5.dp))
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
