package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

import com.salticusteam.vifi.core.model.NotificationLocation
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** iOS `LocationPickerViewModelTests`: the start, the search, a chosen result, the map and the result. */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationPickerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val searcher = StubPlaceSearcher()

    private fun viewModel(initial: NotificationLocation? = null) =
        LocationPickerViewModel(initial, searcher, StubDeviceLocator())

    private val LocationPickerViewModel.state get() = uiState.value

    // --- Start ---

    @Test
    fun `an empty picker starts on Turkey with nothing to confirm`() {
        val sut = viewModel()

        assertEquals(LocationPickerViewModel.DEFAULT_CAMERA, sut.state.initialCamera)
        assertNull(sut.state.location)
        assertFalse(sut.state.canConfirm)
        assertNull(sut.state.cameraRequest)
    }

    @Test
    fun `changing a location starts on it, with its name and address`() {
        val initial = NotificationLocation(38.5, 27.1, "Kafe", "Bornova", "p1")
        val sut = viewModel(initial)

        assertEquals(MapCamera(38.5, 27.1, LocationPickerViewModel.PLACE_ZOOM), sut.state.initialCamera)
        assertEquals("Kafe", sut.state.name)
        assertEquals("Bornova", sut.state.address)
        assertEquals(initial, sut.state.location)
        assertTrue(sut.state.canConfirm)
    }

    // --- Search ---

    @Test
    fun `short queries are not searched`() = runTest {
        val sut = viewModel()

        sut.onQueryChange("ka")
        advanceUntilIdle()

        assertTrue(searcher.queries.isEmpty())
        assertEquals(PlaceSearchState.Idle, sut.state.searchState)
        assertEquals("ka", sut.state.query)
    }

    @Test
    fun `a query is searched after the pause and its results shown`() = runTest {
        val sut = viewModel()
        searcher.suggestionsResult = Result.success(listOf(LocationPickerFixture.suggestion))

        sut.onQueryChange("  Kadıköy ")
        assertEquals(PlaceSearchState.Searching, sut.state.searchState)
        advanceUntilIdle()

        assertEquals("The query is trimmed", listOf("Kadıköy"), searcher.queries)
        assertEquals(PlaceSearchState.Results(listOf(LocationPickerFixture.suggestion)), sut.state.searchState)
    }

    @Test
    fun `typing quickly sends one search, for the latest text`() = runTest {
        val sut = viewModel()

        sut.onQueryChange("kad")
        advanceTimeBy(200)
        sut.onQueryChange("kadı")
        advanceTimeBy(200)
        sut.onQueryChange("kadıköy")
        advanceTimeBy(349)
        assertTrue("Still within the pause", searcher.queries.isEmpty())
        advanceUntilIdle()

        assertEquals(listOf("kadıköy"), searcher.queries)
    }

    @Test
    fun `no suggestions is its own state`() = runTest {
        val sut = viewModel()

        sut.onQueryChange("xyzxyz")
        advanceUntilIdle()

        assertEquals(PlaceSearchState.NoResults, sut.state.searchState)
    }

    @Test
    fun `a failed search reports the reason and keeps the pin usable`() = runTest {
        val sut = viewModel()
        searcher.suggestionsResult = Result.failure(PlaceSearchError.Unavailable)

        sut.onQueryChange("Kadıköy")
        advanceUntilIdle()

        assertEquals(PlaceSearchState.Failed(PlaceSearchError.Unavailable.userMessage), sut.state.searchState)
        sut.onNameChange("Elle seçilen yer")
        assertTrue("The pin can still be placed without the search", sut.state.canConfirm)
    }

    @Test
    fun `an unexpected search failure shows the search's own message`() = runTest {
        val sut = viewModel()
        searcher.suggestionsResult = Result.failure(IllegalStateException("network"))

        sut.onQueryChange("Kadıköy")
        advanceUntilIdle()

        assertEquals(PlaceSearchState.Failed(PlaceSearchError.Unavailable.userMessage), sut.state.searchState)
    }

    @Test
    fun `clearing the box clears the results and drops a pending search`() = runTest {
        val sut = viewModel()
        sut.onQueryChange("Kadıköy")

        sut.onQueryChange("")
        advanceUntilIdle()

        assertTrue(searcher.queries.isEmpty())
        assertEquals(PlaceSearchState.Idle, sut.state.searchState)
    }

    // --- Choosing a result ---

    @Test
    fun `choosing a result moves the map there and fills the name, address and place id`() = runTest {
        val sut = viewModel()
        searcher.suggestionsResult = Result.success(listOf(LocationPickerFixture.suggestion))
        sut.onQueryChange("Kadıköy")
        advanceUntilIdle()

        sut.onSuggestionSelected(LocationPickerFixture.suggestion)
        advanceUntilIdle()

        assertEquals(listOf(LocationPickerFixture.suggestion), searcher.resolved)
        assertEquals("Kadıköy", sut.state.name)
        assertEquals("Caferağa, Kadıköy/İstanbul", sut.state.address)
        assertEquals("place-1", sut.state.placeId)
        assertEquals(40.9903, sut.state.latitude, 0.0)
        assertEquals(29.029, sut.state.longitude, 0.0)
        assertEquals(
            MapCamera(40.9903, 29.029, LocationPickerViewModel.PLACE_ZOOM),
            sut.state.cameraRequest?.camera,
        )
        // The search is finished: box and list are empty again.
        assertTrue(sut.state.query.isEmpty())
        assertEquals(PlaceSearchState.Idle, sut.state.searchState)
        assertFalse(sut.state.isResolving)
        assertEquals(
            NotificationLocation(40.9903, 29.029, "Kadıköy", "Caferağa, Kadıköy/İstanbul", "place-1"),
            sut.state.location,
        )
    }

    @Test
    fun `each chosen result is a new camera request, even to the same spot`() = runTest {
        val sut = viewModel()

        sut.onSuggestionSelected(LocationPickerFixture.suggestion)
        advanceUntilIdle()
        val first = sut.state.cameraRequest
        sut.onSuggestionSelected(LocationPickerFixture.suggestion)
        advanceUntilIdle()
        val second = sut.state.cameraRequest

        assertNotNull(first)
        assertEquals(first?.camera, second?.camera)
        assertNotEquals(first?.id, second?.id)
    }

    @Test
    fun `a second choice while one resolves is ignored`() = runTest {
        val sut = viewModel()
        val gate = Gate()
        searcher.placeGate = gate

        sut.onSuggestionSelected(LocationPickerFixture.suggestion)
        advanceUntilIdle()
        assertTrue(sut.state.isResolving)
        assertFalse("Not confirmable while resolving", sut.state.canConfirm)
        sut.onSuggestionSelected(LocationPickerFixture.suggestion.copy(id = "place-2"))
        gate.open()
        advanceUntilIdle()

        assertEquals(listOf(LocationPickerFixture.suggestion), searcher.resolved)
        assertFalse(sut.state.isResolving)
    }

    @Test
    fun `a result that cannot be resolved is reported and leaves the pin alone`() = runTest {
        val sut = viewModel()
        searcher.placeResult = Result.failure(PlaceSearchError.NotFound)

        sut.onSuggestionSelected(LocationPickerFixture.suggestion)
        advanceUntilIdle()

        assertEquals(PlaceSearchError.NotFound.userMessage, sut.state.resolveError)
        assertTrue(sut.state.showsSearchPanel)
        assertNull(sut.state.cameraRequest)
        assertTrue(sut.state.name.isEmpty())
        assertFalse(sut.state.isResolving)
    }

    @Test
    fun `a very long place name is cut to the limit instead of blocking the notification`() = runTest {
        val sut = viewModel()
        searcher.placeResult = Result.success(
            PlaceDetails(
                placeId = "p",
                name = "a".repeat(NotificationLocation.NAME_LIMIT + 30),
                address = "b".repeat(NotificationLocation.ADDRESS_LIMIT + 30),
                latitude = 1.0,
                longitude = 2.0,
            ),
        )

        sut.onSuggestionSelected(LocationPickerFixture.suggestion)
        advanceUntilIdle()

        assertEquals(NotificationLocation.NAME_LIMIT, sut.state.name.length)
        assertEquals(NotificationLocation.ADDRESS_LIMIT, sut.state.address.length)
        assertTrue(sut.state.canConfirm)
    }

    @Test
    fun `a long name is cut by code points, never inside an emoji`() = runTest {
        val sut = viewModel()
        // 119 letters and then emoji: the cut keeps the first emoji whole.
        val name = "a".repeat(NotificationLocation.NAME_LIMIT - 1) + "📍📍"
        searcher.placeResult = Result.success(LocationPickerFixture.kadikoy.copy(name = name))

        sut.onSuggestionSelected(LocationPickerFixture.suggestion)
        advanceUntilIdle()

        assertEquals("a".repeat(NotificationLocation.NAME_LIMIT - 1) + "📍", sut.state.name)
        assertTrue(sut.state.canConfirm)
    }

    // --- The map ---

    @Test
    fun `the coordinate is wherever the map comes to rest`() {
        val sut = viewModel()

        sut.onMapEvent(LocationPickerMapEvent.Settled(41.5, 28.5))

        assertEquals(41.5, sut.state.latitude, 0.0)
        assertEquals(28.5, sut.state.longitude, 0.0)
        assertEquals("41.50000, 28.50000", sut.state.coordinateText)
    }

    @Test
    fun `the place id survives the map settling on the place and is dropped once the pin is moved off it`() = runTest {
        val sut = viewModel()
        sut.onSuggestionSelected(LocationPickerFixture.suggestion)
        advanceUntilIdle()

        // The animation to the chosen place ends (a hair off is still the place).
        sut.onMapEvent(LocationPickerMapEvent.Settled(40.9903000001, 29.0290000001))
        assertEquals("place-1", sut.state.placeId)

        // The admin drags the map elsewhere: that is no longer the Google place.
        sut.onMapEvent(LocationPickerMapEvent.Settled(40.9950, 29.0350))
        assertNull(sut.state.placeId)
        assertNull(sut.state.location?.placeId)
        assertEquals("The name stays editable text, not tied to the place", "Kadıköy", sut.state.name)

        // Back on the old spot by hand: it was not re-picked, so it stays a plain coordinate.
        sut.onMapEvent(LocationPickerMapEvent.Settled(40.9903, 29.029))
        assertNull(sut.state.placeId)
    }

    @Test
    fun `dragging an existing place off its spot drops its id too`() {
        val sut = viewModel(NotificationLocation(38.5, 27.1, "Kafe", null, "p1"))

        sut.onMapEvent(LocationPickerMapEvent.Settled(38.5, 27.1))
        assertEquals("p1", sut.state.placeId)
        sut.onMapEvent(LocationPickerMapEvent.Settled(38.6, 27.1))
        assertNull(sut.state.placeId)
    }

    // --- Result ---

    @Test
    fun `a name is required, and trimmed`() {
        val sut = viewModel()
        sut.onMapEvent(LocationPickerMapEvent.Settled(41.0, 29.0))

        assertNull(sut.state.location)

        sut.onNameChange("   ")
        assertFalse(sut.state.canConfirm)

        sut.onNameChange("  Kafe  ")
        assertEquals("Kafe", sut.state.location?.name)
        assertTrue(sut.state.canConfirm)
    }

    @Test
    fun `an empty address is left out`() {
        val sut = viewModel()
        sut.onNameChange("Kafe")

        sut.onAddressChange("   ")
        assertNull(sut.state.location?.address)

        sut.onAddressChange(" Bornova ")
        assertEquals("Bornova", sut.state.location?.address)
    }

    @Test
    fun `a name or address over its limit blocks confirming`() {
        val sut = viewModel()
        sut.onNameChange("a".repeat(NotificationLocation.NAME_LIMIT))
        assertTrue(sut.state.canConfirm)
        assertFalse(sut.state.isNameTooLong)

        sut.onNameChange("a".repeat(NotificationLocation.NAME_LIMIT + 1))
        assertTrue(sut.state.isNameTooLong)
        assertFalse(sut.state.canConfirm)

        sut.onNameChange("Kafe")
        sut.onAddressChange("b".repeat(NotificationLocation.ADDRESS_LIMIT + 1))
        assertTrue(sut.state.isAddressTooLong)
        assertFalse(sut.state.canConfirm)
    }

    @Test
    fun `closing the picker stops a search still waiting`() = runTest {
        val sut = viewModel()
        sut.onQueryChange("Kadıköy")

        sut.onDismissed()
        advanceUntilIdle()

        assertTrue(searcher.queries.isEmpty())
    }
}
