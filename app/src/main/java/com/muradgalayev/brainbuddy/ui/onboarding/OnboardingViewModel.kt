package com.muradgalayev.brainbuddy.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.location.LocationCityOutcome
import com.muradgalayev.brainbuddy.data.location.LocationCityResolver
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.data.repository.PlacesRepository
import com.muradgalayev.brainbuddy.domain.model.AdhdProfile
import com.muradgalayev.brainbuddy.domain.model.AdhdSymptom
import com.muradgalayev.brainbuddy.domain.model.AiTone
import com.muradgalayev.brainbuddy.domain.model.City
import com.muradgalayev.brainbuddy.domain.model.CopingStrategy
import com.muradgalayev.brainbuddy.domain.model.DiagnosisStatus
import com.muradgalayev.brainbuddy.domain.model.Medication
import com.muradgalayev.brainbuddy.domain.model.MedicationSlot
import com.muradgalayev.brainbuddy.domain.model.MedicationStatus
import com.muradgalayev.brainbuddy.domain.model.ProductiveTime
import com.muradgalayev.brainbuddy.domain.model.SurveyVersion
import com.muradgalayev.brainbuddy.domain.model.TopGoal
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OnboardingUiState(
    val username: String = "",
    val usernameAvailability: UsernameAvailability = UsernameAvailability.Idle,
    val ageRange: String = "",
    val diagnosisStatus: DiagnosisStatus? = null,
    val primarySymptoms: Set<AdhdSymptom> = emptySet(),
    val topGoal: TopGoal? = null,

    val productiveTime: ProductiveTime? = null,
    val focusDurationMinutes: Int = 25,
    val sleepBedtime: String = "",
    val sleepWakeTime: String = "",
    val medicationStatus: MedicationStatus? = null,
    val medications: List<Medication> = emptyList(),
    val copingStrategies: Set<CopingStrategy> = emptySet(),
    val painPoint: String = "",
    val aiTone: AiTone? = null,

    val cities: List<City> = emptyList(),
    val cityId: String? = null,
    val locationStatus: LocationLookupStatus = LocationLookupStatus.Idle,

    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val previousSurveyVersion: SurveyVersion = SurveyVersion.None,
    val isSubmitting: Boolean = false,
    val submitError: String? = null,
    val submitSuccess: Boolean = false,
)

