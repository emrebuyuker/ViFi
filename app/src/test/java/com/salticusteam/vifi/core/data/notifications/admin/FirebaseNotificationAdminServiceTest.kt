package com.salticusteam.vifi.core.data.notifications.admin

import com.google.android.gms.tasks.Tasks
import com.google.firebase.Timestamp
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.HttpsCallableReference
import com.google.firebase.functions.HttpsCallableResult
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.StorageReference
import com.google.firebase.storage.UploadTask
import com.salticusteam.vifi.core.data.notifications.admin.FirebaseNotificationAdminService.Companion.adminError
import com.salticusteam.vifi.core.data.notifications.admin.FirebaseNotificationAdminService.Companion.callableError
import com.salticusteam.vifi.core.data.notifications.admin.FirebaseNotificationAdminService.Companion.firestoreError
import com.salticusteam.vifi.core.data.notifications.admin.FirebaseNotificationAdminService.Companion.imageError
import com.salticusteam.vifi.core.data.notifications.admin.FirebaseNotificationAdminService.Companion.isObjectNotFound
import com.salticusteam.vifi.core.data.notifications.admin.FirebaseNotificationAdminService.Companion.storageError
import com.salticusteam.vifi.core.model.GENERIC_ERROR_MESSAGE
import com.salticusteam.vifi.core.model.Gender
import com.salticusteam.vifi.core.model.NotificationAdminError
import com.salticusteam.vifi.core.model.NotificationAudience
import com.salticusteam.vifi.core.model.NotificationDraft
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationPreview
import com.salticusteam.vifi.core.model.NotificationSegment
import com.salticusteam.vifi.core.model.NotificationStatus
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * iOS `FirebaseNotificationAdminServiceTests` (8) on the JVM-testable mapping (the SDKs' code enums need Android;
 * their bridging by name is checked on device), plus the calls' wiring against mocked SDK objects. Nothing here
 * reaches a real backend.
 */
class FirebaseNotificationAdminServiceTest {

    // --- Error mapping (iOS FirebaseNotificationAdminServiceTests) ---

    @Test
    fun `permission and sign-in failures mean the user is not an admin`() {
        for (status in listOf(FirebaseStatus.PERMISSION_DENIED, FirebaseStatus.UNAUTHENTICATED)) {
            assertSame(NotificationAdminError.PermissionDenied, callableError(status, "Sunucu mesajı", false))
        }
    }

    @Test
    fun `a refused draft carries the server's Turkish message`() {
        assertEquals(
            NotificationAdminError.InvalidDraft("Başlık en fazla 80 karakter olabilir."),
            callableError(FirebaseStatus.INVALID_ARGUMENT, "Başlık en fazla 80 karakter olabilir.", false),
        )
        val missingImage = "Görsel bulunamadı. Görseli yeniden seçip tekrar dene."
        assertEquals(
            NotificationAdminError.InvalidDraft(missingImage),
            callableError(FirebaseStatus.FAILED_PRECONDITION, missingImage, false),
        )
        assertEquals(missingImage, NotificationAdminError.InvalidDraft(missingImage).userMessage)
    }

    @Test
    fun `a missing object is told apart from other Storage failures`() {
        assertTrue(isObjectNotFound(StorageException.ERROR_OBJECT_NOT_FOUND))
        assertFalse(isObjectNotFound(StorageException.ERROR_NOT_AUTHORIZED))
        assertFalse(isObjectNotFound(IllegalStateException("not found")))
    }

    @Test
    fun `a failed image upload is a permission, connectivity or upload failure`() {
        assertSame(NotificationAdminError.PermissionDenied, storageError(StorageException.ERROR_NOT_AUTHORIZED))
        assertSame(NotificationAdminError.PermissionDenied, storageError(StorageException.ERROR_NOT_AUTHENTICATED))
        assertSame(NotificationAdminError.Offline, storageError(StorageException.ERROR_RETRY_LIMIT_EXCEEDED))
        assertSame(NotificationAdminError.Offline, imageError(IOException("timeout")))
        assertNull(storageError(StorageException.ERROR_UNKNOWN))
        assertSame(NotificationAdminError.ImageUploadFailed, imageError(IllegalStateException("unknown")))
    }

    @Test
    fun `a failed precondition on cancel means it already went out`() {
        assertSame(NotificationAdminError.NotCancelable, callableError(FirebaseStatus.FAILED_PRECONDITION, "Sunucu mesajı", true))
        // Other errors on cancel keep their normal mapping.
        assertSame(NotificationAdminError.PermissionDenied, callableError(FirebaseStatus.PERMISSION_DENIED, null, true))
    }

    @Test
    fun `connectivity problems read as offline`() {
        assertSame(NotificationAdminError.Offline, callableError(FirebaseStatus.UNAVAILABLE, null, false))
        assertSame(NotificationAdminError.Offline, callableError(FirebaseStatus.DEADLINE_EXCEEDED, null, false))
        assertSame(NotificationAdminError.Offline, firestoreError(FirebaseStatus.UNAVAILABLE))
        assertSame(NotificationAdminError.Offline, adminError(IOException("no route to host")))
    }

