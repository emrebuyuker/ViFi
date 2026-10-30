package com.salticusteam.vifi.core.model

import java.time.Instant
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** iOS `NotificationPayloadParserTests` parity, plus the Android-side value types (Long/Double/Date, Boolean). */
class NotificationPayloadParserTest {

    private val created = Instant.ofEpochSecond(1_761_700_000)

    // --- Inbox ---

    @Test
    fun `inboxItem when the document is full parses its action, image and dates`() {
        val data = mapOf<String, Any?>(
            "kind" to "promo",
            "title" to "  Kampüs kafede %20 indirim ",
            "body" to "Öğrenci kartını göster.",
            "image" to mapOf("storagePath" to "notificationImages/n-1.jpg"),
            "action" to mapOf("type" to "location", "latitude" to 41.0848, "longitude" to 29.0510, "name" to "Kampüs Kafe"),
            "createdAt" to Date.from(created),
            "expiresAt" to 1_761_800_000_000L,
        )

        val item = NotificationPayloadParser.inboxItem("n-1", InboxSource.PERSONAL, data)

        assertNotNull(item)
        item as InboxItem
        assertEquals(NotificationKind.PROMO, item.kind)
        assertEquals("Kampüs kafede %20 indirim", item.title)
        assertEquals("notificationImages/n-1.jpg", item.imageStoragePath)
        assertEquals(created, item.createdAt)
        assertEquals(Instant.ofEpochSecond(1_761_800_000), item.expiresAt)
        val action = item.action
        assertTrue(action is NotificationAction.Location)
        val location = (action as NotificationAction.Location).location
        assertEquals("Kampüs Kafe", location.name)
        assertEquals(41.0848, location.latitude, 0.0)
    }

    @Test
    fun `inboxItem when a legacy announcement has only title, body and date reads as general`() {
        val data = mapOf<String, Any?>("title" to "Hoş geldin", "body" to "Merhaba", "createdAt" to created)

        val item = NotificationPayloadParser.inboxItem("AbC123xyz", InboxSource.BROADCAST, data)

        assertNotNull(item)
        assertEquals(NotificationKind.GENERAL, item?.kind)
        assertNull(item?.action)
        assertNull(item?.imageStoragePath)
        assertNull(item?.expiresAt)
    }

    @Test
    fun `imageStoragePath when not a notification image path is ignored`() {
        val valid = mapOf("storagePath" to " notificationImages/3f2b8c1e-0d7a-4b8e-9f3a-333333333333.jpg ")
        assertEquals(
            "notificationImages/3f2b8c1e-0d7a-4b8e-9f3a-333333333333.jpg",
            NotificationPayloadParser.imageStoragePath(valid),
        )
        val invalid = listOf(
            null,
            "notificationImages/x.jpg",
            mapOf("url" to "https://storage.googleapis.com/b/notificationImages/x.jpg"),
            mapOf("storagePath" to "imagess/x.jpg"),
            mapOf("storagePath" to "notificationImages/x.png"),
            mapOf("storagePath" to "notificationImages/../imagess/x.jpg"),
            mapOf("storagePath" to "notificationImages/a/b.jpg"),
            mapOf("storagePath" to "notificationImages/.jpg"),
            mapOf("storagePath" to "notificationImages/${"a".repeat(129)}.jpg"),
            mapOf("storagePath" to 42),
        )
        invalid.forEach { assertNull("$it", NotificationPayloadParser.imageStoragePath(it)) }
        assertEquals("notificationImages/abc.jpg", NotificationImage.storagePath("abc"))
        assertTrue(NotificationImage.isValidStoragePath("notificationImages/${"a".repeat(128)}.jpg"))
    }

    @Test
    fun `inboxItem when the title is blank or the id unsafe drops the document`() {
        assertNull(NotificationPayloadParser.inboxItem("n-1", InboxSource.BROADCAST, mapOf("title" to "  ")))
        assertNull(NotificationPayloadParser.inboxItem("a/b", InboxSource.BROADCAST, mapOf("title" to "x")))
        assertNull(NotificationPayloadParser.inboxItem("", InboxSource.BROADCAST, mapOf("title" to "x")))
    }

