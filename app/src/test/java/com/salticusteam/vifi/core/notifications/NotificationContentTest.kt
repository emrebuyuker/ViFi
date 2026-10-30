package com.salticusteam.vifi.core.notifications

import com.salticusteam.vifi.core.model.NotificationKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationContentTest {

    private val signedImage = "https://storage.googleapis.com/vifi-831a8.appspot.com/notificationImages/n-1.jpg" +
        "?X-Goog-Algorithm=GOOG4-RSA-SHA256&X-Goog-Credential=a%2F20261030%2Fauto&X-Goog-Signature=ab12"

    private fun content(
        data: Map<String, String> = emptyMap(),
        title: String? = "Başlık",
        body: String? = "Metin",
        imageUrl: String? = null,
    ) = NotificationContent.from(title, body, imageUrl, data)

    @Test
    fun `the notification part wins over data keys`() {
        val result = content(mapOf("title" to "x", "body" to "y"))

        assertEquals("Başlık", result.title)
        assertEquals("Metin", result.body)
    }

    @Test
    fun `data title body and the legacy message key are the fallback`() {
        assertEquals(NotificationContent("T", "B"), content(mapOf("title" to "T", "body" to "B"), title = null, body = null))
        assertEquals(NotificationContent(null, "eski"), content(mapOf("message" to "eski"), title = null, body = null))
    }

    @Test
    fun `blank text counts as empty`() {
        val result = content(mapOf("body" to ""), title = " ", body = null)

        assertNull(result.title)
        assertTrue(result.isEmpty)
        assertFalse(NotificationContent("t", null).isEmpty)
    }

    @Test
    fun `the server's notification id and kind are read from the data keys`() {
        val result = content(mapOf("notificationId" to "n-1", "kind" to "promo"))

        assertEquals("n-1", result.notificationId)
        assertEquals(NotificationKind.PROMO, result.kind)
    }

    @Test
    fun `a notification id outside the server's pattern is dropped`() {
        assertNull(content(mapOf("notificationId" to "../inbox")).notificationId)
        assertNull(content(mapOf("notificationId" to "")).notificationId)
    }

    @Test
    fun `channel is picked by kind and an unknown or missing kind is an announcement`() {
        assertEquals(NotificationChannels.GENERAL, content(mapOf("kind" to "general")).channelId)
        assertEquals(NotificationChannels.PROMO, content(mapOf("kind" to "promo")).channelId)
        assertEquals(NotificationChannels.SYSTEM, content(mapOf("kind" to "system")).channelId)
        assertEquals(NotificationChannels.GENERAL, content(mapOf("kind" to "marketing")).channelId)
        assertEquals(NotificationChannels.GENERAL, content().channelId)
    }

    @Test
    fun `tray id follows the notification id so a push only replaces its own copy`() {
        val first = content(mapOf("notificationId" to "n-1"))
        val again = content(mapOf("notificationId" to "n-1"), title = "Yeni başlık")
        val other = content(mapOf("notificationId" to "n-2"))

        assertEquals(first.trayId { error("unused") }, again.trayId { error("unused") })
        assertNotEquals(first.trayId { error("unused") }, other.trayId { error("unused") })
        assertEquals(42, content().trayId { 42 })
    }

    @Test
    fun `tap extras carry the id and kind under the system tray's keys`() {
        assertEquals(
            mapOf("notificationId" to "n-1", "kind" to "system"),
            content(mapOf("notificationId" to "n-1", "kind" to "system")).tapExtras(),
        )
        assertEquals(emptyMap<String, String>(), content(mapOf("kind" to "unknown")).tapExtras())
    }

    @Test
    fun `only a well-formed https image is kept, verbatim`() {
        assertEquals(signedImage, content(imageUrl = " $signedImage ").imageUrl)
        assertNull(content(imageUrl = "http://example.com/a.jpg").imageUrl)
        assertNull(content(imageUrl = "https://").imageUrl)
        assertNull(content(imageUrl = "file:///sdcard/a.jpg").imageUrl)
        assertNull(content(imageUrl = "").imageUrl)
    }

    @Test
    fun `a tap opens the inbox for our flag or an FCM launch intent only`() {
        assertTrue(InboxDeepLink.opensInbox(emptySet(), openInboxFlag = true))
        assertTrue(InboxDeepLink.opensInbox(setOf("google.message_id"), openInboxFlag = false))
        assertFalse(InboxDeepLink.opensInbox(setOf("vifi_signed_out"), openInboxFlag = false))
    }
}
