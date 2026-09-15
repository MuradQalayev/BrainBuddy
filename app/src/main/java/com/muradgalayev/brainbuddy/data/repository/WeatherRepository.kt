package com.muradgalayev.brainbuddy.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class WeatherSnapshot(
    val city: String,
    val temperatureC: Double,
    val apparentC: Double,
    val highC: Double,
    val lowC: Double,
    val weatherCode: Int,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Singleton
class WeatherRepository @Inject constructor(@ApplicationContext context: Context) {
    private val json = Json { ignoreUnknownKeys = true }
    private val prefs = context.getSharedPreferences("weather_cache", Context.MODE_PRIVATE)
    private val fetchMutex = Mutex()

    // the last snapshot for this city at any age, for painting the tile on the first frame
    fun peek(city: String): WeatherSnapshot? = prefs.getString(KEY_SNAPSHOT, null)
        ?.let { runCatching { json.decodeFromString<WeatherSnapshot>(it) }.getOrNull() }
        ?.takeIf { it.city.equals(city, ignoreCase = true) }

    // cached first, network only when the cache has actually aged out. Home calls this on every
    // entry to the tab, and it used to mean two requests, geocode plus forecast, every single time,
    // for a number that moves by a degree an hour. inside FRESH_FOR_MS the stored snapshot is
    // simply returned, and the coordinates for a city name are cached outright since those never
    // change. pass forceRefresh for an explicit user-initiated refresh
    suspend fun current(city: String, forceRefresh: Boolean = false): Result<WeatherSnapshot> {
        if (!forceRefresh) fresh(city)?.let { return Result.success(it) }
        // one fetch at a time: two screens asking at once should cost one round trip, and the loser of
        // the race re-reads what the winner just wrote
        return fetchMutex.withLock {
            if (!forceRefresh) fresh(city)?.let { return@withLock Result.success(it) }
            withContext(Dispatchers.IO) {
                runCatching {
                    val (latitude, longitude, resolvedName) = coordinatesOf(city)
                    val forecast = getJson<ForecastResponse>(
                        "https://api.open-meteo.com/v1/forecast?latitude=$latitude&longitude=$longitude" +
                            "&current=temperature_2m,apparent_temperature,weather_code" +
                            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
                    )
                    WeatherSnapshot(
                        city = resolvedName.ifBlank { city },
                        temperatureC = forecast.current.temperature,
                        apparentC = forecast.current.apparent,
                        highC = forecast.daily.high.firstOrNull() ?: forecast.current.temperature,
                        lowC = forecast.daily.low.firstOrNull() ?: forecast.current.temperature,
                        weatherCode = forecast.current.code,
                    ).also {
                        prefs.edit()
                            .putString(KEY_SNAPSHOT, json.encodeToString(WeatherSnapshot.serializer(), it))
                            .apply()
                    }
                }.recoverCatching { peek(city) ?: throw it }
            }
        }
    }

    // the cached snapshot while it's still worth showing, or null once it has aged out
    private fun fresh(city: String): WeatherSnapshot? =
        peek(city)?.takeIf { System.currentTimeMillis() - it.updatedAt < FRESH_FOR_MS }

    // geocoding, once per city per install. a city's coordinates are a constant, so re-asking for
    // them on every forecast was a request that could never return anything new
    private fun coordinatesOf(city: String): Triple<Double, Double, String> {
        val key = "$KEY_GEO_PREFIX${city.lowercase()}"
        prefs.getString(key, null)
            ?.let { runCatching { json.decodeFromString<CachedGeo>(it) }.getOrNull() }
            ?.let { return Triple(it.latitude, it.longitude, it.name) }

        val encoded = URLEncoder.encode(city, Charsets.UTF_8.name())
        val geo = getJson<GeocodingResponse>(
            "https://geocoding-api.open-meteo.com/v1/search?name=$encoded&count=1&language=${java.util.Locale.getDefault().language}&format=json",
        ).results.firstOrNull() ?: error("Location not found")
        val cached = CachedGeo(geo.latitude, geo.longitude, geo.name)
        prefs.edit().putString(key, json.encodeToString(CachedGeo.serializer(), cached)).apply()
        return Triple(cached.latitude, cached.longitude, cached.name)
    }

    private inline fun <reified T> getJson(url: String): T {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.requestMethod = "GET"
            if (connection.responseCode !in 200..299) error("Weather service unavailable")
            json.decodeFromString(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val KEY_SNAPSHOT = "current"
        const val KEY_GEO_PREFIX = "geo_"

        // half an hour. long enough that flipping between tabs costs nothing, short enough that the
        // tile still tracks a day that's actually changing, and the daily high and low roll over well
        // inside it
        val FRESH_FOR_MS = TimeUnit.MINUTES.toMillis(30)
    }
}

@Serializable private data class CachedGeo(
    val latitude: Double,
    val longitude: Double,
    val name: String,
)

@Serializable private data class GeocodingResponse(val results: List<GeoResult> = emptyList())
@Serializable private data class GeoResult(val name: String = "", val latitude: Double, val longitude: Double)
@Serializable private data class ForecastResponse(val current: CurrentWeather, val daily: DailyWeather)
@Serializable private data class CurrentWeather(
    @kotlinx.serialization.SerialName("temperature_2m") val temperature: Double,
    @kotlinx.serialization.SerialName("apparent_temperature") val apparent: Double,
    @kotlinx.serialization.SerialName("weather_code") val code: Int,
)
@Serializable private data class DailyWeather(
    @kotlinx.serialization.SerialName("temperature_2m_max") val high: List<Double> = emptyList(),
    @kotlinx.serialization.SerialName("temperature_2m_min") val low: List<Double> = emptyList(),
)