    @Test
    fun `inboxItem when the server is newer keeps the entry without the unknown parts`() {
        val data = mapOf<String, Any?>("title" to "x", "kind" to "survey", "action" to mapOf("type" to "poll"))

        val item = NotificationPayloadParser.inboxItem("n-1", InboxSource.PERSONAL, data)

        assertEquals(NotificationKind.GENERAL, item?.kind)
        assertNull(item?.action)
    }

    @Test
    fun `action when exam needs a full path ending in the exam id and url must be https`() {
        val examData = mapOf<String, Any?>(
            "type" to "exam",
            "examId" to "u__f__d__c__vize",
            "path" to listOf("u", "u__f", "u__f__d", "u__f__d__c", "u__f__d__c__vize"),
            "names" to listOf("Üniv", "Fakülte", "Bölüm", "Ders", "Vize"),
            "examKind" to "PDF",
        )
        val exam = NotificationPayloadParser.action(examData)
        assertTrue(exam is NotificationAction.Exam)
        val target = (exam as NotificationAction.Exam).target
        assertEquals(ExamKind.PDF, target.kind)
        assertEquals("Vize", target.path.displayNames.last())

        assertNull(NotificationPayloadParser.action(examData + ("path" to listOf("u", "u__f__d__c__vize"))))
        assertNull(NotificationPayloadParser.action(mapOf("type" to "url", "url" to "http://example.com")))
        assertEquals(
            NotificationAction.Url("https://example.com/a"),
            NotificationPayloadParser.action(mapOf("type" to "url", "url" to "https://example.com/a")),
        )
        assertEquals(NotificationAction.Paywall, NotificationPayloadParser.action(mapOf("type" to "paywall")))
        assertEquals(NotificationAction.AddExam, NotificationPayloadParser.action(mapOf("type" to "addExam")))
    }

    @Test
    fun `action when the exam's names do not match the path falls back to the ids`() {
        val examData = mapOf<String, Any?>(
            "type" to "exam",
            "examId" to "e",
            "path" to listOf("u", "f", "d", "c", "e"),
            "names" to listOf("Üniv"),
            "examKind" to "JPG",
        )

        val target = (NotificationPayloadParser.action(examData) as NotificationAction.Exam).target

        assertEquals(ExamKind.IMAGES, target.kind)
        assertEquals(listOf("u", "f", "d", "c", "e"), target.path.displayNames)
        assertNull(NotificationPayloadParser.action(examData + ("examKind" to "DOC")))
        assertNull(NotificationPayloadParser.action(examData + ("path" to listOf("u", "f", "d", "c", 5))))
    }

    @Test
    fun `action when a location is out of range or has a flag for a coordinate is rejected`() {
        val outOfRange = mapOf("type" to "location", "latitude" to 95.0, "longitude" to 29.0, "name" to "x")
        val flagCoordinate = mapOf("type" to "location", "latitude" to true, "longitude" to 29.0, "name" to "x")
        val nanCoordinate = mapOf("type" to "location", "latitude" to Double.NaN, "longitude" to 29.0, "name" to "x")

        assertNull(NotificationPayloadParser.action(outOfRange))
        assertNull(NotificationPayloadParser.action(flagCoordinate))
        assertNull(NotificationPayloadParser.action(nanCoordinate))
    }

    // --- Admin ---

    @Test
    fun `facets parse into a university, faculty and department tree with counts`() {
        val value = mapOf(
            "total" to 10,
            "universities" to listOf(
                mapOf(
                    "id" to "bogazici-universitesi", "name" to "Boğaziçi Üniversitesi",
                    "count" to 10, "female" to 6, "male" to 4,
                    "faculties" to listOf(
                        mapOf(
                            "id" to "muhendislik-fakultesi", "name" to "Mühendislik Fakültesi",
                            "count" to 7, "female" to 4, "male" to 3,
                            "departments" to listOf(
                                mapOf("id" to "bilgisayar-muhendisligi", "name" to "Bilgisayar", "count" to 5, "female" to 3, "male" to 2),
                            ),
                        ),
                    ),
                ),
            ),
        )

        val facets = NotificationPayloadParser.facets(value)

        assertNotNull(facets)
        facets as AudienceFacets
        assertEquals(10, facets.total)
        assertEquals(listOf("muhendislik-fakultesi"), facets.faculties("bogazici-universitesi").map { it.id })
        assertEquals(3, facets.departments("bogazici-universitesi", "muhendislik-fakultesi").first().counts.female)
    }

