package com.muradgalayev.brainbuddy.data.mapper

import com.muradgalayev.brainbuddy.data.remote.dto.AdhdProfileDto
import com.muradgalayev.brainbuddy.domain.model.AdhdProfile
import com.muradgalayev.brainbuddy.domain.model.AdhdSymptom
import com.muradgalayev.brainbuddy.domain.model.AiTone
import com.muradgalayev.brainbuddy.domain.model.CopingStrategy
import com.muradgalayev.brainbuddy.domain.model.DiagnosisStatus
import com.muradgalayev.brainbuddy.domain.model.Medication
import com.muradgalayev.brainbuddy.domain.model.MedicationStatus
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
    topGoal = TopGoal.fromKey(topGoal),
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
    surveyCompleted = surveyCompleted,
    surveyVersion = SurveyVersion.fromRaw(surveyVersion),
)

fun AdhdProfile.toDto(): AdhdProfileDto = AdhdProfileDto(
    userId = userId,
    ageRange = ageRange,
    diagnosisStatus = diagnosisStatus?.key.orEmpty(),
    primarySymptoms = primarySymptoms.map { it.key },
    topGoal = topGoal?.key.orEmpty(),
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
    surveyCompleted = surveyCompleted,
    surveyVersion = surveyVersion.raw,
)

private fun encodeMedications(meds: List<Medication>): String =
    if (meds.isEmpty()) "" else medsJson.encodeToString(meds)

// Legacy rows store the medication as plain free-form text. New rows store
// a JSON array. Try JSON first, fall back to wrapping the string as a single med.
private fun decodeMedications(raw: String): List<Medication> {
    if (raw.isBlank()) return emptyList()
    val trimmed = raw.trim()
    if (trimmed.startsWith("[")) {
        runCatching { medsJson.decodeFromString<List<Medication>>(trimmed) }
            .getOrNull()
            ?.let { return it }
    }
    return listOf(Medication(name = trimmed))
}
