package com.muradgalayev.brainbuddy.data.repository

import android.util.Log
import com.muradgalayev.brainbuddy.data.remote.dto.CityDto
import com.muradgalayev.brainbuddy.data.remote.dto.PlaceDto
import com.muradgalayev.brainbuddy.domain.model.City
import com.muradgalayev.brainbuddy.domain.model.Place
import com.muradgalayev.brainbuddy.domain.model.PlaceCategory
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "PlacesRepo"

@Singleton
class PlacesRepository @Inject constructor(
    private val supabaseClient: SupabaseClient,
) {
    @Volatile private var cachedCities: List<City>? = null
    private val cachedPlacesByCity = mutableMapOf<String, List<Place>>()

    fun hasCachedCities(): Boolean = cachedCities != null
    fun hasCachedPlaces(cityId: String): Boolean = cachedPlacesByCity.containsKey(cityId)

    /** Synchronous accessors for instant first-frame rendering on revisits. */
    fun peekCities(): List<City>? = cachedCities
    fun peekPlaces(cityId: String): List<Place>? = cachedPlacesByCity[cityId]

    suspend fun listCities(forceRefresh: Boolean = false): Result<List<City>> {
        if (!forceRefresh) cachedCities?.let { return Result.success(it) }
        return runCatching {
            supabaseClient.from("cities")
                .select { order("name", Order.ASCENDING) }
                .decodeList<CityDto>()
                .map { City(it.id, it.name, it.region, it.country) }
        }.onSuccess { cachedCities = it }
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
        }.onSuccess { cachedPlacesByCity[cityId] = it }
            .onFailure { Log.w(TAG, "listPlacesInCity failed: ${it.message}") }
    }

    fun invalidateCache() {
        cachedCities = null
        cachedPlacesByCity.clear()
    }
}
