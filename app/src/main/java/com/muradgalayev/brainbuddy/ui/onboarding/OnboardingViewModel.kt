package com.muradgalayev.brainbuddy.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.location.LocationCityOutcome
import com.muradgalayev.brainbuddy.data.location.LocationCityResolver
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.data.repository.PlacesRepository
import com.muradgalayev.brainbuddy.data.repository.UsernameTakenException
import com.muradgalayev.brainbuddy.data.notifications.QuestionnaireReminderScheduler
import com.muradgalayev.brainbuddy.domain.model.AdhdProfile
import com.muradgalayev.brainbuddy.domain.model.AdhdPresentation
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
import com.muradgalayev.brainbuddy.domain.model.toggleWithExclusives
import com.muradgalayev.brainbuddy.domain.model.AdhdSymptom
import com.muradgalayev.brainbuddy.domain.model.AiTone
import com.muradgalayev.brainbuddy.domain.model.City
import com.muradgalayev.brainbuddy.domain.model.CopingStrategy
import com.muradgalayev.brainbuddy.domain.model.DiagnosisStatus
import com.muradgalayev.brainbuddy.domain.model.Medication
import com.muradgalayev.brainbuddy.domain.model.MedicationSlot
import com.muradgalayev.brainbuddy.domain.model.MedicationStatus
import com.muradgalayev.brainbuddy.domain.model.MedicationUnit
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
import com.muradgalayev.brainbuddy.R
import com.muradgalayev.brainbuddy.ui.utils.UiText
import com.muradgalayev.brainbuddy.ui.utils.asUiText
import com.muradgalayev.brainbuddy.ui.utils.uiText

data class OnboardingUiState(
    val firstName: String = "",
    val lastName: String = "",
    val username: String = "",
    val usernameAvailability: UsernameAvailability = UsernameAvailability.Idle,
    val ageRange: String = "",
    val diagnosisStatus: DiagnosisStatus? = null,
    val primarySymptoms: Set<AdhdSymptom> = emptySet(),
    // N16, up to AdhdProfile.MAX_TOP_GOALS
    val topGoals: List<TopGoal> = emptyList(),

    val productiveTime: ProductiveTime? = null,
    val focusDurationMinutes: Int = 25,
    val sleepBedtime: String = "",
    val sleepWakeTime: String = "",
    val medicationStatus: MedicationStatus? = null,
    val medications: List<Medication> = emptyList(),
    val copingStrategies: Set<CopingStrategy> = emptySet(),
    val painPoint: String = "",
    val aiTone: AiTone? = null,

    // intake revision (N1-N18)
    val presentation: AdhdPresentation? = null,
    val coOccurring: List<CoOccurringCondition> = emptyList(),
    val chronotype: Chronotype? = null,
    val sleepScheduleOrigin: SleepScheduleOrigin? = null,
    val interruptionRecall: InterruptionRecall? = null,
    val captureNeed: CaptureNeed? = null,
    val planChangeImpact: PlanChangeImpact? = null,
    val taskReturnEffort: TaskReturnEffort? = null,
    val impulseAreas: List<ImpulseArea> = emptyList(),
    val nudgeTone: NudgeTone? = null,
    val checkInCeiling: CheckInCeiling? = null,
    val missedTaskResponse: MissedTaskResponse? = null,
    val bodyDoublingInterest: BodyDoublingInterest? = null,
    val workEnvironment: WorkEnvironment? = null,
    val pastStrategies: List<PastStrategy> = emptyList(),

    val cities: List<City> = emptyList(),
    val cityId: String? = null,
    val locationStatus: LocationLookupStatus = LocationLookupStatus.Idle,

    val isLoading: Boolean = false,
    val isEditing: Boolean = false,
    val previousSurveyVersion: SurveyVersion = SurveyVersion.None,
    val isSubmitting: Boolean = false,
    val submitError: SurveyError? = null,
    val submitSuccess: Boolean = false,
    // true when the last save landed on the device but not on the server. the success panel says
    // so rather than pretending everything is uploaded
    val savedOfflineOnly: Boolean = false,
)

