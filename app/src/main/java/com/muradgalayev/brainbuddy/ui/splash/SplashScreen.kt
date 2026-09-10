package com.muradgalayev.brainbuddy.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToOnboarding: () -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val rotation = remember { Animatable(0f) }
    val scale = remember { Animatable(0.5f) }
    val alpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // logo spins in and scales up together
        launch {
            rotation.animateTo(
                targetValue = 360f,
                animationSpec = tween(1400, easing = FastOutSlowInEasing)
            )
        }
        launch {
            scale.animateTo(
                targetValue = 1f,
                animationSpec = tween(1200, easing = FastOutSlowInEasing)
            )
        }
        launch {
            alpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(800, easing = FastOutSlowInEasing)
            )
        }

        // app name fades in after the logo settles
        delay(300)
        launch {
            textAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(400, easing = FastOutSlowInEasing)
            )
        }

        // short hold so the logo has time to register, then route. the survey check is a local
        // DataStore read, so there's no network round-trip here
        delay(500)
        when {
            // a reset link cold-started the app: the session is technically Authenticated, but the user
            // has to set a new password before we let them in
            viewModel.isInPasswordRecovery() -> onNavigateToAuth()
            !viewModel.isLoggedIn() -> onNavigateToAuth()
            viewModel.isSurveyCompleted() -> onNavigateToHome()
            // chose 'I'll do it later', so let them in and the account screen keeps nudging
            viewModel.isOnboardingSkipped() -> onNavigateToHome()
            else -> onNavigateToOnboarding()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(id = R.drawable.ic_ai),
            contentDescription = "Myndora",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .size(96.dp)
                .rotate(rotation.value)
                .scale(scale.value)
                .alpha(alpha.value)
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "Myndora",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.alpha(textAlpha.value)
        )
    }
}
