package com.muradgalayev.brainbuddy.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import javax.inject.Inject
import javax.inject.Singleton

// emits true only when the device has a validated internet connection, which rules out
// captive portals and 'connected to wifi but no actual internet'.
// the callback is registered once for the life of the process and pushes into a hot StateFlow,
// rather than being registered per-subscriber inside a callbackFlow. that distinction was a
// real bug, not a tidiness point: with the old design the registration was torn down whenever
// the last subscriber went away and rebuilt on the next one, and rebuilding re-read
// currentlyOnline, so every re-subscription quietly repaired a stale value.
// screen-scoped consumers got that repair for free just by being navigated away from and back
// to. the nav-bar AI button did not, because its view-model is activity-scoped: it subscribed
// once at launch and stayed subscribed, and the registration was never rebuilt. miss a single
// connectivity callback, which happens routinely while backgrounded or dozing, and that
// button was stuck offline until the process died while every other entry point recovered.
// one always-on registration plus refresh() on foreground removes the whole class of staleness
@Singleton
class NetworkObserver @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val rawState = MutableStateFlow(currentlyOnline())

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = publish()
        override fun onLost(network: Network) = publish()
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) = publish()
    }

    init {
        // never unregistered: this is a process-lifetime singleton, and the whole point is that the
        // registration outlives any individual screen
        runCatching {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder().build(),
                callback
            )
        }.onFailure {
            Log.w(TAG, "Could not register network callback: ${it.message}")
        }
    }

    // re-reads the system's connectivity state, called when the app returns to the foreground.
    // connectivity callbacks aren't guaranteed to reach a backgrounded process, so the state can
    // be wrong by the time the user is looking at it again, and the system's answer is authoritative
    fun refresh() = publish()

    private fun publish() {
        rawState.value = currentlyOnline()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val isOnline: StateFlow<Boolean> = rawState
        // going offline is held back for a moment, coming back is instant. the raw callbacks flap:
        // handing over between wifi and cellular, or a network re-validating, drops VALIDATED for a
        // fraction of a second, and anything driven off this flow flickered in step with it. a short
        // debounce on the falling edge only costs a late offline notice, and leaving the rising edge
        // immediate means recovery is still felt the instant it happens
        .transformLatest { online ->
            if (online) emit(true) else {
                delay(OFFLINE_GRACE_MS)
                emit(false)
            }
        }
        .distinctUntilChanged()
        // Eagerly, not WhileSubscribed: a connectivity signal that arrives while nothing happens to
        // be collecting must still be recorded, or the next subscriber reads a value from whenever
        // the last one lost interest
        .stateIn(scope, SharingStarted.Eagerly, currentlyOnline())

    fun currentlyOnline(): Boolean {
        val active = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private companion object {
        private const val TAG = "NetworkObserver"

        // how long a connection has to stay down before the app calls itself offline
        const val OFFLINE_GRACE_MS = 2_000L
    }
}
