package com.muradgalayev.brainbuddy.domain.ai.tools

import com.muradgalayev.brainbuddy.data.location.LocationCityResolver
import com.muradgalayev.brainbuddy.data.location.LocationFixOutcome
import com.muradgalayev.brainbuddy.data.location.distanceMeters
import com.muradgalayev.brainbuddy.data.location.formatDistance
import com.muradgalayev.brainbuddy.data.repository.AdhdProfileRepository
import com.muradgalayev.brainbuddy.data.repository.CareViewCoordinator
import com.muradgalayev.brainbuddy.data.repository.PlacesRepository
import com.muradgalayev.brainbuddy.domain.ai.AiNavigator
import com.muradgalayev.brainbuddy.domain.ai.AiTool
import com.muradgalayev.brainbuddy.domain.model.Place
import com.muradgalayev.brainbuddy.domain.model.PlaceCategory
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

// looks up nearby healthcare places (hospitals, specialists, dentists, pharmacies, ASL,
// support groups) in the user's city and, on request, opens the Care Nearby screen. read-only
// over PlacesRepository, the catalogue is curated in Supabase
class FindCareTool @Inject constructor(
    private val placesRepository: PlacesRepository,
    private val adhdProfileRepository: AdhdProfileRepository,
    private val locationResolver: LocationCityResolver,
    private val careViewCoordinator: CareViewCoordinator,
    private val navigator: AiNavigator,
) : AiTool {

    override val name: String = "find_care_nearby"

    override val description: String =
        "Find nearby healthcare places for the user — hospitals, specialists (incl. " +
            "dentists), pharmacies, ASL offices, listening centres, support groups. " +
            "Use for 'find me a dentist', 'any pharmacies near me?', 'where's the " +
            "nearest OPEN clinic?'. When the device has location permission it sorts by " +
            "real distance and reports how far each place is. Each result includes its " +
            "opening hours plus the current time, so you can pick the closest one that's " +
            "actually open right now and tell the user just that one. Optional 'category' " +
            "narrows the type; 'query' matches a name; 'limit' caps how many come back " +
            "(use 1 when the user wants only the single closest/open match). Set " +
            "open_screen=true to also open the Care Nearby screen."

    override val parametersSchema: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("category") {
                put("type", "string")
                putJsonArray("enum") {
                    add("hospital"); add("specialist"); add("pharmacy")
                    add("asl"); add("centro_ascolto"); add("support_group"); add("other")
                }
                put("description", "Type of place. 'specialist' covers dentists and doctors.")
            }
            putJsonObject("query") {
                put("type", "string")
                put("description", "Optional free-text to match a place name, e.g. 'dentist'.")
            }
            putJsonObject("limit") {
                put("type", "integer")
                put(
                    "description",
                    "Max results to return (default 5, max 10). Use 1 when the user " +
                        "wants just the single closest match."
                )
            }
            putJsonObject("open_screen") {
                put("type", "boolean")
                put("description", "Also open the Care Nearby screen for the user.")
            }
        }
    }

    override suspend fun execute(args: JsonObject): String {
        val cityId = resolveCityId()
            ?: return "I don't have a city set for you yet — open Care Nearby and pick " +
                "your city first."

        val places = placesRepository.listPlacesInCity(cityId).getOrElse {
            return "Couldn't load care places right now (${it.message ?: "network issue"})."
        }

        val categoryFilter = args["category"]?.jsonPrimitive?.content
            ?.let { PlaceCategory.fromKey(it.trim().lowercase()) }
        val query = args["query"]?.jsonPrimitive?.content?.trim()?.lowercase()

        var matches = places
        if (categoryFilter != null) matches = matches.filter { it.category == categoryFilter }
        if (!query.isNullOrBlank()) {
            matches = matches.filter {
                it.name.lowercase().contains(query) || it.notes.lowercase().contains(query)
            }
        }

        val openScreen = args["open_screen"]?.jsonPrimitive?.content?.toBoolean() ?: false
        if (openScreen) {
            // hand the exact matches to the Care Nearby screen so it mirrors the chat's filter instead of
            // listing every place in the city
            careViewCoordinator.request(
                cityId = cityId,
                category = categoryFilter,
                placeIds = matches.map { it.id }.toSet(),
            )
            navigator.navigateTo("care_nearby")
        }

        if (matches.isEmpty()) {
            return "I couldn't find a matching place in your city's list" +
                (if (openScreen) " — I opened Care Nearby so you can browse everything." else ".")
        }

        val limit = (args["limit"]?.jsonPrimitive?.content?.toIntOrNull() ?: DEFAULT_LIMIT)
            .coerceIn(1, MAX_LIMIT)
        // stamp the current time so the model can decide what's open right now from each place's
        // free-text hours, rather than us parsing them unreliably
        val nowLabel = LocalTime.now().truncatedTo(ChronoUnit.MINUTES).toString()
        val prefix = "Right now it's $nowLabel. "

        // if we can read the device location, sort by real distance and label each result with how
        // far it is. otherwise fall back to the curated name order
        val fix = locationResolver.currentCoordinates()
        if (fix is LocationFixOutcome.Located) {
            val ranked = matches
                .filter { it.lat != null && it.lng != null }
                .map { it to distanceMeters(fix.lat, fix.lng, it.lat!!, it.lng!!) }
                .sortedBy { it.second }
            if (ranked.isNotEmpty()) {
                // exactly one place in the whole category or query, so say so plainly rather than dressing a
                // single result up as a ranked list
                if (matches.size == 1) {
                    val (p, d) = ranked.first()
                    return "${prefix}Only one match — ${formatPlace(p)} — ${formatDistance(d)} away."
                }
                val shown = ranked.take(limit).joinToString("; ") { (p, d) ->
                    "${formatPlace(p)} — ${formatDistance(d)} away"
                }
                val more = if (ranked.size > limit) " (+${ranked.size - limit} more)" else ""
                return "${prefix}Closest first — $shown$more."
            }
        }

        val locationNote = when (fix) {
            LocationFixOutcome.PermissionMissing ->
                " (turn on location to sort by distance)"
            LocationFixOutcome.LocationServicesOff ->
                " (location is off, so I can't rank by distance)"
            else -> ""
        }
        if (matches.size == 1) {
            return "${prefix}Only one match — ${formatPlace(matches.first())}.$locationNote"
        }
        val shown = matches.take(limit).joinToString("; ") { formatPlace(it) }
        val more = if (matches.size > limit) " (+${matches.size - limit} more)" else ""
        return "${prefix}Found ${matches.size}: $shown$more.$locationNote"
    }

    private suspend fun resolveCityId(): String? {
        adhdProfileRepository.peekProfile()?.cityId?.let { return it }
        runCatching { adhdProfileRepository.getProfile()?.cityId }.getOrNull()?.let { return it }
        // fall back to the first available city so the tool still returns something
        return placesRepository.peekCities()?.firstOrNull()?.id
            ?: placesRepository.listCities().getOrNull()?.firstOrNull()?.id
    }

    private fun formatPlace(p: Place): String {
        val bits = buildList {
            add("${p.name} (${p.category.label})")
            if (p.address.isNotBlank()) add(p.address)
            // hours are the signal the model needs to judge open or closed
            if (p.hours.isNotBlank()) add("hours: ${p.hours}") else add("hours: not listed")
            // contact details, so the model can call, email or open the site via the contact tool
            if (p.phone.isNotBlank()) add("tel: ${p.phone}")
            if (p.email.isNotBlank()) add("email: ${p.email}")
            if (p.website.isNotBlank()) add("web: ${p.website}")
        }
        return bits.joinToString(", ")
    }

    companion object {
        private const val DEFAULT_LIMIT = 5
        private const val MAX_LIMIT = 10
    }
}
