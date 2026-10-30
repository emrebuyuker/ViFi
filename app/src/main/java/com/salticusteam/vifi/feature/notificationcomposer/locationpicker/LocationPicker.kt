package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import com.salticusteam.vifi.core.model.NotificationLocation

/**
 * Provides the screen on which an admin picks a campaign's location, shown full screen from the composer (iOS
 * `LocationPicking`). Behind an interface so the Google Maps / Places SDKs and the location permission stay out of
 * the mock flavor: each flavor binds its own picker, and [MainActivity][com.salticusteam.vifi.app.MainActivity]
 * provides it as [LocalLocationPicker].
 */
interface LocationPicker {

    /**
     * The picker screen. Calls [onPick] with a valid location, or [onCancel] when the admin backs out; the caller
     * dismisses it in both cases. [initial] is the location being changed, if any; the map starts on it.
     */
    @Composable
    fun Content(
        initial: NotificationLocation?,
        onPick: (NotificationLocation) -> Unit,
        onCancel: () -> Unit,
    )
}

/**
 * Stands in until the real pickers are bound (the Google one in live, a fixed-place stub in mock): it offers no
 * place and backs out at once, so a composer opening it simply keeps its current location.
 */
object UnavailableLocationPicker : LocationPicker {

    @Composable
    override fun Content(
        initial: NotificationLocation?,
        onPick: (NotificationLocation) -> Unit,
        onCancel: () -> Unit,
    ) {
        LaunchedEffect(Unit) { onCancel() }
    }
}

/** The flavor's [LocationPicker]; previews and tests without a provider get [UnavailableLocationPicker]. */
val LocalLocationPicker = staticCompositionLocalOf<LocationPicker> { UnavailableLocationPicker }
