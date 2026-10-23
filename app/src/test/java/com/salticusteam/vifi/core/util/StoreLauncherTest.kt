package com.salticusteam.vifi.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class StoreLauncherTest {

    @Test
    fun `marketUrl takes the package from the play url`() {
        assertEquals(
            "market://details?id=com.example.app",
            StoreLauncher.marketUrl("https://play.google.com/store/apps/details?id=com.example.app&hl=tr", "com.fallback"),
        )
    }

    @Test
    fun `marketUrl without an id uses the installed package`() {
        assertEquals("market://details?id=com.fallback", StoreLauncher.marketUrl("https://example.com/update", "com.fallback"))
        assertEquals("market://details?id=com.fallback", StoreLauncher.marketUrl("not a url", "com.fallback"))
    }
}
