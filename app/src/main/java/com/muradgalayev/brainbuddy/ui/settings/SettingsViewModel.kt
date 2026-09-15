package com.muradgalayev.brainbuddy.ui.settings

import com.muradgalayev.brainbuddy.domain.model.AdhdProfile
import com.muradgalayev.brainbuddy.domain.model.SurveyVersion
import com.muradgalayev.brainbuddy.ui.onboarding.questionnaireCompletion
import com.muradgalayev.brainbuddy.ui.onboarding.toSurveyAnswers
import android.util.Log

import android.app.PendingIntent
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.muradgalayev.brainbuddy.data.auth.authErrorMessage
import com.muradgalayev.brainbuddy.ui.utils.resolve
import com.muradgalayev.brainbuddy.data.google.GoogleCalendarAuthClient
import com.muradgalayev.brainbuddy.data.google.GoogleCalendarTokenStore
import com.muradgalayev.brainbuddy.data.health.HealthConnectManager
import com.muradgalayev.brainbuddy.data.local.FontMode
import com.muradgalayev.brainbuddy.data.local.FontSize
import com.muradgalayev.brainbuddy.data.local.PreferencesManager
import com.muradgalayev.brainbuddy.data.local.TextSpacing
import com.muradgalayev.brainbuddy.data.local.ThemeMode
import com.muradgalayev.brainbuddy.data.notifications.PomodoroNudgeFrequency
import com.muradgalayev.brainbuddy.data.notifications.ReminderBootstrapper
import com.muradgalayev.brainbuddy.data.notifications.ReminderCategory
import com.muradgalayev.brainbuddy.data.notifications.ReminderKind
import com.muradgalayev.brainbuddy.data.notifications.QuestionnaireReminderScheduler
import com.muradgalayev.brainbuddy.data.network.NetworkObserver
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.data.repository.ExportResult
import com.muradgalayev.brainbuddy.data.repository.GoogleCalendarRepository
import com.muradgalayev.brainbuddy.data.repository.UsernameTakenException
import com.muradgalayev.brainbuddy.data.sync.CalendarSyncFrequency
import com.muradgalayev.brainbuddy.data.sync.CalendarSyncScheduler
import com.muradgalayev.brainbuddy.ui.onboarding.UsernameAvailability
import com.muradgalayev.brainbuddy.ui.onboarding.isUsernameSyntaxValid
import io.github.jan.supabase.auth.status.SessionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.muradgalayev.brainbuddy.ui.theme.AppTheme
import com.muradgalayev.brainbuddy.ui.theme.CustomThemeSpec
import com.muradgalayev.brainbuddy.ui.theme.ThemeSelection
import javax.inject.Inject
import com.muradgalayev.brainbuddy.R

private const val TAG = "SettingsViewModel"

