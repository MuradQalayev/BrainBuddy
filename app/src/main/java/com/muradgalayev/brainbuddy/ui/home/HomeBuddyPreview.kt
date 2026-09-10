package com.muradgalayev.brainbuddy.ui.home

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.ui.theme.MyndoraTheme
import com.muradgalayev.brainbuddy.ui.theme.myndoraAccents

// the four moods side by side, and the tile itself. here because a character is the one thing
// you can't review by reading its source: the difference between charming and unsettling is a
// few percent on an eye radius, and it has to be looked at in both themes to be believed
@Preview(name = "Buddy · moods", showBackground = true, widthDp = 380, heightDp = 220)
@Preview(name = "Buddy · moods dark", showBackground = true, widthDp = 380, heightDp = 220, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun BuddyMoodsPreview() {
    MyndoraTheme(fontSize = FontSize.Medium) {
        val accents = MaterialTheme.myndoraAccents
        Row(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(
                "asleep" to (0 to 4),
                "stirring" to (1 to 4),
                "awake" to (3 to 4),
                "beaming" to (4 to 4),
            ).forEach { (label, counts) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    HomeBuddy(
                        done = counts.first,
                        total = counts.second,
                        bodyStart = accents.accent,
                        bodyEnd = accents.accentEnd,
                        lively = true,
                        modifier = Modifier.size(84.dp),
                    )
                    Text(label, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Preview(name = "Progress tile", showBackground = true, widthDp = 200, heightDp = 210)
@Preview(name = "Progress tile · dark", showBackground = true, widthDp = 200, heightDp = 210, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun ProgressTilePreview() {
    MyndoraTheme(fontSize = FontSize.Medium) {
        Column(
            Modifier
                .background(MaterialTheme.colorScheme.background)
                .padding(8.dp),
        ) {
            HomeWinsTile(
                done = 3,
                total = 9,
                onOpen = {},
                modifier = Modifier.fillMaxWidth().height(190.dp),
            )
        }
    }
}
