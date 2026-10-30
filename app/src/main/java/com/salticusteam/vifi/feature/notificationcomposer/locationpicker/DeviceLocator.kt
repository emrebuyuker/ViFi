package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

/** Whether the app may read the device's location while it is in use. */
enum class LocationAuthorization {
    /** Never asked (or the request was dismissed): the location picker asks once, when it opens. */
    NOT_DETERMINED,

    /** Refused. The picker works without it and never asks again. */
    DENIED,

    /** Allowed while the app is in use (approximate: the app asks for nothing finer). */
    AUTHORIZED,
}

/** One location fix of the device. */
data class DeviceCoordinate(
    val latitude: Double,
    val longitude: Double,
    /** The radius of uncertainty in meters: a few meters when precise, kilometers when approximate. */
    val accuracy: Double,
)

/**
 * The device's location, used only by the admin's location picker to center the map on where the admin is (iOS
 * `DeviceLocating`). It is shown on the map and never stored, logged or sent anywhere.
 *
 * Asking for the permission needs the Activity, so the picker screen does that (see
 * [LocationPickerEvent.RequestLocationPermission]); this only reads a fix once it is granted. Behind an interface so
 * tests can script it and so the mock flavor never touches the location: only the live flavor binds the fused
 * location provider.
 */
interface DeviceLocator {

    /** One fix of the device's location, or `null` when it cannot be determined (no permission, no fix, cancelled). */
    suspend fun currentLocation(): DeviceCoordinate?
}
