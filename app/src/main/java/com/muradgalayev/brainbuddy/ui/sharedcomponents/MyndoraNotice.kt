package com.muradgalayev.brainbuddy.ui.sharedcomponents

import android.media.RingtoneManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

private enum class NoticeKind(
    @androidx.annotation.StringRes val titleRes: Int,
    val color: Color,
    val softColor: Color,
    val icon: ImageVector,
) {
    Success(R.string.notice_all_done, Color(0xFF3B9362), Color(0xFFE2F2E8), Icons.Outlined.CheckCircle),
    Error(R.string.notice_error, Color(0xFFC95656), Color(0xFFF8E4E2), Icons.Outlined.ErrorOutline),
    Info(R.string.notice_heads_up, Color(0xFFEA580C), Color(0xFFFFEDD5), Icons.Outlined.Info),
}

@Composable
fun MyndoraSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        MyndoraNotice(data)
    }
}

@Composable
private fun MyndoraNotice(data: SnackbarData) {
    val context = LocalContext.current
    val message = data.visuals.message
    val kind = noticeKind(message)
    LaunchedEffect(message) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context.applicationContext, uri)?.play()
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp,
        tonalElevation = 3.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 13.dp, bottom = 13.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(kind.softColor, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(kind.icon, null, tint = kind.color, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(kind.titleRes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = data::dismiss, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.Close, stringResource(R.string.common_dismiss), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

private fun noticeKind(message: String): NoticeKind {
    val lower = message.lowercase()
    return when {
        listOf("couldn't", "failed", "error", "invalid", "please", "unable").any(lower::contains) -> NoticeKind.Error
        listOf("saved", "success", "updated", "added", "connected", "done", "created").any(lower::contains) -> NoticeKind.Success
        else -> NoticeKind.Info
    }
}

fun playDefaultNotificationTone(context: android.content.Context) {
    runCatching {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        RingtoneManager.getRingtone(context.applicationContext, uri)?.play()
    }
}