    @Test
    fun `preview when counts arrive as Integer and Long reads 0 and 1 as counts`() {
        val preview = NotificationPayloadParser.preview(mapOf("recipients" to 1, "pushTargets" to 0L))

        assertEquals(NotificationPreview(recipients = 1, pushTargets = 0), preview)
        assertNull(NotificationPayloadParser.preview(mapOf("pushTargets" to 3)))
    }

    @Test
    fun `counts when negative, a flag or not a number read as zero`() {
        val preview = NotificationPayloadParser.preview(mapOf("recipients" to -4, "pushTargets" to true))

        assertEquals(NotificationPreview(recipients = 0, pushTargets = 0), preview)
    }

    @Test
    fun `sendResult needs a safe id and a known status`() {
        val value = mapOf(
            "notificationId" to "abc-123", "status" to "scheduled", "scheduledAt" to 1_761_717_600_000L, "recipients" to 5,
        )

        val result = NotificationPayloadParser.sendResult(value)

        assertEquals(NotificationStatus.SCHEDULED, result?.status)
        assertEquals(Instant.ofEpochSecond(1_761_717_600), result?.scheduledAt)
        assertEquals(5, result?.recipients)
        assertNull(NotificationPayloadParser.sendResult(mapOf("notificationId" to "abc", "status" to "queued")))
        assertNull(NotificationPayloadParser.sendResult(mapOf("notificationId" to "../x", "status" to "sent")))
    }

    @Test
    fun `sentNotification parses its segment audience, source and stats`() {
        val data = mapOf<String, Any?>(
            "kind" to "general", "title" to "Duyuru", "body" to "Metin", "status" to "sent", "source" to "automatic",
            "audience" to mapOf("scope" to "segment", "universityId" to "u", "facultyId" to "f", "gender" to "female"),
            "audienceLabel" to "Üniv › Fakülte · Kadın",
            "stats" to mapOf("recipients" to 3, "opened" to 2),
        )

        val row = NotificationPayloadParser.sentNotification("n-1", data)

        assertNotNull(row)
        row as SentNotification
        assertTrue(row.isAutomatic)
        assertEquals(
            NotificationAudience.Segment(NotificationSegment(universityId = "u", facultyId = "f", gender = Gender.FEMALE)),
            row.audience,
        )
        assertEquals(3, row.stats.recipients)
        assertEquals(2, row.stats.opened)
        assertEquals(0, row.stats.pushSent)
        assertFalse(row.isTest)
        assertNull(NotificationPayloadParser.sentNotification("n-1", data - "audience"))
    }

    // --- Push ---

    @Test
    fun `notificationIdFromPush yields the id and nil without one or with an unsafe one`() {
        assertEquals("abc-123", NotificationPayloadParser.notificationIdFromPush(mapOf("notificationId" to "abc-123")))
        assertNull(NotificationPayloadParser.notificationIdFromPush(mapOf("google.message_id" to "0:1")))
        assertNull(NotificationPayloadParser.notificationIdFromPush(mapOf("notificationId" to "../x")))
        assertNull(NotificationPayloadParser.notificationIdFromPush(mapOf("notificationId" to 42)))
    }

    // --- Values ---

    @Test
    fun `date accepts an Instant, a Date or epoch milliseconds and nothing else`() {
        val instant = Instant.ofEpochMilli(1_761_700_000_123)

        assertEquals(instant, NotificationPayloadParser.date(instant))
        assertEquals(instant, NotificationPayloadParser.date(Date.from(instant)))
        assertEquals(instant, NotificationPayloadParser.date(1_761_700_000_123L))
        assertEquals(instant, NotificationPayloadParser.date(1_761_700_000_123.0))
        assertNull(NotificationPayloadParser.date(true))
        assertNull(NotificationPayloadParser.date("2026-10-30T10:00:00Z"))
        assertNull(NotificationPayloadParser.date(null))
    }
}
