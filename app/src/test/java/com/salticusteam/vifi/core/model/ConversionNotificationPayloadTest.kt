package com.salticusteam.vifi.core.model

import com.salticusteam.vifi.core.notifications.NotificationChannels
import com.salticusteam.vifi.core.notifications.NotificationContent
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SMP-04 — the 4.2.0 automatic admin notices (server `notifications/automatic.js`: `conversionReady`,
 * `conversionFailed`, `conversionBudgetExceeded`) are `system` notices without an action, sent to the `admins`
 * audience. The Android parsers must show them as they are, without dropping anything.
 */
class ConversionNotificationPayloadTest {

    private val created = Instant.ofEpochSecond(1_763_700_000)

    private data class Sample(val id: String, val trigger: String, val title: String, val body: String)

    private val samples = listOf(
        Sample(
            "auto-convready-sub-8a1f3c",
            "conversionReady",
            "Sınav taslağı hazır",
            "Fizik I – 2024 VİZE soru soru hazırlandı, incelemeni bekliyor.",
        ),
        Sample(
            "auto-convfailed-sub-8a1f3c-0",
            "conversionFailed",
            "Sınav dönüştürülemedi",
            "Bir sınav yapay zekâ ile dönüştürülemedi. Moderasyon ekranından tekrar başlatabilirsin.",
        ),
        Sample(
            "auto-convbudget-2026-11",
            "conversionBudgetExceeded",
            "Yapay zekâ bütçesi doldu",
            "2026-11 ayının yapay zekâ harcaması (\$50.00) aylık \$50.00 sınırına ulaştı. " +
                "Yeni dönüştürmeler bekletiliyor; gelecek ayın başında kendiliğinden başlayacak.",
        ),
    )

    private fun inboxData(sample: Sample): Map<String, Any?> = mapOf(
        "kind" to "system",
        "title" to sample.title,
        "body" to sample.body,
        "createdAt" to created,
    )

    @Test
    fun `inboxItem when an automatic conversion notice arrives shows it as a system item without an action`() {
        for (sample in samples) {
            val item = NotificationPayloadParser.inboxItem(sample.id, InboxSource.PERSONAL, inboxData(sample))

            assertNotNull(sample.id, item)
            item as InboxItem
            assertEquals(NotificationKind.SYSTEM, item.kind)
            assertEquals(sample.title, item.title)
            assertEquals(sample.body, item.body)
            assertNull(item.action)
            assertNull(item.imageStoragePath)
            assertEquals(created, item.createdAt)
        }
    }

    @Test
    fun `inboxItem when the job id is hashed keeps the notice`() {
        val hashed = "auto-convready-h" + "a".repeat(40)

        assertNotNull(NotificationPayloadParser.inboxItem(hashed, InboxSource.PERSONAL, inboxData(samples.first())))
    }

    @Test
    fun `inboxItem when the server adds unknown fields still parses`() {
        val data = inboxData(samples.first()) + mapOf("trigger" to "conversionReady", "source" to "automatic")

        assertNotNull(NotificationPayloadParser.inboxItem(samples.first().id, InboxSource.PERSONAL, data))
    }

    @Test
    fun `sentNotification when an automatic admin notice is in the history reads as automatic for admins`() {
        for (sample in samples) {
            val data = inboxData(sample) + mapOf(
                "audience" to mapOf("scope" to "admins"),
                "audienceLabel" to "Yöneticiler",
                "status" to "sent",
                "isTest" to false,
                "source" to "automatic",
                "trigger" to sample.trigger,
                "createdBy" to "system",
                "sentAt" to created,
                "stats" to mapOf("recipients" to 2, "pushTargets" to 2, "pushSent" to 2, "pushFailed" to 0),
            )

            val sent = NotificationPayloadParser.sentNotification(sample.id, data)

            assertNotNull(sample.id, sent)
            sent as SentNotification
            assertTrue(sent.isAutomatic)
            assertFalse(sent.isTest)
            assertEquals(NotificationAudience.Admins, sent.audience)
            assertEquals(NotificationKind.SYSTEM, sent.kind)
            assertEquals(2, sent.stats.pushSent)
        }
    }

    @Test
    fun `push content when a conversion notice arrives has system channel, id and no image`() {
        for (sample in samples) {
            val content = NotificationContent.from(
                notificationTitle = sample.title,
                notificationBody = sample.body,
                notificationImageUrl = null,
                data = mapOf("notificationId" to sample.id, "kind" to "system"),
            )

            assertFalse(content.isEmpty)
            assertEquals(sample.title, content.title)
            assertEquals(sample.body, content.body)
            assertEquals(sample.id, content.notificationId)
            assertEquals(NotificationKind.SYSTEM, content.kind)
            assertEquals(NotificationChannels.SYSTEM, content.channelId)
            assertNull(content.imageUrl)
        }
    }
}
