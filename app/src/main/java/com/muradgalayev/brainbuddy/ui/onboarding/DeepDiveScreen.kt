package com.muradgalayev.brainbuddy.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.activity.compose.BackHandler
import androidx.hilt.navigation.compose.hiltViewModel
import com.muradgalayev.brainbuddy.domain.model.AdhdPresentation
import com.muradgalayev.brainbuddy.domain.model.AdhdProfile
import com.muradgalayev.brainbuddy.domain.model.BodyDoublingInterest
import com.muradgalayev.brainbuddy.domain.model.CaptureNeed
import com.muradgalayev.brainbuddy.domain.model.CheckInCeiling
import com.muradgalayev.brainbuddy.domain.model.Chronotype
import com.muradgalayev.brainbuddy.domain.model.CoOccurringCondition
import com.muradgalayev.brainbuddy.domain.model.ImpulseArea
import com.muradgalayev.brainbuddy.domain.model.InterruptionRecall
import com.muradgalayev.brainbuddy.domain.model.MissedTaskResponse
import com.muradgalayev.brainbuddy.domain.model.NudgeTone
import com.muradgalayev.brainbuddy.domain.model.PastStrategy
import com.muradgalayev.brainbuddy.domain.model.PlanChangeImpact
import com.muradgalayev.brainbuddy.domain.model.SleepScheduleOrigin
import com.muradgalayev.brainbuddy.domain.model.TaskReturnEffort
import com.muradgalayev.brainbuddy.domain.model.WorkEnvironment
import com.muradgalayev.brainbuddy.domain.model.AGE_RANGES
import com.muradgalayev.brainbuddy.domain.model.AdhdSymptom
import com.muradgalayev.brainbuddy.domain.model.AiTone
import com.muradgalayev.brainbuddy.domain.model.CopingStrategy
import com.muradgalayev.brainbuddy.domain.model.DiagnosisStatus
import com.muradgalayev.brainbuddy.domain.model.MedicationStatus
import com.muradgalayev.brainbuddy.domain.model.ProductiveTime
import com.muradgalayev.brainbuddy.domain.model.SurveyVersion
import com.muradgalayev.brainbuddy.domain.model.TopGoal
import com.muradgalayev.brainbuddy.ui.sharedcomponents.TimePickerDialog
import com.muradgalayev.brainbuddy.ui.sharedcomponents.TimePickerField

