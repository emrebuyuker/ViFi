package com.salticusteam.vifi.feature.notificationcomposer.locationpicker

import com.salticusteam.vifi.core.model.NotificationLocation
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * iOS `LocationPickerDeviceLocationTests`. On Android the screen asks for the permission (it needs the Activity):
 * the view model requests it with [LocationPickerEvent.RequestLocationPermission] and is told the answer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LocationPickerDeviceLocationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val searcher = StubPlaceSearcher()
    private val locator = StubDeviceLocator()
    private val permissionRequests = mutableListOf<LocationPickerEvent>()

    private val hereCamera = MapCamera(
        LocationPickerFixture.here.latitude,
        LocationPickerFixture.here.longitude,
        LocationPickerViewModel.PLACE_ZOOM,
    )

    private fun TestScope.viewModel(initial: NotificationLocation? = null): LocationPickerViewModel {
        val sut = LocationPickerViewModel(initial, searcher, locator)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            sut.events.collect { permissionRequests += it }
        }
        return sut
    }

    private val LocationPickerViewModel.state get() = uiState.value

    // --- Permission and first fix ---

    @Test
    fun `never asked - asks once, then opens on the first fix at street level`() = runTest {
        val sut = viewModel()

        sut.locateDevice(LocationAuthorization.NOT_DETERMINED)
        advanceUntilIdle()
        assertEquals(listOf(LocationPickerEvent.RequestLocationPermission), permissionRequests)
        assertNull("Nothing is known until the admin answers", sut.state.locationAuthorization)
        sut.onLocationPermissionResult(granted = true)
        advanceUntilIdle()

        assertEquals(1, locator.locateCount)
        assertEquals(LocationAuthorization.AUTHORIZED, sut.state.locationAuthorization)
        assertTrue(sut.state.showsDeviceLocation)
        assertFalse(sut.state.isLocationDenied)
        assertEquals(hereCamera, sut.state.cameraRequest?.camera)
        // Only the camera moves: nothing is picked until the map settles and the admin names the place.
        assertNull(sut.state.placeId)
        assertTrue(sut.state.name.isEmpty())
        assertNull(sut.state.location)
    }

    @Test
    fun `already allowed - no prompt, straight to the fix`() = runTest {
        val sut = viewModel()

        sut.locateDevice(LocationAuthorization.AUTHORIZED)
        advanceUntilIdle()

        assertTrue(permissionRequests.isEmpty())
        assertEquals(hereCamera, sut.state.cameraRequest?.camera)
    }

    @Test
    fun `it runs once per picker, however often the screen appears`() = runTest {
        val sut = viewModel()

        sut.locateDevice(LocationAuthorization.NOT_DETERMINED)
        sut.onLocationPermissionResult(granted = true)
        sut.locateDevice(LocationAuthorization.AUTHORIZED)
        sut.onLocationPermissionResult(granted = true)
        advanceUntilIdle()

        assertEquals(1, permissionRequests.size)
        assertEquals(1, locator.locateCount)
        assertEquals(1, sut.state.cameraRequest?.id)
    }

    @Test
    fun `an approximate fix opens on the district, not a street`() = runTest {
        locator.fix = DeviceCoordinate(latitude = 41.0, longitude = 29.0, accuracy = 3_000.0)
        val sut = viewModel()

        sut.locateDevice(LocationAuthorization.AUTHORIZED)
        advanceUntilIdle()

        assertEquals(LocationPickerViewModel.APPROXIMATE_ZOOM, sut.state.cameraRequest?.camera?.zoom)
    }

    @Test
    fun `refused now - no fix is read, the map stays on Turkey, and the picker only notes it`() = runTest {
        val sut = viewModel()

        sut.locateDevice(LocationAuthorization.NOT_DETERMINED)
        sut.onLocationPermissionResult(granted = false)
        advanceUntilIdle()

        assertEquals(1, permissionRequests.size)
        assertRefused(sut)
    }

    @Test
    fun `refused before - never asked again, and the picker only notes it`() = runTest {
        val sut = viewModel()

        sut.locateDevice(LocationAuthorization.DENIED)
        advanceUntilIdle()

        assertTrue("A refusal is never asked again", permissionRequests.isEmpty())
        assertRefused(sut)
    }

    private fun assertRefused(sut: LocationPickerViewModel) {
        assertEquals(0, locator.locateCount)
        assertNull(sut.state.cameraRequest)
        assertEquals(LocationPickerViewModel.DEFAULT_CAMERA, sut.state.initialCamera)
        assertTrue(sut.state.isLocationDenied)
        assertFalse(sut.state.showsDeviceLocation)

        // The picker works as before.
        sut.onMapEvent(LocationPickerMapEvent.Settled(41.0, 29.0))
        sut.onNameChange("Kafe")
        assertTrue(sut.state.canConfirm)
    }

    @Test
    fun `no fix leaves the map where it is`() = runTest {
        locator.fix = null
        val sut = viewModel()

        sut.locateDevice(LocationAuthorization.AUTHORIZED)
        advanceUntilIdle()

        assertNull(sut.state.cameraRequest)
        assertTrue("The dot still shows once a fix comes in", sut.state.showsDeviceLocation)
    }

    @Test
    fun `until the picker locates, nothing is asked`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()

        assertNull(sut.state.locationAuthorization)
        assertFalse(sut.state.showsDeviceLocation)
        assertFalse(sut.state.isLocationDenied)
        assertTrue(permissionRequests.isEmpty())
        assertEquals(0, locator.locateCount)
    }

    @Test
    fun `an answer nobody asked for is ignored`() = runTest {
        val sut = viewModel()

        sut.onLocationPermissionResult(granted = true)
        advanceUntilIdle()

        assertNull(sut.state.locationAuthorization)
        assertEquals(0, locator.locateCount)
    }

    // --- The admin comes first ---

    @Test
    fun `changing a location keeps the map on it - permission is asked for the dot, but no fix is read`() = runTest {
        val initial = NotificationLocation(38.5, 27.1, "Kafe", null, "p1")
        val sut = viewModel(initial)

        sut.locateDevice(LocationAuthorization.NOT_DETERMINED)
        sut.onLocationPermissionResult(granted = true)
        advanceUntilIdle()

        assertEquals(1, permissionRequests.size)
        assertEquals(0, locator.locateCount)
        assertNull(sut.state.cameraRequest)
        assertTrue(sut.state.showsDeviceLocation)
        assertEquals(initial, sut.state.location)
    }

    @Test
    fun `a drag before the fix arrives keeps the map where the admin took it`() = runTest {
        val gate = Gate().also { locator.fixGate = it }
        val sut = viewModel()

        sut.locateDevice(LocationAuthorization.AUTHORIZED)
        advanceUntilIdle()
        assertEquals(1, gate.arrivals)
        sut.onMapEvent(LocationPickerMapEvent.GestureStarted)
        gate.open()
        advanceUntilIdle()

        assertEquals(1, locator.locateCount)
        assertNull(sut.state.cameraRequest)
    }

    @Test
    fun `a drag while the permission is asked means no fix is read at all`() = runTest {
        val sut = viewModel()

        sut.locateDevice(LocationAuthorization.NOT_DETERMINED)
        sut.onMapEvent(LocationPickerMapEvent.GestureStarted)
        sut.onLocationPermissionResult(granted = true)
        advanceUntilIdle()

        assertEquals(0, locator.locateCount)
        assertNull(sut.state.cameraRequest)
        assertTrue(sut.state.showsDeviceLocation)
    }

    @Test
    fun `a search result chosen before the fix arrives wins`() = runTest {
        val gate = Gate().also { locator.fixGate = it }
        val sut = viewModel()

        sut.locateDevice(LocationAuthorization.AUTHORIZED)
        advanceUntilIdle()
        sut.onSuggestionSelected(LocationPickerFixture.suggestion)
        advanceUntilIdle()
        gate.open()
        advanceUntilIdle()

        val request = sut.state.cameraRequest
        assertEquals(1, request?.id)
        assertEquals(LocationPickerFixture.kadikoy.latitude, request?.camera?.latitude)
        assertEquals(LocationPickerFixture.kadikoy.placeId, sut.state.placeId)
    }

    @Test
    fun `a search result still resolving when the fix arrives wins`() = runTest {
        val gate = Gate().also { searcher.placeGate = it }
        val sut = viewModel()

        sut.onSuggestionSelected(LocationPickerFixture.suggestion)
        advanceUntilIdle()
        sut.locateDevice(LocationAuthorization.AUTHORIZED)
        advanceUntilIdle()
        assertNull(sut.state.cameraRequest)
        gate.open()
        advanceUntilIdle()

        assertEquals(LocationPickerFixture.kadikoy.latitude, sut.state.cameraRequest?.camera?.latitude)
    }

    @Test
    fun `a place tapped on the map before the fix arrives wins`() = runTest {
        val gate = Gate().also { locator.fixGate = it }
        val sut = viewModel()

        sut.locateDevice(LocationAuthorization.AUTHORIZED)
        advanceUntilIdle()
        sut.onMapEvent(LocationPickerFixture.pastane)
        gate.open()
        advanceUntilIdle()

        val request = sut.state.cameraRequest
        assertEquals(1, request?.id)
        assertEquals(LocationPickerFixture.pastane.latitude, request?.camera?.latitude)
    }

    @Test
    fun `closing the picker stops a fix still on its way`() = runTest {
        val gate = Gate().also { locator.fixGate = it }
        val sut = viewModel()

        sut.locateDevice(LocationAuthorization.AUTHORIZED)
        advanceUntilIdle()
        sut.onDismissed()
        gate.open()
        advanceUntilIdle()

        assertNull(sut.state.cameraRequest)
    }
}
