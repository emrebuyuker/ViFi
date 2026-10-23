package com.salticusteam.vifi.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilitiesTest {

    // --- Slug ---

    @Test
    fun `slug folds Turkish letters and collapses separators`() {
        assertEquals("istanbul-teknik-universitesi", Slug.make("  İSTANBUL Teknik Üniversitesi! "))
        assertEquals("cukurova-universitesi-iibf", Slug.make("Çukurova Üniversitesi / İİBF"))
        assertEquals("unknown", Slug.make("—"))
    }

    // --- StorageUrl ---

    @Test
    fun `storage url encodes the whole path as one segment`() {
        assertEquals(
            "https://firebasestorage.googleapis.com/v0/b/vifi-831a8.appspot.com/o/" +
                "imagess%2Fs%C4%B1nav%201.jpg?alt=media",
            StorageUrl.canonical("imagess/sınav 1.jpg", "vifi-831a8.appspot.com"),
        )
        assertNull(StorageUrl.canonical("", "bucket"))
        assertNull(StorageUrl.canonical("a.jpg", ""))
    }

    // --- AppVersion ---

    @Test
    fun `app version compares numerically with zero padding`() {
        assertTrue(AppVersion.isNewer("2.0.10", "2.0.9"))
        assertFalse(AppVersion.isNewer("3.0", "3.0.0"))
        assertTrue(AppVersion.isNewer("3.1.0", "2.5"))
        assertEquals(listOf(1, 0), AppVersion.numericComponents("1-beta.x"))
    }

    // --- ContactSupport ---

    @Test
    fun `contact support body carries context and versions only`() {
        assertEquals(
            "BLG 101 / 2023 Vize\n\n\n—\nViFi 3.0.0 · Android 15",
            ContactSupport.body(appVersion = "3.0.0", androidVersion = "15", context = "BLG 101 / 2023 Vize"),
        )
        val uri = ContactSupport.mailtoUri(appVersion = "3.0.0", androidVersion = "15")
        assertTrue(uri.startsWith("mailto:buyukeryazilim@gmail.com?subject=ViFi%20%E2%80%94%20Bize%20Ula%C5%9F&body="))
        assertFalse(uri.contains('+'))
    }
}
