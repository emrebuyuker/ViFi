package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

import com.salticusteam.vifi.core.model.NotificationLocation
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** iOS `LocationPickerMapPlaceTests`: places tapped on the map, and the area searches favor. */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationPickerMapPlaceTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val searcher = StubPlaceSearcher()
    private val pastane = LocationPickerFixture.pastane

    private fun viewModel(initial: NotificationLocation? = null) =
        LocationPickerViewModel(initial, searcher, StubDeviceLocator())

    private val LocationPickerViewModel.state get() = uiState.value

    private fun LocationPickerViewModel.tapPastane(name: String = pastane.name) =
        onMapEvent(pastane.copy(name = name))

    // --- Tapping a place ---

    @Test
    fun `tapping a place picks it at once - name, coordinate and place id, and the map moves onto it`() = runTest {
        val sut = viewModel()
        searcher.addresses = mapOf(pastane.placeId to Result.success(LocationPickerFixture.PASTANE_ADDRESS))

        sut.tapPastane()

        assertEquals("A Pastanesi", sut.state.name)
        assertEquals("poi-1", sut.state.placeId)
        assertEquals(pastane.latitude, sut.state.latitude, 0.0)
        assertEquals(pastane.longitude, sut.state.longitude, 0.0)
        assertTrue(sut.state.isLoadingAddress)
        assertTrue("The address is optional: the place can be confirmed before it arrives", sut.state.canConfirm)
        assertEquals(
            MapCamera(pastane.latitude, pastane.longitude, LocationPickerViewModel.PLACE_ZOOM),
            sut.state.cameraRequest?.camera,
        )

        advanceUntilIdle()

        assertEquals(listOf("poi-1"), searcher.addressLookups)
        assertEquals(LocationPickerFixture.PASTANE_ADDRESS, sut.state.address)
        assertFalse(sut.state.isLoadingAddress)
        assertEquals(
            NotificationLocation(
                latitude = pastane.latitude,
                longitude = pastane.longitude,
                name = "A Pastanesi",
                address = LocationPickerFixture.PASTANE_ADDRESS,
                placeId = "poi-1",
            ),
            sut.state.location,
        )
    }

    @Test
    fun `the address arrives after the tap and fills the field then`() = runTest {
        val gate = Gate().also { searcher.addressGate = it }
        val sut = viewModel()
        searcher.addresses = mapOf(pastane.placeId to Result.success(LocationPickerFixture.PASTANE_ADDRESS))

        sut.tapPastane()
        advanceUntilIdle()
        assertTrue(sut.state.address.isEmpty())
        assertTrue(sut.state.isLoadingAddress)

        gate.open()
        advanceUntilIdle()

        assertEquals(LocationPickerFixture.PASTANE_ADDRESS, sut.state.address)
        assertFalse(sut.state.isLoadingAddress)
    }

    @Test
    fun `a failed address lookup keeps the place, without an address`() = runTest {
        val sut = viewModel()
        searcher.addresses = mapOf(pastane.placeId to Result.failure(PlaceSearchError.Unavailable))

        sut.tapPastane()
        advanceUntilIdle()

        assertEquals("A Pastanesi", sut.state.name)
        assertEquals("poi-1", sut.state.placeId)
        assertTrue(sut.state.address.isEmpty())
        assertFalse(sut.state.isLoadingAddress)
        assertNull("Nothing to report: the address is optional", sut.state.resolveError)
        assertTrue(sut.state.canConfirm)
    }

    @Test
    fun `an address the admin types while the lookup runs is kept`() = runTest {
        val gate = Gate().also { searcher.addressGate = it }
        val sut = viewModel()
        searcher.addresses = mapOf(pastane.placeId to Result.success(LocationPickerFixture.PASTANE_ADDRESS))

        sut.tapPastane()
        advanceUntilIdle()
        sut.onAddressChange("Arka kapı")
        gate.open()
        advanceUntilIdle()

        assertEquals("Arka kapı", sut.state.address)
        assertFalse(sut.state.isLoadingAddress)
    }

    @Test
    fun `a newer tap drops the older place's address`() = runTest {
        val gate = Gate().also { searcher.addressGate = it }
        val sut = viewModel()
        searcher.addresses = mapOf(
            "poi-1" to Result.success(LocationPickerFixture.PASTANE_ADDRESS),
            "poi-2" to Result.success("Ortaköy, Beşiktaş"),
        )

        sut.tapPastane()
        advanceUntilIdle()
        sut.onMapEvent(LocationPickerMapEvent.PlaceTapped("poi-2", "B Kafe", 41.05, 29.02))
        gate.open()
        advanceUntilIdle()

        assertEquals("B Kafe", sut.state.name)
        assertEquals("poi-2", sut.state.placeId)
        assertEquals("Ortaköy, Beşiktaş", sut.state.address)
        assertFalse(sut.state.isLoadingAddress)
    }

    @Test
    fun `a search result chosen while a tapped place's address loads replaces it entirely`() = runTest {
        val gate = Gate().also { searcher.addressGate = it }
        val sut = viewModel()
        searcher.addresses = mapOf(pastane.placeId to Result.success(LocationPickerFixture.PASTANE_ADDRESS))

        sut.tapPastane()
        advanceUntilIdle()
        sut.onSuggestionSelected(LocationPickerFixture.suggestion)
        advanceUntilIdle()
        gate.open()
        advanceUntilIdle()

        assertEquals(LocationPickerFixture.kadikoy.name, sut.state.name)
        assertEquals(LocationPickerFixture.kadikoy.address, sut.state.address)
        assertEquals(LocationPickerFixture.kadikoy.placeId, sut.state.placeId)
        assertFalse(sut.state.isLoadingAddress)
    }

    @Test
    fun `a place tapped while a search result resolves wins over it`() = runTest {
        val gate = Gate().also { searcher.placeGate = it }
        val sut = viewModel()

        sut.onSuggestionSelected(LocationPickerFixture.suggestion)
        advanceUntilIdle()
        sut.tapPastane()
        gate.open()
        advanceUntilIdle()

        assertEquals("A Pastanesi", sut.state.name)
        assertEquals("poi-1", sut.state.placeId)
        assertEquals(pastane.latitude, sut.state.cameraRequest?.camera?.latitude)
        assertFalse(sut.state.isResolving)
    }

    @Test
    fun `tapping replaces an earlier place's name and address`() = runTest {
        val sut = viewModel()
        sut.onSuggestionSelected(LocationPickerFixture.suggestion)
        advanceUntilIdle()

        sut.tapPastane()
        advanceUntilIdle()

        assertEquals("A Pastanesi", sut.state.name)
        assertTrue("A place without an address leaves none, not the old place's", sut.state.address.isEmpty())
    }

    @Test
    fun `tapping closes the search list and empties the box`() = runTest {
        val sut = viewModel()
        searcher.suggestionsResult = Result.success(listOf(LocationPickerFixture.suggestion))
        sut.onQueryChange("Kadıköy")
        advanceUntilIdle()

        sut.tapPastane()

        assertTrue(sut.state.query.isEmpty())
        assertEquals(PlaceSearchState.Idle, sut.state.searchState)
    }

    @Test
    fun `dragging off a tapped place drops its id but keeps the name and address, as after a search`() = runTest {
        val sut = viewModel()
        searcher.addresses = mapOf(pastane.placeId to Result.success(LocationPickerFixture.PASTANE_ADDRESS))
        sut.tapPastane()
        advanceUntilIdle()

        sut.onMapEvent(LocationPickerMapEvent.Settled(pastane.latitude + 0.0000001, pastane.longitude))
        assertEquals("The move onto the place ends a hair off; still the place", "poi-1", sut.state.placeId)

        sut.onMapEvent(LocationPickerMapEvent.GestureStarted)
        sut.onMapEvent(LocationPickerMapEvent.Settled(41.06, 29.01))
        assertNull(sut.state.placeId)
        assertEquals("A Pastanesi", sut.state.name)
        assertEquals(LocationPickerFixture.PASTANE_ADDRESS, sut.state.address)
        assertNull(sut.state.location?.placeId)
    }

    @Test
    fun `a label over two lines becomes one line, a very long one is cut to the limit`() {
        val sut = viewModel()

        sut.tapPastane(name = "  A Pastanesi\nBeşiktaş ")
        assertEquals("A Pastanesi Beşiktaş", sut.state.name)

        sut.tapPastane(name = "A  Pastanesi\t\tKadıköy")
        assertEquals("A Pastanesi Kadıköy", sut.state.name)

        sut.tapPastane(name = "a".repeat(NotificationLocation.NAME_LIMIT + 10))
        assertEquals(NotificationLocation.NAME_LIMIT, sut.state.name.length)
    }

    @Test
    fun `zoomed in past street level, tapping keeps the zoom instead of zooming out`() {
        val sut = viewModel()
        sut.onMapEvent(LocationPickerMapEvent.Settled(41.04, 29.0, zoom = 18.5f))

        sut.tapPastane()

        assertEquals(18.5f, sut.state.cameraRequest?.camera?.zoom)
    }

    // --- Search area ---

    @Test
    fun `searches favor what the map shows once the admin placed it and it is zoomed in`() = runTest {
        val sut = viewModel()
        sut.onMapEvent(LocationPickerMapEvent.GestureStarted)
        sut.onMapEvent(LocationPickerMapEvent.Settled(41.04, 29.0, zoom = 14f, visibleRadius = 3_000.0))

        sut.onQueryChange("pastane")
        advanceUntilIdle()

        assertEquals(listOf(SearchArea(41.04, 29.0, 3_000.0)), searcher.areas)
    }

    @Test
    fun `a map only the device's location put there favors no area, so that location stays on the device`() =
        runTest {
            val locator = StubDeviceLocator()
            val sut = LocationPickerViewModel(null, searcher, locator)
            sut.locateDevice(LocationAuthorization.AUTHORIZED)
            advanceUntilIdle()
            val here = LocationPickerFixture.here
            sut.onMapEvent(LocationPickerMapEvent.Settled(here.latitude, here.longitude, 16f, visibleRadius = 800.0))

            sut.onQueryChange("pastane")
            advanceUntilIdle()
            assertEquals("Only the region code goes with the search", listOf<SearchArea?>(null), searcher.areas)

            // The admin drags the map a little: from then on the area is theirs.
            sut.onMapEvent(LocationPickerMapEvent.GestureStarted)
            sut.onMapEvent(LocationPickerMapEvent.Settled(41.045, 29.01, 16f, visibleRadius = 800.0))
            sut.onQueryChange("kafe")
            advanceUntilIdle()
            assertEquals(listOf(null, SearchArea(41.045, 29.01, 800.0)), searcher.areas)
        }

    @Test
    fun `after a chosen place the next search favors the area around it`() = runTest {
        val sut = viewModel()
        sut.onSuggestionSelected(LocationPickerFixture.suggestion)
        advanceUntilIdle()
        val kadikoy = LocationPickerFixture.kadikoy
        sut.onMapEvent(LocationPickerMapEvent.Settled(kadikoy.latitude, kadikoy.longitude, 16f, visibleRadius = 900.0))

        sut.onQueryChange("pastane")
        advanceUntilIdle()

        assertEquals(listOf(SearchArea(kadikoy.latitude, kadikoy.longitude, 900.0)), searcher.areas)
    }

    @Test
    fun `before the map settles and on a country-wide view, searches favor no area`() = runTest {
        val sut = viewModel()
        sut.onQueryChange("pastane")
        advanceUntilIdle()

        sut.onMapEvent(LocationPickerMapEvent.GestureStarted)
        sut.onMapEvent(LocationPickerMapEvent.Settled(41.04, 29.0, zoom = 14f, visibleRadius = 3_000.0))
        val countryWide = SearchArea.MAXIMUM_RADIUS * 10
        sut.onMapEvent(LocationPickerMapEvent.Settled(39.0, 35.0, zoom = 5f, visibleRadius = countryWide))
        sut.onQueryChange("kafe")
        advanceUntilIdle()

        assertEquals(listOf<SearchArea?>(null, null), searcher.areas)
    }
}
