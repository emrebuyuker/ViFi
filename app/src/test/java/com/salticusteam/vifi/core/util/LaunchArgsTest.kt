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

    @Test
    fun `parse never treats a valued argument as a flag`() {
        val flags = mapOf("vifi_open_notification" to true, "vifi_consent_reask" to true)

        assertEquals(setOf(LaunchArg.CONSENT_REASK), LaunchArgs.parse(flags))
    }

    @Test
    fun `parseValues when a valued argument is given returns it trimmed, blank as absent`() {
        assertEquals(
            mapOf(LaunchArg.OPEN_NOTIFICATION to "p-paywall"),
            LaunchArgs.parseValues(mapOf("vifi_open_notification" to " p-paywall ", "vifi_admin" to "true")),
        )
        assertTrue(LaunchArgs.parseValues(mapOf("vifi_open_notification" to "  ")).isEmpty())
        assertTrue(LaunchArgs.parseValues(null).isEmpty())
    }

    @Test
    fun `extra keys are unique and only open_notification takes a value`() {
        assertEquals(LaunchArg.entries.size, LaunchArg.entries.map { it.extraKey }.toSet().size)
        assertEquals(listOf(LaunchArg.OPEN_NOTIFICATION), LaunchArg.entries.filter { it.takesValue })
    }
}
