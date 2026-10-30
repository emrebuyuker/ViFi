package com.salticusteam.vifi.core.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The contract's wire values and the model-level rules (expiry, location validity) shared by every track. */
class AppNotificationTest {

    @Test
    fun `wire values match the shared schema`() {
        assertEquals(listOf("general", "promo", "system"), NotificationKind.entries.map { it.raw })
        assertEquals(
            listOf("scheduled", "sending", "sent", "failed", "canceled"),
            NotificationStatus.entries.map { it.raw },
        )
        assertEquals(listOf("opened", "actionTapped"), NotificationEvent.entries.map { it.raw })
        assertEquals(listOf("prompt", "reask", "settings"), NotificationPromptSource.entries.map { it.raw })
        assertEquals(listOf("prompt", "settings"), NotificationConsentSource.entries.map { it.raw })
        assertEquals(1, NotificationConsent.CURRENT_VERSION)
        assertEquals(1024 * 1024, NotificationImage.MAX_BYTES)
        assertNull(NotificationKind.fromRaw("survey"))
    }

    @Test
    fun `action types and audience scopes match the shared schema`() {
        val target = NotificationExamTarget("e", ArchivePath(listOf("u", "f", "d", "c", "e")), ExamKind.PDF)
        val location = NotificationLocation(latitude = 1.0, longitude = 2.0, name = "x")

        assertEquals(
            listOf("location", "exam", "paywall", "addExam", "url"),
            listOf(
                NotificationAction.Location(location),
                NotificationAction.Exam(target),
                NotificationAction.Paywall,
                NotificationAction.AddExam,
                NotificationAction.Url("https://x.com"),
            ).map { it.type },
        )
        assertEquals(
            listOf("all", "segment", "user", "admins"),
            listOf(
                NotificationAudience.All,
                NotificationAudience.Segment(NotificationSegment("u")),
                NotificationAudience.User("x"),
                NotificationAudience.Admins,
            ).map { it.scope },
        )
    }

    @Test
    fun `isExpired when the expiry is now or past is expired`() {
        val now = Instant.ofEpochSecond(1_761_700_000)
        val item = InboxItem(id = "n", source = InboxSource.BROADCAST, kind = NotificationKind.GENERAL, title = "t", body = "")

        assertFalse(item.isExpired(now))
        assertFalse(item.copy(expiresAt = now.plusSeconds(1)).isExpired(now))
        assertTrue(item.copy(expiresAt = now).isExpired(now))
        assertTrue(item.copy(expiresAt = now.minusSeconds(1)).isExpired(now))
    }

    @Test
    fun `location isValid needs a range, a trimmed name and lengths in code points`() {
        val valid = NotificationLocation(latitude = 90.0, longitude = -180.0, name = " Kafe ")

        assertTrue(valid.isValid)
        assertFalse(valid.copy(latitude = 90.1).isValid)
        assertFalse(valid.copy(longitude = 180.1).isValid)
        assertFalse(valid.copy(name = "   ").isValid)
        assertTrue(valid.copy(name = "😀".repeat(120)).isValid)
        assertFalse(valid.copy(name = "a".repeat(121)).isValid)
        assertTrue(valid.copy(address = "a".repeat(200)).isValid)
        assertFalse(valid.copy(address = "a".repeat(201)).isValid)
    }

    @Test
    fun `userMessage of the notification errors is their own Turkish text`() {
        assertEquals("Bildirimler yüklenemedi. Bağlantını kontrol edip tekrar dene.", InboxError.Unavailable.userMessage)
        assertEquals(
            "Bildirim tercihin şu an okunamıyor. Bağlantını kontrol edip tekrar dene.",
            (NotificationSettingsError.Unavailable as Throwable).userMessage,
        )
        assertEquals("Sebep", (NotificationAdminError.InvalidDraft("Sebep") as Throwable).userMessage)
        assertEquals(GENERIC_ERROR_MESSAGE, (NotificationAdminError.Unknown as Throwable).userMessage)
    }
}
