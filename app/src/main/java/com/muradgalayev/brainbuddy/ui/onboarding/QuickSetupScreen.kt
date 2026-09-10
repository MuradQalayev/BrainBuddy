package com.muradgalayev.brainbuddy.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.domain.model.AdhdProfile
import com.muradgalayev.brainbuddy.domain.model.AGE_RANGES
import com.muradgalayev.brainbuddy.domain.model.AdhdSymptom
import com.muradgalayev.brainbuddy.domain.model.DiagnosisStatus
import com.muradgalayev.brainbuddy.domain.model.SurveyVersion
import com.muradgalayev.brainbuddy.domain.model.TopGoal

@Composable
fun QuickSetupScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val complete = questionnaireCompletion(state, SurveyVersion.Quick)
    val saveAndSkip = {
        viewModel.saveDraft(SurveyVersion.Quick) {
            onSkip()
        }
    }
    val saveAndExit = { viewModel.saveDraft(SurveyVersion.Quick, onBack) }
    BackHandler(enabled = !state.submitSuccess) {
        if (!state.isLoading && !state.isSubmitting) saveAndExit()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.submitSuccess -> NiceWorkPanel(
                onContinue = onDone,
                savedOfflineOnly = state.savedOfflineOnly,
            )
            else -> SwipeQuestionnaire(
                title = "Let's get to know you",
                pageCount = complete.size,
                completed = complete,
                onExit = saveAndExit,
                isSubmitting = state.isSubmitting,
                submitLabel = if (state.isEditing) "Save changes" else "Finish setup",
                onSubmit = { viewModel.submit(SurveyVersion.Quick) },
                onSaveExit = saveAndExit,
                // editing an existing profile has nothing to skip
                onSkip = if (state.isEditing) null else saveAndSkip,
            ) { page ->
                when (page) {
                    0 -> SurveyQuestionPage("Personal info", "Tell us what we should call you.") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LabeledTextField(state.firstName, viewModel::setFirstName, "First name", Modifier.weight(1f))
                            LabeledTextField(state.lastName, viewModel::setLastName, "Surname", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        UsernameField(state.username, state.usernameAvailability, viewModel::setUsername)
                    }
                    1 -> SurveyQuestionPage("Where do you live?", "We use this to show relevant care in your area.") {
                        UseCurrentLocationRow(state.locationStatus, viewModel::useCurrentLocation)
                        Spacer(Modifier.height(16.dp))
                        SingleChipGrid(state.cities, state.cities.firstOrNull { it.id == state.cityId }, { it.name }) { viewModel.setCity(it.id) }
                    }
                    2 -> SurveyQuestionPage("Your age range", "Choose the range that fits you.") {
                        SingleChipGrid(AGE_RANGES, state.ageRange.takeIf(String::isNotEmpty), { it }, viewModel::setAgeRange)
                    }
                    3 -> SurveyQuestionPage("Diagnosis", "This helps tailor advice to you.") {
                        SingleChipGrid(DiagnosisStatus.OPTIONS, state.diagnosisStatus, { it.label }, viewModel::setDiagnosisStatus)
                    }
                    4 -> SurveyQuestionPage("What feels hardest?", "Pick all that apply.") {
                        ChipGrid(AdhdSymptom.entries.toList(), state.primarySymptoms, { it.label }, viewModel::toggleSymptom)
                    }
                    else -> SurveyQuestionPage("What do you want to get better at?", "Pick up to three — we'll shape Myndora around them.") {
                        CappedChipGrid(
                            options = TopGoal.entries.toList(),
                            selected = state.topGoals,
                            max = AdhdProfile.MAX_TOP_GOALS,
                            label = { it.label },
                            onToggle = viewModel::toggleTopGoal,
                        )
                    }
                }
            }
        }
        com.muradgalayev.brainbuddy.ui.sharedcomponents.MyndoraSnackbarHost(
            snackbar,
            Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
        )

        // errors are a panel, not a snackbar: they carry the way out with them
        SurveyErrorPanel(
            error = state.submitError,
            onDismiss = viewModel::consumeError,
            onRetry = {},
            onSkip = if (state.isEditing) null else saveAndSkip,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 90.dp),
        )
    }
}

@Composable
fun SurveyQuestionPage(title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary.copy(alpha = .11f),
            shape = RoundedCornerShape(50),
        ) {
            Text(
                text = "MAKE IT YOURS",
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(15.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier
                .width(42.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = .7f)),
        )
        Spacer(Modifier.height(22.dp))
        content()
    }
}

@Composable
fun NiceWorkPanel(onContinue: () -> Unit, savedOfflineOnly: Boolean = false) {
    val rotation = remember { Animatable(-320f) }
    val scale = remember { Animatable(.15f) }
    LaunchedEffect(Unit) {
        rotation.animateTo(0f, tween(760, easing = FastOutSlowInEasing))
        scale.animateTo(1f, spring(dampingRatio = .48f, stiffness = 260f))
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier = Modifier.size(112.dp).graphicsLayer {
                rotationZ = rotation.value
                scaleX = scale.value
                scaleY = scale.value
            },
            shape = CircleShape,
            color = Color(0xFF2EAD67),
            shadowElevation = 12.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Completed",
                    tint = Color.White,
                    modifier = Modifier.size(62.dp),
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Nice work!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Your Myndora experience is ready.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // saying 'ready' when the answers are still only on the phone would be a small lie, and the
        // kind that costs trust when a device is later lost
        if (savedOfflineOnly) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "Saved on this device — it'll upload when you're back online",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(28.dp))
        Button(onClick = onContinue, modifier = Modifier.fillMaxWidth().height(54.dp)) {
            Text("Continue")
        }
    }
}
