package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

/** One line of the place search's suggestion list. */
data class PlaceSuggestion(
    /** The Google place id; also what [PlaceSearcher.place] resolves. */
    val id: String,
    val title: String,
    val subtitle: String?,
)

/** A circle place searches favor: results inside it rank higher, places elsewhere still come back. */
data class SearchArea(
    val latitude: Double,
    val longitude: Double,
    /** In meters, more than 0 and at most [MAXIMUM_RADIUS]. */
    val radius: Double,
) {
    companion object {
        /** The largest radius, in meters, the Places service accepts for a circle. */
        const val MAXIMUM_RADIUS = 50_000.0
    }
}

/** A place resolved from a suggestion. */
data class PlaceDetails(
    val placeId: String,
    val name: String,
    val address: String?,
    val latitude: Double,
    val longitude: Double,
)

/** Why a place search failed; [userMessage] is what the picker shows (inline Turkish, like every domain error). */
sealed class PlaceSearchError(val userMessage: String) : Exception(userMessage) {

    /** The service refused or failed the request (an invalid key, a restricted key, quota, the network). */
    data object Unavailable : PlaceSearchError(
        "Arama şu an kullanılamıyor. Haritayı kaydırarak konumu yine de seçebilirsin.",
    )

    /** The suggestion no longer resolves to a place. */
    data object NotFound : PlaceSearchError("Bu yer bulunamadı. Başka bir sonuç seç.")
}

/**
 * Searches for places by name (iOS `PlaceSearching`): Google Places autocomplete in the live flavor, stubs in
 * tests. Failures are thrown as [PlaceSearchError].
 *
 * A search session is what the Places service bills by: the suggestions fetched while typing and the one place
 * fetch that follows belong to one session, which [place] (or [address]) ends.
 */
interface PlaceSearcher {

    /** Suggestions for what the admin has typed so far, favoring [area] when there is one. */
    suspend fun suggestions(query: String, area: SearchArea?): List<PlaceSuggestion>

    /** The place behind a suggestion; ends the current search session. */
    suspend fun place(suggestion: PlaceSuggestion): PlaceDetails

    /**
     * The formatted address of a place tapped on the map (its name and coordinate come with the tap), or `null`
     * when it has none; ends the current search session.
     */
    suspend fun address(placeId: String): String?
}
