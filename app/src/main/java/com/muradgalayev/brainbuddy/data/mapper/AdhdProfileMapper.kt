package com.muradgalayev.brainbuddy.data.mapper

import com.muradgalayev.brainbuddy.data.remote.dto.AdhdProfileDto
import com.muradgalayev.brainbuddy.domain.model.AdhdPresentation
import com.muradgalayev.brainbuddy.domain.model.AdhdProfile
import com.muradgalayev.brainbuddy.domain.model.AdhdSymptom
import com.muradgalayev.brainbuddy.domain.model.AiTone
import com.muradgalayev.brainbuddy.domain.model.BodyDoublingInterest
import com.muradgalayev.brainbuddy.domain.model.CaptureNeed
import com.muradgalayev.brainbuddy.domain.model.CheckInCeiling
import com.muradgalayev.brainbuddy.domain.model.Chronotype
import com.muradgalayev.brainbuddy.domain.model.CoOccurringCondition
import com.muradgalayev.brainbuddy.domain.model.CopingStrategy
import com.muradgalayev.brainbuddy.domain.model.DiagnosisStatus
import com.muradgalayev.brainbuddy.domain.model.ImpulseArea
import com.muradgalayev.brainbuddy.domain.model.InterruptionRecall
import com.muradgalayev.brainbuddy.domain.model.MissedTaskResponse
import com.muradgalayev.brainbuddy.domain.model.NudgeTone
import com.muradgalayev.brainbuddy.domain.model.PastStrategy
import com.muradgalayev.brainbuddy.domain.model.PlanChangeImpact
import com.muradgalayev.brainbuddy.domain.model.SleepScheduleOrigin
import com.muradgalayev.brainbuddy.domain.model.TaskReturnEffort
import com.muradgalayev.brainbuddy.domain.model.WorkEnvironment
import com.muradgalayev.brainbuddy.domain.model.Medication
import com.muradgalayev.brainbuddy.domain.model.MedicationStatus
import com.muradgalayev.brainbuddy.domain.model.MedicationUnit
import com.muradgalayev.brainbuddy.domain.model.ProductiveTime
import com.muradgalayev.brainbuddy.domain.model.SurveyVersion
import com.muradgalayev.brainbuddy.domain.model.TopGoal
import kotlinx.serialization.json.Json

private val medsJson = Json { ignoreUnknownKeys = true }

fun AdhdProfileDto.toDomain(): AdhdProfile = AdhdProfile(
    userId = userId,
    ageRange = ageRange,
    diagnosisStatus = DiagnosisStatus.fromKey(diagnosisStatus),
    primarySymptoms = primarySymptoms.mapNotNull { AdhdSymptom.fromKey(it) },
    // N16 turned one goal into up to three. a row written before that has only the single column,
    // so it's promoted to a one-item list rather than read as 'no goal chosen', which would
    // silently wipe what the user told us
    topGoals = topGoals.mapNotNull { TopGoal.fromKey(it) }
        .ifEmpty { listOfNotNull(TopGoal.fromKey(topGoal)) }
        .take(AdhdProfile.MAX_TOP_GOALS),
    productiveTime = ProductiveTime.fromKey(productiveTime),
    focusDurationMinutes = focusDurationMinutes,
    sleepBedtime = sleepBedtime,
    sleepWakeTime = sleepWakeTime,
    medicationStatus = MedicationStatus.fromKey(medicationStatus),
    medications = decodeMedications(medicationName),
    copingStrategies = copingStrategies.mapNotNull { CopingStrategy.fromKey(it) },
    painPoint = painPoint,
    aiTonePreference = AiTone.fromKey(aiTonePreference),
    cityId = cityId,
    presentation = AdhdPresentation.fromKey(presentation),
    coOccurring = coOccurring.mapNotNull { CoOccurringCondition.fromKey(it) },
    chronotype = Chronotype.fromKey(chronotype),
    sleepScheduleOrigin = SleepScheduleOrigin.fromKey(sleepScheduleOrigin),
    interruptionRecall = InterruptionRecall.fromKey(interruptionRecall),
    captureNeed = CaptureNeed.fromKey(captureNeed),
    planChangeImpact = PlanChangeImpact.fromKey(planChangeImpact),
    taskReturnEffort = TaskReturnEffort.fromKey(taskReturnEffort),
    impulseAreas = impulseAreas.mapNotNull { ImpulseArea.fromKey(it) },
    nudgeTone = NudgeTone.fromKey(nudgeTone),
    checkInCeiling = CheckInCeiling.fromKey(checkInCeiling),
    missedTaskResponse = MissedTaskResponse.fromKey(missedTaskResponse),
    bodyDoublingInterest = BodyDoublingInterest.fromKey(bodyDoublingInterest),
    workEnvironment = WorkEnvironment.fromKey(workEnvironment),
    pastStrategies = pastStrategies.mapNotNull { PastStrategy.fromKey(it) },
    surveyCompleted = surveyCompleted,
    surveyVersion = SurveyVersion.fromRaw(surveyVersion),
)

