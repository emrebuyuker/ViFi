package com.salticusteam.vifi.feature.notificationcomposer

import com.salticusteam.vifi.core.data.files.SubmissionJpegEncoder
import com.salticusteam.vifi.core.data.notifications.admin.EncodedNotificationImage
import com.salticusteam.vifi.core.model.NotificationAdminError
import com.salticusteam.vifi.core.model.NotificationDraft
import com.salticusteam.vifi.core.model.NotificationImage
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * iOS `NotificationComposerAttachmentTests` (13): the campaign image (encoded off the main thread, uploaded under the
 * notification's own id right before it is sent, never replaced) and the exam action (picked from the archive). The
 * Bitmap side of `downsamples` runs on device (`ContentResolverNotificationImageEncoderTest`).
 */
class NotificationComposerAttachmentTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixture = NotificationComposerFixture()

    /** A ready form with an image picked. */
    private fun TestScope.readyWithImage(service: StubNotificationAdminService = StubNotificationAdminService()) =
        fixture.readyViewModel(service).also {
            it.onImagePicked("content://photos/1")
            advanceUntilIdle()
        }

    private fun TestScope.send(viewModel: NotificationComposerViewModel) {
        viewModel.requestSend()
        advanceUntilIdle()
        viewModel.confirmSend()
        advanceUntilIdle()
    }

    // --- Exam action ---

    @Test
    fun `the exam action needs a picked exam, then sends that exam's id`() = runTest {
        val viewModel = fixture.readyViewModel()
        viewModel.onActionChoiceChange(ActionChoice.EXAM)

        assertEquals(NotificationDraft.Issue.EMPTY_EXAM, viewModel.uiState.value.issue)
        assertFalse(viewModel.uiState.value.canSend)
        assertFalse(viewModel.uiState.value.canSendTest)

        viewModel.onExamPicked(NotificationComposerFixture.EXAM)

        val draft = viewModel.uiState.value.draft
        assertNull(viewModel.uiState.value.issue)
        assertEquals(NotificationDraft.Action.Exam("exam-42"), draft.action)
        assertEquals(mapOf("type" to "exam", "examId" to "exam-42"), draft.payload()["action"])
        assertEquals("Not a campaign by itself", NotificationKind.GENERAL, draft.effectiveKind)
    }

    @Test
    fun `another action drops the picked exam from the draft`() = runTest {
        val viewModel = fixture.readyViewModel()
        viewModel.onActionChoiceChange(ActionChoice.EXAM)
        viewModel.onExamPicked(NotificationComposerFixture.EXAM)

        viewModel.onActionChoiceChange(ActionChoice.PAYWALL)

        assertEquals(NotificationDraft.Action.Paywall, viewModel.uiState.value.draft.action)
    }

    @Test
    fun `a sent exam notification resets the picked exam`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = fixture.readyViewModel(service)
        viewModel.onActionChoiceChange(ActionChoice.EXAM)
        viewModel.onExamPicked(NotificationComposerFixture.EXAM)

        send(viewModel)

        assertEquals(NotificationDraft.Action.Exam("exam-42"), service.sentDrafts.single().action)
        assertNull(viewModel.uiState.value.selectedExam)
        assertEquals(ActionChoice.NONE, viewModel.uiState.value.actionChoice)
    }

    // --- Image preparation ---

    @Test
    fun `a picked image is encoded within 1600 px and becomes the image to send`() = runTest {
        val viewModel = fixture.readyViewModel()
        val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1)
        fixture.encoder.results["content://photos/big"] = EncodedNotificationImage.Jpeg(jpeg)

        viewModel.onImagePicked("content://photos/big")
        assertTrue(viewModel.uiState.value.isPreparingImage)
        advanceUntilIdle()

        assertArrayEquals(jpeg, viewModel.uiState.value.image?.jpeg)
        assertFalse(viewModel.uiState.value.isPreparingImage)
        assertEquals(listOf("content://photos/big"), fixture.encoder.requests)
        // The sizing the encoder applies: 2400×1200 comes down to 1600×800, never up.
        assertEquals(1600 to 800, SubmissionJpegEncoder.targetSize(2400, 1200, NotificationImage.MAX_PIXEL_SIZE))
        assertEquals(800 to 600, SubmissionJpegEncoder.targetSize(800, 600, NotificationImage.MAX_PIXEL_SIZE))
    }

    @Test
    fun `data that is not an image is reported and the previous image stays`() = runTest {
        val viewModel = readyWithImage()
        val previous = viewModel.uiState.value.image
        fixture.encoder.results["content://photos/bad"] = EncodedNotificationImage.Unusable

        viewModel.onImagePicked("content://photos/bad")
        advanceUntilIdle()

        assertSame(previous, viewModel.uiState.value.image)
        assertEquals(ComposerImageError.UNUSABLE, viewModel.uiState.value.imageError)
        assertFalse(viewModel.uiState.value.isPreparingImage)
    }

    @Test
    fun `removing the image sends the notification without one`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = readyWithImage(service)
        viewModel.onImageRemoved()

        send(viewModel)

        assertTrue(service.uploadRequests.isEmpty())
        assertNull(service.sentDrafts.single().imagePath)
    }

    // --- Upload on send ---

    @Test
    fun `the image is uploaded under the notification's id only after the admin confirms`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = readyWithImage(service)
        val id = viewModel.uiState.value.draftId

        viewModel.requestSend()
        advanceUntilIdle()
        assertTrue("Nothing is uploaded before the admin confirms", service.uploadRequests.isEmpty())
        viewModel.confirmSend()
        advanceUntilIdle()

        assertEquals(listOf(id), service.uploadRequests)
        val sent = service.sentDrafts.single()
        assertEquals(id, sent.id)
        assertEquals("notificationImages/$id.jpg", sent.imagePath)
        assertEquals("notificationImages/$id.jpg", sent.payload()["imagePath"])
        assertNull("The next notification starts without the image", viewModel.uiState.value.image)
    }

    @Test
    fun `declining the confirmation uploads nothing`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = readyWithImage(service)
        viewModel.requestSend()
        advanceUntilIdle()

        viewModel.cancelConfirmation()

        assertTrue(service.uploadRequests.isEmpty())
        assertNotNull(viewModel.uiState.value.image)
    }

    @Test
    fun `a failed upload sends nothing, the retry uploads under the same id and sends`() = runTest {
        val service = StubNotificationAdminService()
        service.uploadError = NotificationAdminError.ImageUploadFailed
        val viewModel = readyWithImage(service)
        val id = viewModel.uiState.value.draftId

        send(viewModel)

        assertTrue(service.sentDrafts.isEmpty())
        assertEquals(
            ComposerError.Message(NotificationAdminError.ImageUploadFailed.userMessage),
            viewModel.uiState.value.error,
        )
        assertEquals(id, viewModel.uiState.value.draftId)
        assertNotNull(viewModel.uiState.value.image)

        service.uploadError = null
        send(viewModel)

        assertEquals(listOf(id, id), service.uploadRequests)
        assertEquals("notificationImages/$id.jpg", service.sentDrafts.single().imagePath)
    }

    @Test
    fun `a send that fails after the upload is retried under the same id, reusing the uploaded image`() = runTest {
        val service = StubNotificationAdminService()
        service.sendResult = Result.failure(NotificationAdminError.Offline)
        val viewModel = readyWithImage(service)
        val id = viewModel.uiState.value.draftId

        send(viewModel)
        assertNotNull(viewModel.uiState.value.error)

        service.sendResult = null
        send(viewModel)

        assertEquals(listOf(id, id), service.sentDrafts.map { it.id })
        assertEquals("The image is stored once; the retry does not replace it", 1, service.storedImages.size)
        assertNotNull(viewModel.uiState.value.success)
    }

    @Test
    fun `a different image after an upload starts a new id, before any upload the id stays`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = readyWithImage(service)
        val firstId = viewModel.uiState.value.draftId

        viewModel.onImagePicked("content://photos/2")
        advanceUntilIdle()
        assertEquals("Nothing was uploaded under it yet", firstId, viewModel.uiState.value.draftId)

        service.sendResult = Result.failure(NotificationAdminError.Offline)
        send(viewModel)
        assertEquals(listOf(firstId), service.uploadRequests)

        viewModel.onImagePicked("content://photos/3")
        advanceUntilIdle()
        val secondId = viewModel.uiState.value.draftId
        assertNotEquals("The uploaded image of the first id can never be replaced", firstId, secondId)

        send(viewModel)
        viewModel.onImageRemoved()
        assertNotEquals(secondId, viewModel.uiState.value.draftId)
    }

    @Test
    fun `a test send uploads the image under its own fresh id`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = readyWithImage(service)
        val draftId = viewModel.uiState.value.draftId

        viewModel.sendTest()
        advanceUntilIdle()

        val sent = service.sentDrafts.single()
        assertTrue(sent.isTest)
        assertNotEquals(draftId, sent.id)
        assertEquals(listOf(sent.id), service.uploadRequests)
        assertEquals("notificationImages/${sent.id}.jpg", sent.imagePath)
        // The real notification keeps its id and image.
        assertEquals(draftId, viewModel.uiState.value.draftId)
        assertNotNull(viewModel.uiState.value.image)
    }

    @Test
    fun `a draft's image must be its own upload`() {
        val draft = NotificationDraft(id = "n-1", title = "Başlık", body = "Mesaj", imagePath = "notificationImages/n-1.jpg")
        assertNull(draft.validationIssue())
        assertEquals(
            NotificationDraft.Issue.INVALID_IMAGE,
            draft.copy(imagePath = "notificationImages/other.jpg").validationIssue(),
        )
    }

    // --- Android (ADM-04) ---

    @Test
    fun `a slow earlier encode never replaces a later pick`() = runTest {
        val viewModel = fixture.readyViewModel()
        val slow = CompletableDeferred<Unit>()
        fixture.encoder.gates["content://photos/slow"] = slow

        viewModel.onImagePicked("content://photos/slow")
        runCurrent()
        viewModel.onImagePicked("content://photos/fast")
        advanceUntilIdle()
        slow.complete(Unit)
        advanceUntilIdle()

        assertArrayEquals("content://photos/fast".toByteArray(), viewModel.uiState.value.image?.jpeg)
        assertFalse(viewModel.uiState.value.isPreparingImage)
    }

    @Test
    fun `an image being prepared blocks sending until it is ready`() = runTest {
        val viewModel = fixture.readyViewModel()
        val gate = CompletableDeferred<Unit>()
        fixture.encoder.gates["content://photos/1"] = gate

        viewModel.onImagePicked("content://photos/1")
        runCurrent()
        assertFalse(viewModel.uiState.value.canSend)
        assertFalse(viewModel.uiState.value.canSendTest)

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.canSend)
    }

    @Test
    fun `an unreadable photo is reported and removing clears the message`() = runTest {
        val viewModel = fixture.readyViewModel()
        fixture.encoder.results["content://photos/gone"] = EncodedNotificationImage.Unreadable

        viewModel.onImagePicked("content://photos/gone")
        advanceUntilIdle()
        assertEquals(ComposerImageError.UNREADABLE, viewModel.uiState.value.imageError)
        assertNull(viewModel.uiState.value.image)

        viewModel.onImageRemoved()
        assertNull(viewModel.uiState.value.imageError)
    }
}
