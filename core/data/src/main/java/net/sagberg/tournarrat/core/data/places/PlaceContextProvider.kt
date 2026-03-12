package net.sagberg.tournarrat.core.data.places

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.location.Location
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import net.sagberg.tournarrat.core.model.PlaceContext

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
