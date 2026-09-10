package com.muradgalayev.brainbuddy.data.repository

import android.content.Context
import android.util.Log
import com.muradgalayev.brainbuddy.data.remote.dto.CityDto
import com.muradgalayev.brainbuddy.data.remote.dto.PlaceDto
import com.muradgalayev.brainbuddy.domain.model.City
import com.muradgalayev.brainbuddy.domain.model.Place
import com.muradgalayev.brainbuddy.domain.model.PlaceCategory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PlacesRepo"

@Singleton
class PlacesRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("places_cache", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val diskCache = prefs.getString(KEY_CACHE, null)?.let {
        runCatching { json.decodeFromString<PlacesDiskCache>(it) }.getOrNull()
    }
    @Volatile private var cachedCities: List<City>? = diskCache?.cities
    private val cachedPlacesByCity = diskCache?.placesByCity?.toMutableMap() ?: mutableMapOf()

    fun hasCachedCities(): Boolean = cachedCities != null
    fun hasCachedPlaces(cityId: String): Boolean = cachedPlacesByCity.containsKey(cityId)

    // synchronous accessors for instant first-frame rendering on revisits
    fun peekCities(): List<City>? = cachedCities
    fun peekPlaces(cityId: String): List<Place>? = cachedPlacesByCity[cityId]

    suspend fun listCities(forceRefresh: Boolean = false): Result<List<City>> {
        if (!forceRefresh) cachedCities?.let { return Result.success(it) }
        return runCatching {
            supabaseClient.from("cities")
                .select { order("name", Order.ASCENDING) }
                .decodeList<CityDto>()
                .map { City(it.id, it.name, it.region, it.country) }
        }.onSuccess {
            cachedCities = it
            persistCache()
        }
            .onFailure { Log.w(TAG, "listCities failed: ${it.message}") }
    }

    suspend fun listPlacesInCity(
        cityId: String,
        forceRefresh: Boolean = false,
    ): Result<List<Place>> {
        if (!forceRefresh) cachedPlacesByCity[cityId]?.let { return Result.success(it) }
        return runCatching {
            supabaseClient.from("places")
                .select {
                    filter { eq("city_id", cityId) }
                    order("name", Order.ASCENDING)
                }
                .decodeList<PlaceDto>()
                .map {
                    Place(
                        id = it.id,
                        cityId = it.cityId,
                        name = it.name,
                        category = PlaceCategory.fromKey(it.category),
                        address = it.address,
                        phone = it.phone,
                        email = it.email,
                        website = it.website,
                        hours = it.hours,
                        lat = it.lat,
                        lng = it.lng,
                        notes = it.notes,
                    )
                }
        }.onSuccess {
            cachedPlacesByCity[cityId] = it
            persistCache()
        }
            .onFailure { Log.w(TAG, "listPlacesInCity failed: ${it.message}") }
    }

    fun invalidateCache() {
        cachedCities = null
        cachedPlacesByCity.clear()
        prefs.edit().remove(KEY_CACHE).apply()
    }

    private fun persistCache() {
        val cities = cachedCities ?: return
        val snapshot = synchronized(cachedPlacesByCity) { cachedPlacesByCity.toMap() }
        prefs.edit().putString(
            KEY_CACHE,
            json.encodeToString(PlacesDiskCache(cities, snapshot)),
        ).apply()
    }

    private companion object { const val KEY_CACHE = "care_places" }
}

@Serializable
private data class PlacesDiskCache(
    val cities: List<City> = emptyList(),
    val placesByCity: Map<String, List<Place>> = emptyMap(),
)
