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
import com.muradgalayev.brainbuddy.R
import androidx.compose.ui.res.stringResource

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
                title = stringResource(R.string.deep_grow_profile),
                pageCount = 27,
                completed = complete,
                onExit = saveAndExit,
                isSubmitting = state.isSubmitting,
                submitLabel = if (state.isEditing) stringResource(R.string.deep_save_profile) else stringResource(R.string.deep_finish_profile),
                onSubmit = { viewModel.submit(SurveyVersion.Deep) },
                onSaveExit = saveAndExit,
                // editing an existing profile has nothing to skip
                onSkip = if (state.isEditing) null else saveAndSkip,
            ) { page ->
                when (page) {
                    0 -> SurveyQuestionPage(stringResource(R.string.onboarding_personal_info), stringResource(R.string.onboarding_personal_info_sub)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LabeledTextField(state.firstName, viewModel::setFirstName, stringResource(R.string.auth_first_name), Modifier.weight(1f))
                            LabeledTextField(state.lastName, viewModel::setLastName, stringResource(R.string.onboarding_surname), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        UsernameField(state.username, state.usernameAvailability, viewModel::setUsername)
                    }
                    1 -> SurveyQuestionPage(stringResource(R.string.onboarding_where_live), stringResource(R.string.onboarding_where_live_sub)) {
                        UseCurrentLocationRow(state.locationStatus, viewModel::useCurrentLocation)
                        Spacer(Modifier.height(16.dp))
                        SingleChipGrid(state.cities, state.cities.firstOrNull { it.id == state.cityId }, { it.name }, onSelect = { viewModel.setCity(it.id) })
                    }
                    2 -> SurveyQuestionPage(stringResource(R.string.onboarding_age_range), stringResource(R.string.onboarding_age_range_sub)) {
                        SingleChipGrid(AGE_RANGES, state.ageRange.takeIf(String::isNotEmpty), { ageRangeLabel(it) }, viewModel::setAgeRange)
                    }
                    3 -> SurveyQuestionPage(stringResource(R.string.onboarding_diagnosis), stringResource(R.string.deep_diagnosis_sub)) {
                        // 'diagnosed' opens the presentation question below, so it stays on the page for that
                        SingleChipGrid(
                            DiagnosisStatus.OPTIONS,
                            state.diagnosisStatus,
                            { stringResource(it.labelRes) },
                            viewModel::setDiagnosisStatus,
                            advanceOn = { it != DiagnosisStatus.Diagnosed },
                        )
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
                                    stringResource(R.string.deep_which_presentation),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    stringResource(R.string.deep_optional_skip),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(12.dp))
                                SingleChipGrid(AdhdPresentation.entries.toList(), state.presentation, { stringResource(it.labelRes) }, viewModel::setPresentation)
                            }
                        }
                    }
                    4 -> SurveyQuestionPage(stringResource(R.string.deep_anything_else), stringResource(R.string.deep_anything_else_sub)) {
                        ChipGrid(CoOccurringCondition.entries.toList(), state.coOccurring.toSet(), { stringResource(it.labelRes) }, viewModel::toggleCoOccurring)
                    }
                    5 -> SurveyQuestionPage(stringResource(R.string.onboarding_hardest), stringResource(R.string.deep_hardest_sub)) {
                        ChipGrid(AdhdSymptom.entries.toList(), state.primarySymptoms, { stringResource(it.labelRes) }, viewModel::toggleSymptom)
                    }
                    6 -> SurveyQuestionPage(stringResource(R.string.onboarding_improve), stringResource(R.string.deep_pick_three)) {
                        CappedChipGrid(
                            options = TopGoal.entries.toList(),
                            selected = state.topGoals,
                            max = AdhdProfile.MAX_TOP_GOALS,
                            label = { stringResource(it.labelRes) },
                            onToggle = viewModel::toggleTopGoal,
                        )
                    }
                    7 -> SurveyQuestionPage(stringResource(R.string.deep_best_time), stringResource(R.string.deep_best_time_sub)) {
                        SingleChipGrid(ProductiveTime.entries.toList(), state.productiveTime, { stringResource(it.labelRes) }, viewModel::setProductiveTime)
                    }
                    8 -> SurveyQuestionPage(stringResource(R.string.deep_most_yourself), stringResource(R.string.deep_most_yourself_sub)) {
                        SingleChipGrid(Chronotype.entries.toList(), state.chronotype, { stringResource(it.labelRes) }, viewModel::setChronotype)
                    }
                    9 -> SurveyQuestionPage(stringResource(R.string.deep_focus_stretch), stringResource(R.string.deep_focus_stretch_sub)) {
                        Text(stringResource(R.string.pdf_n_minutes, state.focusDurationMinutes), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Slider(state.focusDurationMinutes.toFloat(), { viewModel.setFocusDuration(it.toInt()) }, valueRange = 5f..90f, steps = 16)
                    }
                    10 -> SurveyQuestionPage(stringResource(R.string.deep_sleep_rhythm), stringResource(R.string.deep_sleep_rhythm_sub)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TimePickerField(
                                value = state.sleepBedtime,
                                label = stringResource(R.string.deep_bedtime),
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
                                label = stringResource(R.string.deep_wake_up),
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
                    11 -> SurveyQuestionPage(stringResource(R.string.deep_schedule_choice), stringResource(R.string.deep_schedule_choice_sub)) {
                        SingleChipGrid(SleepScheduleOrigin.entries.toList(), state.sleepScheduleOrigin, { stringResource(it.labelRes) }, viewModel::setSleepScheduleOrigin)
                    }
                    12 -> SurveyQuestionPage(stringResource(R.string.deep_medication), stringResource(R.string.deep_medication_sub)) {
                        // 'yes' opens the medication list, which still needs filling in
                        SingleChipGrid(
                            MedicationStatus.entries.toList(),
                            state.medicationStatus,
                            { stringResource(it.labelRes) },
                            viewModel::setMedicationStatus,
                            advanceOn = { it != MedicationStatus.Yes },
                        )
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
                    13 -> SurveyQuestionPage(stringResource(R.string.deep_interrupted), stringResource(R.string.deep_interrupted_sub)) {
                        SingleChipGrid(InterruptionRecall.entries.toList(), state.interruptionRecall, { stringResource(it.labelRes) }, viewModel::setInterruptionRecall)
                    }
                    14 -> SurveyQuestionPage(stringResource(R.string.deep_write_down), stringResource(R.string.deep_write_down_sub)) {
                        SingleChipGrid(CaptureNeed.entries.toList(), state.captureNeed, { stringResource(it.labelRes) }, viewModel::setCaptureNeed)
                    }
                    15 -> SurveyQuestionPage(stringResource(R.string.deep_plans_change), stringResource(R.string.deep_plans_change_sub)) {
                        SingleChipGrid(PlanChangeImpact.entries.toList(), state.planChangeImpact, { stringResource(it.labelRes) }, viewModel::setPlanChangeImpact)
                    }
                    16 -> SurveyQuestionPage(stringResource(R.string.deep_back_to_task), stringResource(R.string.deep_back_to_task_sub)) {
                        SingleChipGrid(TaskReturnEffort.entries.toList(), state.taskReturnEffort, { stringResource(it.labelRes) }, viewModel::setTaskReturnEffort)
                    }
                    17 -> SurveyQuestionPage(stringResource(R.string.deep_hold_back), stringResource(R.string.deep_hold_back_sub)) {
                        ChipGrid(ImpulseArea.entries.toList(), state.impulseAreas.toSet(), { stringResource(it.labelRes) }, viewModel::toggleImpulseArea)
                    }
                    18 -> SurveyQuestionPage(stringResource(R.string.deep_reminder_sound), stringResource(R.string.deep_change_any_time)) {
                        SingleChipGrid(NudgeTone.entries.toList(), state.nudgeTone, { stringResource(it.labelRes) }, viewModel::setNudgeTone)
                    }
                    19 -> SurveyQuestionPage(stringResource(R.string.deep_too_much), stringResource(R.string.deep_too_much_sub)) {
                        SingleChipGrid(CheckInCeiling.entries.toList(), state.checkInCeiling, { stringResource(it.labelRes) }, viewModel::setCheckInCeiling)
                    }
                    20 -> SurveyQuestionPage(stringResource(R.string.deep_miss_something), stringResource(R.string.deep_miss_something_sub)) {
                        SingleChipGrid(MissedTaskResponse.entries.toList(), state.missedTaskResponse, { stringResource(it.labelRes) }, viewModel::setMissedTaskResponse)
                    }
                    21 -> SurveyQuestionPage(stringResource(R.string.deep_alongside), stringResource(R.string.deep_alongside_sub)) {
                        SingleChipGrid(BodyDoublingInterest.entries.toList(), state.bodyDoublingInterest, { stringResource(it.labelRes) }, viewModel::setBodyDoublingInterest)
                    }
                    22 -> SurveyQuestionPage(stringResource(R.string.deep_where_done), stringResource(R.string.deep_where_done_sub)) {
                        SingleChipGrid(WorkEnvironment.entries.toList(), state.workEnvironment, { stringResource(it.labelRes) }, viewModel::setWorkEnvironment)
                    }
                    23 -> SurveyQuestionPage(stringResource(R.string.deep_tried), stringResource(R.string.deep_tried_sub)) {
                        ChipGrid(PastStrategy.entries.toList(), state.pastStrategies.toSet(), { stringResource(it.labelRes) }, viewModel::togglePastStrategy)
                    }
                    24 -> SurveyQuestionPage(stringResource(R.string.deep_already_helps), stringResource(R.string.deep_already_helps_sub)) {
                        ChipGrid(CopingStrategy.entries.toList(), state.copingStrategies, { stringResource(it.labelRes) }, viewModel::toggleCopingStrategy)
                    }
                    25 -> SurveyQuestionPage(stringResource(R.string.deep_pain_point), stringResource(R.string.deep_pain_point_sub)) {
                        OutlinedTextField(
                            value = state.painPoint,
                            onValueChange = viewModel::setPainPoint,
                            placeholder = { Text(stringResource(R.string.deep_pain_point_placeholder)) },
                            modifier = Modifier.fillMaxWidth().height(130.dp),
                            shape = RoundedCornerShape(18.dp),
                            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary),
                        )
                        Text("${state.painPoint.length} / 280", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> SurveyQuestionPage(stringResource(R.string.deep_choose_voice), stringResource(R.string.deep_choose_voice_sub)) {
                        SingleChipGrid(AiTone.entries.toList(), state.aiTone, { stringResource(it.labelRes) }, viewModel::setAiTone)
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
            title = if (target == "bedtime") stringResource(R.string.deep_choose_bedtime) else stringResource(R.string.deep_choose_wake),
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
