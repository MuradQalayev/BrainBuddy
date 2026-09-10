package com.muradgalayev.brainbuddy.data.location

import android.location.Location

// great-circle distance in metres between two lat/lng points
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
    val result = FloatArray(1)
    Location.distanceBetween(lat1, lng1, lat2, lng2, result)
    return result[0]
}

// human-friendly distance, e.g. '450 m' or '2.3 km'
fun formatDistance(meters: Float): String =
    if (meters < 1000f) "${meters.toInt()} m"
    else "%.1f km".format(meters / 1000f)
