package com.muradgalayev.brainbuddy.data.sync

import com.muradgalayev.brainbuddy.data.google.GoogleCalendarTokenStore
import com.muradgalayev.brainbuddy.data.network.NetworkObserver
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.HabitTimingRepository
import com.muradgalayev.brainbuddy.data.repository.MedicationLogRepository
import com.muradgalayev.brainbuddy.data.repository.PlacesRepository
import com.muradgalayev.brainbuddy.data.repository.PomodoroRepository
import com.muradgalayev.brainbuddy.data.repository.PreferencesRepository
import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import com.muradgalayev.brainbuddy.data.repository.TogetherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

// single entry point for pushing and pulling Supabase state. ViewModels must not call
// repository.sync() directly, that couples the UI to network latency. instead: app start calls
// start() from MyndoraApp.onCreate, sign-in calls syncAll(), and reconnect is handled
// internally through NetworkObserver
@Singleton
class SyncCoordinator @Inject constructor(
    private val todoRepository: TodoRepository,
    private val calendarRepository: CalendarRepository,
    private val pomodoroRepository: PomodoroRepository,
    private val preferencesRepository: PreferencesRepository,
    private val aiConsentRepository: com.muradgalayev.brainbuddy.data.repository.AiConsentRepository,
    private val modeRepository: com.muradgalayev.brainbuddy.data.repository.ModeRepository,
    private val adhdProfileRepository: AdhdProfileRepository,
    private val placesRepository: PlacesRepository,
    private val togetherRepository: TogetherRepository,
    private val habitTimingRepository: HabitTimingRepository,
    private val medicationLogRepository: MedicationLogRepository,
    private val authRepository: AuthRepository,
    private val googleCalendarTokenStore: GoogleCalendarTokenStore,
    private val networkObserver: NetworkObserver,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)

    fun start() {
        if (!started.compareAndSet(false, true)) return
        syncAll()
        scope.launch {
            // drop(1) skips the initial current-state emission, we already synced above
            networkObserver.isOnline.drop(1).filter { it }.collect { syncAll() }
        }
        scope.launch {
            // session restoration happens asynchronously on a cold start, so if the first sync ran before
            // authentication was restored it was a no-op. retry the moment the session becomes available
            authRepository.isLoggedIn.distinctUntilChanged().filter { it }.collect {
                syncAll()
            }
        }
    }

    // fire-and-forget. safe to call from anywhere, never throws
    fun syncAll() {
        scope.launch {
            listOf(
                async { runCatching { todoRepository.sync() } },
                async { runCatching { calendarRepository.sync() } },
                async { runCatching { pomodoroRepository.sync() } },
                // dose ticks. also migrates the old DataStore set into Room the first time it runs on a
                // device that has one
                async { runCatching { medicationLogRepository.sync() } },
                // learned event timings. starting the local observation here means the calendar's suggestions
                // are warm before the user ever opens it
                async {
                    runCatching {
                        habitTimingRepository.observeForCurrentUser()
                        habitTimingRepository.sync()
                    }
                },
                async {
                    runCatching {
                        // push before pull, always. settings changed while offline live only on this device, and
                        // pulling first would apply the stale remote row and undo them
                        preferencesRepository.pushPending()
                        preferencesRepository.pullRemoteAndApply()
                    }
                },
                // same push-then-pull order, and for a sharper reason: pulling first on a device that revoked
                // consent offline would apply the server's stale granted and quietly resume sending wellness
                // data to the model
                async {
                    runCatching {
                        aiConsentRepository.pushPending()
                        aiConsentRepository.pullRemoteAndApply()
                    }
                },
                async { runCatching { modeRepository.sync() } },
                async { runCatching { adhdProfileRepository.pushPendingProfile() } },
                async { runCatching { adhdProfileRepository.refreshSurveyCompletedCache() } },
                async { runCatching { pullLinkedGoogleEmail() } },
                async { runCatching { authRepository.syncPendingProfileUpdate() } },
                async { runCatching { prewarmPlaces() } },
                // seeds the Together screen and, more importantly, surfaces a pending request without the
                // user having to open the screen first
                async { runCatching { togetherRepository.refresh() } },
                // keeps already-granted wellness snapshots current, rather than leaving a connection reading
                // whatever happened to be true at grant time
                async { runCatching { togetherRepository.refreshWellnessSnapshots() } },
            ).awaitAll()
        }
    }

    // warms the Care Nearby caches during app start so the first visit renders without a spinner.
    // fetches cities plus the places for the user's home city. cheap, one SELECT each
    private suspend fun prewarmPlaces() {
        val cities = placesRepository.listCities().getOrNull().orEmpty()
        if (cities.isEmpty()) return
        val homeCityId = adhdProfileRepository.peekProfile()?.cityId
            ?.takeIf { id -> cities.any { it.id == id } }
            ?: cities.first().id
        placesRepository.listPlacesInCity(homeCityId)
    }

    // reads the linked Google Calendar email from the profile and mirrors it into local prefs.
    // only overwrites when the remote value is present, so a linked device that hasn't synced yet
    // doesn't get its local link wiped by a transient hiccup that returns null
    private suspend fun pullLinkedGoogleEmail() {
        val remote = authRepository.getLinkedGoogleEmail() ?: return
        if (remote != googleCalendarTokenStore.getLinkedEmail()) {
            googleCalendarTokenStore.saveLinkedEmail(remote)
        }
    }
}
