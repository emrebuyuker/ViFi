package com.salticusteam.vifi.core.data.profile

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.repository.fake.FakeProfileService
import com.salticusteam.vifi.core.model.ProfileError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileStoreTest {

    private val uid = FakeProfileService.SAMPLE_UID
    private val profile = FakeProfileService.SAMPLE_PROFILE

    @Test
    fun `initial state is loading`() {
        val sut = ProfileStore(FakeProfileService())
        assertEquals(ProfileState.Loading, sut.state.value)
        assertNull(sut.profile)
    }

    @Test
    fun `load when profile stored becomes present`() = runTest {
        val service = FakeProfileService(profiles = mapOf(uid to profile))
        val sut = ProfileStore(service)

        sut.load(uid)

        assertEquals(ProfileState.Present(profile), sut.state.value)
        assertEquals(profile, sut.profile)
        assertEquals(listOf(FakeProfileService.Call.Load(uid)), service.calls)
    }

    @Test
    fun `load when nothing stored becomes absent`() = runTest {
        val sut = ProfileStore(FakeProfileService())
        sut.load(uid)
        assertEquals(ProfileState.Absent, sut.state.value)
    }

    @Test
    fun `load when read fails surfaces userMessage instead of absent`() = runTest {
        val sut = ProfileStore(FakeProfileService(failure = ProfileError.Offline))

        sut.load(uid)

        assertEquals(ProfileState.Failed(ProfileError.Offline.userMessage), sut.state.value)
        assertNull(sut.profile)
    }

    @Test
    fun `update opens the gate without a reload and reset clears to loading`() = runTest {
        val service = FakeProfileService()
        val sut = ProfileStore(service)

        sut.update(profile)
        assertEquals(ProfileState.Present(profile), sut.state.value)

        sut.reset()
        assertEquals(ProfileState.Loading, sut.state.value)
        assertEquals(emptyList<FakeProfileService.Call>(), service.calls)
    }

    @Test
    fun `load when same user reloads keeps profile visible without loading flash`() = runTest {
        val service = FakeProfileService(profiles = mapOf(uid to profile))
        val sut = ProfileStore(service)
        sut.load(uid)

        sut.state.test {
            assertEquals(ProfileState.Present(profile), awaitItem())
            val gate = CompletableDeferred<Unit>().also { service.gate = it }
            val reload = launch { sut.load(uid) }
            runCurrent()
            expectNoEvents()   // no Loading emitted while the same user re-reads
            gate.complete(Unit)
            reload.join()
            expectNoEvents()   // same Present value, StateFlow does not re-emit
        }
    }

    @Test
    fun `load when different user shows loading`() = runTest {
        val service = FakeProfileService(profiles = mapOf(uid to profile))
        val sut = ProfileStore(service)
        sut.load(uid)

        val gate = CompletableDeferred<Unit>().also { service.gate = it }
        val reload = launch { sut.load("user-2") }
        runCurrent()
        assertEquals(ProfileState.Loading, sut.state.value)

        gate.complete(Unit)
        reload.join()
        assertEquals(ProfileState.Absent, sut.state.value)
    }

    @Test
    fun `load when cancelled leaves the current state untouched`() = runTest {
        val service = FakeProfileService(profiles = mapOf(uid to profile))
        val sut = ProfileStore(service)
        sut.load(uid)

        service.gate = CompletableDeferred()
        val reload = launch { sut.load(uid) }
        runCurrent()
        reload.cancel()
        reload.join()

        assertEquals(ProfileState.Present(profile), sut.state.value)
    }

    @Test
    fun `load when reset while in flight does not overwrite the reset state`() = runTest {
        val service = FakeProfileService(profiles = mapOf(uid to profile))
        val sut = ProfileStore(service)
        val gate = CompletableDeferred<Unit>().also { service.gate = it }

        val load = launch { sut.load(uid) }
        runCurrent()
        sut.reset()
        gate.complete(Unit)
        load.join()

        assertEquals(ProfileState.Loading, sut.state.value)
    }
}
