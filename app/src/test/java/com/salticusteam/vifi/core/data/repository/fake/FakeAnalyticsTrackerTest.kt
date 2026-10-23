package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsUser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FakeAnalyticsTrackerTest {

    private val user = AnalyticsUser("u1", "itu", null, null, null, isAdmin = false)

    @Test
    fun `track records events in order`() {
        val sut = FakeAnalyticsTracker()

        sut.track(AnalyticsEvent.LoginSucceeded)
        sut.track(AnalyticsEvent.SignedOut)

        assertEquals(listOf(AnalyticsEvent.LoginSucceeded, AnalyticsEvent.SignedOut), sut.events)
    }

    @Test
    fun `setUser records identity and null clears it`() {
        val sut = FakeAnalyticsTracker()

        sut.setUser(user)
        assertEquals(user, sut.currentUser)
        sut.setUser(null)

        assertNull(sut.currentUser)
        assertEquals(listOf(user, null), sut.users)
    }
}
