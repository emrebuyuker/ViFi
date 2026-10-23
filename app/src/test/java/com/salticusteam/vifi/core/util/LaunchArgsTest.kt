package com.salticusteam.vifi.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchArgsTest {

    @Test
    fun `parse when flags set returns matching args only`() {
        val flags = mapOf("vifi_no_coins" to true, "vifi_admin" to false, "unknown" to true)

        assertEquals(setOf(LaunchArg.NO_COINS), LaunchArgs.parse(flags))
    }

    @Test
    fun `parse when no extras returns empty`() {
        assertTrue(LaunchArgs.parse(null).isEmpty())
    }
}
