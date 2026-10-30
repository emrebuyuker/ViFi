package com.salticusteam.vifi.core.data.firebase

import com.salticusteam.vifi.core.model.NotificationConsent
import com.salticusteam.vifi.core.model.NotificationConsentSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `FirebaseNotificationSettingsServiceTests` (4): the record's shape is the shared rules' contract. */
class FirestoreNotificationSettingsServiceTest {

    private val serverTime = Any()

    @Test
    fun `a boolean marketing is the answer`() {
        assertEquals(NotificationConsent(true), FirestoreNotificationSettingsService.consent(mapOf("marketing" to true)))
        assertEquals(
            NotificationConsent(false),
            FirestoreNotificationSettingsService.consent(mapOf("marketing" to false, "source" to "prompt", "version" to 1L)),
        )
    }

    @Test
    fun `a missing, empty or odd record means never answered`() {
        assertNull(FirestoreNotificationSettingsService.consent(null))
        assertNull(FirestoreNotificationSettingsService.consent(emptyMap()))
        assertNull(FirestoreNotificationSettingsService.consent(mapOf("marketing" to "true")))
        assertNull(FirestoreNotificationSettingsService.consent(mapOf("marketing" to 1L)))
    }

    @Test
    fun `the record has exactly the four fields the rules allow`() {
        val fields = FirestoreNotificationSettingsService.fields(true, NotificationConsentSource.SETTINGS, serverTime)

        assertEquals(setOf("marketing", "source", "version", "updatedAt"), fields.keys)
        assertEquals("settings", fields["source"])
        val version = fields["version"] as Int
        assertTrue(version in 1..99)
        assertEquals(NotificationConsent.CURRENT_VERSION, version)
        assertEquals(serverTime, fields["updatedAt"])
    }

    @Test
    fun `a no is written as marketing false`() {
        val fields = FirestoreNotificationSettingsService.fields(false, NotificationConsentSource.PROMPT, serverTime)

        assertEquals(false, fields["marketing"])
        assertEquals("prompt", fields["source"])
    }
}
