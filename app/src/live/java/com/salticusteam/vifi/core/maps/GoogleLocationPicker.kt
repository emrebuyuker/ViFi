package com.salticusteam.vifi.core.maps

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.salticusteam.vifi.core.model.NotificationLocation
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.LocationPicker
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.LocationPickerScreen
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.LocationPickerUnavailable
import javax.inject.Inject

/**
 * The live flavor's picker (iOS `GoogleLocationPicker`): a Google map with a Places search that opens on the
 * device's location (the permission is asked when it opens). Without a configured key it shows
 * [LocationPickerUnavailable] instead (a map would fail) and touches neither the Google SDKs nor the location.
 */
class GoogleLocationPicker @Inject constructor(
    private val sdk: GoogleMapsSdk,
) : LocationPicker {

    @Composable
    override fun Content(
        initial: NotificationLocation?,
        onPick: (NotificationLocation) -> Unit,
        onCancel: () -> Unit,
    ) {
        // The SDKs start here, the first time a picker is shown, never at launch.
        val isReady = remember { sdk.startIfPossible() }
        if (!isReady) {
            LocationPickerUnavailable(onClose = onCancel)
            return
        }
        LocationPickerScreen(
            initial = initial,
            onPick = onPick,
            onCancel = onCancel,
            map = { initialCamera, cameraRequest, showsDeviceLocation, onEvent ->
                GoogleLocationMap(initialCamera, cameraRequest, showsDeviceLocation, onEvent)
            },
        )
    }
}
