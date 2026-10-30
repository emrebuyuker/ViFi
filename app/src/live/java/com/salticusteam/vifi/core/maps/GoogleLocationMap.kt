package com.salticusteam.vifi.core.maps

import android.location.Location
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.VisibleRegion
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.ComposeMapColorScheme
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.CameraRequest
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.LocationPickerMapEvent
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.MapCamera
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * The Google map under the location picker's pin (iOS `GoogleMapView`). It reports where it comes to rest (the pin
 * is fixed over its center, so that is the chosen coordinate), when the admin starts dragging or pinching it, and
 * taps on places (shop and landmark labels); it moves where the picker asks it to, once per request.
 *
 * It shows the device's location (the dot and the button that re-centers on it) only when [showsDeviceLocation],
 * which the picker sets once the permission is granted; the map itself never asks for it.
 * [GoogleMapsSdk.startIfPossible] must have succeeded first: a map without a key fails.
 */
@Composable
internal fun GoogleLocationMap(
    initialCamera: MapCamera,
    cameraRequest: CameraRequest?,
    showsDeviceLocation: Boolean,
    onEvent: (LocationPickerMapEvent) -> Unit,
) {
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(
            LatLng(initialCamera.latitude, initialCamera.longitude),
            initialCamera.zoom,
        )
    }
    val currentOnEvent by rememberUpdatedState(onEvent)
    var isLoaded by remember { mutableStateOf(false) }
    // The last request acted on, so a recreated map (rotation) does not replay it over where the admin went since.
    var handledRequestId by rememberSaveable { mutableStateOf<Int?>(null) }

    LaunchedEffect(cameraRequest?.id) {
        val request = cameraRequest ?: return@LaunchedEffect
        if (request.id == handledRequestId) return@LaunchedEffect
        handledRequestId = request.id
        val camera = request.camera
        // Waits for the map if it is not ready yet; a drag during the animation ends it, where the admin left it.
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(camera.latitude, camera.longitude), camera.zoom),
        )
    }

    LaunchedEffect(cameraPositionState) {
        snapshotFlow { cameraPositionState.isMoving to isLoaded }
            .distinctUntilChanged()
            .collect { (isMoving, loaded) ->
                when {
                    // Moves the picker asked for, and the my-location button, are not the admin taking over.
                    isMoving -> if (cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
                        currentOnEvent(LocationPickerMapEvent.GestureStarted)
                    }
                    loaded -> {
                        val position = cameraPositionState.position
                        currentOnEvent(
                            LocationPickerMapEvent.Settled(
                                latitude = position.target.latitude,
                                longitude = position.target.longitude,
                                zoom = position.zoom,
                                visibleRadius = cameraPositionState.projection?.visibleRegion?.radiusMeters(),
                            ),
                        )
                    }
                }
            }
    }

    GoogleMap(
        modifier = Modifier.fillMaxSize(),
        cameraPositionState = cameraPositionState,
        properties = MapProperties(isMyLocationEnabled = showsDeviceLocation),
        uiSettings = MapUiSettings(
            compassEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = showsDeviceLocation,
            rotationGesturesEnabled = false,
            tiltGesturesEnabled = false,
        ),
        onMapLoaded = { isLoaded = true },
        onPOIClick = { poi ->
            currentOnEvent(
                LocationPickerMapEvent.PlaceTapped(poi.placeId, poi.name, poi.latLng.latitude, poi.latLng.longitude),
            )
        },
        // Equal at the top and bottom, so the camera's target stays at the center under the pin; the top inset keeps
        // the my-location button clear of the search field over the map.
        contentPadding = PaddingValues(vertical = MAP_CONTROLS_INSET),
        mapColorScheme = ComposeMapColorScheme.FOLLOW_SYSTEM,
    )
}

/** Meters from the center to the corners of what the map shows (iOS: `distance(nearLeft, farRight) / 2`). */
private fun VisibleRegion.radiusMeters(): Double {
    val distance = FloatArray(1)
    Location.distanceBetween(nearLeft.latitude, nearLeft.longitude, farRight.latitude, farRight.longitude, distance)
    return distance[0] / 2.0
}

/** Clears the search field (one line, 12 dp from the map's top edge). */
private val MAP_CONTROLS_INSET = 76.dp
