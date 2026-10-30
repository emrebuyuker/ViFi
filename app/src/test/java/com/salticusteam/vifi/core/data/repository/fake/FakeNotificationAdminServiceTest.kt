package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.model.Gender
import com.salticusteam.vifi.core.model.NotificationAdminError
import com.salticusteam.vifi.core.model.NotificationAudience
import com.salticusteam.vifi.core.model.NotificationDraft
import com.salticusteam.vifi.core.model.NotificationImage
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationSegment
import com.salticusteam.vifi.core.model.NotificationStatus
import java.time.Instant
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The admin fake backs the mock flavor and every composer test, so its server-like behaviour is pinned here. */
class FakeNotificationAdminServiceTest {

    private val now = Instant.ofEpochSecond(1_761_700_000)
    private val draft = NotificationDraft(id = "d-1", title = "Başlık", body = "Mesaj")

    @Test
    fun `send when repeated with the same id returns the first result and lists it once`() = runTest {
        val service = FakeNotificationAdminService(now = { now })

        val first = service.send(draft)
        val second = service.send(draft.copy(title = "Başka"))

        assertEquals(first, second)
        assertEquals(NotificationStatus.SENT, first.status)
        assertEquals(64, first.recipients)
        assertEquals(1, service.history().count { it.id == "d-1" })
        assertEquals("d-1", service.history().first().id)
    }

    @Test
    fun `send when scheduled reports no recipients yet and can then be canceled once`() = runTest {
        val service = FakeNotificationAdminService(now = { now })

        val result = service.send(draft.copy(scheduledAt = now.plusSeconds(3600)))
        service.cancel("d-1")

        assertEquals(NotificationStatus.SCHEDULED, result.status)
        assertEquals(0, result.recipients)
        assertEquals(NotificationStatus.CANCELED, service.history().first().status)
        try {
            service.cancel("d-1")
            fail("A canceled notification cannot be canceled again")
        } catch (error: NotificationAdminError.NotCancelable) {
            assertEquals(listOf("d-1"), service.canceledIds)
        }
    }

    @Test
    fun `preview when a campaign reaches about half of the audience`() = runTest {
        val service = FakeNotificationAdminService()
        val segment = NotificationAudience.Segment(NotificationSegment("bogazici-universitesi", "muhendislik-fakultesi"))

        assertEquals(28, service.preview(NotificationKind.GENERAL, segment).recipients)
        assertEquals(14, service.preview(NotificationKind.PROMO, segment).recipients)
        assertEquals(2, service.previewRequests.size)
    }

    @Test
    fun `uploadImage when repeated for an id keeps the first image like the Storage rules`() = runTest {
        val service = FakeNotificationAdminService()

        val path = service.uploadImage(byteArrayOf(1), "d-1")
        service.uploadImage(byteArrayOf(2), "d-1")

        assertEquals(NotificationImage.storagePath("d-1"), path)
        assertArrayEquals(byteArrayOf(1), service.uploadedImages.getValue(path))
        assertEquals(2, service.uploadCount)
    }

    @Test
    fun `history labels a segment the way the server does`() = runTest {
        val service = FakeNotificationAdminService(now = { now })
        val segment = NotificationSegment(
            universityId = "bogazici-universitesi",
            facultyId = "muhendislik-fakultesi",
            departmentId = "bilgisayar-muhendisligi",
            gender = Gender.FEMALE,
        )

        service.send(draft.copy(audience = NotificationAudience.Segment(segment)))

        assertEquals(
            "Boğaziçi Üniversitesi › Mühendislik Fakültesi › Bilgisayar Mühendisliği · Kadın",
            service.history().first().audienceLabel,
        )
    }

    @Test
    fun `a failure set for a call is thrown by that call`() = runTest {
        val service = FakeNotificationAdminService()
        service.sendFailure = NotificationAdminError.Offline

        try {
            service.send(draft)
            fail("Expected the send to fail")
        } catch (error: NotificationAdminError.Offline) {
            assertTrue(service.history().none { it.id == "d-1" })
        }
    }
}
