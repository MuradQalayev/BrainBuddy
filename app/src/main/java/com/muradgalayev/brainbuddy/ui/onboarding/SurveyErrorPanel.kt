package com.muradgalayev.brainbuddy.ui.onboarding

import android.content.res.Configuration.UI_MODE_NIGHT_YES
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.ui.theme.MyndoraTheme

// what the survey shows when a save doesn't go through. replaces a snackbar carrying a raw
// exception message, for three reasons: a snackbar disappears before you've finished reading
// it, 'Couldn't save: UnknownHostException' tells the user nothing they can act on, and worst,
// a transient failure used to leave people stuck on this page with no visible way forward.
// every state here offers a way out, and the way out never needs a connection
@Composable
fun SurveyErrorPanel(
    error: SurveyError?,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSkip: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = error != null,
        enter = slideInVertically(tween(260)) { it / 3 } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(200)) { it / 3 } + fadeOut(tween(150)),
        modifier = modifier,
    ) {
        // keep the last non-null error while the exit animation plays, so the card fades out with its
        // text intact instead of blanking mid-slide
        val shown = remember { mutableStateOf(error) }
        if (error != null) shown.value = error
        shown.value?.let { ErrorCard(it, onDismiss, onRetry, onSkip) }
    }
}

@Composable
private fun ErrorCard(
    error: SurveyError,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onSkip: (() -> Unit)?,
) {
    val copy = error.wording()
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, copy.accent().copy(alpha = .30f)),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(copy.accent().copy(alpha = .14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = copy.icon,
                        contentDescription = null,
                        tint = copy.accent(),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = copy.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = copy.body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = { onDismiss(); onRetry() },
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = copy.accent()),
                ) {
                    Text(copy.primaryAction, fontWeight = FontWeight.SemiBold)
                }
            }

            // the escape hatch. only hidden when the fix is entirely in the user's hands (a missing answer
            // or a taken username), because those don't strand anyone: the page itself is where they get
            // fixed
            if (onSkip != null && error.kind == SurveyError.Kind.SaveFailed) {
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Skip for now — I'll finish this later",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// copy: one honest sentence per failure

private data class ErrorCopy(
    val title: String,
    val body: String,
    val primaryAction: String,
    val icon: ImageVector,
    val danger: Boolean,
)

@Composable
private fun ErrorCopy.accent() =
    if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

private fun SurveyError.wording(): ErrorCopy = when (kind) {
    SurveyError.Kind.MissingAnswers -> ErrorCopy(
        title = "A couple of answers are missing",
        body = detail?.let { "Have a look at $it and you're done." }
            ?: "Swipe back through the questions — the dots at the top show which ones still need you.",
        primaryAction = "Back to the questions",
        icon = Icons.Outlined.EditNote,
        danger = false,
    )

    SurveyError.Kind.UsernameTaken -> ErrorCopy(
        title = "That username is taken",
        body = "Pick another one and we'll carry on from here.",
        primaryAction = "Choose another",
        icon = Icons.Rounded.AlternateEmail,
        danger = false,
    )

    SurveyError.Kind.SaveFailed -> ErrorCopy(
        title = "Couldn't save just now",
        body = "Your answers are still here. Try again, or skip and come back to " +
            "it — nothing you've typed will be lost.",
        primaryAction = "Try again",
        icon = Icons.Rounded.CloudOff,
        danger = true,
    )
}

// previews

@Preview(showBackground = true)
@Preview(showBackground = true, uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun SurveyErrorSaveFailedPreview() {
    MyndoraTheme(fontSize = FontSize.Medium) {
        SurveyErrorPanel(
            error = SurveyError(SurveyError.Kind.SaveFailed, "Unable to resolve host"),
            onDismiss = {}, onRetry = {}, onSkip = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SurveyErrorMissingPreview() {
    MyndoraTheme(fontSize = FontSize.Medium) {
        SurveyErrorPanel(
            error = SurveyError(SurveyError.Kind.MissingAnswers, "the Deep Dive questions"),
            onDismiss = {}, onRetry = {}, onSkip = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SurveyErrorUsernamePreview() {
    MyndoraTheme(fontSize = FontSize.Medium) {
        SurveyErrorPanel(
            error = SurveyError(SurveyError.Kind.UsernameTaken),
            onDismiss = {}, onRetry = {}, onSkip = {},
        )
    }
}
