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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.domain.model.AGE_RANGES
import com.muradgalayev.brainbuddy.domain.model.AdhdSymptom
import com.muradgalayev.brainbuddy.domain.model.AiTone
import com.muradgalayev.brainbuddy.domain.model.CopingStrategy
import com.muradgalayev.brainbuddy.domain.model.DiagnosisStatus
import com.muradgalayev.brainbuddy.domain.model.MedicationStatus
import com.muradgalayev.brainbuddy.domain.model.ProductiveTime
import com.muradgalayev.brainbuddy.domain.model.SurveyVersion
import com.muradgalayev.brainbuddy.domain.model.TopGoal

@Composable
fun DeepDiveScreen(
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
                    text = "Deep Dive",
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
                    SectionTitle(1, "Pick a username")
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

                // 4 — Diagnosis
                Column {
                    SectionTitle(4, "Diagnosis")
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
                    SectionTitle(5, "What do you struggle with most?", "Pick all that apply.")
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

                // 7 — Productive time
                Column {
                    SectionTitle(7, "When are you most productive?")
                    Spacer(Modifier.height(12.dp))
                    SingleChipGrid(
                        options = ProductiveTime.entries.toList(),
                        selected = state.productiveTime,
                        label = { it.label },
                        onSelect = viewModel::setProductiveTime,
                    )
                }

                // 8 — Focus duration slider
                Column {
                    SectionTitle(
                        8,
                        "Average focus stretch",
                        "How long can you typically focus before getting distracted?",
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "${state.focusDurationMinutes} min",
                        color = colors.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    )
                    Slider(
                        value = state.focusDurationMinutes.toFloat(),
                        onValueChange = { viewModel.setFocusDuration(it.toInt()) },
                        valueRange = 5f..90f,
                        steps = 16,
                        colors = SliderDefaults.colors(
                            thumbColor = colors.primary,
                            activeTrackColor = colors.primary,
                        ),
                    )
                }

                // 9 — Sleep
                Column {
                    SectionTitle(9, "Sleep schedule", "Optional. Format: 23:00 / 07:30")
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.sleepBedtime,
                            onValueChange = viewModel::setBedtime,
                            label = { Text("Bedtime") },
                            placeholder = { Text("23:00") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                cursorColor = colors.primary,
                            ),
                        )
                        OutlinedTextField(
                            value = state.sleepWakeTime,
                            onValueChange = viewModel::setWakeTime,
                            label = { Text("Wake-up") },
                            placeholder = { Text("07:30") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = colors.primary,
                                cursorColor = colors.primary,
                            ),
                        )
                    }
                }

                // 10 — Medication
                Column {
                    SectionTitle(10, "Medication", "We'll add full meds tracking later.")
                    Spacer(Modifier.height(12.dp))
                    SingleChipGrid(
                        options = MedicationStatus.entries.toList(),
                        selected = state.medicationStatus,
                        label = { it.label },
                        onSelect = viewModel::setMedicationStatus,
                    )
                    if (state.medicationStatus == MedicationStatus.Yes) {
                        Spacer(Modifier.height(12.dp))
                        MedicationListSection(
                            medications = state.medications,
                            onAdd = viewModel::addMedication,
                            onRemove = viewModel::removeMedication,
                            onNameChange = viewModel::setMedicationName,
                            onDoseChange = viewModel::setMedicationDose,
                            onSlotToggle = viewModel::toggleMedicationSlot,
                        )
                    }
                }

                // 11 — Coping strategies
                Column {
                    SectionTitle(11, "What helps you today?", "Pick anything that's already part of your routine.")
                    Spacer(Modifier.height(12.dp))
                    ChipGrid(
                        options = CopingStrategy.entries.toList(),
                        selected = state.copingStrategies,
                        label = { it.label },
                        onToggle = viewModel::toggleCopingStrategy,
                    )
                }

                // 12 — Pain point
                Column {
                    SectionTitle(12, "What's your biggest pain point right now?", "Up to 280 characters. Skip if you'd rather not say.")
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = state.painPoint,
                        onValueChange = viewModel::setPainPoint,
                        placeholder = { Text("e.g. Can't get started in the morning") },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.primary,
                            cursorColor = colors.primary,
                        ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "${state.painPoint.length} / 280",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // 13 — AI tone
                Column {
                    SectionTitle(13, "How should the assistant talk to you?")
                    Spacer(Modifier.height(12.dp))
                    SingleChipGrid(
                        options = AiTone.entries.toList(),
                        selected = state.aiTone,
                        label = { it.label },
                        onSelect = viewModel::setAiTone,
                    )
                }
            }

            Button(
                onClick = { viewModel.submit(SurveyVersion.Deep) },
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
