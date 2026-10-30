package com.salticusteam.vifi.core.maps

import org.junit.Assert.assertEquals
import org.junit.Test

/** The Places session token: shared by a search's requests, renewed after its lifetime and after a place fetch. */
class PlacesSessionTest {

    private var now = 0L
    private var issued = 0
    private val session = PlacesSession(lifetimeMillis = 150_000L, newToken = { "token-${++issued}" }, now = { now })

    @Test
    fun `searches within the lifetime share one token`() {
        now = 150_000L
        session.renewIfStale()

        assertEquals("token-1", session.token)
    }

    @Test
    fun `a search after a longer pause starts a new session`() {
        now = 150_001L
        session.renewIfStale()

        assertEquals("token-2", session.token)
    }

    @Test
    fun `a place fetch ends the session and the lifetime starts over`() {
        now = 100_000L
        session.restart()
        assertEquals("token-2", session.token)

        now = 250_000L
        session.renewIfStale()
        assertEquals("150 seconds after the restart is still the same session", "token-2", session.token)
    }
}