sealed class LocationLookupStatus {
    data object Idle : LocationLookupStatus()
    data object Locating : LocationLookupStatus()
    data class Matched(val cityName: String, val viaName: String) : LocationLookupStatus()
    data class NoMatch(val detected: String) : LocationLookupStatus()
    data object PermissionDenied : LocationLookupStatus()
    data object LocationServicesOff : LocationLookupStatus()
    data class Error(val message: String) : LocationLookupStatus()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val adhdProfileRepository: AdhdProfileRepository,
    private val authRepository: AuthRepository,
    private val placesRepository: PlacesRepository,
    private val locationCityResolver: LocationCityResolver,
) : ViewModel() {

    private val _state = MutableStateFlow(buildInitialState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var usernameCheckJob: Job? = null

    init {
        // If caches were cold we showed isLoading=true; do a fresh fetch.
        // Otherwise we already painted with cached data — silently re-validate
        // in the background so any server-side change appears soon.
        prefillFromNetwork(initialPaintFromCache = !_state.value.isLoading)
    }

    private fun buildInitialState(): OnboardingUiState {
        val cachedProfile = adhdProfileRepository.peekProfile()
        val cachedAuth = authRepository.peekProfile()
        val cachedCities = placesRepository.peekCities()

        if (cachedProfile == null && cachedAuth == null && cachedCities == null) {
            return OnboardingUiState(isLoading = true)
        }

        val cities = cachedCities.orEmpty()
        val username = cachedAuth?.username.orEmpty()

        return if (cachedProfile != null) {
            OnboardingUiState(
                username = username,
                ageRange = cachedProfile.ageRange,
                diagnosisStatus = cachedProfile.diagnosisStatus,
                primarySymptoms = cachedProfile.primarySymptoms.toSet(),
                topGoal = cachedProfile.topGoal,
                productiveTime = cachedProfile.productiveTime,
                focusDurationMinutes = cachedProfile.focusDurationMinutes ?: 25,
                sleepBedtime = cachedProfile.sleepBedtime,
                sleepWakeTime = cachedProfile.sleepWakeTime,
                medicationStatus = cachedProfile.medicationStatus,
                medications = cachedProfile.medications,
                copingStrategies = cachedProfile.copingStrategies.toSet(),
                painPoint = cachedProfile.painPoint,
                aiTone = cachedProfile.aiTonePreference,
                cities = cities,
                cityId = cachedProfile.cityId,
                isLoading = false,
                isEditing = cachedProfile.surveyCompleted,
                previousSurveyVersion = cachedProfile.surveyVersion,
            )
        } else {
            OnboardingUiState(
                username = username,
                cities = cities,
                isLoading = false,
            )
        }
    }

    private fun prefillFromNetwork(initialPaintFromCache: Boolean) {
        viewModelScope.launch {
            if (!initialPaintFromCache) {
                _state.update { it.copy(isLoading = true) }
            }
            val existing = runCatching { adhdProfileRepository.getProfile() }.getOrNull()
            val currentUsername = runCatching { authRepository.getProfile()?.username }
                .getOrNull()
                .orEmpty()
            val cities = placesRepository.listCities().getOrDefault(emptyList())

            _state.update { current ->
                if (existing != null) {
                    current.copy(
                        // Don't clobber edits in flight — only fill blanks/defaults.
                        username = current.username.ifBlank { currentUsername },
                        ageRange = current.ageRange.ifBlank { existing.ageRange },
                        diagnosisStatus = current.diagnosisStatus ?: existing.diagnosisStatus,
                        primarySymptoms = current.primarySymptoms.ifEmpty { existing.primarySymptoms.toSet() },
                        topGoal = current.topGoal ?: existing.topGoal,
                        productiveTime = current.productiveTime ?: existing.productiveTime,
                        focusDurationMinutes = if (current.focusDurationMinutes == 25)
                            existing.focusDurationMinutes ?: current.focusDurationMinutes
                        else current.focusDurationMinutes,
                        sleepBedtime = current.sleepBedtime.ifBlank { existing.sleepBedtime },
                        sleepWakeTime = current.sleepWakeTime.ifBlank { existing.sleepWakeTime },
                        medicationStatus = current.medicationStatus ?: existing.medicationStatus,
                        medications = if (current.medications.isEmpty()) existing.medications else current.medications,
                        copingStrategies = current.copingStrategies.ifEmpty { existing.copingStrategies.toSet() },
                        painPoint = current.painPoint.ifBlank { existing.painPoint },
                        aiTone = current.aiTone ?: existing.aiTonePreference,
                        cities = if (cities.isNotEmpty()) cities else current.cities,
                        cityId = current.cityId ?: existing.cityId,
                        isLoading = false,
                        isEditing = existing.surveyCompleted,
                        previousSurveyVersion = existing.surveyVersion,
                    )
                } else {
                    current.copy(
                        username = current.username.ifBlank { currentUsername },
                        cities = if (cities.isNotEmpty()) cities else current.cities,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun setCity(cityId: String) = _state.update {
        it.copy(cityId = cityId, locationStatus = LocationLookupStatus.Idle)
    }

    fun useCurrentLocation() {
        if (_state.value.locationStatus is LocationLookupStatus.Locating) return
        _state.update { it.copy(locationStatus = LocationLookupStatus.Locating) }

        viewModelScope.launch {
            val outcome = locationCityResolver.detectCityCandidates()
            _state.update { current ->
                when (outcome) {
                    is LocationCityOutcome.PermissionMissing ->
                        current.copy(locationStatus = LocationLookupStatus.PermissionDenied)
                    is LocationCityOutcome.LocationServicesOff ->
                        current.copy(locationStatus = LocationLookupStatus.LocationServicesOff)
                    is LocationCityOutcome.GeocoderUnavailable ->
                        current.copy(locationStatus = LocationLookupStatus.Error(
                            "Address lookup isn't available on this device — pick your city manually."
                        ))
                    is LocationCityOutcome.NoLocation ->
                        current.copy(locationStatus = LocationLookupStatus.Error(
                            "Couldn't get a location fix. Try again in a moment."
                        ))
                    is LocationCityOutcome.Error ->
                        current.copy(locationStatus = LocationLookupStatus.Error(outcome.message))
                    is LocationCityOutcome.Detected -> {
                        val match = matchCandidate(outcome.candidates, current.cities)
                        if (match != null) {
                            current.copy(
                                cityId = match.city.id,
                                locationStatus = LocationLookupStatus.Matched(
                                    cityName = match.city.name,
                                    viaName = match.matchedOn,
                                ),
                            )
                        } else {
                            current.copy(
                                locationStatus = LocationLookupStatus.NoMatch(
                                    detected = outcome.candidates.first()
                                )
                            )
                        }
                    }
                }
            }
        }
    }

    private data class CityMatch(val city: City, val matchedOn: String)

    private fun matchCandidate(candidates: List<String>, cities: List<City>): CityMatch? {
        if (cities.isEmpty()) return null
        for (candidate in candidates) {
            cities.firstOrNull { it.name.equals(candidate, ignoreCase = true) }
                ?.let { return CityMatch(it, candidate) }
        }
        for (candidate in candidates) {
            cities.firstOrNull {
                candidate.contains(it.name, ignoreCase = true) ||
                    it.name.contains(candidate, ignoreCase = true)
            }?.let { return CityMatch(it, candidate) }
        }
        return null
    }

    fun consumeLocationStatus() = _state.update {
        it.copy(locationStatus = LocationLookupStatus.Idle)
    }

    fun setUsername(value: String) {
        _state.update { it.copy(username = value) }
        scheduleUsernameCheck(value)
    }

    private fun scheduleUsernameCheck(raw: String) {
        usernameCheckJob?.cancel()
        if (!isUsernameSyntaxValid(raw)) {
            _state.update {
                it.copy(
                    usernameAvailability = if (raw.isEmpty()) UsernameAvailability.Idle
                    else UsernameAvailability.Invalid
                )
            }
            return
        }
        _state.update { it.copy(usernameAvailability = UsernameAvailability.Checking) }
        usernameCheckJob = viewModelScope.launch {
            delay(400) // debounce
            val available = authRepository.isUsernameAvailable(raw)
            _state.update {
                if (it.username == raw) {
                    it.copy(
                        usernameAvailability = if (available) UsernameAvailability.Available
                        else UsernameAvailability.Taken
                    )
                } else {
                    it
                }
            }
        }
    }

    fun setAgeRange(value: String) = _state.update { it.copy(ageRange = value) }
    fun setDiagnosisStatus(value: DiagnosisStatus) = _state.update { it.copy(diagnosisStatus = value) }
    fun toggleSymptom(symptom: AdhdSymptom) = _state.update {
        val newSet = if (symptom in it.primarySymptoms) it.primarySymptoms - symptom
        else it.primarySymptoms + symptom
        it.copy(primarySymptoms = newSet)
    }
    fun setTopGoal(value: TopGoal) = _state.update { it.copy(topGoal = value) }

    fun setProductiveTime(value: ProductiveTime) = _state.update { it.copy(productiveTime = value) }
    fun setFocusDuration(minutes: Int) = _state.update { it.copy(focusDurationMinutes = minutes) }
    fun setBedtime(value: String) = _state.update { it.copy(sleepBedtime = value) }
    fun setWakeTime(value: String) = _state.update { it.copy(sleepWakeTime = value) }
    fun setMedicationStatus(value: MedicationStatus) = _state.update {
        it.copy(
            medicationStatus = value,
            medications = if (value != MedicationStatus.Yes) emptyList() else it.medications,
        )
    }

    fun addMedication() = _state.update {
        it.copy(medications = it.medications + Medication())
    }

    fun removeMedication(id: String) = _state.update {
        it.copy(medications = it.medications.filterNot { m -> m.id == id })
    }

    fun setMedicationName(id: String, name: String) = updateMed(id) { it.copy(name = name) }
    fun setMedicationDose(id: String, dose: String) = updateMed(id) { it.copy(dose = dose) }
    fun toggleMedicationSlot(id: String, slot: MedicationSlot) = updateMed(id) { current ->
        val newSlots = if (slot.key in current.slots) current.slots - slot.key
        else current.slots + slot.key
        current.copy(slots = newSlots)
    }

    private inline fun updateMed(id: String, transform: (Medication) -> Medication) {
        _state.update { state ->
            state.copy(medications = state.medications.map { m ->
                if (m.id == id) transform(m) else m
            })
        }
    }
    fun toggleCopingStrategy(value: CopingStrategy) = _state.update {
        val newSet = if (value in it.copingStrategies) it.copingStrategies - value
        else it.copingStrategies + value
        it.copy(copingStrategies = newSet)
    }
    fun setPainPoint(value: String) = _state.update {
        it.copy(painPoint = value.take(280))
    }
    fun setAiTone(value: AiTone) = _state.update { it.copy(aiTone = value) }

    /**
     * Validates the fields the chosen survey requires, then persists.
     * Sets `submitSuccess = true` on success so the screen can navigate.
     */
    fun submit(version: SurveyVersion) {
        val s = _state.value
        if (s.isSubmitting) return

        val usernameOk =
            s.usernameAvailability == UsernameAvailability.Available ||
                (s.usernameAvailability == UsernameAvailability.Idle && s.username.isNotBlank())
        if (!usernameOk) {
            _state.update { it.copy(submitError = "Pick an available username first") }
            return
        }
        // First-time onboarding still has to answer the required questions.
        // When editing an already-completed profile, allow partial saves —
        // the user might just want to update one field (e.g. their city).
        if (!s.isEditing) {
            if (s.ageRange.isBlank() || s.diagnosisStatus == null ||
                s.primarySymptoms.isEmpty() || s.topGoal == null
            ) {
                _state.update { it.copy(submitError = "Please answer all questions") }
                return
            }
            if (version == SurveyVersion.Deep) {
                if (s.productiveTime == null || s.medicationStatus == null || s.aiTone == null) {
                    _state.update { it.copy(submitError = "Please answer all Deep Dive questions") }
                    return
                }
            }
        }

        _state.update { it.copy(isSubmitting = true, submitError = null) }

        viewModelScope.launch {
            try {
                // First-time signups don't always have a profiles row yet
                // (the auth.users → profiles trigger may be missing). Make
                // sure one exists before we try to update it, otherwise
                // decodeSingle() blows up with "List is empty.".
                val existing = authRepository.ensureProfileExists()
                val displayName = existing.displayName
                    ?: authRepository.getCurrentUserFullName()
                    ?: ""
                authRepository.updateProfile(
                    displayName = displayName,
                    username = s.username.trim().lowercase(),
                )

                val userId = authRepository.getCurrentUserId()
                    ?: error("No logged-in user")

                // Preserve the highest survey tier the user has reached. Editing
                // shouldn't quietly downgrade Deep → Quick.
                val effectiveVersion = if (s.previousSurveyVersion == SurveyVersion.Deep) {
                    SurveyVersion.Deep
                } else {
                    version
                }
                val profile = AdhdProfile(
                    userId = userId,
                    ageRange = s.ageRange,
                    diagnosisStatus = s.diagnosisStatus,
                    primarySymptoms = s.primarySymptoms.toList(),
                    topGoal = s.topGoal,
                    productiveTime = s.productiveTime,
                    focusDurationMinutes = if (effectiveVersion == SurveyVersion.Deep) s.focusDurationMinutes else null,
                    sleepBedtime = s.sleepBedtime,
                    sleepWakeTime = s.sleepWakeTime,
                    medicationStatus = s.medicationStatus,
                    medications = s.medications
                        .filter { it.name.isNotBlank() }
                        .map { it.copy(name = it.name.trim(), dose = it.dose.trim()) },
                    copingStrategies = s.copingStrategies.toList(),
                    painPoint = s.painPoint.trim(),
                    aiTonePreference = s.aiTone,
                    cityId = s.cityId,
                    surveyCompleted = true,
                    surveyVersion = effectiveVersion,
                )
                adhdProfileRepository.saveProfile(profile)

                _state.update { it.copy(isSubmitting = false, submitSuccess = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        submitError = "Couldn't save: ${e.message ?: "unknown error"}",
                    )
                }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(submitError = null) }
}
