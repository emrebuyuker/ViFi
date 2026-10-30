package com.salticusteam.vifi.core.maps

import com.salticusteam.vifi.BuildConfig
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `GoogleMapsConfigurationTests`: which build values count as a usable key, and no SDK start without one. */
class GoogleMapsConfigurationTest {

    @Test
    fun `a configured key is usable, trimmed`() {
        val configuration = GoogleMapsConfiguration.from("  test-key-123 \n")

        assertEquals("test-key-123", configuration.apiKey)
        assertTrue(configuration.isAvailable)
    }

    @Test
    fun `a missing, empty or blank key means unavailable`() {
        assertFalse(GoogleMapsConfiguration.from(null).isAvailable)
        assertFalse(GoogleMapsConfiguration.from("").isAvailable)
        assertFalse(GoogleMapsConfiguration.from("   ").isAvailable)
        assertNull(GoogleMapsConfiguration.from("").apiKey)
    }

    @Test
    fun `a placeholder that was never filled in means unavailable`() {
        assertFalse(GoogleMapsConfiguration.from("\${MAPS_API_KEY}").isAvailable)
        assertFalse(GoogleMapsConfiguration.from("$(GOOGLE_MAPS_API_KEY)").isAvailable)
    }

    @Test
    fun `a value that is not text means unavailable`() {
        assertFalse(GoogleMapsConfiguration.from(42).isAvailable)
    }

    @Test
    fun `without a key the SDKs are not started`() {
        // A relaxed context: with no key nothing may reach it, or Places.
        val sdk = GoogleMapsSdk(mockk(relaxed = true))

        assertFalse(sdk.startIfPossible(GoogleMapsConfiguration.from("")))
        assertFalse(sdk.startIfPossible(GoogleMapsConfiguration.from("\${MAPS_API_KEY}")))
    }

    @Test
    fun `the app reads the key the build was given, and never prints it`() {
        assertEquals(GoogleMapsConfiguration.from(BuildConfig.MAPS_API_KEY).apiKey, GoogleMapsConfiguration.current.apiKey)
        assertFalse(GoogleMapsConfiguration.from("secret-key").toString().contains("secret-key"))
    }
}
