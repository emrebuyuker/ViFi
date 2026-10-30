package com.salticusteam.vifi.core.maps

import android.content.Context
import androidx.annotation.MainThread
import com.google.android.libraries.places.api.Places
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hands the build's key to the Places SDK, once (iOS `GoogleMapsSDK`).
 *
 * Nothing here runs at launch: the admin's location picker calls [startIfPossible] right before it first needs a
 * map, so the Google SDKs never start in the mock flavor, for regular users, or without a key. The Maps SDK reads
 * its key from the manifest when the first map is created; a map without a key fails, so callers check the result
 * and show the "not configured" state instead.
 */
@Singleton
class GoogleMapsSdk @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Starts Places if it has not been yet. Whether the SDKs are ready; `false` when the build has no usable key. */
    @MainThread
    fun startIfPossible(configuration: GoogleMapsConfiguration = GoogleMapsConfiguration.current): Boolean {
        val apiKey = configuration.apiKey ?: return false
        if (!Places.isInitialized()) {
            // Places API (New), the API the key is restricted to; Turkish results, like the rest of the app.
            Places.initializeWithNewPlacesApiEnabled(context, apiKey, PLACES_LOCALE)
        }
        return true
    }

    private companion object {
        val PLACES_LOCALE: Locale = Locale.forLanguageTag("tr-TR")
    }
}