@Composable
fun DeepDiveScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
    onSkip: () -> Unit = {},
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    var sleepTimePicker by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val complete = questionnaireCompletion(state, SurveyVersion.Deep)
    val saveAndSkip = {
        viewModel.saveDraft(SurveyVersion.Deep) {
            onSkip()
        }
    }
    val saveAndExit = { viewModel.saveDraft(SurveyVersion.Deep, onBack) }
    BackHandler(enabled = !state.submitSuccess) {
        if (!state.isLoading && !state.isSubmitting) saveAndExit()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.isLoading -> CircularProgressIndicator(Modifier.align(Alignment.Center))
            state.submitSuccess -> NiceWorkPanel(onDone)
            else -> SwipeQuestionnaire(
                title = "Grow your profile",
                pageCount = 27,
                completed = complete,
                onExit = saveAndExit,
                isSubmitting = state.isSubmitting,
                submitLabel = if (state.isEditing) "Save profile" else "Finish my profile",
                onSubmit = { viewModel.submit(SurveyVersion.Deep) },
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
                    3 -> SurveyQuestionPage("Diagnosis", "This helps us personalize guidance without assumptions.") {
                        SingleChipGrid(DiagnosisStatus.OPTIONS, state.diagnosisStatus, { it.label }, viewModel::setDiagnosisStatus)
                        // N2 lives here rather than on its own page: it's a follow-up to one specific answer, so for
                        // everyone else a separate page was a blank screen to swipe past, and for the people it does
                        // apply to it belongs next to the answer that prompted it
                        AnimatedVisibility(
                            visible = state.diagnosisStatus == DiagnosisStatus.Diagnosed,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            Column {
                                Spacer(Modifier.height(22.dp))
                                Text(
                                    "Which presentation?",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Optional — skip if you'd rather not say.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                                SingleChipGrid(AdhdPresentation.entries.toList(), state.presentation, { it.label }, viewModel::setPresentation)
                            }
                        }
                    }
                    4 -> SurveyQuestionPage("Does anything else apply?", "Optional. This changes what we suggest, it isn't a diagnosis.") {
                        ChipGrid(CoOccurringCondition.entries.toList(), state.coOccurring.toSet(), { it.label }, viewModel::toggleCoOccurring)
                    }
                    5 -> SurveyQuestionPage("What feels hardest?", "Choose everything that sounds familiar.") {
                        ChipGrid(AdhdSymptom.entries.toList(), state.primarySymptoms, { it.label }, viewModel::toggleSymptom)
                    }
                    6 -> SurveyQuestionPage("What do you want to get better at?", "Pick up to three.") {
                        CappedChipGrid(
                            options = TopGoal.entries.toList(),
                            selected = state.topGoals,
                            max = AdhdProfile.MAX_TOP_GOALS,
                            label = { it.label },
                            onToggle = viewModel::toggleTopGoal,
                        )
                    }
                    7 -> SurveyQuestionPage("Your best time", "When does focus usually come most naturally?") {
                        SingleChipGrid(ProductiveTime.entries.toList(), state.productiveTime, { it.label }, viewModel::setProductiveTime)
                    }
                    8 -> SurveyQuestionPage("When do you feel most like yourself?", "Most alert, clearest-headed.") {
                        SingleChipGrid(Chronotype.entries.toList(), state.chronotype, { it.label }, viewModel::setChronotype)
                    }
                    9 -> SurveyQuestionPage("Your focus stretch", "How long can you usually focus before needing a reset?") {
                        Text("${state.focusDurationMinutes} minutes", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Slider(state.focusDurationMinutes.toFloat(), { viewModel.setFocusDuration(it.toInt()) }, valueRange = 5f..90f, steps = 16)
                    }
                    10 -> SurveyQuestionPage("Your sleep rhythm", "Optional — a rough time is completely fine.") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimePickerField(
                                value = state.sleepBedtime,
                                label = "Bedtime",
                                placeholder = "23:00",
                                mutedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                accentColor = MaterialTheme.colorScheme.primary,
                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                                textColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                onClick = { sleepTimePicker = "bedtime" },
                            )
                            TimePickerField(
                                value = state.sleepWakeTime,
                                label = "Wake-up",
                                placeholder = "07:30",
                                mutedColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                accentColor = MaterialTheme.colorScheme.primary,
                                borderColor = MaterialTheme.colorScheme.outlineVariant,
                                textColor = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                onClick = { sleepTimePicker = "wake" },
                            )
                        }
                    }
                    11 -> SurveyQuestionPage("Is that schedule your choice?", "Or is it shaped by work, family or other obligations?") {
                        SingleChipGrid(SleepScheduleOrigin.entries.toList(), state.sleepScheduleOrigin, { it.label }, viewModel::setSleepScheduleOrigin)
                    }
                    12 -> SurveyQuestionPage("Medication", "This stays private and helps reminders fit your routine.") {
                        SingleChipGrid(MedicationStatus.entries.toList(), state.medicationStatus, { it.label }, viewModel::setMedicationStatus)
                        if (state.medicationStatus == MedicationStatus.Yes) {
                            Spacer(Modifier.height(16.dp))
                            MedicationListSection(
                                medications = state.medications,
                                onAdd = viewModel::addMedication,
                                onRemove = viewModel::removeMedication,
                                onNameChange = viewModel::setMedicationName,
                                onDoseChange = viewModel::setMedicationDose,
                                onDoseUnitChange = viewModel::setMedicationDoseUnit,
                                onSlotToggle = viewModel::toggleMedicationSlot,
                                onTimeChange = viewModel::setMedicationTime,
                                onToggleAsNeeded = viewModel::toggleMedicationAsNeeded,
                            )
                        }
                    }
                    13 -> SurveyQuestionPage("Interrupted mid-task?", "How often do you lose track of what you were doing?") {
                        SingleChipGrid(InterruptionRecall.entries.toList(), state.interruptionRecall, { it.label }, viewModel::setInterruptionRecall)
                    }
                    14 -> SurveyQuestionPage("Do you have to write things down?", "Or will you likely forget them?") {
                        SingleChipGrid(CaptureNeed.entries.toList(), state.captureNeed, { it.label }, viewModel::setCaptureNeed)
                    }
                    15 -> SurveyQuestionPage("When plans change unexpectedly", "How does that land for you?") {
                        SingleChipGrid(PlanChangeImpact.entries.toList(), state.planChangeImpact, { it.label }, viewModel::setPlanChangeImpact)
                    }
                    16 -> SurveyQuestionPage("Getting back to a task", "After you've been pulled away from it.") {
                        SingleChipGrid(TaskReturnEffort.entries.toList(), state.taskReturnEffort, { it.label }, viewModel::setTaskReturnEffort)
                    }
                    17 -> SurveyQuestionPage("Hardest to hold back?", "Choose anything that sounds familiar.") {
                        ChipGrid(ImpulseArea.entries.toList(), state.impulseAreas.toSet(), { it.label }, viewModel::toggleImpulseArea)
                    }
                    18 -> SurveyQuestionPage("How should reminders sound?", "You can change this at any time.") {
                        SingleChipGrid(NudgeTone.entries.toList(), state.nudgeTone, { it.label }, viewModel::setNudgeTone)
                    }
                    19 -> SurveyQuestionPage("How much is too much?", "We'll treat this as a ceiling, never a target.") {
                        SingleChipGrid(CheckInCeiling.entries.toList(), state.checkInCeiling, { it.label }, viewModel::setCheckInCeiling)
                    }
                    20 -> SurveyQuestionPage("When you miss something", "What would actually help in that moment?") {
                        SingleChipGrid(MissedTaskResponse.entries.toList(), state.missedTaskResponse, { it.label }, viewModel::setMissedTaskResponse)
                    }
                    21 -> SurveyQuestionPage("Working alongside someone?", "Some people focus better with company, even virtually.") {
                        SingleChipGrid(BodyDoublingInterest.entries.toList(), state.bodyDoublingInterest, { it.label }, viewModel::setBodyDoublingInterest)
                    }
                    22 -> SurveyQuestionPage("Where do you get things done?", "Your usual spot, not your ideal one.") {
                        SingleChipGrid(WorkEnvironment.entries.toList(), state.workEnvironment, { it.label }, viewModel::setWorkEnvironment)
                    }
                    23 -> SurveyQuestionPage("What have you already tried?", "So we don't suggest things that didn't work.") {
                        ChipGrid(PastStrategy.entries.toList(), state.pastStrategies.toSet(), { it.label }, viewModel::togglePastStrategy)
                    }
                    24 -> SurveyQuestionPage("What already helps?", "Choose anything that is part of your routine.") {
                        ChipGrid(CopingStrategy.entries.toList(), state.copingStrategies, { it.label }, viewModel::toggleCopingStrategy)
                    }
                    25 -> SurveyQuestionPage("Your biggest pain point", "Optional — describe what is difficult in your own words.") {
                        OutlinedTextField(
                            value = state.painPoint,
                            onValueChange = viewModel::setPainPoint,
                            placeholder = { Text("For example: I can't get started in the morning") },
                            modifier = Modifier.fillMaxWidth().height(130.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                        )
                        Text("${state.painPoint.length} / 280", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> SurveyQuestionPage("Choose my voice", "How should Myndora speak with you?") {
                        SingleChipGrid(AiTone.entries.toList(), state.aiTone, { it.label }, viewModel::setAiTone)
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
    sleepTimePicker?.let { target ->
        val current = if (target == "bedtime") state.sleepBedtime else state.sleepWakeTime
        val parts = current.split(":")
        val fallbackHour = if (target == "bedtime") 23 else 7
        TimePickerDialog(
            title = if (target == "bedtime") "Choose bedtime" else "Choose wake-up time",
            initialHour = parts.getOrNull(0)?.toIntOrNull() ?: fallbackHour,
            initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
            onConfirm = { hour, minute ->
                val value = "%02d:%02d".format(hour, minute)
                if (target == "bedtime") viewModel.setBedtime(value) else viewModel.setWakeTime(value)
                sleepTimePicker = null
            },
            onDismiss = { sleepTimePicker = null },
        )
    }
}