fun AdhdProfile.toDto(): AdhdProfileDto = AdhdProfileDto(
    userId = userId,
    ageRange = ageRange,
    diagnosisStatus = diagnosisStatus?.key.orEmpty(),
    primarySymptoms = primarySymptoms.map { it.key },
    // both columns, always. the legacy one keeps an older build on another device readable rather
    // than showing that user an empty goal
    topGoal = primaryGoal?.key.orEmpty(),
    topGoals = topGoals.map { it.key },
    productiveTime = productiveTime?.key.orEmpty(),
    focusDurationMinutes = focusDurationMinutes,
    sleepBedtime = sleepBedtime,
    sleepWakeTime = sleepWakeTime,
    medicationStatus = medicationStatus?.key.orEmpty(),
    medicationName = encodeMedications(medications),
    copingStrategies = copingStrategies.map { it.key },
    painPoint = painPoint,
    aiTonePreference = aiTonePreference?.key.orEmpty(),
    cityId = cityId,
    presentation = presentation?.key.orEmpty(),
    coOccurring = coOccurring.map { it.key },
    chronotype = chronotype?.key.orEmpty(),
    sleepScheduleOrigin = sleepScheduleOrigin?.key.orEmpty(),
    interruptionRecall = interruptionRecall?.key.orEmpty(),
    captureNeed = captureNeed?.key.orEmpty(),
    planChangeImpact = planChangeImpact?.key.orEmpty(),
    taskReturnEffort = taskReturnEffort?.key.orEmpty(),
    impulseAreas = impulseAreas.map { it.key },
    nudgeTone = nudgeTone?.key.orEmpty(),
    checkInCeiling = checkInCeiling?.key.orEmpty(),
    missedTaskResponse = missedTaskResponse?.key.orEmpty(),
    bodyDoublingInterest = bodyDoublingInterest?.key.orEmpty(),
    workEnvironment = workEnvironment?.key.orEmpty(),
    pastStrategies = pastStrategies.map { it.key },
    surveyCompleted = surveyCompleted,
    surveyVersion = surveyVersion.raw,
)

private fun encodeMedications(meds: List<Medication>): String =
    if (meds.isEmpty()) "" else medsJson.encodeToString(meds)

// legacy rows store the medication as plain free-form text, new rows store a JSON array. try
// JSON first, fall back to wrapping the string as a single med
private fun decodeMedications(raw: String): List<Medication> {
    if (raw.isBlank()) return emptyList()
    val trimmed = raw.trim()
    if (trimmed.startsWith("[")) {
        runCatching { medsJson.decodeFromString<List<Medication>>(trimmed) }
            .getOrNull()
            ?.let { meds -> return meds.map { it.withSplitDoseUnit() } }
    }
    return listOf(Medication(name = trimmed))
}

// separates '20mg' into an amount and a unit for rows written before the unit was its own
// field. left alone once a unit is set, and left alone when the text doesn't end in a unit we
// know, so someone's free-form note stays as they wrote it
private fun Medication.withSplitDoseUnit(): Medication {
    if (doseUnit.isNotBlank()) return this
    val (amount, unit) = MedicationUnit.splitLegacy(dose) ?: return this
    return copy(dose = amount, doseUnit = unit.key)
}
