package net.sagberg.tournarrat.core.data.places

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.sagberg.tournarrat.core.model.PlaceContext
import net.sagberg.tournarrat.core.data.preferences.ApiKeyStore

interface PlaceContextProvider {
    suspend fun resolve(location: Location): PlaceContext
}

class GeocoderPlaceContextProvider(
    context: Context,
) : PlaceContextProvider {
    private val geocoder = Geocoder(context, Locale.getDefault())

    override suspend fun resolve(location: Location): PlaceContext {
        val address = getAddress(location)

        val areaName = listOfNotNull(
            address?.featureName,
            address?.subLocality,
            address?.locality,
            address?.adminArea,
        ).firstOrNull().orEmpty().ifBlank {
            "${"%.4f".format(location.latitude)}, ${"%.4f".format(location.longitude)}"
        }

        val hints = listOfNotNull(
            address?.featureName,
            address?.thoroughfare,
            address?.subLocality,
            address?.locality,
            address?.adminArea,
        ).map(String::trim).filter(String::isNotBlank).distinct()

        return PlaceContext(
            latitude = location.latitude,
            longitude = location.longitude,
            areaName = areaName,
            locality = address?.locality ?: address?.subAdminArea,
            countryName = address?.countryName,
            hints = hints,
        )
    }

    private suspend fun getAddress(location: Location): Address? = suspendCancellableCoroutine { continuation ->
        runCatching {
            geocoder.getFromLocation(
                location.latitude,
                location.longitude,
                1,
                object : Geocoder.GeocodeListener {
                    override fun onGeocode(addresses: MutableList<Address>) {
                        continuation.resume(addresses.firstOrNull())
                    }

                    override fun onError(errorMessage: String?) {
                        continuation.resume(null)
                    }
                },
            )
        }.onFailure {
            continuation.resume(null)
        }
    }
}

class GooglePlacesContextProvider(
    context: Context,
    private val apiKeyStore: ApiKeyStore,
    private val json: Json,
) : PlaceContextProvider {
    private val fallbackProvider = GeocoderPlaceContextProvider(context)

    override suspend fun resolve(location: Location): PlaceContext {
        val fallbackContext = fallbackProvider.resolve(location)
        val apiKey = apiKeyStore.getGooglePlacesApiKey().orEmpty().trim()
        if (apiKey.isBlank()) return fallbackContext

        return runCatching {
            enrichWithNearbyPlaces(
                apiKey = apiKey,
                location = location,
                fallbackContext = fallbackContext,
            )
        }.getOrElse { fallbackContext }
    }

    private suspend fun enrichWithNearbyPlaces(
        apiKey: String,
        location: Location,
        fallbackContext: PlaceContext,
    ): PlaceContext = withContext(Dispatchers.IO) {
        val connection = createConnection(apiKey)
        val body = NearbySearchRequest(
            maxResultCount = MAX_RESULT_COUNT,
            languageCode = Locale.getDefault().toLanguageTag(),
            rankPreference = "POPULARITY",
            locationRestriction = LocationRestriction(
                circle = SearchCircle(
                    center = SearchCenter(
                        latitude = location.latitude,
                        longitude = location.longitude,
                    ),
                    radius = SEARCH_RADIUS_METERS,
                ),
            ),
        )
        connection.outputStream.use { output ->
            output.writer().use { writer ->
                writer.write(json.encodeToString(body))
            }
        }

        val payload = connection.readResponseText()
        if (connection.responseCode !in 200..299) {
            error(payload.ifBlank { "Google Places request failed with ${connection.responseCode}." })
        }

        val response = json.decodeFromString<NearbySearchResponse>(payload)
        response.mergeInto(fallbackContext)
    }

    private fun createConnection(apiKey: String): HttpURLConnection {
        return (URL(NEARBY_SEARCH_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", apiKey)
            setRequestProperty(
                "X-Goog-FieldMask",
                "places.displayName,places.formattedAddress,places.primaryType,places.types",
            )
        }
    }

    private fun NearbySearchResponse.mergeInto(fallbackContext: PlaceContext): PlaceContext {
        val placeNames = places.mapNotNull { it.displayName?.text?.trim() }
            .filter(String::isNotBlank)
        val typeHints = places.asSequence()
            .flatMap { place ->
                sequence {
                    place.primaryType?.let { yield(it) }
                    place.types.forEach { yield(it) }
                }
            }
            .map(::humanizeType)
            .filter(String::isNotBlank)
            .distinct()
            .take(4)
            .toList()

        val mergedHints = buildList {
            addAll(placeNames.take(4))
            addAll(typeHints)
            addAll(fallbackContext.hints)
        }.distinct()

        return fallbackContext.copy(
            areaName = placeNames.firstOrNull() ?: fallbackContext.areaName,
            hints = mergedHints,
        )
    }

    private fun humanizeType(type: String): String =
        type.replace('_', ' ')
            .lowercase()
            .replaceFirstChar(Char::titlecase)

    private companion object {
        const val NEARBY_SEARCH_URL = "https://places.googleapis.com/v1/places:searchNearby"
        const val MAX_RESULT_COUNT = 5
        const val SEARCH_RADIUS_METERS = 300.0
    }
}

private fun HttpURLConnection.readResponseText(): String {
    val stream = if (responseCode in 200..299) inputStream else errorStream
    return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
}

@Serializable
private data class NearbySearchRequest(
    val maxResultCount: Int,
    val languageCode: String,
    val rankPreference: String,
    val locationRestriction: LocationRestriction,
)

@Serializable
private data class LocationRestriction(
    val circle: SearchCircle,
)

@Serializable
private data class SearchCircle(
    val center: SearchCenter,
    val radius: Double,
)

@Serializable
private data class SearchCenter(
    val latitude: Double,
    val longitude: Double,
)

@Serializable
private data class NearbySearchResponse(
    val places: List<NearbyPlace> = emptyList(),
)

@Serializable
private data class NearbyPlace(
    val displayName: NearbyDisplayName? = null,
    val formattedAddress: String? = null,
    val primaryType: String? = null,
    val types: List<String> = emptyList(),
)

@Serializable
private data class NearbyDisplayName(
    val text: String? = null,
    @SerialName("languageCode")
    val languageCode: String? = null,
)
