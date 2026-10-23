package com.salticusteam.vifi.app

import com.salticusteam.vifi.core.data.profile.ProfileState
import com.salticusteam.vifi.core.data.profile.ProfileStore
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeProfileService
import com.salticusteam.vifi.core.model.ProfileError
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileGateViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val uid = FakeAuthService.DEFAULT_USER.id

    @Test
    fun `load when a profile is stored opens the gate`() = runTest {
        val store = ProfileStore(FakeProfileService(mapOf(uid to FakeProfileService.SAMPLE_PROFILE)))
        val sut = ProfileGateViewModel(store)

        sut.load(uid)

        assertEquals(ProfileState.Present(FakeProfileService.SAMPLE_PROFILE), sut.profile.value)
    }

    @Test
    fun `load when no profile is stored asks for completion`() = runTest {
        val sut = ProfileGateViewModel(ProfileStore(FakeProfileService()))

        sut.load(uid)

        assertEquals(ProfileState.Absent, sut.profile.value)
    }

    @Test
    fun `retry after a failed read loads the profile`() = runTest {
        val service = FakeProfileService(mapOf(uid to FakeProfileService.SAMPLE_PROFILE), failure = ProfileError.Offline)
        val sut = ProfileGateViewModel(ProfileStore(service))

        sut.load(uid)
        assertEquals(ProfileState.Failed(ProfileError.Offline.userMessage), sut.profile.value)
        service.failure = null
        sut.retry(uid)
        advanceUntilIdle()

        assertEquals(ProfileState.Present(FakeProfileService.SAMPLE_PROFILE), sut.profile.value)
    }
}
