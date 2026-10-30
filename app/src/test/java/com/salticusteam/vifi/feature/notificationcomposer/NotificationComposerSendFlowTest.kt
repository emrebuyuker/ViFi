package com.salticusteam.vifi.feature.notificationcomposer

import com.salticusteam.vifi.core.model.Gender
import com.salticusteam.vifi.core.model.NotificationAdminError
import com.salticusteam.vifi.core.model.NotificationAudience
import com.salticusteam.vifi.core.model.NotificationDraft.Issue
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationPreview
import com.salticusteam.vifi.core.model.NotificationSegment
import com.salticusteam.vifi.core.model.NotificationSendResult
import com.salticusteam.vifi.core.model.NotificationStatus
import com.salticusteam.vifi.testing.MainDispatcherRule
import java.time.Duration
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** iOS `NotificationComposerSendFlowTests` (15): preview, confirmation, send, retries and the test send. */
class NotificationComposerSendFlowTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixture = NotificationComposerFixture()

    private fun TestScope.send(viewModel: NotificationComposerViewModel) {
        viewModel.requestSend()
        advanceUntilIdle()
        viewModel.confirmSend()
        advanceUntilIdle()
    }

    // --- Send flow ---

    @Test
    fun `send previews first and sends only after confirmation`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = fixture.readyViewModel(service)
        service.previewResult = Result.success(NotificationPreview(recipients = 12, pushTargets = 15))
        advanceUntilIdle()
        viewModel.onKindSelected(NotificationKind.PROMO)
        viewModel.onAudienceModeChange(AudienceMode.SEGMENT)
        viewModel.onUniversitySelected("sakarya")
        viewModel.onGenderSelected(Gender.FEMALE)
        val idBefore = viewModel.uiState.value.draftId

        viewModel.requestSend()
        advanceUntilIdle()

        val (previewedKind, previewedAudience) = service.previewRequests.single()
        assertEquals(NotificationKind.PROMO, previewedKind)
        assertEquals(
            NotificationAudience.Segment(NotificationSegment("sakarya", gender = Gender.FEMALE)),
            previewedAudience,
        )
        assertTrue(viewModel.uiState.value.isConfirming)
        assertEquals(NotificationPreview(12, 15), viewModel.uiState.value.confirmation?.preview)
        assertTrue("Nothing goes out before the admin confirms", service.sentDrafts.isEmpty())

        viewModel.confirmSend()
        advanceUntilIdle()

        val sent = service.sentDrafts.single()
        assertEquals(idBefore, sent.id)
        assertEquals("Başlık", sent.title)
        assertEquals(NotificationKind.PROMO, sent.kind)
        assertFalse(sent.isTest)
        with(viewModel.uiState.value) {
            assertEquals("An immediate send reports its recipients", ComposerSuccess.Sent(0), success)
            assertNull(error)
            assertNull(confirmation)
            assertFalse(isWorking)
            // The form is ready for the next notification, under a new id.
            assertNotEquals(idBefore, draftId)
            assertTrue(title.isEmpty())
            assertTrue(body.isEmpty())
            assertEquals(AudienceMode.EVERYONE, audienceMode)
            assertNull(universityId)
            assertEquals(NotificationKind.GENERAL, kind)
            assertEquals("The facets are kept", NotificationComposerFixture.FACETS, facets)
        }
    }

    @Test
    fun `declining the confirmation sends nothing and keeps the form`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = fixture.readyViewModel(service)
        viewModel.requestSend()
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isConfirming)

        viewModel.cancelConfirmation()
        viewModel.confirmSend()
        advanceUntilIdle()

        assertTrue(service.sentDrafts.isEmpty())
        assertFalse(viewModel.uiState.value.isConfirming)
        assertNull(viewModel.uiState.value.confirmation)
        assertEquals("Başlık", viewModel.uiState.value.title)
    }

    @Test
    fun `confirming takes the dialog down at once and still sends what was confirmed`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = fixture.readyViewModel(service)
        viewModel.requestSend()
        advanceUntilIdle()

        viewModel.confirmSend()
        assertFalse("The dialog goes away before the send resolves", viewModel.uiState.value.isConfirming)
        advanceUntilIdle()

        assertEquals(1, service.sentDrafts.size)
    }

    @Test
    fun `a send the form cannot validate never reaches the server`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = fixture.makeViewModel(service)

        viewModel.requestSend()
        viewModel.confirmSend()
        viewModel.sendTest()
        advanceUntilIdle()

        assertTrue(service.previewRequests.isEmpty())
        assertTrue(service.sentDrafts.isEmpty())
        assertFalse(viewModel.uiState.value.isConfirming)
    }

    @Test
    fun `a scheduled send reports when it goes out`() = runTest {
        val service = StubNotificationAdminService()
        val goesOut = fixture.clock.now + Duration.ofHours(2)
        // Like the server: a scheduled send reports 0 recipients, as the audience is only resolved at delivery.
        service.sendResult = Result.success(NotificationSendResult("n", NotificationStatus.SCHEDULED, goesOut, 0))
        val viewModel = fixture.readyViewModel(service)
        viewModel.onSchedulingToggle(true)

        send(viewModel)

        assertEquals(fixture.clock.now + Duration.ofHours(1), service.sentDrafts.single().scheduledAt)
        assertEquals(
            "The audience is resolved at delivery, so a scheduled send has no recipient count to report",
            ComposerSuccess.Scheduled(goesOut),
            viewModel.uiState.value.success,
        )
        assertNull("The form is reset", viewModel.uiState.value.scheduledAt)
    }

    @Test
    fun `a failed send keeps the form and the id, so the retry is the same send`() = runTest {
        val service = StubNotificationAdminService()
        service.sendResult = Result.failure(NotificationAdminError.Offline)
        val viewModel = fixture.readyViewModel(service)
        val id = viewModel.uiState.value.draftId

        send(viewModel)

        with(viewModel.uiState.value) {
            assertEquals(ComposerError.Message(NotificationAdminError.Offline.userMessage), error)
            assertNull(success)
            assertEquals(id, draftId)
            assertEquals("The form is kept for the retry", "Başlık", title)
            assertNull(confirmation)
            assertTrue(canSend)
        }

        service.sendResult = null
        send(viewModel)

        assertEquals(listOf(id, id), service.sentDrafts.map { it.id })
        assertNotNull(viewModel.uiState.value.success)
        assertNull(viewModel.uiState.value.error)
        assertNotEquals("A completed send moves on to a new id", id, viewModel.uiState.value.draftId)
    }

    @Test
    fun `a server rejection shows the server's message`() = runTest {
        val service = StubNotificationAdminService()
        service.sendResult = Result.failure(NotificationAdminError.InvalidDraft("Görsel henüz desteklenmiyor."))
        val viewModel = fixture.readyViewModel(service)

        send(viewModel)

        assertEquals(ComposerError.Message("Görsel henüz desteklenmiyor."), viewModel.uiState.value.error)
    }

    @Test
    fun `a failed record is not reused, the next try is a new notification`() = runTest {
        val service = StubNotificationAdminService()
        service.sendResult = Result.success(NotificationSendResult("n", NotificationStatus.FAILED, null, 0))
        val viewModel = fixture.readyViewModel(service)
        val id = viewModel.uiState.value.draftId

        send(viewModel)

        with(viewModel.uiState.value) {
            assertEquals(ComposerError.SendFailed, error)
            assertNull(success)
            assertNotEquals(id, draftId)
            assertEquals("Başlık", title)
        }
    }

    @Test
    fun `a failed preview reports the error and confirms nothing`() = runTest {
        val service = StubNotificationAdminService()
        service.previewResult = Result.failure(NotificationAdminError.PermissionDenied)
        val viewModel = fixture.readyViewModel(service)

        viewModel.requestSend()
        advanceUntilIdle()

        with(viewModel.uiState.value) {
            assertEquals(ComposerError.Message(NotificationAdminError.PermissionDenied.userMessage), error)
            assertFalse(isConfirming)
            assertNull(confirmation)
            assertFalse(isWorking)
        }
        assertTrue(service.sentDrafts.isEmpty())
    }

    @Test
    fun `a second tap while a request is in flight is ignored`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = fixture.readyViewModel(service)
        advanceUntilIdle()
        val gate = CompletableDeferred<Unit>()
        service.gate = gate

        viewModel.requestSend()
        runCurrent()
        assertTrue(viewModel.uiState.value.isWorking)
        assertFalse(viewModel.uiState.value.canSend)
        assertFalse(viewModel.uiState.value.canSendTest)

        viewModel.requestSend()
        viewModel.sendTest()
        runCurrent()
        assertEquals(1, service.previewRequests.size)
        assertTrue(service.sentDrafts.isEmpty())

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isWorking)
        assertTrue(viewModel.uiState.value.isConfirming)
    }

    // --- Test send ---

    @Test
    fun `the test goes to the admin only, under its own id, and leaves the form alone`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = fixture.readyViewModel(service)
        advanceUntilIdle()
        viewModel.onAudienceModeChange(AudienceMode.SEGMENT)
        viewModel.onUniversitySelected("sakarya")
        viewModel.onSchedulingToggle(true)
        viewModel.onExpiryToggle(true)
        val id = viewModel.uiState.value.draftId

        viewModel.sendTest()
        advanceUntilIdle()
        viewModel.sendTest()
        advanceUntilIdle()

        assertTrue("A test needs no confirmation", service.previewRequests.isEmpty())
        assertEquals(2, service.sentDrafts.size)
        val (first, second) = service.sentDrafts
        assertTrue(first.isTest)
        assertNotEquals("The real draft's id is not used up", id, first.id)
        assertNotEquals(first.id, second.id)
        assertNull(first.scheduledAt)
        assertNull(first.expiresAt)
        with(viewModel.uiState.value) {
            assertEquals(id, draftId)
            assertEquals(ComposerSuccess.TestSent, success)
            assertEquals("The form is kept", "Başlık", title)
            assertEquals(AudienceMode.SEGMENT, audienceMode)
            assertNotNull(scheduledAt)
        }
    }

    @Test
    fun `a test does not need a finished audience`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = fixture.readyViewModel(service)
        viewModel.onAudienceModeChange(AudienceMode.SEGMENT)
        assertEquals(Issue.INVALID_AUDIENCE, viewModel.uiState.value.issue)
        assertFalse(viewModel.uiState.value.canSend)
        assertTrue(viewModel.uiState.value.canSendTest)

        viewModel.sendTest()
        advanceUntilIdle()

        val draft = service.sentDrafts.single()
        assertEquals(NotificationAudience.All, draft.audience)
        assertTrue(draft.isTest)
    }

    @Test
    fun `a test still needs its content`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = fixture.makeViewModel(service)
        viewModel.onTitleChange("Başlık")

        viewModel.sendTest()
        advanceUntilIdle()

        assertTrue(service.sentDrafts.isEmpty())
    }

    @Test
    fun `a failed test reports the error`() = runTest {
        val service = StubNotificationAdminService()
        service.sendResult = Result.failure(NotificationAdminError.Offline)
        val viewModel = fixture.readyViewModel(service)

        viewModel.sendTest()
        advanceUntilIdle()

        assertEquals(ComposerError.Message(NotificationAdminError.Offline.userMessage), viewModel.uiState.value.error)
        assertNull(viewModel.uiState.value.success)
    }

    @Test
    fun `a campaign is previewed and sent as a campaign`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = fixture.readyViewModel(service)
        viewModel.onKindSelected(NotificationKind.PROMO)

        send(viewModel)

        assertEquals(NotificationKind.PROMO, service.previewRequests.first().first)
        assertEquals("promo", service.sentDrafts.single().payload()["kind"])
    }

    // --- Android ---

    @Test
    fun `a time that slipped into the past is caught when the admin taps send`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = fixture.readyViewModel(service)
        viewModel.onSchedulingToggle(true)
        assertTrue(viewModel.uiState.value.canSend)

        fixture.clock.advance(Duration.ofHours(2))
        viewModel.requestSend()
        advanceUntilIdle()

        assertTrue(service.previewRequests.isEmpty())
        assertEquals(Issue.SCHEDULE_IN_PAST, viewModel.uiState.value.issue)
    }
}