    @Test
    fun `reading the history without the admin claim is a permission error`() {
        assertSame(NotificationAdminError.PermissionDenied, firestoreError(FirebaseStatus.PERMISSION_DENIED))
        assertSame(NotificationAdminError.PermissionDenied, firestoreError(FirebaseStatus.UNAUTHENTICATED))
    }

    @Test
    fun `anything else is unknown, without the underlying technical message`() {
        assertNull(callableError(FirebaseStatus.OTHER, "INTERNAL", false))
        val error = adminError(IllegalStateException("Beklenmeyen"))
        assertSame(NotificationAdminError.Unknown, error)
        assertEquals(GENERIC_ERROR_MESSAGE, error.userMessage)
        assertNotEquals(NotificationAdminError.Offline, firestoreError(FirebaseStatus.OTHER))
    }

    // --- Android additions ---

    @Test
    fun `SDK code names map to the shared statuses and anything else to other`() {
        assertEquals(FirebaseStatus.PERMISSION_DENIED, FirebaseStatus.named("PERMISSION_DENIED"))
        assertEquals(FirebaseStatus.FAILED_PRECONDITION, FirebaseStatus.named("FAILED_PRECONDITION"))
        assertEquals(FirebaseStatus.DEADLINE_EXCEEDED, FirebaseStatus.named("DEADLINE_EXCEEDED"))
        assertEquals(FirebaseStatus.OTHER, FirebaseStatus.named("INTERNAL"))
        assertEquals(FirebaseStatus.OTHER, FirebaseStatus.named("NOT_FOUND"))
        assertEquals(FirebaseStatus.OTHER, FirebaseStatus.named(""))
    }

    @Test
    fun `a refusal without a readable message is unknown rather than blank`() {
        assertNull(callableError(FirebaseStatus.INVALID_ARGUMENT, null, false))
        assertNull(callableError(FirebaseStatus.FAILED_PRECONDITION, "  ", false))
    }

    @Test
    fun `an admin error already mapped passes through`() {
        assertSame(NotificationAdminError.NotCancelable, adminError(NotificationAdminError.NotCancelable))
        assertSame(NotificationAdminError.ImageUploadFailed, imageError(NotificationAdminError.ImageUploadFailed))
    }

    @Test
    fun `only a non-empty JPEG within 1 MiB under a safe id is uploadable`() {
        assertTrue(FirebaseNotificationAdminService.isUploadable(byteArrayOf(1), "n-1"))
        assertFalse(FirebaseNotificationAdminService.isUploadable(ByteArray(0), "n-1"))
        assertFalse(FirebaseNotificationAdminService.isUploadable(ByteArray(1024 * 1024 + 1), "n-1"))
        assertFalse(FirebaseNotificationAdminService.isUploadable(byteArrayOf(1), "../other"))
    }

    @Test
    fun `history dates are read from Firestore timestamps`() {
        val data = FirebaseNotificationAdminService.readingDates(
            mapOf("createdAt" to Timestamp(1_761_000_000, 500), "title" to "Başlık", "count" to 3L),
        )

        assertEquals(Instant.ofEpochSecond(1_761_000_000, 500), data["createdAt"])
        assertEquals("Başlık", data["title"])
        assertEquals(3L, data["count"])
    }

    // --- Calls (mocked SDK objects) ---

    private val functions = mockk<FirebaseFunctions>()
    private val firestore = mockk<FirebaseFirestore>()
    private val storage = mockk<FirebaseStorage>()
    private val service = FirebaseNotificationAdminService(functions, firestore, storage)

    /** Answers [reference]'s calls with [result]; returns the data each call was made with. */
    private fun answerCalls(reference: HttpsCallableReference, result: Any?): MutableList<Any?> {
        val sent = mutableListOf<Any?>()
        val callResult = mockk<HttpsCallableResult> { every { getData() } returns result }
        every { reference.call(any()) } answers {
            sent += firstArg<Any?>()
            Tasks.forResult(callResult)
        }
        return sent
    }

    /** Answers the callable [name] with [result]; returns the data each call was made with. */
    private fun stubCallable(name: String, result: Any?): MutableList<Any?> {
        val reference = mockk<HttpsCallableReference>()
        every { functions.getHttpsCallable(name) } returns reference
        return answerCalls(reference, result)
    }

    @Test
    fun `send waits up to 300 seconds and sends the draft's payload`() = runTest {
        val reference = mockk<HttpsCallableReference>()
        val withTimeout = mockk<HttpsCallableReference>()
        every { functions.getHttpsCallable("sendNotification") } returns reference
        every { reference.withTimeout(any(), any()) } returns withTimeout
        val sent = answerCalls(withTimeout, mapOf("notificationId" to "n-1", "status" to "sent", "recipients" to 12L))
        val draft = NotificationDraft(id = "n-1", title = "Başlık", body = "Mesaj")

        val result = service.send(draft)

        verify(exactly = 1) { reference.withTimeout(300, TimeUnit.SECONDS) }
        verify(exactly = 0) { reference.call(any()) }
        assertEquals(listOf<Any?>(draft.payload()), sent)
        assertEquals(NotificationStatus.SENT, result.status)
        assertEquals(12, result.recipients)
    }

