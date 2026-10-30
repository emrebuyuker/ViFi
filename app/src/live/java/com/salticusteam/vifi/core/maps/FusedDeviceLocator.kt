package com.salticusteam.vifi.core.maps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.DeviceCoordinate
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.DeviceLocator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * [DeviceLocator] on the fused location provider, for the admin's location picker (iOS `CoreLocationProvider`).
 * The client is created on first use, so a picker without the permission costs nothing. A fix is only handed to
 * the picker to move the map; it is never logged, stored or sent.
 */
class FusedDeviceLocator @Inject constructor(
    @ApplicationContext private val context: Context,
) : DeviceLocator {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    override suspend fun currentLocation(): DeviceCoordinate? {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val request = CurrentLocationRequest.Builder()
            // About 100 m: enough to center a street-level map; a finer fix only takes longer.
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setDurationMillis(FIX_TIMEOUT_MILLIS)
            .build()
        val cancellation = CancellationTokenSource()
        return try {
            val location = client.getCurrentLocation(request, cancellation.token).await() ?: return null
            val isValid = location.hasAccuracy() &&
                location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0
            if (!isValid) return null
            DeviceCoordinate(location.latitude, location.longitude, location.accuracy.toDouble())
        } catch (e: CancellationException) {
            // The picker went away: stop the request too.
            cancellation.cancel()
            throw e
        } catch (e: Exception) {
            // The reason only, no coordinates: no fix available, location off, and so on.
            Timber.i("Device location unavailable: %s", e.javaClass.simpleName)
            null
        }
    }

    private companion object {
        const val FIX_TIMEOUT_MILLIS = 15_000L
    }
}