sealed class LocationLookupStatus {
    data object Idle : LocationLookupStatus()
    data object Locating : LocationLookupStatus()
    data class Matched(val cityName: String, val viaName: String) : LocationLookupStatus()
    data class NoMatch(val detected: String) : LocationLookupStatus()
    data object PermissionDenied : LocationLookupStatus()
    data object LocationServicesOff : LocationLookupStatus()
    data class Error(val message: UiText) : LocationLookupStatus()
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val adhdProfileRepository: AdhdProfileRepository,
    private val authRepository: AuthRepository,
    private val placesRepository: PlacesRepository,
    private val locationCityResolver: LocationCityResolver,
    private val questionnaireReminderScheduler: QuestionnaireReminderScheduler,
) : ViewModel() {

    private val _state = MutableStateFlow(buildInitialState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private var usernameCheckJob: Job? = null

    init {
        // cold caches meant isLoading=true, so fetch fresh. otherwise we already painted from cache,
        // so re-validate quietly in the background instead
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
        val (firstName, lastName) = splitName(cachedAuth?.displayName)

        return if (cachedProfile != null) {
            cachedProfile.toSurveyAnswers(firstName = firstName, lastName = lastName, username = username).copy(
                cities = cities,
                isLoading = false,
                isEditing = cachedProfile.surveyCompleted,
                previousSurveyVersion = cachedProfile.surveyVersion,
            )
        } else {
            OnboardingUiState(
                firstName = firstName,
                lastName = lastName,
                username = username,
                cities = cities,
                isLoading = false,
            )
        }
    }

    private fun splitName(full: String?): Pair<String, String> {
        val parts = full?.trim().orEmpty().split(" ").filter { it.isNotBlank() }
        if (parts.isEmpty()) return "" to ""
        return parts.first() to parts.drop(1).joinToString(" ")
    }

    private fun prefillFromNetwork(initialPaintFromCache: Boolean) {
        viewModelScope.launch {
            if (!initialPaintFromCache) {
                _state.update { it.copy(isLoading = true) }
            }
            val existing = runCatching { adhdProfileRepository.getProfile() }.getOrNull()
            val authProfile = runCatching { authRepository.getProfile() }.getOrNull()
            val currentUsername = authProfile?.username.orEmpty()
            val (netFirst, netLast) = splitName(authProfile?.displayName)
            val cities = placesRepository.listCities().getOrDefault(emptyList())

            _state.update { current ->
                if (existing != null) {
                    current.copy(
                        // don't clobber edits in flight, only fill blanks and defaults
                        firstName = current.firstName.ifBlank { netFirst },
                        lastName = current.lastName.ifBlank { netLast },
                        username = current.username.ifBlank { currentUsername },
                        ageRange = current.ageRange.ifBlank { existing.ageRange },
                        diagnosisStatus = current.diagnosisStatus ?: existing.diagnosisStatus,
                        primarySymptoms = current.primarySymptoms.ifEmpty { existing.primarySymptoms.toSet() },
                        topGoals = current.topGoals.ifEmpty { existing.topGoals },
                        presentation = current.presentation ?: existing.presentation,
                        coOccurring = current.coOccurring.ifEmpty { existing.coOccurring },
                        chronotype = current.chronotype ?: existing.chronotype,
                        sleepScheduleOrigin = current.sleepScheduleOrigin ?: existing.sleepScheduleOrigin,
                        interruptionRecall = current.interruptionRecall ?: existing.interruptionRecall,
                        captureNeed = current.captureNeed ?: existing.captureNeed,
                        planChangeImpact = current.planChangeImpact ?: existing.planChangeImpact,
                        taskReturnEffort = current.taskReturnEffort ?: existing.taskReturnEffort,
                        impulseAreas = current.impulseAreas.ifEmpty { existing.impulseAreas },
                        nudgeTone = current.nudgeTone ?: existing.nudgeTone,
                        checkInCeiling = current.checkInCeiling ?: existing.checkInCeiling,
                        missedTaskResponse = current.missedTaskResponse ?: existing.missedTaskResponse,
                        bodyDoublingInterest = current.bodyDoublingInterest ?: existing.bodyDoublingInterest,
                        workEnvironment = current.workEnvironment ?: existing.workEnvironment,
                        pastStrategies = current.pastStrategies.ifEmpty { existing.pastStrategies },
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
                        firstName = current.firstName.ifBlank { netFirst },
                        lastName = current.lastName.ifBlank { netLast },
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
            // the fix is often near-instant, so hold the 'locating' animation for a beat and it reads as
            // deliberate work rather than a flicker
            val startedAt = System.currentTimeMillis()
            val outcome = locationCityResolver.detectCityCandidates()
            val elapsed = System.currentTimeMillis() - startedAt
            val minVisible = 1600L
            if (elapsed < minVisible) delay(minVisible - elapsed)
            _state.update { current ->
                when (outcome) {
                    is LocationCityOutcome.PermissionMissing ->
                        current.copy(locationStatus = LocationLookupStatus.PermissionDenied)
                    is LocationCityOutcome.LocationServicesOff ->
                        current.copy(locationStatus = LocationLookupStatus.LocationServicesOff)
                    is LocationCityOutcome.GeocoderUnavailable ->
                        current.copy(locationStatus = LocationLookupStatus.Error(
                            uiText(R.string.loc_geocoder_unavailable)
                        ))
                    is LocationCityOutcome.NoLocation ->
                        current.copy(locationStatus = LocationLookupStatus.Error(
                            uiText(R.string.loc_no_fix)
                        ))
                    is LocationCityOutcome.Error ->
                        current.copy(locationStatus = LocationLookupStatus.Error(outcome.message.asUiText()))
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

    fun setFirstName(value: String) = _state.update { it.copy(firstName = value.take(40)) }
    fun setLastName(value: String) = _state.update { it.copy(lastName = value.take(40)) }

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
    // N1. changing away from 'formally diagnosed' clears the presentation with it: N2 only exists
    // as a follow-up to that answer, and a stale value would store a subtype for someone who has
    // just said they don't have one
    fun setDiagnosisStatus(value: DiagnosisStatus) = _state.update {
        it.copy(
            diagnosisStatus = value,
            presentation = if (value == DiagnosisStatus.Diagnosed) it.presentation else null,
        )
    }
    fun toggleSymptom(symptom: AdhdSymptom) = _state.update {
        val newSet = if (symptom in it.primarySymptoms) it.primarySymptoms - symptom
        else it.primarySymptoms + symptom
        it.copy(primarySymptoms = newSet)
    }
    // N16, multi-select and capped. tapping a fourth is a no-op rather than silently dropping one
    // of the three already chosen, and the UI greys the rest out so the cap is visible first
    fun toggleTopGoal(value: TopGoal) = _state.update { s ->
        val next = when {
            value in s.topGoals -> s.topGoals - value
            s.topGoals.size >= AdhdProfile.MAX_TOP_GOALS -> s.topGoals
            else -> s.topGoals + value
        }
        s.copy(topGoals = next)
    }

    // intake revision (N1-N18)
    fun setPresentation(value: AdhdPresentation) = _state.update { it.copy(presentation = value) }
    fun toggleCoOccurring(value: CoOccurringCondition) = _state.update {
        it.copy(coOccurring = toggleWithExclusives(it.coOccurring, value, CoOccurringCondition.EXCLUSIVE))
    }
    fun setChronotype(value: Chronotype) = _state.update { it.copy(chronotype = value) }
    fun setSleepScheduleOrigin(value: SleepScheduleOrigin) =
        _state.update { it.copy(sleepScheduleOrigin = value) }
    fun setInterruptionRecall(value: InterruptionRecall) =
        _state.update { it.copy(interruptionRecall = value) }
    fun setCaptureNeed(value: CaptureNeed) = _state.update { it.copy(captureNeed = value) }
    fun setPlanChangeImpact(value: PlanChangeImpact) =
        _state.update { it.copy(planChangeImpact = value) }
    fun setTaskReturnEffort(value: TaskReturnEffort) =
        _state.update { it.copy(taskReturnEffort = value) }
    fun toggleImpulseArea(value: ImpulseArea) = _state.update {
        it.copy(impulseAreas = toggleWithExclusives(it.impulseAreas, value, ImpulseArea.EXCLUSIVE))
    }
    fun setNudgeTone(value: NudgeTone) = _state.update { it.copy(nudgeTone = value) }
    fun setCheckInCeiling(value: CheckInCeiling) = _state.update { it.copy(checkInCeiling = value) }
    fun setMissedTaskResponse(value: MissedTaskResponse) =
        _state.update { it.copy(missedTaskResponse = value) }
    fun setBodyDoublingInterest(value: BodyDoublingInterest) =
        _state.update { it.copy(bodyDoublingInterest = value) }
    fun setWorkEnvironment(value: WorkEnvironment) = _state.update { it.copy(workEnvironment = value) }
    fun togglePastStrategy(value: PastStrategy) = _state.update {
        it.copy(pastStrategies = toggleWithExclusives(it.pastStrategies, value, PastStrategy.EXCLUSIVE))
    }
    fun setMedicationTime(id: String, time: String) = updateMed(id) { it.copy(times = listOf(time)) }
    fun toggleMedicationAsNeeded(id: String) = updateMed(id) { it.copy(asNeeded = !it.asNeeded) }

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
    fun setMedicationDoseUnit(id: String, unit: MedicationUnit) =
        updateMed(id) { it.copy(doseUnit = unit.key) }
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

    // validates what the chosen survey requires, then persists. sets submitSuccess so we can navigate
    // what still blocks a submit, or null when the survey can be completed
    private fun missingAnswers(s: OnboardingUiState, version: SurveyVersion): SurveyError? {
        val usernameOk =
            s.usernameAvailability == UsernameAvailability.Available ||
                (s.usernameAvailability == UsernameAvailability.Idle && s.username.isNotBlank())
        if (!usernameOk) return SurveyError(SurveyError.Kind.UsernameTaken)
        // first-time onboarding still has to answer the required questions. when editing an already
        // completed profile, allow partial saves: they might just want to update their city
        if (s.isEditing) return null
        if (s.firstName.isBlank()) {
            return SurveyError(SurveyError.Kind.MissingAnswers, uiText(R.string.survey_detail_name))
        }
        if (s.ageRange.isBlank() || s.diagnosisStatus == null ||
            s.primarySymptoms.isEmpty() || s.topGoals.isEmpty()
        ) {
            return SurveyError(SurveyError.Kind.MissingAnswers)
        }
        if (version == SurveyVersion.Deep &&
            (s.productiveTime == null || s.medicationStatus == null || s.aiTone == null)
        ) {
            return SurveyError(SurveyError.Kind.MissingAnswers, uiText(R.string.survey_detail_deep_dive))
        }
        return null
    }

    fun submit(version: SurveyVersion) {
        val s = _state.value
        if (s.isSubmitting) return

        missingAnswers(s, version)?.let { error ->
            _state.update { it.copy(submitError = error) }
            return
        }

        _state.update { it.copy(isSubmitting = true, submitError = null) }

        viewModelScope.launch {
            try {
                // the name/username half of the save talks to Supabase. offline it can't, and that must not
                // stop the ADHD profile itself from being saved. the whole survey being unfinishable without
                // a connection is what used to trap people on this page with no way out. AuthRepository
                // queues the profile edit and SyncCoordinator sends it
                var reachedServer = true

                // first-time signups don't always have a profiles row yet (the auth.users trigger may be
                // missing), so make sure one exists before updating, or decodeSingle() blows up with
                // 'List is empty.'
                val existing = runCatching { authRepository.ensureProfileExists() }
                    .onFailure { reachedServer = false }
                    .getOrNull()
                val enteredName = listOf(s.firstName.trim(), s.lastName.trim())
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
                val displayName = enteredName.ifBlank {
                    existing?.displayName ?: authRepository.getCurrentUserFullName() ?: ""
                }
                authRepository.updateProfile(
                    displayName = displayName,
                    username = s.username.trim().lowercase(),
                )

                // cached id, not the live-session one: offline on a cold start the session may not be
                // restored yet, and the survey still has to save
                val userId = authRepository.getCurrentOrCachedUserId()
                    ?: error("No logged-in user")

                // keep the highest tier reached, editing shouldn't quietly downgrade Deep to Quick
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
                    topGoals = s.topGoals,
                    presentation = s.presentation,
                    coOccurring = s.coOccurring,
                    chronotype = s.chronotype,
                    sleepScheduleOrigin = s.sleepScheduleOrigin,
                    interruptionRecall = s.interruptionRecall,
                    captureNeed = s.captureNeed,
                    planChangeImpact = s.planChangeImpact,
                    taskReturnEffort = s.taskReturnEffort,
                    impulseAreas = s.impulseAreas,
                    nudgeTone = s.nudgeTone,
                    checkInCeiling = s.checkInCeiling,
                    missedTaskResponse = s.missedTaskResponse,
                    bodyDoublingInterest = s.bodyDoublingInterest,
                    workEnvironment = s.workEnvironment,
                    pastStrategies = s.pastStrategies,
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
                // local-first: this lands on the device with no connection, and pushes itself on reconnect
                adhdProfileRepository.saveProfile(profile)

                // updateProfile swallows a failed push by queueing it, so asking the queue is the only
                // reliable way to know the server actually has this
                val queued = !reachedServer || authRepository.hasPendingProfileUpdate()
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        submitSuccess = true,
                        savedOfflineOnly = queued,
                    )
                }
            } catch (e: Exception) {
                if (e is UsernameTakenException) {
                    _state.update {
                        it.copy(
                            isSubmitting = false,
                            submitError = SurveyError(SurveyError.Kind.UsernameTaken),
                        )
                    }
                    return@launch
                }
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        submitError = SurveyError(SurveyError.Kind.SaveFailed, e.message?.asUiText()),
                    )
                }
            }
        }
    }

    fun consumeError() = _state.update { it.copy(submitError = null) }

    // persists whatever is answered so far, keeping the last completed tier
    fun saveDraft(version: SurveyVersion, onSaved: () -> Unit) {
        val s = _state.value
        if (s.isSubmitting) return
        // nothing left to answer: that's a finished survey, whichever page it was left from. the
        // question rail lets people answer the last page first, and only the Finish button on that
        // page used to complete it, so a fully answered survey left via Save stayed a draft and the
        // AI stayed locked behind '0 remaining'. same list Settings counts 'remaining' from, so the
        // two can't disagree. submitting shows the finished panel instead of leaving, the right
        // ending for someone who has just answered everything
        if (!s.isEditing &&
            questionnaireCompletion(s, version).all { it } &&
            missingAnswers(s, version) == null
        ) {
            submit(version)
            return
        }
        _state.update { it.copy(isSubmitting = true, submitError = null) }
        viewModelScope.launch {
            try {
                val userId = authRepository.getCurrentOrCachedUserId()
                    ?: error("No logged-in user")
                val completedTier = s.previousSurveyVersion
                val usernameCanSave = s.username.isNotBlank() &&
                    (s.usernameAvailability == UsernameAvailability.Available || s.isEditing)
                if (usernameCanSave) {
                    // best-effort, same as submit: no connection must never be the reason someone can't leave a
                    // half-finished survey
                    val existing = runCatching { authRepository.ensureProfileExists() }.getOrNull()
                    val enteredName = listOf(s.firstName.trim(), s.lastName.trim())
                        .filter(String::isNotBlank)
                        .joinToString(" ")
                    authRepository.updateProfile(
                        displayName = enteredName.ifBlank { existing?.displayName.orEmpty() },
                        username = s.username.trim().lowercase(),
                    )
                }
                val draft = AdhdProfile(
                    userId = userId,
                    ageRange = s.ageRange,
                    diagnosisStatus = s.diagnosisStatus,
                    primarySymptoms = s.primarySymptoms.toList(),
                    topGoals = s.topGoals,
                    presentation = s.presentation,
                    coOccurring = s.coOccurring,
                    chronotype = s.chronotype,
                    sleepScheduleOrigin = s.sleepScheduleOrigin,
                    interruptionRecall = s.interruptionRecall,
                    captureNeed = s.captureNeed,
                    planChangeImpact = s.planChangeImpact,
                    taskReturnEffort = s.taskReturnEffort,
                    impulseAreas = s.impulseAreas,
                    nudgeTone = s.nudgeTone,
                    checkInCeiling = s.checkInCeiling,
                    missedTaskResponse = s.missedTaskResponse,
                    bodyDoublingInterest = s.bodyDoublingInterest,
                    workEnvironment = s.workEnvironment,
                    pastStrategies = s.pastStrategies,
                    productiveTime = s.productiveTime,
                    focusDurationMinutes = s.focusDurationMinutes,
                    sleepBedtime = s.sleepBedtime,
                    sleepWakeTime = s.sleepWakeTime,
                    medicationStatus = s.medicationStatus,
                    medications = s.medications.filter { it.name.isNotBlank() },
                    copingStrategies = s.copingStrategies.toList(),
                    painPoint = s.painPoint.trim(),
                    aiTonePreference = s.aiTone,
                    cityId = s.cityId,
                    surveyCompleted = completedTier != SurveyVersion.None,
                    surveyVersion = completedTier,
                )
                adhdProfileRepository.saveDraft(draft)
                if (!draft.surveyCompleted) {
                    val completion = questionnaireCompletion(s, version)
                    questionnaireReminderScheduler.updateProgress(
                        userId = userId,
                        version = version,
                        answered = completion.count { it },
                        total = completion.size,
                        wakeTime = s.sleepWakeTime,
                    )
                }
                _state.update { it.copy(isSubmitting = false) }
                onSaved()
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        submitError = SurveyError(SurveyError.Kind.SaveFailed, e.message?.asUiText()),
                    )
                }
            }
        }
    }

    // remember it so Splash stops forcing onboarding
    fun skipOnboarding() = adhdProfileRepository.markOnboardingSkipped()
}
