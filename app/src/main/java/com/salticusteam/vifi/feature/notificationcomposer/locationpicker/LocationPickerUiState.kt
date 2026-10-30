package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

import com.salticusteam.vifi.core.model.NotificationLocation
import com.salticusteam.vifi.core.model.codePointLength
import java.util.Locale

/** Where the map is looking: the first camera, and the moves the picker asks the map for. */
data class MapCamera(
    val latitude: Double,
    val longitude: Double,
    val zoom: Float,
)

/** A request to move the map. The [id] makes two moves to the same spot distinct, so the map acts on each. */
data class CameraRequest(
    val id: Int,
    val camera: MapCamera,
)

/** What the search box shows under the field. */
sealed interface PlaceSearchState {
    data object Idle : PlaceSearchState
    data object Searching : PlaceSearchState
    data class Results(val suggestions: List<PlaceSuggestion>) : PlaceSearchState
    data object NoResults : PlaceSearchState
    data class Failed(val message: String) : PlaceSearchState
}

/**
 * The location picker's single state (iOS `LocationPickerViewModel`'s observed properties). The pin is fixed at
 * the map's center, so [latitude]/[longitude] are wherever the map came to rest; [location] is the result, only
 * once it is valid.
 */
data class LocationPickerUiState(
    /** Where the map starts: the location being changed, otherwise Turkey as a whole. */
    val initialCamera: MapCamera,
    val latitude: Double,
    val longitude: Double,
    val name: String = "",
    val address: String = "",
    /** The Google place the pin is on, while it is still on it. */
    val placeId: String? = null,
    /** The text in the search box. */
    val query: String = "",
    val searchState: PlaceSearchState = PlaceSearchState.Idle,
    /** Whether a chosen suggestion is being resolved to a place. */
    val isResolving: Boolean = false,
    /** Why the chosen suggestion could not be used. */
    val resolveError: String? = null,
    /** The latest move the map was asked to make; `null` until there is somewhere to go. */
    val cameraRequest: CameraRequest? = null,
    /** Whether the address of a place tapped on the map is still being looked up. */
    val isLoadingAddress: Boolean = false,
    /** The location permission once the picker has checked it; `null` before that. */
    val locationAuthorization: LocationAuthorization? = null,
) {

    val trimmedName: String get() = name.trim()
    val trimmedAddress: String get() = address.trim()

    // Code points, like the server counts the limits.
    val isNameTooLong: Boolean get() = trimmedName.codePointLength() > NotificationLocation.NAME_LIMIT
    val isAddressTooLong: Boolean get() = trimmedAddress.codePointLength() > NotificationLocation.ADDRESS_LIMIT

    /** The chosen place, or `null` while something is missing (a name) or over its limit. */
    val location: NotificationLocation?
        get() = NotificationLocation(
            latitude = latitude,
            longitude = longitude,
            name = trimmedName,
            address = trimmedAddress.ifEmpty { null },
            placeId = placeId,
        ).takeIf { it.isValid }

    val canConfirm: Boolean get() = location != null && !isResolving

    /** "41.01514, 28.97953": the pin's coordinates, shown so the admin sees what will be sent (iOS `%.5f`). */
    val coordinateText: String get() = String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude)

    /** Whether the map shows the device's location (its dot and the button that re-centers on it). */
    val showsDeviceLocation: Boolean get() = locationAuthorization == LocationAuthorization.AUTHORIZED

    /** Whether the location permission is off; the picker then only says so, quietly. */
    val isLocationDenied: Boolean get() = locationAuthorization == LocationAuthorization.DENIED

    /** Whether the panel under the search box is open: a search under way or done, or a failed resolve. */
    val showsSearchPanel: Boolean get() = searchState != PlaceSearchState.Idle || resolveError != null
}

/** One-shot requests from the picker to its screen. */
sealed interface LocationPickerEvent {

    /** Ask for the location permission (once per picker) and report the answer to the view model. */
    data object RequestLocationPermission : LocationPickerEvent
}

/** What happened on the map (iOS `GoogleMapView.Event`). */
sealed interface LocationPickerMapEvent {

    /** The map came to rest. [visibleRadius] is the distance from the center to the corners, in meters. */
    data class Settled(
        val latitude: Double,
        val longitude: Double,
        val zoom: Float? = null,
        val visibleRadius: Double? = null,
    ) : LocationPickerMapEvent

    /** The admin started a drag or pinch. Moves the picker asked for, and the my-location button, are not one. */
    data object GestureStarted : LocationPickerMapEvent

    /** The admin tapped a place's label on the map. */
    data class PlaceTapped(
        val placeId: String,
        val name: String,
        val latitude: Double,
        val longitude: Double,
    ) : LocationPickerMapEvent
}
