package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

import kotlinx.coroutines.CompletableDeferred

/**
 * Holds whoever passes it until the test opens it (iOS `AsyncGate`). With the test dispatcher, `advanceUntilIdle()`
 * brings a coroutine to the gate; [open] then lets every waiter through.
 */
class Gate {
    private val opened = CompletableDeferred<Unit>()

    var arrivals = 0
        private set

    suspend fun pass() {
        arrivals++
        opened.await()
    }

    fun open() {
        opened.complete(Unit)
    }
}

/** A [PlaceSearcher] with scripted answers that records every call (iOS `StubPlaceSearcher`). */
class StubPlaceSearcher : PlaceSearcher {
    var suggestionsResult: Result<List<PlaceSuggestion>> = Result.success(emptyList())
    var placeResult: Result<PlaceDetails> = Result.success(LocationPickerFixture.kadikoy)

    /** Addresses of places tapped on the map, by place id; an id not listed has no address. */
    var addresses: Map<String, Result<String?>> = emptyMap()

    /** When set, every place fetch for a suggestion waits at the gate before it is answered. */
    var placeGate: Gate? = null

    /** When set, every address lookup waits at the gate before it is answered. */
    var addressGate: Gate? = null

    val queries = mutableListOf<String>()

    /** The area each search favored, in order. */
    val areas = mutableListOf<SearchArea?>()
    val resolved = mutableListOf<PlaceSuggestion>()
    val addressLookups = mutableListOf<String>()

    override suspend fun suggestions(query: String, area: SearchArea?): List<PlaceSuggestion> {
        queries += query
        areas += area
        return suggestionsResult.getOrThrow()
    }

    override suspend fun place(suggestion: PlaceSuggestion): PlaceDetails {
        resolved += suggestion
        placeGate?.pass()
        return placeResult.getOrThrow()
    }

    override suspend fun address(placeId: String): String? {
        addressLookups += placeId
        addressGate?.pass()
        return (addresses[placeId] ?: Result.success(null)).getOrThrow()
    }
}

/** A [DeviceLocator] with a scripted fix that counts its reads (iOS `StubDeviceLocator`). */
class StubDeviceLocator(var fix: DeviceCoordinate? = LocationPickerFixture.here) : DeviceLocator {

    /** When set, the fix waits at the gate until the test opens it. */
    var fixGate: Gate? = null

    var locateCount = 0
        private set

    override suspend fun currentLocation(): DeviceCoordinate? {
        locateCount++
        fixGate?.pass()
        return fix
    }
}

object LocationPickerFixture {
    val suggestion = PlaceSuggestion(id = "place-1", title = "Kadıköy", subtitle = "İstanbul")
    val kadikoy = PlaceDetails(
        placeId = "place-1",
        name = "Kadıköy",
        address = "Caferağa, Kadıköy/İstanbul",
        latitude = 40.9903,
        longitude = 29.029,
    )

    /** A precise fix of the device, in Beşiktaş. */
    val here = DeviceCoordinate(latitude = 41.0422, longitude = 29.0083, accuracy = 12.0)

    /** A place label on the map. */
    val pastane = LocationPickerMapEvent.PlaceTapped(
        placeId = "poi-1",
        name = "A Pastanesi",
        latitude = 41.0431,
        longitude = 29.0071,
    )
    const val PASTANE_ADDRESS = "Sinanpaşa, Beşiktaş/İstanbul"
}