data class AccountDeletionState(
    val deleting: Boolean = false,
    val failed: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val preferencesManager: PreferencesManager,
    private val authRepository: AuthRepository,
    private val todoRepository: com.muradgalayev.brainbuddy.data.repository.TodoRepository,
    private val calendarRepository: com.muradgalayev.brainbuddy.data.repository.CalendarRepository,
    private val pomodoroRepository: com.muradgalayev.brainbuddy.data.repository.PomodoroRepository,
    private val habitTimingRepository: com.muradgalayev.brainbuddy.data.repository.HabitTimingRepository,
    private val medicationLogRepository: com.muradgalayev.brainbuddy.data.repository.MedicationLogRepository,
    private val modeRepository: com.muradgalayev.brainbuddy.data.repository.ModeRepository,
    private val preferencesRepository: com.muradgalayev.brainbuddy.data.repository.PreferencesRepository,
    private val aiConsentRepository: com.muradgalayev.brainbuddy.data.repository.AiConsentRepository,
    private val modeManager: com.muradgalayev.brainbuddy.data.local.ModeManager,
    private val googleCalendarRepository: GoogleCalendarRepository,
    private val googleCalendarAuthClient: GoogleCalendarAuthClient,
    private val googleCalendarTokenStore: GoogleCalendarTokenStore,
    private val calendarSyncScheduler: CalendarSyncScheduler,
    private val reminderBootstrapper: ReminderBootstrapper,
    private val questionnaireReminderScheduler: QuestionnaireReminderScheduler,
    private val adhdProfileRepository: com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository,
    private val placesRepository: com.muradgalayev.brainbuddy.data.repository.PlacesRepository,
    private val healthConnectManager: HealthConnectManager,
    private val profilePdfExporter: com.muradgalayev.brainbuddy.data.export.ProfilePdfExporter,
    private val togetherRepository: com.muradgalayev.brainbuddy.data.repository.TogetherRepository,
    private val networkObserver: NetworkObserver,
    private val planRepository: com.muradgalayev.brainbuddy.data.repository.PlanRepository,
    private val aiNavigator: com.muradgalayev.brainbuddy.domain.ai.AiNavigator,
) : ViewModel() {

    // the assistant can send someone here to change their password or the app's language. both are
    // sheets rather than screens, so it latches which one and the screen opens it on arrival
    val pendingSettingsSheet = aiNavigator.pendingSettingsSheet

    fun consumeSettingsSheet() = aiNavigator.consumeSettingsSheet()
    val plan = planRepository.plan
    val questionnaireProgress = questionnaireReminderScheduler.progress
    // drives the '2 waiting' badge and subtitle on the Myndora Together row
    val pendingTogetherRequests = togetherRepository.incomingRequests
    val togetherConnections = togetherRepository.connections
    val healthConnectState = healthConnectManager.state
    val healthConnectPermissions = healthConnectManager.permissions
    val healthPermissionLabels = healthConnectManager.permissionLabels

    // download my profile (GDPR art. 20 portability, entirely on-device)
    private val _profileExportInProgress = MutableStateFlow(false)
    val profileExportInProgress: StateFlow<Boolean> = _profileExportInProgress.asStateFlow()
    private val _profileExportMessage = MutableStateFlow<String?>(null)
    val profileExportMessage: StateFlow<String?> = _profileExportMessage.asStateFlow()

    fun clearProfileExportMessage() { _profileExportMessage.value = null }

    // builds the PDF from the profile row, the survey answers and seven days of Health Connect
    // figures. nothing leaves the device
    fun downloadMyProfile() {
        if (_profileExportInProgress.value) return
        _profileExportInProgress.value = true
        viewModelScope.launch {
            val profile = runCatching { adhdProfileRepository.getProfile() }.getOrNull()
                ?: adhdProfileRepository.peekProfile()
            val report = runCatching { healthConnectManager.weeklyReport() }.getOrNull()
                ?: com.muradgalayev.brainbuddy.data.health.WeeklyHealthReport(
                    from = java.time.LocalDate.now().minusDays(6),
                    to = java.time.LocalDate.now(),
                )
            val cached = authRepository.peekProfile()
            val result = profilePdfExporter.export(
                displayName = cached?.displayName ?: authRepository.getCurrentUserFullName(),
                email = cached?.email ?: authRepository.getCurrentUserEmail(),
                profile = profile,
                report = report,
            )
            _profileExportMessage.value = when (result) {
                is com.muradgalayev.brainbuddy.data.export.PdfExportResult.Saved ->
                    context.getString(R.string.settings_export_saved_to, result.displayPath)
                is com.muradgalayev.brainbuddy.data.export.PdfExportResult.Failed ->
                    context.getString(R.string.settings_export_failed, result.message)
            }
            _profileExportInProgress.value = false
        }
    }

    fun refreshHealthConnect() {
        viewModelScope.launch { healthConnectManager.refresh() }
    }

    fun disconnectHealthConnect() {
        viewModelScope.launch { healthConnectManager.disconnect() }
    }

    fun prepareHealthConnect() {
        healthConnectManager.prepareToConnect()
    }
    // the whole mode rather than just its name, so the status card can use its icon and accent
    val activeMode: StateFlow<com.muradgalayev.brainbuddy.domain.model.AppMode?> =
        modeManager.activeMode

    // for small sub-pages that only need to know whether customisation is locked
    val activeModeName: StateFlow<String?> = modeManager.activeMode
        .map { it?.name }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // durable lookup for actions that mustn't trust a cold-start StateFlow seed
    suspend fun activeModeNow(): com.muradgalayev.brainbuddy.domain.model.AppMode? =
        modeManager.activeModeNow()

    val aiSpokenResponses = preferencesManager.aiSpokenResponses
    val aiChatReadAloud = preferencesManager.aiChatReadAloud
    val aiVoiceName = preferencesManager.aiVoiceName
    val aiHealthPersonalization = preferencesManager.aiHealthPersonalization

    fun setAiSpokenResponses(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setAiSpokenResponses(enabled) }
    }

    fun setAiChatReadAloud(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setAiChatReadAloud(enabled) }
    }

    val readAloudTaps = preferencesManager.readAloudTaps

    val reduceMotion = preferencesManager.reduceMotion
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            preferencesManager.setReduceMotion(enabled)
        }
    }

    fun setReadAloudTaps(enabled: Boolean) {
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            preferencesManager.setReadAloudTaps(enabled)
        }
    }

    fun setAiVoiceName(name: String?) {
        viewModelScope.launch { preferencesManager.setAiVoiceName(name) }
    }

    // goes through the consent repository rather than straight to DataStore: this is the one
    // setting we have to be able to account for later, so the decision is stamped and sent to
    // the account instead of only flipping a local flag
    fun setAiHealthPersonalization(enabled: Boolean) {
        viewModelScope.launch { aiConsentRepository.setWellnessConsent(enabled) }
    }

    // drives the red 'complete your survey' prompt on the account screen. seeded from the
    // in-memory profile cache, refreshed from cache/network in init
    private val _surveyCompleted = MutableStateFlow(
        adhdProfileRepository.peekProfile()?.surveyCompleted == true
    )
    val surveyCompleted: StateFlow<Boolean> = _surveyCompleted.asStateFlow()

    // what the profile banner counts. an unfinished survey reports its saved draft. a finished
    // Quick setup unlocks the app but still leaves the Deep Dive open, and the banner used to
    // vanish at that point, so those questions are counted from the saved profile. null hides it
    val surveyProgress: StateFlow<SurveyProgressUi?> = kotlinx.coroutines.flow.combine(
        questionnaireReminderScheduler.progress,
        adhdProfileRepository.profile,
        _surveyCompleted,
    ) { draft, profile, completed -> surveyProgressOf(draft, profile, completed) }
        .stateIn(
            viewModelScope,
            kotlinx.coroutines.flow.SharingStarted.Eagerly,
            surveyProgressOf(
                questionnaireReminderScheduler.progress.value,
                adhdProfileRepository.peekProfile(),
                _surveyCompleted.value,
            ),
        )

    private fun surveyProgressOf(
        draft: com.muradgalayev.brainbuddy.data.notifications.QuestionnaireReminderProgress?,
        profile: AdhdProfile?,
        completed: Boolean,
    ): SurveyProgressUi? {
        if (!completed) return draft?.let { SurveyProgressUi(it.version, it.answered, it.total) }
        if (profile?.surveyVersion != SurveyVersion.Quick) return null
        val answered = questionnaireCompletion(profile.toSurveyAnswers(), SurveyVersion.Deep)
            // the first page is name and username, which a finished Quick setup already required
            .mapIndexed { index, done -> done || index == 0 }
        return SurveyProgressUi(SurveyVersion.Deep, answered.count { it }, answered.size)
            .takeIf { it.remaining > 0 }
    }

    fun refreshSurveyCompleted() {
        viewModelScope.launch {
            // null = couldn't resolve (offline, session not restored). keep what we had rather than
            // accusing a finished user of not having done the survey
            val done = runCatching { adhdProfileRepository.surveyCompletedOrNull() }
                .getOrNull() ?: return@launch
            if (done != _surveyCompleted.value) _surveyCompleted.value = done
        }
    }

    private val _loggedOut = MutableStateFlow(false)
    val loggedOut: StateFlow<Boolean> = _loggedOut.asStateFlow()

    private val _signingOut = MutableStateFlow(false)
    val signingOut: StateFlow<Boolean> = _signingOut.asStateFlow()

    private val _accountDeletion = MutableStateFlow(AccountDeletionState())
    val accountDeletion: StateFlow<AccountDeletionState> = _accountDeletion.asStateFlow()

    // same shape as deletion: in flight, or failed
    private val _accountDeactivation = MutableStateFlow(AccountDeletionState())
    val accountDeactivation: StateFlow<AccountDeletionState> = _accountDeactivation.asStateFlow()

    private val _exportingToCalendar = MutableStateFlow(false)
    val exportingToCalendar: StateFlow<Boolean> = _exportingToCalendar.asStateFlow()

    private val _calendarExportMessage = MutableStateFlow<String?>(null)
    val calendarExportMessage: StateFlow<String?> = _calendarExportMessage.asStateFlow()

    private val _calendarAuthorizationRequest = MutableStateFlow<PendingIntent?>(null)
    val calendarAuthorizationRequest: StateFlow<PendingIntent?> =
        _calendarAuthorizationRequest.asStateFlow()

    // flips when SyncCoordinator pulls the linked email after sign-in, or when the user
    // connects or disconnects locally
    val linkedGoogleEmail: StateFlow<String?> = googleCalendarTokenStore.linkedEmail

    // seed from AuthRepository's cached profile if it's been fetched before in this process,
    // otherwise fall back to the JWT metadata. re-entering Settings in the same session then
    // starts with the full profile already visible, no refetch flash
    private val _profile = MutableStateFlow(seedProfileFromCache())

    private fun seedProfileFromCache(): UserProfile {
        val cached = authRepository.peekProfile()
        return UserProfile(
            email = cached?.email ?: authRepository.getCurrentUserEmail(),
            name = cached?.displayName ?: authRepository.getCurrentUserFullName(),
            username = cached?.username ?: authRepository.getCurrentUserUsername(),
            avatarUrl = cached?.avatarUrl ?: authRepository.getCurrentUserAvatarUrl(),
            phone = authRepository.getCurrentUserPhone(),
        )
    }

    val profile: StateFlow<UserProfile> = _profile.asStateFlow()

    private val _profileLocation = MutableStateFlow<String?>(
        adhdProfileRepository.peekProfile()?.cityId?.let { cityId ->
            placesRepository.peekCities()?.firstOrNull { it.id == cityId }?.name
        },
    )
    val profileLocation: StateFlow<String?> = _profileLocation.asStateFlow()

    // re-read the profile from the in-memory cache. Edit Profile is a separate ViewModel and
    // updates that cache synchronously, so calling this on resume shows the change instantly
    fun reseedProfileFromCache() {
        val next = seedProfileFromCache()
        if (next != _profile.value) _profile.value = next
    }

    private val _profileSaving = MutableStateFlow(false)
    val profileSaving: StateFlow<Boolean> = _profileSaving.asStateFlow()

    private val _profileSaveMessage = MutableStateFlow<String?>(null)
    val profileSaveMessage: StateFlow<String?> = _profileSaveMessage.asStateFlow()

    private val _passwordResetState = MutableStateFlow(PasswordResetUiState())
    val passwordResetState: StateFlow<PasswordResetUiState> = _passwordResetState.asStateFlow()

    private val _usernameAvailability = MutableStateFlow(UsernameAvailability.Idle)
    val usernameAvailability: StateFlow<UsernameAvailability> = _usernameAvailability.asStateFlow()
    private var usernameCheckJob: Job? = null

    init {
        // the constructor already seeded the header from the disk-backed cache, so no 'Welcome'
        // flash on cold start. but the Supabase session restores from disk asynchronously, so right
        // now getCurrentUser* may still be null and peekProfile's id-guard can't confirm the cache.
        // re-seed as soon as the session is authenticated, and revalidate once per process
        viewModelScope.launch {
            authRepository.sessionStatus
                .filterIsInstance<SessionStatus.Authenticated>()
                .collect {
                    reseedProfileFromCache()
                    if (authRepository.consumeProfileRevalidationToken()) {
                        refreshProfile()
                    }
                }
        }
        refreshSurveyCompleted()
        refreshProfileLocation()
        // linkedGoogleEmail is reactive and SyncCoordinator refreshes it after sign-in and on
        // reconnect. firing a network call here was the source of the flash
    }

    private fun refreshProfileLocation() {
        viewModelScope.launch {
            val adhdProfile = adhdProfileRepository.peekProfile()
                ?: runCatching { adhdProfileRepository.getProfile() }.getOrNull()
            val cityId = adhdProfile?.cityId
            if (cityId == null) {
                _profileLocation.value = null
                return@launch
            }
            val cities = placesRepository.peekCities()
                ?: placesRepository.listCities().getOrNull().orEmpty()
            _profileLocation.value = cities.firstOrNull { it.id == cityId }?.name
        }
    }

    private fun refreshLinkedGoogleEmail() {
        viewModelScope.launch {
            val remote = runCatching { authRepository.getLinkedGoogleEmail() }.getOrNull()
                ?: return@launch
            if (remote != googleCalendarTokenStore.getLinkedEmail()) {
                googleCalendarTokenStore.saveLinkedEmail(remote)
            }
        }
    }
    // legacy getters, kept for other callers that still read these
    val userEmail: String?
        get() = _profile.value.email

    val userFullName: String?
        get() = _profile.value.name

    val fontSize = preferencesManager.fontSize
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FontSize.Medium)
    val textSpacing = preferencesManager.textSpacing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TextSpacing.DEFAULT)
    val themeMode = preferencesManager.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.System)

    val fontMode = preferencesManager.fontMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FontMode.DEFAULT)

    val appTheme = preferencesManager.appTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeSelection.DEFAULT)

    val customThemeSpec = preferencesManager.customThemeSpec
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CustomThemeSpec())

    val enabledNavItems = preferencesManager.enabledNavItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // notification frequency

    val todoReminderKinds = preferencesManager.todoReminderKinds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderKind.entries.toSet())

    val calendarReminderKinds = preferencesManager.calendarReminderKinds
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ReminderKind.entries.toSet())

    val dailySummaryEnabled = preferencesManager.dailySummaryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val pomodoroNudgeFrequency = preferencesManager.pomodoroNudgeFrequency
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PomodoroNudgeFrequency.OFF)

    val pomodoroNudgeTime = preferencesManager.pomodoroNudgeTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PreferencesManager.DEFAULT_NUDGE_TIME)

    val pomodoroBreakReminders = preferencesManager.pomodoroBreakReminders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun toggleReminderKind(category: ReminderCategory, kind: ReminderKind, enabled: Boolean) {
        viewModelScope.launch {
            val current = when (category) {
                ReminderCategory.TODO -> todoReminderKinds.value
                ReminderCategory.CALENDAR -> calendarReminderKinds.value
            }
            val next = if (enabled) current + kind else current - kind
            preferencesRepository.setReminderKinds(category, next)
            // re-arm alarms so the change takes effect for already-scheduled items
            reminderBootstrapper.rescheduleItemReminders()
        }
    }

    fun setDailySummaryEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setDailySummaryEnabled(enabled)
            reminderBootstrapper.rescheduleMorningSummary()
        }
    }

    fun setPomodoroNudgeFrequency(freq: PomodoroNudgeFrequency) {
        viewModelScope.launch {
            preferencesRepository.setPomodoroNudgeFrequency(freq)
            reminderBootstrapper.rescheduleFocusNudge()
        }
    }

    fun setPomodoroNudgeTime(hhmm: String) {
        viewModelScope.launch {
            preferencesRepository.setPomodoroNudgeTime(hhmm)
            reminderBootstrapper.rescheduleFocusNudge()
        }
    }

    fun setPomodoroBreakReminders(enabled: Boolean) {
        viewModelScope.launch {
            preferencesRepository.setPomodoroBreakReminders(enabled)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            preferencesRepository.setThemeMode(mode)
        }
    }

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            preferencesManager.setAppTheme(theme)
        }
    }

    fun setCustomTheme(spec: CustomThemeSpec) {
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            preferencesManager.setCustomTheme(spec)
        }
    }

    fun setFontMode(mode: FontMode) {
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            preferencesRepository.setFontMode(mode)
        }
    }

    fun toggleNavItem(route: String, enabled: Boolean) {
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            // read the current set once and compute the new one
            val set = preferencesManager.enabledNavItems.firstOrNull() ?: emptySet()
            val newSet = if (enabled) set + route else set - route
            preferencesRepository.setEnabledNavItems(newSet)
        }
    }

    fun setFontSize(fontSize: FontSize) {
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            preferencesRepository.setFontSize(fontSize)
        }
    }

    fun setTextSpacing(spacing: TextSpacing) {
        viewModelScope.launch {
            if (modeManager.activeModeNow() != null) return@launch
            preferencesRepository.setTextSpacing(spacing)
        }
    }

    fun exportToGoogleCalendar() {
        if (_exportingToCalendar.value) return
        _exportingToCalendar.value = true
        viewModelScope.launch {
            val first = googleCalendarRepository.exportAllEvents()
            if (first !is ExportResult.NeedsGoogleSignIn) {
                publishExportResult(first)
                _exportingToCalendar.value = false
                return@launch
            }
            try {
                when (val step = googleCalendarAuthClient.requestAuthorization()) {
                    is GoogleCalendarAuthClient.AuthorizationStep.AccessToken -> {
                        refreshLinkedEmail(step.token)
                        publishExportResult(googleCalendarRepository.exportAllEvents())
                        _exportingToCalendar.value = false
                    }
                    is GoogleCalendarAuthClient.AuthorizationStep.NeedsUserConsent -> {
                        // hand the PendingIntent to the screen, which launches it and calls back. keep
                        // _exportingToCalendar true so the spinner stays until consent is done or cancelled
                        _calendarAuthorizationRequest.value = step.pendingIntent
                    }
                }
            } catch (e: Exception) {
                _calendarExportMessage.value =
                    context.getString(R.string.settings_google_connect_failed, e.message ?: context.getString(R.string.common_unknown_error))
                _exportingToCalendar.value = false
            }
        }
    }

    fun consumeCalendarAuthorizationRequest() {
        _calendarAuthorizationRequest.value = null
    }

    fun onCalendarAuthorizationResult(data: Intent?) {
        viewModelScope.launch {
            val token = googleCalendarAuthClient.extractFromActivityResult(data)
            if (token == null) {
                _calendarExportMessage.value = context.getString(R.string.settings_calendar_connect_cancelled)
                _exportingToCalendar.value = false
                return@launch
            }
            refreshLinkedEmail(token)
            publishExportResult(googleCalendarRepository.exportAllEvents())
            _exportingToCalendar.value = false
        }
    }

    private suspend fun refreshLinkedEmail(token: String) {
        val email = googleCalendarAuthClient.fetchAndStoreUserEmail(token)
        // linkedGoogleEmail comes from the token store now, no need to write it here
        if (email != null) {
            // first connect: a mode may overlay the base cadence but must never replace it in DataStore
            calendarSyncScheduler.applyFrequency(
                modeManager.effectiveCalendarSyncFrequencyNow()
            )
        }
    }

    val calendarSyncFrequency: StateFlow<CalendarSyncFrequency> =
        preferencesManager.calendarSyncFrequency
            .stateIn(viewModelScope, SharingStarted.Eagerly, CalendarSyncFrequency.DEFAULT)

    // written by CalendarSyncWorker so the settings screen can prove sync ran
    val calendarLastSyncedAt: StateFlow<Long?> = preferencesManager.calendarLastSyncedAt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val calendarLastSyncResult: StateFlow<String?> = preferencesManager.calendarLastSyncResult
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setCalendarSyncFrequency(freq: CalendarSyncFrequency) {
        viewModelScope.launch {
            // writes local DataStore and syncs to user_preferences, so it crosses devices
            preferencesRepository.setCalendarSyncFrequency(freq)
            // only affects background sync, manual export still works. if a mode overlays this, keep its
            // effective cadence in force but remember the new base for when the mode ends
            if (googleCalendarTokenStore.getLinkedEmail() != null) {
                calendarSyncScheduler.applyFrequency(
                    modeManager.effectiveCalendarSyncFrequencyNow()
                )
            }
        }
    }

    private fun publishExportResult(result: ExportResult) {
        _calendarExportMessage.value = when (result) {
            is ExportResult.Success -> {
                val parts = mutableListOf<String>()
                if (result.pushed > 0) parts += context.getString(R.string.cal_export_added, result.pushed)
                if (result.alreadyExisted > 0) parts += context.getString(R.string.cal_export_already, result.alreadyExisted)
                if (result.failed > 0) parts += context.getString(R.string.cal_export_failed, result.failed)
                val core = if (parts.isEmpty()) context.getString(R.string.cal_export_nothing) else parts.joinToString(", ")
                val email = linkedGoogleEmail.value
                if (email != null && parts.isNotEmpty()) "$core → $email" else core
            }
            ExportResult.NeedsGoogleSignIn ->
                context.getString(R.string.cal_export_need_google)
        }
    }

    fun disconnectGoogleCalendar() {
        viewModelScope.launch {
            // explicit user action: revoke the Google grant and null the Supabase column, so signing in
            // on another device won't resurrect the link
            googleCalendarAuthClient.signOut()
            runCatching { authRepository.setLinkedGoogleEmail(null) }
            calendarSyncScheduler.cancel()
            _calendarExportMessage.value = context.getString(R.string.settings_google_disconnected)
        }
    }

    fun clearCalendarExportMessage() {
        _calendarExportMessage.value = null
    }

    // This also gives Google-created accounts an email/password sign-in method. Supabase keeps
    // the Google identity linked, so the user can use either method after choosing a password.
    fun sendPasswordReset() {
        if (_passwordResetState.value.sending) return
        val email = authRepository.getCurrentUserEmail()
            ?: _profile.value.email

        if (email.isNullOrBlank()) {
            _passwordResetState.value = PasswordResetUiState(
                error = context.getString(R.string.settings_no_email),
            )
            return
        }

        _passwordResetState.value = PasswordResetUiState(sending = true)
        viewModelScope.launch {
            if (!networkObserver.isOnline.first()) {
                _passwordResetState.value = PasswordResetUiState(
                    error = context.getString(R.string.common_no_internet),
                )
                return@launch
            }

            try {
                authRepository.sendPasswordReset(email)
                _passwordResetState.value = PasswordResetUiState(sentTo = email)
            } catch (e: Exception) {
                _passwordResetState.value = PasswordResetUiState(
                    error = authErrorMessage(
                        error = e,
                        fallback = R.string.settings_password_email_failed,
                    ).resolve(context),
                )
            }
        }
    }

    fun clearPasswordResetFeedback() {
        if (!_passwordResetState.value.sending) {
            _passwordResetState.value = PasswordResetUiState()
        }
    }

    fun signOut() {
        if (_signingOut.value) return
        // raised before any clearing starts, so the screen covers itself and nobody watches their
        // profile, modes and connections empty out one by one
        _signingOut.value = true
        viewModelScope.launch {
            // capture the user id before signing out so we know which local caches to clear
            clearLocalAccount(authRepository.getCurrentOrCachedUserId())
            _loggedOut.value = true
        }
    }

    // permanent. the server deletes the account first; only once that has succeeded is anything on
    // this phone touched, so a failed delete leaves the user signed in with everything intact
    fun deleteAccount() {
        if (_accountDeletion.value.deleting) return
        _accountDeletion.value = AccountDeletionState(deleting = true)
        viewModelScope.launch {
            val currentUserId = authRepository.getCurrentOrCachedUserId()
            authRepository.deleteAccount()
                .onSuccess {
                    clearLocalAccount(currentUserId)
                    _loggedOut.value = true
                }
                .onFailure { _accountDeletion.value = AccountDeletionState(failed = true) }
        }
    }

    fun clearAccountDeletionError() {
        if (!_accountDeletion.value.deleting) _accountDeletion.value = AccountDeletionState()
    }

    // the reversible option. marked on the server first, then signed out and wiped locally like any
    // sign-out; the data comes back from the server when they sign in again
    fun deactivateAccount() {
        if (_accountDeactivation.value.deleting || _accountDeletion.value.deleting) return
        _accountDeactivation.value = AccountDeletionState(deleting = true)
        viewModelScope.launch {
            val currentUserId = authRepository.getCurrentOrCachedUserId()
            authRepository.deactivateAccount()
                .onSuccess {
                    clearLocalAccount(currentUserId)
                    _loggedOut.value = true
                }
                .onFailure { _accountDeactivation.value = AccountDeletionState(failed = true) }
        }
    }

    fun clearDeactivationError() {
        if (!_accountDeactivation.value.deleting) _accountDeactivation.value = AccountDeletionState()
    }

    // everything signing out has to wipe from this device, shared by sign-out and account deletion
    private suspend fun clearLocalAccount(currentUserId: String?) {
        run {
            healthConnectManager.clearForAccountSwitch()
            planRepository.clearForSignOut()
            currentUserId?.let { modeRepository.clearLocalForUser(it) }
            // clear the selection and restore ringer/DND while the departing account still exists,
            // otherwise its mode leaks into the signed-out screen or into the next account
            modeManager.clearForSignOut()
            currentUserId?.let(questionnaireReminderScheduler::cancelForUser)
            authRepository.signOut()
            // don't call googleCalendarAuthClient.signOut() here: that revokes Google consent on this
            // device and forces a full re-auth after Supabase login. we only want out of Supabase, the
            // Google link stays in profiles and is re-hydrated on the next sign-in
            googleCalendarTokenStore.clearForSupabaseSignOut()
            calendarSyncScheduler.cancel()
            currentUserId?.let {
                // so the next user doesn't see the previous one's tasks, events or sessions
                todoRepository.clearLocalForUser(it)
                calendarRepository.clearLocalForUser(it)
                pomodoroRepository.clearLocalForUser(it)
                // learned event timings are as personal as the events behind them
                habitTimingRepository.clearLocalForUser(it)
                medicationLogRepository.clearLocalForUser(it)
            }
            adhdProfileRepository.clearLocalCache()
            // connections and pending requests are another user's business entirely
            togetherRepository.clear()
        }
    }

    // debounced availability check from the edit dialog. skips the call when the field matches
    // the user's current username, that's their own row and not a conflict
    fun onEditingUsername(raw: String) {
        usernameCheckJob?.cancel()
        val trimmed = raw.trim()
        val current = _profile.value.username?.trim().orEmpty()

        when {
            trimmed.isEmpty() -> {
                _usernameAvailability.value = UsernameAvailability.Idle
                return
            }
            trimmed.equals(current, ignoreCase = true) -> {
                // same as current, treat as available so Save isn't blocked
                _usernameAvailability.value = UsernameAvailability.Available
                return
            }
            !isUsernameSyntaxValid(trimmed) -> {
                _usernameAvailability.value = UsernameAvailability.Invalid
                return
            }
        }

        _usernameAvailability.value = UsernameAvailability.Checking
        usernameCheckJob = viewModelScope.launch {
            delay(400)
            val available = authRepository.isUsernameAvailable(trimmed)
            _usernameAvailability.value =
                if (available) UsernameAvailability.Available else UsernameAvailability.Taken
        }
    }

    fun updateProfile(name: String, username: String, phone: String = _profile.value.phone.orEmpty()) {
        if (_profileSaving.value) return

        val cleanName = name.trim()
        val cleanUsername = username.trim()
        val cleanPhone = phone.trim()

        if (cleanName.isBlank()) {
            _profileSaveMessage.value = context.getString(R.string.settings_name_empty)
            return
        }
        // block save when we already know it's taken. the unique index is still the real guard,
        // this only avoids the round-trip
        when (_usernameAvailability.value) {
            UsernameAvailability.Taken -> {
                _profileSaveMessage.value = context.getString(R.string.settings_username_taken)
                return
            }
            UsernameAvailability.Invalid -> {
                _profileSaveMessage.value = context.getString(R.string.username_invalid)
                return
            }
            UsernameAvailability.Checking -> {
                _profileSaveMessage.value = context.getString(R.string.settings_username_checking)
                return
            }
            else -> Unit
        }

        _profileSaving.value = true

        viewModelScope.launch {
            try {
                val updatedProfile = authRepository.updateProfile(
                    displayName = cleanName,
                    username = cleanUsername
                )

                // phone lives in auth metadata, not in the profiles row
                if (cleanPhone != _profile.value.phone.orEmpty()) {
                    authRepository.updateUserPhone(cleanPhone)
                }

                _profile.value = UserProfile(
                    email = updatedProfile.email ?: _profile.value.email ?: authRepository.getCurrentUserEmail(),
                    name = updatedProfile.displayName ?: cleanName,
                    username = updatedProfile.username ?: cleanUsername,
                    avatarUrl = updatedProfile.avatarUrl ?: _profile.value.avatarUrl,
                    phone = cleanPhone.ifBlank { null },
                )

                _profileSaveMessage.value = if (authRepository.hasPendingProfileUpdate()) {
                    context.getString(R.string.settings_saved_offline)
                } else {
                    context.getString(R.string.settings_profile_updated)
                }
            } catch (e: UsernameTakenException) {
                // the unique index rejected it: either our check said available wrongly (RLS masked the row)
                // or someone grabbed the name between check and save. same message either way
                _usernameAvailability.value = UsernameAvailability.Taken
                _profileSaveMessage.value = context.getString(R.string.settings_username_taken)
            } catch (e: Exception) {
                _profileSaveMessage.value = context.getString(R.string.settings_save_failed, e.message ?: context.getString(R.string.common_unknown_error))
            } finally {
                _profileSaving.value = false
            }
        }
    }

    fun clearProfileSaveMessage() {
        _profileSaveMessage.value = null
    }

    private val _avatarUploading = MutableStateFlow(false)
    val avatarUploading: StateFlow<Boolean> = _avatarUploading.asStateFlow()

    // uploads a picked image to the profile_photos bucket and shows it as the avatar
    fun uploadAvatar(bytes: ByteArray, extension: String) {
        if (_avatarUploading.value) return
        _avatarUploading.value = true
        viewModelScope.launch {
            try {
                val url = authRepository.uploadAvatar(bytes, extension)
                _profile.value = _profile.value.copy(avatarUrl = url)
                _profileSaveMessage.value = context.getString(R.string.settings_photo_updated)
            } catch (e: Exception) {
                _profileSaveMessage.value = context.getString(R.string.settings_photo_failed, e.message ?: context.getString(R.string.common_unknown_error))
            } finally {
                _avatarUploading.value = false
            }
        }
    }

    private fun refreshProfile() {
        viewModelScope.launch {
            // silent on failure by design. this is a background top-up of a screen that already rendered
            // from the disk cache, so nothing is missing and the user asked for nothing. it used to raise
            // 'Unable to resolve host' whenever Settings opened offline, which read like a broken account
            val fresh = runCatching { authRepository.ensureProfileExists() }.getOrElse {
                Log.d(TAG, "Background profile refresh skipped: ${it.message}")
                return@launch
            }
            val current = _profile.value
            val next = UserProfile(
                email = fresh.email ?: current.email,
                name = fresh.displayName ?: current.name,
                username = fresh.username ?: current.username,
                avatarUrl = fresh.avatarUrl ?: current.avatarUrl,
                phone = authRepository.getCurrentUserPhone() ?: current.phone,
            )
            // MutableStateFlow dedupes on == anyway, this just makes it explicit
            if (next != current) _profile.value = next
        }
    }
}

data class UserProfile(
    val email: String?,
    val name: String?,
    val username: String?,
    val avatarUrl: String?,
    val phone: String? = null,
)

data class PasswordResetUiState(
    val sending: Boolean = false,
    val error: String? = null,
    val sentTo: String? = null,
)

// the profile banner's numbers, whichever survey they come from
data class SurveyProgressUi(
    val version: SurveyVersion,
    val answered: Int,
    val total: Int,
) {
    val remaining: Int get() = (total - answered).coerceAtLeast(0)
    val fraction: Float get() = if (total == 0) 0f else answered.toFloat() / total
}