    @Test
    fun `audience asks with no data and reads the tree`() = runTest {
        val sent = stubCallable("getNotificationAudience", mapOf("total" to 5L, "universities" to emptyList<Any>()))

        val facets = service.audience()

        assertEquals(listOf<Any?>(null), sent)
        assertEquals(5, facets.total)
    }

    @Test
    fun `preview sends the kind and the draft's audience shape`() = runTest {
        val sent = stubCallable("previewNotification", mapOf("recipients" to 12L, "pushTargets" to 15L))
        val audience = NotificationAudience.Segment(NotificationSegment("sakarya", gender = Gender.FEMALE))

        val preview = service.preview(NotificationKind.PROMO, audience)

        assertEquals(NotificationPreview(recipients = 12, pushTargets = 15), preview)
        assertEquals(
            listOf<Any?>(
                mapOf(
                    "kind" to "promo",
                    "audience" to mapOf("scope" to "segment", "universityId" to "sakarya", "gender" to "female"),
                ),
            ),
            sent,
        )
    }

    @Test
    fun `cancel sends the notification id`() = runTest {
        val sent = stubCallable("cancelNotification", mapOf("ok" to true))

        service.cancel("n-9")

        assertEquals(listOf<Any?>(mapOf("notificationId" to "n-9")), sent)
    }

    @Test
    fun `a result the parser cannot read is an unexpected response`() = runTest {
        stubCallable("previewNotification", "not a map")

        try {
            service.preview(NotificationKind.GENERAL, NotificationAudience.All)
            fail("Expected an unexpected response")
        } catch (e: NotificationAdminError) {
            assertSame(NotificationAdminError.UnexpectedResponse, e)
        }
    }

    @Test
    fun `history reads the newest 50 by creation time and drops rows that do not parse`() = runTest {
        val collection = mockk<CollectionReference>()
        val ordered = mockk<Query>()
        val limited = mockk<Query>()
        val good = mockk<DocumentSnapshot> {
            every { id } returns "n-1"
            every { data } returns mapOf(
                "title" to "Başlık",
                "status" to "scheduled",
                "audience" to mapOf("scope" to "all"),
                "scheduledAt" to Timestamp(1_761_100_000, 0),
            )
        }
        val bad = mockk<DocumentSnapshot> {
            every { id } returns "n-2"
            every { data } returns mapOf("title" to "Durumsuz")
        }
        val snapshot = mockk<QuerySnapshot> { every { documents } returns listOf(good, bad) }
        every { firestore.collection("notifications") } returns collection
        every { collection.orderBy("createdAt", Query.Direction.DESCENDING) } returns ordered
        every { ordered.limit(50) } returns limited
        every { limited.get() } returns Tasks.forResult(snapshot)

        val rows = service.history()

        assertEquals(listOf("n-1"), rows.map { it.id })
        assertEquals(Instant.ofEpochSecond(1_761_100_000), rows.single().scheduledAt)
    }

    private fun stubImageReference(path: String): StorageReference {
        val root = mockk<StorageReference>()
        val reference = mockk<StorageReference>()
        every { storage.reference } returns root
        every { root.child(path) } returns reference
        return reference
    }

    @Test
    fun `an image already uploaded under the id is reused, never overwritten`() = runTest {
        val reference = stubImageReference("notificationImages/n-1.jpg")
        every { reference.metadata } returns Tasks.forResult(mockk<StorageMetadata>())

        val path = service.uploadImage(byteArrayOf(1, 2, 3), "n-1")

        assertEquals("notificationImages/n-1.jpg", path)
        verify(exactly = 0) { reference.putBytes(any(), any()) }
    }

    @Test
    fun `a missing image is uploaded as a JPEG`() = runTest {
        val reference = stubImageReference("notificationImages/n-1.jpg")
        val notFound = mockk<StorageException>(relaxed = true) {
            every { errorCode } returns StorageException.ERROR_OBJECT_NOT_FOUND
        }
        every { reference.metadata } returns Tasks.forException(notFound)
        val metadata = slot<StorageMetadata>()
        val upload = mockk<UploadTask>(relaxed = true) {
            every { isComplete } returns true
            every { isCanceled } returns false
            every { exception } returns null
            every { result } returns mockk()
        }
        every { reference.putBytes(any(), capture(metadata)) } returns upload

        val path = service.uploadImage(byteArrayOf(1, 2, 3), "n-1")

        assertEquals("notificationImages/n-1.jpg", path)
        assertEquals("image/jpeg", metadata.captured.contentType)
    }

    @Test
    fun `an image refused before upload never reaches Storage`() = runTest {
        try {
            service.uploadImage(ByteArray(0), "n-1")
            fail("Expected an upload failure")
        } catch (e: NotificationAdminError) {
            assertSame(NotificationAdminError.ImageUploadFailed, e)
        }
        verify(exactly = 0) { storage.reference }
    }
}
