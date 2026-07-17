package com.muradgalayev.brainbuddy.data.sync

import com.muradgalayev.brainbuddy.data.google.GoogleCalendarTokenStore
import com.muradgalayev.brainbuddy.data.network.NetworkObserver
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.AuthRepository
import com.muradgalayev.brainbuddy.data.repository.CalendarRepository
import com.muradgalayev.brainbuddy.data.repository.PlacesRepository
import com.muradgalayev.brainbuddy.data.repository.PomodoroRepository
import com.muradgalayev.brainbuddy.data.repository.PreferencesRepository
import com.muradgalayev.brainbuddy.data.repository.TodoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single entry point for pushing/pulling Supabase state. ViewModels must NOT call
 * repository.sync() directly — that couples UI to network latency. Instead:
 *
 *  - App start:      SyncCoordinator.start() (from BrainBuddyApp.onCreate)
 *  - After sign-in:  SyncCoordinator.syncAll()
 *  - On reconnect:   handled internally via NetworkObserver
 */
@Singleton
class SyncCoordinator @Inject constructor(
    private val todoRepository: TodoRepository,
    private val calendarRepository: CalendarRepository,
    private val pomodoroRepository: PomodoroRepository,
    private val preferencesRepository: PreferencesRepository,
    private val adhdProfileRepository: AdhdProfileRepository,
    private val placesRepository: PlacesRepository,
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
            // drop(1) skips the initial "current state" emission — we already synced above.
            networkObserver.isOnline.drop(1).filter { it }.collect { syncAll() }
        }
    }

    /** Fire-and-forget. Safe to call from anywhere; never throws. */
    fun syncAll() {
        scope.launch {
            listOf(
                async { runCatching { todoRepository.sync() } },
                async { runCatching { calendarRepository.sync() } },
                async { runCatching { pomodoroRepository.sync() } },
                async { runCatching { preferencesRepository.pullRemoteAndApply() } },
                async { runCatching { adhdProfileRepository.refreshSurveyCompletedCache() } },
                async { runCatching { pullLinkedGoogleEmail() } },
                async { runCatching { prewarmPlaces() } },
            ).awaitAll()
        }
    }

    /**
     * Warm up the Care Nearby caches during app start so the first visit renders
     * without a spinner. Fetches cities + the places for the user's home city.
     * Cheap: single SELECT for cities, one for places.
     */
    private suspend fun prewarmPlaces() {
        val cities = placesRepository.listCities().getOrNull().orEmpty()
        if (cities.isEmpty()) return
        val homeCityId = adhdProfileRepository.peekProfile()?.cityId
            ?.takeIf { id -> cities.any { it.id == id } }
            ?: cities.first().id
        placesRepository.listPlacesInCity(homeCityId)
    }

    /**
     * Read the linked Google Calendar email from the Supabase profile and mirror it
     * into local prefs. Only overwrites when the remote value is present — this way
     * a linked device that hasn't synced yet won't get its local link wiped by a
     * transient network hiccup that returns null.
     */
    private suspend fun pullLinkedGoogleEmail() {
        val remote = authRepository.getLinkedGoogleEmail() ?: return
        if (remote != googleCalendarTokenStore.getLinkedEmail()) {
            googleCalendarTokenStore.saveLinkedEmail(remote)
        }
    }
}
