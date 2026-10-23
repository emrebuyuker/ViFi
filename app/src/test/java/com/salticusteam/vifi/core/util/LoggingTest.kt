package com.salticusteam.vifi.core.util

import android.util.Log
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoggingTest {

    private val tree = ReleaseTree()

    @Test
    fun `release tree drops debug and info`() {
        assertFalse(tree.isLoggable("App", Log.VERBOSE))
        assertFalse(tree.isLoggable("App", Log.DEBUG))
        assertFalse(tree.isLoggable("App", Log.INFO))
    }

    @Test
    fun `release tree keeps warnings and errors`() {
        assertTrue(tree.isLoggable("App", Log.WARN))
        assertTrue(tree.isLoggable("App", Log.ERROR))
    }
}
