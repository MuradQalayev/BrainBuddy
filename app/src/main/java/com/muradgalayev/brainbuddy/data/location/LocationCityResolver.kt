package com.muradgalayev.brainbuddy.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

sealed class LocationCityOutcome {
    data class Detected(val candidates: List<String>) : LocationCityOutcome()
    data object PermissionMissing : LocationCityOutcome()
    data object LocationServicesOff : LocationCityOutcome()
    data object NoLocation : LocationCityOutcome()
    data object GeocoderUnavailable : LocationCityOutcome()
    data class Error(val message: String) : LocationCityOutcome()
}

@Singleton
class LocationCityResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val fusedClient by lazy {
        LocationServices.getFusedLocationProviderClient(context)
    }

    suspend fun detectCityCandidates(): LocationCityOutcome {
        if (!hasCoarsePermission()) return LocationCityOutcome.PermissionMissing
        if (!isLocationEnabled()) return LocationCityOutcome.LocationServicesOff

        val location = obtainLocation() ?: return LocationCityOutcome.NoLocation
        Log.d(TAG, "lat=${location.latitude} lng=${location.longitude}")

        if (!Geocoder.isPresent()) return LocationCityOutcome.GeocoderUnavailable

        val candidates = withContext(Dispatchers.IO) {
            reverseGeocodeCandidates(location.latitude, location.longitude)
        }
        Log.d(TAG, "candidates: $candidates")

        return if (candidates.isEmpty()) LocationCityOutcome.NoLocation
        else LocationCityOutcome.Detected(candidates)
    }

    private fun hasCoarsePermission(): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

    private fun isLocationEnabled(): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            lm.isLocationEnabled
        } else {
            @Suppress("DEPRECATION")
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun obtainLocation(): Location? {
        // Last-known is instant if cached, so try it first to short-circuit
        // the spinner. Then race a fresh fix with a hard 8s ceiling.
        lastLocation()?.let { return it }
        return withTimeoutOrNull(CURRENT_FIX_TIMEOUT_MS) { currentLocation() }
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? = suspendCancellableCoroutine { cont ->
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setMaxUpdateAgeMillis(5 * 60_000L)
            .setDurationMillis(CURRENT_FIX_TIMEOUT_MS)
            .build()
        try {
            fusedClient.getCurrentLocation(request, null)
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener {
                    Log.w(TAG, "current fix failed: ${it.message}")
                    cont.resume(null)
                }
        } catch (t: Throwable) {
            Log.w(TAG, "current fix threw: ${t.message}")
            cont.resume(null)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastLocation(): Location? = suspendCancellableCoroutine { cont ->
        try {
            fusedClient.lastLocation
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener {
                    Log.w(TAG, "lastLocation failed: ${it.message}")
                    cont.resume(null)
                }
        } catch (t: Throwable) {
            Log.w(TAG, "lastLocation threw: ${t.message}")
            cont.resume(null)
        }
    }

    private fun reverseGeocodeCandidates(lat: Double, lng: Double): List<String> {
        val geocoder = runCatching { Geocoder(context, Locale.getDefault()) }.getOrNull()
            ?: return emptyList()

        val addresses = runCatching {
            @Suppress("DEPRECATION")
            geocoder.getFromLocation(lat, lng, 1)
        }.onFailure {
            Log.w(TAG, "geocode failed: ${it.message}")
        }.getOrNull() ?: return emptyList()

        val a = addresses.firstOrNull() ?: return emptyList()
        return listOfNotNull(a.locality, a.subLocality, a.subAdminArea, a.adminArea)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    companion object {
        private const val TAG = "LocationCityResolver"
        private const val CURRENT_FIX_TIMEOUT_MS = 8_000L
    }
}
