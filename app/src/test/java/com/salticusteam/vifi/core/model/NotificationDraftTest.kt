package com.salticusteam.vifi.core.model

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `NotificationDraftTests` parity, plus code-point counting (the server's rule) and the https check. */
class NotificationDraftTest {

    private val now = Instant.ofEpochSecond(1_761_700_000)

    private val ready = NotificationDraft(title = "Başlık", body = "Mesaj")

    @Test
    fun `validationIssue when titled and bodied to everyone is ready`() {
        assertNull(ready.validationIssue(now))
    }

    @Test
    fun `validationIssue when title or body is empty or too long reports it`() {
        assertEquals(NotificationDraft.Issue.EMPTY_TITLE, ready.copy(title = "   ").validationIssue(now))
        assertEquals(NotificationDraft.Issue.TITLE_TOO_LONG, ready.copy(title = "a".repeat(81)).validationIssue(now))
        assertEquals(NotificationDraft.Issue.EMPTY_BODY, ready.copy(body = "").validationIssue(now))
        assertEquals(NotificationDraft.Issue.BODY_TOO_LONG, ready.copy(body = "a".repeat(501)).validationIssue(now))
    }

    @Test
    fun `validationIssue counts code points like the server, not UTF-16 chars`() {
        // Each emoji is one code point but two chars.
        assertNull(ready.copy(title = "😀".repeat(80)).validationIssue(now))
        assertEquals(NotificationDraft.Issue.TITLE_TOO_LONG, ready.copy(title = "😀".repeat(81)).validationIssue(now))
        assertNull(ready.copy(body = "ğ".repeat(500)).validationIssue(now))
    }

    @Test
    fun `validationIssue when a segment lacks a university or a department lacks its faculty is invalid`() {
        val noUniversity = NotificationSegment(universityId = "")
        val departmentOnly = NotificationSegment(universityId = "u", departmentId = "d")
        val full = NotificationSegment(universityId = "u", facultyId = "f", departmentId = "d", gender = Gender.FEMALE)

        assertEquals(
            NotificationDraft.Issue.INVALID_AUDIENCE,
            ready.copy(audience = NotificationAudience.Segment(noUniversity)).validationIssue(now),
        )
        assertEquals(
            NotificationDraft.Issue.INVALID_AUDIENCE,
            ready.copy(audience = NotificationAudience.Segment(departmentOnly)).validationIssue(now),
        )
        assertNull(ready.copy(audience = NotificationAudience.Segment(full)).validationIssue(now))
    }

    @Test
    fun `validationIssue when addressed to one user or the admins is invalid`() {
        assertEquals(
            NotificationDraft.Issue.INVALID_AUDIENCE,
            ready.copy(audience = NotificationAudience.User("x")).validationIssue(now),
        )
        assertEquals(
            NotificationDraft.Issue.INVALID_AUDIENCE,
            ready.copy(audience = NotificationAudience.Admins).validationIssue(now),
        )
    }

    @Test
    fun `effectiveKind when a location is attached is promo whatever kind was picked`() {
        val located = ready.copy(
            kind = NotificationKind.GENERAL,
            action = NotificationDraft.Action.Location(NotificationLocation(latitude = 41.0, longitude = 29.0, name = "Kafe")),
        )

        assertEquals(NotificationKind.PROMO, located.effectiveKind)
        assertEquals("promo", located.payload()["kind"])
        assertEquals(NotificationKind.GENERAL, located.copy(action = NotificationDraft.Action.Paywall).effectiveKind)
    }

    @Test
    fun `validationIssue checks the location range, https links and a picked exam`() {
        val badLocation = NotificationLocation(latitude = 120.0, longitude = 29.0, name = "x")

        assertEquals(
            NotificationDraft.Issue.INVALID_LOCATION,
            ready.copy(action = NotificationDraft.Action.Location(badLocation)).validationIssue(now),
        )
        assertEquals(
            NotificationDraft.Issue.INVALID_URL,
            ready.copy(action = NotificationDraft.Action.Url("http://example.com")).validationIssue(now),
        )
        assertEquals(
            NotificationDraft.Issue.EMPTY_EXAM,
            ready.copy(action = NotificationDraft.Action.Exam(" ")).validationIssue(now),
        )
    }

    @Test
    fun `isHttpsUrl accepts only https links with a host within the limit`() {
        assertTrue(NotificationDraft.isHttpsUrl("https://example.com/a?b=c"))
        assertTrue(NotificationDraft.isHttpsUrl("HTTPS://example.com"))
        assertFalse(NotificationDraft.isHttpsUrl("http://example.com"))
        assertFalse(NotificationDraft.isHttpsUrl("https://"))
        assertFalse(NotificationDraft.isHttpsUrl("example.com"))
        assertFalse(NotificationDraft.isHttpsUrl(""))
        assertFalse(NotificationDraft.isHttpsUrl("https://example.com/" + "a".repeat(481)))
    }

    @Test
    fun `validationIssue when the image path is another notification's is invalid`() {
        val draft = ready.copy(id = "abc")

        assertNull(draft.copy(imagePath = NotificationImage.storagePath("abc")).validationIssue(now))
        assertEquals(
            NotificationDraft.Issue.INVALID_IMAGE,
            draft.copy(imagePath = NotificationImage.storagePath("other")).validationIssue(now),
        )
    }

    @Test
    fun `validationIssue when scheduled in the past or expiring before delivery reports it`() {
        assertEquals(
            NotificationDraft.Issue.SCHEDULE_IN_PAST,
            ready.copy(scheduledAt = now.minusSeconds(3600)).validationIssue(now),
        )
        // Within the minute of grace.
        assertNull(ready.copy(scheduledAt = now.minusSeconds(30)).validationIssue(now))
        assertEquals(NotificationDraft.Issue.EXPIRY_BEFORE_DELIVERY, ready.copy(expiresAt = now).validationIssue(now))
        assertEquals(
            NotificationDraft.Issue.EXPIRY_BEFORE_DELIVERY,
            ready.copy(scheduledAt = now.plusSeconds(7200), expiresAt = now.plusSeconds(3600)).validationIssue(now),
        )
    }

    @Test
    fun `payload carries trimmed text, the segment, the action and epoch-millisecond dates`() {
        val segment = NotificationSegment(universityId = "u", facultyId = "f", gender = Gender.MALE)
        val value = ready.copy(
            id = "abc-123",
            title = "  Başlık  ",
            audience = NotificationAudience.Segment(segment),
            action = NotificationDraft.Action.Exam("u__f__d__c__vize"),
            scheduledAt = Instant.ofEpochSecond(1_761_717_600),
            isTest = true,
        ).payload()

        assertEquals("abc-123", value["notificationId"])
        assertEquals("Başlık", value["title"])
        assertEquals(true, value["isTest"])
        assertEquals(1_761_717_600_000L, value["scheduledAt"])
        assertFalse(value.containsKey("expiresAt"))
        assertFalse(value.containsKey("imagePath"))
        val audience = value["audience"] as Map<*, *>
        assertEquals("segment", audience["scope"])
        assertEquals("f", audience["facultyId"])
        assertFalse(audience.containsKey("departmentId"))
        assertEquals("male", audience["gender"])
        assertEquals(mapOf("type" to "exam", "examId" to "u__f__d__c__vize"), value["action"])
    }

    @Test
    fun `payload of a location omits an empty address and place id and trims the name`() {
        val location = NotificationLocation(latitude = 41.0, longitude = 29.0, name = "  Kafe ", address = "", placeId = null)

        val action = ready.copy(action = NotificationDraft.Action.Location(location)).payload()["action"]

        assertEquals(mapOf("type" to "location", "latitude" to 41.0, "longitude" to 29.0, "name" to "Kafe"), action)
        assertEquals(mapOf("scope" to "all"), ready.payload()["audience"])
    }

    @Test
    fun `estimatedRecipients follows the most specific level picked and the gender`() {
        val facets = AudienceFacets(
            total = 10,
            universities = listOf(
                AudienceNode(
                    "u", "Üniv", AudienceCounts(10, 6, 4),
                    listOf(
                        AudienceNode(
                            "f", "Fak", AudienceCounts(7, 4, 3),
                            listOf(AudienceNode("d", "Böl", AudienceCounts(5, 3, 2))),
                        ),
                    ),
                ),
            ),
        )

        assertEquals(10, facets.estimatedRecipients(NotificationSegment(universityId = "u")))
        assertEquals(3, facets.estimatedRecipients(NotificationSegment("u", facultyId = "f", gender = Gender.MALE)))
        assertEquals(3, facets.estimatedRecipients(NotificationSegment("u", "f", "d", Gender.FEMALE)))
        assertEquals(0, facets.estimatedRecipients(NotificationSegment(universityId = "x")))
    }

    @Test
    fun `newId is a lowercase UUID the server accepts as a document id`() {
        val id = NotificationDraft.newId()

        assertEquals(id.lowercase(), id)
        assertTrue(NotificationPayloadParser.isValidNotificationId(id))
        assertTrue(NotificationDraft().id != NotificationDraft().id)
    }
}
