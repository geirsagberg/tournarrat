package net.sagberg.tournarrat.core.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface CurrentLocationProvider {
    suspend fun getCurrentLocation(): Result<Location>
}

class LocationPermissionMissingException : IllegalStateException(
    "Location permission is missing. Grant location access and try again.",
)

class LocationFixUnavailableException : IllegalStateException(
    "No location fix is available yet. If you are on an emulator, set a location in Extended Controls > Location and try again.",
)

class FusedCurrentLocationProvider(
    context: Context,
) : CurrentLocationProvider {
    private val appContext = context.applicationContext
    private val client = LocationServices.getFusedLocationProviderClient(appContext)

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): Result<Location> {
        if (!hasLocationPermission()) return Result.failure(LocationPermissionMissingException())
        val location = runCatching {
            withTimeoutOrNull(LOCATION_TIMEOUT_MILLIS) {
                resolveCurrentLocation()
            }
        }.getOrNull()

        return location?.let(Result.Companion::success)
            ?: Result.failure(LocationFixUnavailableException())
    }

    private suspend fun resolveCurrentLocation(): Location? {
        client.getCurrentLocation(
            CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setDurationMillis(FAST_REQUEST_TIMEOUT_MILLIS)
                .setMaxUpdateAgeMillis(0)
                .build(),
            null,
        ).await()?.let { return it }

        client.lastLocation.await()?.let { return it }

        return client.getCurrentLocation(
            CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setDurationMillis(FRESH_FIX_TIMEOUT_MILLIS)
                .setMaxUpdateAgeMillis(0)
                .build(),
            null,
        ).await()
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    private companion object {
        const val LOCATION_TIMEOUT_MILLIS = 15_000L
        const val FAST_REQUEST_TIMEOUT_MILLIS = 5_000L
        const val FRESH_FIX_TIMEOUT_MILLIS = 10_000L
    }
}

private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(it) }
    addOnFailureListener { exception -> continuation.resumeWithException(exception) }
    addOnCanceledListener { continuation.resume(null) }
}
