package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.model.ProfileError
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeProfileServiceTest {

    @Test
    fun `save then load then delete round-trips in memory`() = runTest {
        val sut = FakeProfileService()
        val profile = FakeProfileService.SAMPLE_PROFILE

        sut.saveProfile(profile, "u1")
        assertEquals(profile, sut.loadProfile("u1"))

        sut.deleteProfile("u1")
        assertNull(sut.loadProfile("u1"))
    }

    @Test
    fun `failure makes every call throw`() = runTest {
        val sut = FakeProfileService(failure = ProfileError.PermissionDenied)
        assertEquals(ProfileError.PermissionDenied, runCatching { sut.loadProfile("u1") }.exceptionOrNull())
    }
}
