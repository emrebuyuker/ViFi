package com.salticusteam.vifi.core.navigation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ModalPresenceTest {

    @Test
    fun `isShowing when modals open and close follows the count`() {
        val presence = ModalPresence()

        presence.opened()
        presence.opened()
        presence.closed()

        assertTrue(presence.isShowing)
        presence.closed()
        assertFalse(presence.isShowing)
    }

    @Test
    fun `closed when nothing is open never goes below zero`() {
        val presence = ModalPresence()

        presence.closed()
        presence.opened()

        assertEquals(1, presence.count.value)
        assertTrue(presence.isShowing)
    }

    @Test
    fun `awaitNoModal returns at once when none is shown and waits for the last one to close`() = runTest {
        val presence = ModalPresence()
        presence.awaitNoModal()

        presence.opened()
        val waiting = async { presence.awaitNoModal() }
        runCurrent()
        assertFalse(waiting.isCompleted)

        presence.closed()
        runCurrent()
        assertTrue(waiting.isCompleted)
    }
}
