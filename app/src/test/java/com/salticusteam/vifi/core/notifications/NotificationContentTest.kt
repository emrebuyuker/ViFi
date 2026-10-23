package com.salticusteam.vifi.core.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationContentTest {

    @Test
    fun `the notification part wins over data keys`() {
        val content = NotificationContent.from("Başlık", "Metin", mapOf("title" to "x", "body" to "y"))

        assertEquals(NotificationContent("Başlık", "Metin"), content)
    }

    @Test
    fun `data title body and the legacy message key are the fallback`() {
        assertEquals(NotificationContent("T", "B"), NotificationContent.from(null, null, mapOf("title" to "T", "body" to "B")))
        assertEquals(NotificationContent(null, "eski"), NotificationContent.from(null, null, mapOf("message" to "eski")))
    }

    @Test
    fun `blank text counts as empty`() {
        val content = NotificationContent.from(" ", null, mapOf("body" to ""))

        assertNull(content.title)
        assertTrue(content.isEmpty)
        assertFalse(NotificationContent("t", null).isEmpty)
    }

    @Test
    fun `a tap opens the inbox for our flag or an FCM launch intent only`() {
        assertTrue(InboxDeepLink.opensInbox(emptySet(), openInboxFlag = true))
        assertTrue(InboxDeepLink.opensInbox(setOf("google.message_id"), openInboxFlag = false))
        assertFalse(InboxDeepLink.opensInbox(setOf("vifi_signed_out"), openInboxFlag = false))
    }
}
