package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.salticusteam.vifi.core.model.NotificationLocation
import com.salticusteam.vifi.core.model.codePointLength
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlin.math.abs
import kotlin.math.max
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Drives the admin's location picker (iOS `LocationPickerViewModel`): a map with a fixed center pin (the coordinate
 * is wherever the map is centered), a place search that moves the map to the chosen result, places on the map that
 * can be tapped, and the name and address the notification shows. Ends in a [NotificationLocation], only when it is
 * valid ([LocationPickerUiState.location]).
 *
 * It also opens on the device's (approximate) location, asking for the permission once, unless the admin has already
 * put the map somewhere (see [locateDevice]). That location only moves the map: it is never stored, logged or sent,
 * and searches favor the map's area only once the admin has placed it (see [mapDidSettle]), so it never reaches
 * Google either.
 *
 * One per picker presentation: the screen keys it, with [initial] the location being changed, if any.
 */
@HiltViewModel(assistedFactory = LocationPickerViewModel.Factory::class)
class LocationPickerViewModel @AssistedInject constructor(
    @Assisted initial: NotificationLocation?,
    private val searcher: PlaceSearcher,
    private val locator: DeviceLocator,
) : ViewModel() {

    @AssistedFactory
    interface Factory {
        fun create(initial: NotificationLocation?): LocationPickerViewModel
    }

    // State
    private val _uiState = MutableStateFlow(initialState(initial))
    val uiState: StateFlow<LocationPickerUiState> = _uiState.asStateFlow()

    // One-shot events
    private val _events = Channel<LocationPickerEvent>(Channel.BUFFERED)
    val events: Flow<LocationPickerEvent> = _events.receiveAsFlow()

    // Internal state
    private var searchJob: Job? = null
    private var resolveJob: Job? = null
    private var addressJob: Job? = null
    private var locateJob: Job? = null
    private var cameraRequestCount = 0

    /** Bumped by every chosen place (a search result or a tap on the map), so an answer for an older one is dropped. */
    private var selectionCount = 0

    /** Where the picked place is, to tell when the map has been moved away from it. */
    private var placeCoordinate: Pair<Double, Double>? =
        initial?.takeIf { it.placeId != null }?.let { it.latitude to it.longitude }

    /** The zoom the map last came to rest at. */
    private var zoom: Float = _uiState.value.initialCamera.zoom

    /** Where searches rank results first: what the map shows, once the admin placed it and it is zoomed in enough. */
    private var searchArea: SearchArea? = null

    /**
     * Set once the map is where the admin wants it: the location being changed, a drag or pinch, a chosen search
     * result or a tapped place. The device's location then no longer moves the map.
     */
    private var isPlacedByAdmin = initial != null
    private var hasStartedLocating = false
    private var isAwaitingPermission = false

    // Search

    /** Sends the search once typing has paused. Each keystroke restarts the wait, so a word costs one request. */
    fun onQueryChange(text: String) {
        searchJob?.cancel()
        searchJob = null
        val trimmed = text.trim()
        if (trimmed.codePointLength() < MINIMUM_QUERY_LENGTH) {
            _uiState.update { it.copy(query = text, searchState = PlaceSearchState.Idle) }
            return
        }
        _uiState.update { it.copy(query = text, searchState = PlaceSearchState.Searching) }
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            search(trimmed)
        }
    }

    /** Puts the pin on a search result: fetches the place and moves the map to it, with its name and address. */
    fun onSuggestionSelected(suggestion: PlaceSuggestion) {
        if (_uiState.value.isResolving) return
        _uiState.update { it.copy(isResolving = true, resolveError = null) }
        val selection = selectionCount
        resolveJob = viewModelScope.launch {
            try {
                val place = searcher.place(suggestion)
                // A place tapped on the map while this one resolved is the newer choice.
                if (selection == selectionCount) {
                    commitPlace(
                        placeId = place.placeId,
                        name = place.name,
                        address = place.address.orEmpty(),
                        camera = MapCamera(place.latitude, place.longitude, PLACE_ZOOM),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(resolveError = e.searchMessage()) }
            } finally {
                _uiState.update { it.copy(isResolving = false) }
            }
        }
    }

    // Map

    fun onMapEvent(event: LocationPickerMapEvent) {
        when (event) {
            is LocationPickerMapEvent.Settled ->
                mapDidSettle(event.latitude, event.longitude, event.zoom, event.visibleRadius)
            // From now on the map stays where the admin takes it.
            LocationPickerMapEvent.GestureStarted -> isPlacedByAdmin = true
            is LocationPickerMapEvent.PlaceTapped ->
                selectMapPlace(event.placeId, event.name, event.latitude, event.longitude)
        }
    }

    // Details

    fun onNameChange(text: String) {
        _uiState.update { it.copy(name = text) }
    }

    fun onAddressChange(text: String) {
        _uiState.update { it.copy(address = text) }
    }

    // Device location

    /**
     * Opens on the device's first fix, once per picker. [authorization] is the permission when the picker appears:
     * never asked, the screen is asked to ask ([LocationPickerEvent.RequestLocationPermission]) and reports the
     * answer to [onLocationPermissionResult]; refused, the picker only notes it and never asks again. The map is
     * not moved when the admin has already put it somewhere: changing an existing location, or a drag, pinch, chosen
     * search result or tapped place before the fix arrives.
     */
    fun locateDevice(authorization: LocationAuthorization) {
        if (hasStartedLocating) return
        hasStartedLocating = true
        if (authorization == LocationAuthorization.NOT_DETERMINED) {
            isAwaitingPermission = true
            _events.trySend(LocationPickerEvent.RequestLocationPermission)
        } else {
            useAuthorization(authorization)
        }
    }

    /** The admin answered the permission request [locateDevice] asked for (approximate counts as granted). */
    fun onLocationPermissionResult(granted: Boolean) {
        if (!isAwaitingPermission) return
        isAwaitingPermission = false
        useAuthorization(if (granted) LocationAuthorization.AUTHORIZED else LocationAuthorization.DENIED)
    }

    /** The picker is closing (a place was picked or the admin backed out): nothing it started may still run. */
    fun onDismissed() {
        searchJob?.cancel()
        resolveJob?.cancel()
        addressJob?.cancel()
        locateJob?.cancel()
    }

    // Helpers

    private suspend fun search(query: String) {
        val next = try {
            val suggestions = searcher.suggestions(query, searchArea)
            if (suggestions.isEmpty()) PlaceSearchState.NoResults else PlaceSearchState.Results(suggestions)
        } catch (e: CancellationException) {
            // Superseded by a newer search, a cleared box or a chosen place.
            throw e
        } catch (e: Exception) {
            PlaceSearchState.Failed(e.searchMessage())
        }
        _uiState.update { it.copy(searchState = next) }
    }

    private fun mapDidSettle(latitude: Double, longitude: Double, zoom: Float?, visibleRadius: Double?) {
        // The place id describes the place that was picked, not wherever the pin is dragged to afterwards.
        val leftThePlace = placeCoordinate?.let { !isNear(it, latitude, longitude) } ?: false
        if (leftThePlace) placeCoordinate = null
        if (zoom != null) this.zoom = zoom
        if (visibleRadius != null) {
            // A country-wide view is no "area"; searches then rank for Turkey only. Nor is a map only the device's
            // location put there: its area would send that location to Google with the search (product owner,
            // 2026-10-30). Once the admin drags, searches or taps a place, the area is theirs.
            val isUsable = isPlacedByAdmin && visibleRadius > 0 && visibleRadius <= SearchArea.MAXIMUM_RADIUS
            searchArea = if (isUsable) SearchArea(latitude, longitude, visibleRadius) else null
        }
        _uiState.update {
            it.copy(latitude = latitude, longitude = longitude, placeId = if (leftThePlace) null else it.placeId)
        }
    }

    /**
     * Puts the pin on a place the admin tapped on the map (a shop or landmark label): its name, coordinate and place
     * id at once, its address when the lookup answers. The place stays chosen if the lookup fails, and an address
     * the admin typed meanwhile is kept.
     */
    private fun selectMapPlace(placeId: String, name: String, latitude: Double, longitude: Double) {
        // Close enough to read the label means close enough already: only zoom in, never out.
        val camera = MapCamera(latitude, longitude, max(zoom, PLACE_ZOOM))
        commitPlace(placeId = placeId, name = singleLine(name), address = "", camera = camera)
        _uiState.update { it.copy(isLoadingAddress = true) }
        val selection = selectionCount
        addressJob = viewModelScope.launch { lookUpAddress(placeId, selection) }
    }

    private suspend fun lookUpAddress(placeId: String, selection: Int) {
        val found = try {
            searcher.address(placeId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // The address is optional: the place stays chosen without one (the searcher logged why).
            null
        }
        // A newer choice owns the fields now.
        if (selection != selectionCount) return
        _uiState.update { state ->
            val address = if (found != null && state.trimmedAddress.isEmpty()) {
                found.prefixCodePoints(NotificationLocation.ADDRESS_LIMIT)
            } else {
                state.address
            }
            state.copy(isLoadingAddress = false, address = address)
        }
    }

    /**
     * Puts the pin on a chosen place (a search result or a place tapped on the map) and moves the map there.
     * Supersedes any earlier choice, including an address still being looked up for it, and ends the search.
     */
    private fun commitPlace(placeId: String, name: String, address: String, camera: MapCamera) {
        selectionCount++
        isPlacedByAdmin = true
        addressJob?.cancel()
        addressJob = null
        searchJob?.cancel()
        searchJob = null
        placeCoordinate = camera.latitude to camera.longitude
        cameraRequestCount++
        _uiState.update {
            it.copy(
                latitude = camera.latitude,
                longitude = camera.longitude,
                placeId = placeId,
                // Cut instead of blocking: a long Google name must never keep the admin from confirming.
                name = name.prefixCodePoints(NotificationLocation.NAME_LIMIT),
                address = address.prefixCodePoints(NotificationLocation.ADDRESS_LIMIT),
                isLoadingAddress = false,
                query = "",
                searchState = PlaceSearchState.Idle,
                resolveError = null,
                cameraRequest = CameraRequest(cameraRequestCount, camera),
            )
        }
    }

    private fun useAuthorization(authorization: LocationAuthorization) {
        _uiState.update { it.copy(locationAuthorization = authorization) }
        // Changing a location (or one placed while the permission was asked) needs no fix at all.
        if (authorization != LocationAuthorization.AUTHORIZED || !followsDevice) return
        locateJob = viewModelScope.launch {
            val fix = locator.currentLocation() ?: return@launch
            if (!followsDevice) return@launch
            val zoom = if (fix.accuracy > PRECISE_ACCURACY) APPROXIMATE_ZOOM else PLACE_ZOOM
            cameraRequestCount++
            _uiState.update {
                it.copy(cameraRequest = CameraRequest(cameraRequestCount, MapCamera(fix.latitude, fix.longitude, zoom)))
            }
        }
    }

    /** Whether the device's location may still move the map. */
    private val followsDevice: Boolean get() = !isPlacedByAdmin && !_uiState.value.isResolving

    private fun isNear(place: Pair<Double, Double>, latitude: Double, longitude: Double): Boolean =
        abs(place.first - latitude) <= PLACE_TOLERANCE && abs(place.second - longitude) <= PLACE_TOLERANCE

    private fun Exception.searchMessage(): String =
        ((this as? PlaceSearchError) ?: PlaceSearchError.Unavailable).userMessage

    companion object {
        /** Fewer characters than this are not searched: each search is a billable Places request. */
        const val MINIMUM_QUERY_LENGTH = 3

        /** The zoom of a map that was just moved to a place (street level). */
        const val PLACE_ZOOM = 16f

        /** The zoom for an approximate device location: the district, not a wrong street. */
        const val APPROXIMATE_ZOOM = 12f

        /** A fix less certain than this many meters is approximate. */
        const val PRECISE_ACCURACY = 1_000.0

        /** Roughly Turkey as a whole: where an empty picker starts, before the admin searches or pans. */
        val DEFAULT_CAMERA = MapCamera(latitude = 39.0, longitude = 35.2, zoom = 5f)

        /** How long typing must pause before the search is sent. */
        val SEARCH_DEBOUNCE = 350.milliseconds

        /** About a meter. A map dragged further than this is no longer on the place that was picked. */
        private const val PLACE_TOLERANCE = 0.00001

        private fun initialState(initial: NotificationLocation?): LocationPickerUiState =
            if (initial != null) {
                LocationPickerUiState(
                    initialCamera = MapCamera(initial.latitude, initial.longitude, PLACE_ZOOM),
                    latitude = initial.latitude,
                    longitude = initial.longitude,
                    name = initial.name,
                    address = initial.address.orEmpty(),
                    placeId = initial.placeId,
                )
            } else {
                LocationPickerUiState(
                    initialCamera = DEFAULT_CAMERA,
                    latitude = DEFAULT_CAMERA.latitude,
                    longitude = DEFAULT_CAMERA.longitude,
                )
            }

        /** Map labels can break over lines ("A Pastanesi\nKadıköy"); the notification shows one. */
        private fun singleLine(text: String): String = buildString {
            for (char in text.trim()) {
                if (!char.isWhitespace()) {
                    append(char)
                } else if (last() != ' ') {
                    // Each run of whitespace (Unicode, line breaks included) becomes one space.
                    append(' ')
                }
            }
        }

        /** At most [limit] code points (how the server counts), never splitting a surrogate pair. */
        private fun String.prefixCodePoints(limit: Int): String =
            if (codePointLength() <= limit) this else substring(0, offsetByCodePoints(0, limit))
    }
}
