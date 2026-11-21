package com.salticusteam.vifi.feature.examviewer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** FLAG_SECURE stays on while protected screens overlap (SQA-04: the file viewer redirecting to the questions). */
class HoldCounterTest {

    private val window = Any()

    @Test
    fun `a single screen sets the flag and clears it when it leaves`() {
        val holds = HoldCounter<Any>()

        assertTrue(holds.acquire(window))
        assertTrue(holds.release(window))
    }

    @Test
    fun `the redirect keeps the flag - the viewer leaving after the question screen does not clear it`() {
        val holds = HoldCounter<Any>()

        assertTrue(holds.acquire(window)) // file viewer
        assertFalse(holds.acquire(window)) // question screen, set before the viewer is disposed
        assertFalse(holds.release(window)) // viewer disposed: the question screen is still protected
        assertTrue(holds.release(window)) // question screen left: now clear
    }

    @Test
    fun `an unmatched release changes nothing`() {
        val holds = HoldCounter<Any>()

        assertFalse(holds.release(window))
        assertTrue(holds.acquire(window))
    }

    @Test
    fun `windows are counted separately`() {
        val holds = HoldCounter<Any>()
        val other = Any()

        assertTrue(holds.acquire(window))
        assertTrue(holds.acquire(other))
        assertTrue(holds.release(other))
        assertTrue(holds.release(window))
    }
}
