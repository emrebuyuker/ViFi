package com.salticusteam.vifi.core.maps

import android.content.Context
import android.os.SystemClock
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.PlaceDetails
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.PlaceSearchError
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.PlaceSearcher
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.PlaceSuggestion
import com.salticusteam.vifi.feature.notificationcomposer.locationpicker.SearchArea
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import timber.log.Timber

/**
 * [PlaceSearcher] on the Google Places SDK, Places API (New) (iOS `GooglePlacesService`): autocomplete suggestions
 * while typing, then one place fetch, all under one session token so Google bills the whole search as a single
 * session. A place tapped on the map is fetched the same way (its address only), ending the session too.
 *
 * Unscoped, so each picker has its own session. [GoogleMapsSdk.startIfPossible] must have succeeded before it is
 * used; nothing is logged but a failure's status code (never the query or a place).
 */
class GooglePlacesSearcher @Inject constructor(
    @ApplicationContext private val context: Context,
) : PlaceSearcher {

    private val client: PlacesClient by lazy { Places.createClient(context) }
    private val session = PlacesSession(SESSION_LIFETIME_MILLIS, AutocompleteSessionToken::newInstance) {
        SystemClock.elapsedRealtime()
    }

    override suspend fun suggestions(query: String, area: SearchArea?): List<PlaceSuggestion> {
        session.renewIfStale()
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(query)
            .setSessionToken(session.token)
            // Ranks and formats for Turkey without hiding results elsewhere.
            .setRegionCode(REGION_CODE)
            .apply {
                // Ranks what the map shows first; a bias, so places elsewhere still come back. Not billed extra.
                if (area != null) {
                    setLocationBias(CircularBounds.newInstance(LatLng(area.latitude, area.longitude), area.radius))
                }
            }
            .build()
        val response = call("Place autocomplete") { client.findAutocompletePredictions(request).await() }
        return response.autocompletePredictions.map { prediction ->
            PlaceSuggestion(
                id = prediction.placeId,
                title = prediction.getPrimaryText(null).toString(),
                subtitle = prediction.getSecondaryText(null).toString().takeIf { it.isNotBlank() },
            )
        }
    }

    override suspend fun place(suggestion: PlaceSuggestion): PlaceDetails {
        // Only what the notification needs; every extra field is billed at a higher tier.
        val place = fetchPlace(suggestion.id, PLACE_FIELDS)
        val location = place.location ?: throw PlaceSearchError.NotFound
        if (location.latitude !in -90.0..90.0 || location.longitude !in -180.0..180.0) {
            throw PlaceSearchError.NotFound
        }
        return PlaceDetails(
            placeId = place.id ?: suggestion.id,
            name = place.displayName ?: suggestion.title,
            address = place.formattedAddress,
            latitude = location.latitude,
            longitude = location.longitude,
        )
    }

    // The tap already gave the name and coordinate; the address alone is the cheapest field set.
    override suspend fun address(placeId: String): String? =
        fetchPlace(placeId, listOf(Place.Field.FORMATTED_ADDRESS)).formattedAddress

    /** Fetches one place under the current session token; the fetch ends the session. */
    private suspend fun fetchPlace(id: String, fields: List<Place.Field>): Place {
        val request = FetchPlaceRequest.builder(id, fields).setSessionToken(session.token).build()
        val place = call("Place fetch") { client.fetchPlace(request).await() }.place
        session.restart()
        return place
    }

    /** Runs a Places request; any failure becomes [PlaceSearchError.Unavailable] (an invalid key, quota, network). */
    private suspend fun <T> call(what: String, request: suspend () -> T): T =
        try {
            request()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w("%s failed: %s", what, (e as? ApiException)?.statusCode ?: e.javaClass.simpleName)
            throw PlaceSearchError.Unavailable
        }

    private companion object {
        /** A session lasts a few minutes; renewing earlier keeps a long pause from sending a stale token. */
        const val SESSION_LIFETIME_MILLIS = 150_000L
        const val REGION_CODE = "TR"
        val PLACE_FIELDS = listOf(
            Place.Field.ID,
            Place.Field.DISPLAY_NAME,
            Place.Field.LOCATION,
            Place.Field.FORMATTED_ADDRESS,
        )
    }
}
