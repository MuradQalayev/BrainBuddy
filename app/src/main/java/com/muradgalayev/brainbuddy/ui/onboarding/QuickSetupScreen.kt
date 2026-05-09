package com.muradgalayev.brainbuddy.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.domain.model.AGE_RANGES
import com.muradgalayev.brainbuddy.domain.model.AdhdSymptom
import com.muradgalayev.brainbuddy.domain.model.DiagnosisStatus
import com.muradgalayev.brainbuddy.domain.model.SurveyVersion
import com.muradgalayev.brainbuddy.domain.model.TopGoal

@Composable
fun QuickSetupScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.submitSuccess) {
        if (state.submitSuccess) onDone()
    }
    LaunchedEffect(state.submitError) {
        state.submitError?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = colors.onSurface)
                }
                Spacer(Modifier.size(4.dp))
                Text(
                    text = "Quick Setup",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                // 1 — Username
                Column {
                    SectionTitle(1, "Pick a username", "How you'll show up to the assistant.")
                    Spacer(Modifier.height(12.dp))
                    UsernameField(
                        value = state.username,
                        availability = state.usernameAvailability,
                        onChange = viewModel::setUsername,
                    )
                }

                // 2 — City
                Column {
                    SectionTitle(2, "Where do you live?", "We use this to show care nearby.")
                    Spacer(Modifier.height(12.dp))
                    UseCurrentLocationRow(
                        status = state.locationStatus,
                        onRequest = viewModel::useCurrentLocation,
                    )
                    Spacer(Modifier.height(12.dp))
                    SingleChipGrid(
                        options = state.cities,
                        selected = state.cities.firstOrNull { it.id == state.cityId },
                        label = { it.name },
                        onSelect = { viewModel.setCity(it.id) },
                    )
                }

                // 3 — Age range
                Column {
                    SectionTitle(3, "Your age range")
                    Spacer(Modifier.height(12.dp))
                    SingleChipGrid(
                        options = AGE_RANGES,
                        selected = state.ageRange.takeIf { it.isNotEmpty() },
                        label = { it },
                        onSelect = viewModel::setAgeRange,
                    )
                }

                // 4 — Diagnosis status
                Column {
                    SectionTitle(4, "Diagnosis", "Helps us tailor advice — never required.")
                    Spacer(Modifier.height(12.dp))
                    SingleChipGrid(
                        options = DiagnosisStatus.entries.toList(),
                        selected = state.diagnosisStatus,
                        label = { it.label },
                        onSelect = viewModel::setDiagnosisStatus,
                    )
                }

                // 5 — Symptoms
                Column {
                    SectionTitle(
                        5,
                        "What do you struggle with most?",
                        "Pick all that apply.",
                    )
                    Spacer(Modifier.height(12.dp))
                    ChipGrid(
                        options = AdhdSymptom.entries.toList(),
                        selected = state.primarySymptoms,
                        label = { it.label },
                        onToggle = viewModel::toggleSymptom,
                    )
                }

                // 6 — Top goal
                Column {
                    SectionTitle(6, "What do you most want to improve?")
                    Spacer(Modifier.height(12.dp))
                    SingleChipGrid(
                        options = TopGoal.entries.toList(),
                        selected = state.topGoal,
                        label = { it.label },
                        onSelect = viewModel::setTopGoal,
                    )
                }
            }

            // Submit
            Button(
                onClick = { viewModel.submit(SurveyVersion.Quick) },
                enabled = !state.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary),
            ) {
                if (state.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = colors.onPrimary,
                    )
                } else {
                    Text(
                        text = if (state.isEditing) "Save changes" else "Save & continue",
                        color = colors.onPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
        )
    }
}
