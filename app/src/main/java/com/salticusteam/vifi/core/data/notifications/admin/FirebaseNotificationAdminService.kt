package com.salticusteam.vifi.core.data.notifications.admin

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.salticusteam.vifi.core.data.repository.NotificationAdminService
import com.salticusteam.vifi.core.model.AudienceFacets
import com.salticusteam.vifi.core.model.NotificationAdminError
import com.salticusteam.vifi.core.model.NotificationAudience
import com.salticusteam.vifi.core.model.NotificationDraft
import com.salticusteam.vifi.core.model.NotificationImage
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationPayloadParser
import com.salticusteam.vifi.core.model.NotificationPreview
import com.salticusteam.vifi.core.model.NotificationSendResult
import com.salticusteam.vifi.core.model.SentNotification
import com.salticusteam.vifi.core.util.LogCategory
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * The admin notification backend (iOS `FirebaseNotificationAdminService`): the four admin callables
 * (`getNotificationAudience`, `previewNotification`, `sendNotification`, `cancelNotification`; europe-west1, admin
 * claim required), campaign images in Cloud Storage (`notificationImages/<id>.jpg`, admin create-only) and the
 * `notifications` history in the named 'vifi' Firestore database.
 *
 * Callable results and history documents are turned into models by the Firebase-free [NotificationPayloadParser];
 * an answer it cannot read is reported as [NotificationAdminError.UnexpectedResponse] rather than guessed at.
 *
 * Added in 4.1.0 — replaces the live flavor's `UnavailableNotificationAdminService` (bound in NIT-01).
 */
class FirebaseNotificationAdminService @Inject constructor(
    private val functions: FirebaseFunctions,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) : NotificationAdminService {

    override suspend fun audience(): AudienceFacets =
        NotificationPayloadParser.facets(call(AUDIENCE, null)) ?: throw NotificationAdminError.UnexpectedResponse

    override suspend fun preview(kind: NotificationKind, audience: NotificationAudience): NotificationPreview {
        // The wire shape of an audience lives with the draft's payload; reuse it instead of copying it.
        val audiencePayload = NotificationDraft(audience = audience).payload().getValue(AUDIENCE_KEY)
        val data = call(PREVIEW, mapOf("kind" to kind.raw, AUDIENCE_KEY to audiencePayload))
        return NotificationPayloadParser.preview(data) ?: throw NotificationAdminError.UnexpectedResponse
    }

    override suspend fun uploadImage(jpeg: ByteArray, notificationId: String): String {
        if (!isUploadable(jpeg, notificationId)) throw NotificationAdminError.ImageUploadFailed
        val path = NotificationImage.storagePath(notificationId)
        val reference = storage.reference.child(path)
        // A retried send finds its image already there: the rules forbid overwriting it, so it is reused as is.
        try {
            reference.metadata.await()
            return path
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!isObjectNotFound(e)) throw imageError(e)
        }
        // Only the content type: the server checks it (and the size) before sending.
        val metadata = StorageMetadata.Builder().setContentType(JPEG_CONTENT_TYPE).build()
        try {
            reference.putBytes(jpeg, metadata).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw imageError(e)
        }
        return path
    }

    override suspend fun send(draft: NotificationDraft): NotificationSendResult {
        // A large fan-out runs for minutes on the server (300 s limit); the client's 70 s default would give up first.
        val data = call(SEND, draft.payload(), timeoutSeconds = SEND_TIMEOUT_SECONDS)
        return NotificationPayloadParser.sendResult(data) ?: throw NotificationAdminError.UnexpectedResponse
    }

    override suspend fun cancel(notificationId: String) {
        call(CANCEL, mapOf("notificationId" to notificationId), isCancellation = true)
    }

    override suspend fun history(): List<SentNotification> = try {
        firestore.collection(NOTIFICATIONS)
            .orderBy(CREATED_AT, Query.Direction.DESCENDING)
            .limit(HISTORY_LIMIT)
            .get()
            .await()
            .documents
            .mapNotNull { NotificationPayloadParser.sentNotification(it.id, readingDates(it.data.orEmpty())) }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw adminError(e)
    }

    // Helpers
    private suspend fun call(
        name: String,
        data: Any?,
        isCancellation: Boolean = false,
        timeoutSeconds: Long? = null,
    ): Any? = try {
        val callable = functions.getHttpsCallable(name).let { reference ->
            if (timeoutSeconds == null) reference else reference.withTimeout(timeoutSeconds, TimeUnit.SECONDS)
        }
        callable.call(data).await().getData()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw adminError(e, isCancellation)
    }

    internal companion object {
        const val AUDIENCE = "getNotificationAudience"
        const val PREVIEW = "previewNotification"
        const val SEND = "sendNotification"
        const val CANCEL = "cancelNotification"
        const val NOTIFICATIONS = "notifications"
        const val CREATED_AT = "createdAt"
        const val HISTORY_LIMIT = 50L
        const val SEND_TIMEOUT_SECONDS = 300L
        const val JPEG_CONTENT_TYPE = "image/jpeg"
        private const val AUDIENCE_KEY = "audience"

        /** What Storage and the server accept: a non-empty JPEG within the cap, under a safe notification id. */
        fun isUploadable(jpeg: ByteArray, notificationId: String): Boolean =
            jpeg.isNotEmpty() && jpeg.size <= NotificationImage.MAX_BYTES &&
                NotificationPayloadParser.isValidNotificationId(notificationId)

        /**
         * Maps a Functions, Firestore or transport failure to a [NotificationAdminError] (iOS `adminError(from:)`),
         * logging the original when it is not one the admin can act on. A failed precondition on
         * `cancelNotification` ([isCancellation]) means the notification already went out, which has its own error.
         */
        fun adminError(error: Throwable, isCancellation: Boolean = false): NotificationAdminError {
            val mapped = when (error) {
                is NotificationAdminError -> error
                is FirebaseFunctionsException ->
                    callableError(FirebaseStatus.named(error.code.name), error.message, isCancellation)
                        // The SDK reports a request that never reached the server as INTERNAL around the IOException.
                        ?: (error.cause as? IOException)?.let { NotificationAdminError.Offline }
                is FirebaseFirestoreException -> firestoreError(FirebaseStatus.named(error.code.name))
                is IOException -> NotificationAdminError.Offline
                else -> null
            }
            if (mapped != null) return mapped
            LogCategory.APP.log.e(error, "Notification admin call failed")
            // iOS shows localizedDescription here; Android never surfaces a raw (English) exception message.
            return NotificationAdminError.Unknown
        }

        /** The server's Turkish message travels with [NotificationAdminError.InvalidDraft], verbatim. */
        fun callableError(status: FirebaseStatus, message: String?, isCancellation: Boolean): NotificationAdminError? =
            when (status) {
                FirebaseStatus.PERMISSION_DENIED,
                FirebaseStatus.UNAUTHENTICATED -> NotificationAdminError.PermissionDenied
                FirebaseStatus.INVALID_ARGUMENT -> invalidDraft(message)
                FirebaseStatus.FAILED_PRECONDITION ->
                    if (isCancellation) NotificationAdminError.NotCancelable else invalidDraft(message)
                FirebaseStatus.UNAVAILABLE,
                FirebaseStatus.DEADLINE_EXCEEDED -> NotificationAdminError.Offline
                FirebaseStatus.OTHER -> null
            }

        /** Reading the history: no admin claim, or no connection. */
        fun firestoreError(status: FirebaseStatus): NotificationAdminError? = when (status) {
            FirebaseStatus.PERMISSION_DENIED,
            FirebaseStatus.UNAUTHENTICATED -> NotificationAdminError.PermissionDenied
            FirebaseStatus.UNAVAILABLE,
            FirebaseStatus.DEADLINE_EXCEEDED -> NotificationAdminError.Offline
            FirebaseStatus.INVALID_ARGUMENT,
            FirebaseStatus.FAILED_PRECONDITION,
            FirebaseStatus.OTHER -> null
        }

        /** Whether a Storage call failed because the object does not exist (yet). */
        fun isObjectNotFound(error: Throwable): Boolean =
            error is StorageException && isObjectNotFound(error.errorCode)

        fun isObjectNotFound(storageErrorCode: Int): Boolean =
            storageErrorCode == StorageException.ERROR_OBJECT_NOT_FOUND

        /** A failed image upload: no permission and no connection as usual, anything else as an upload failure. */
        fun imageError(error: Throwable): NotificationAdminError {
            val mapped = when (error) {
                is NotificationAdminError -> error
                // Storage reports a transport failure as ERROR_UNKNOWN around the IOException.
                is StorageException -> storageError(error.errorCode)
                    ?: (error.cause as? IOException)?.let { NotificationAdminError.Offline }
                is IOException -> NotificationAdminError.Offline
                else -> null
            }
            if (mapped != null) return mapped
            LogCategory.APP.log.e(error, "Notification image upload failed")
            return NotificationAdminError.ImageUploadFailed
        }

        fun storageError(storageErrorCode: Int): NotificationAdminError? = when (storageErrorCode) {
            StorageException.ERROR_NOT_AUTHORIZED,
            StorageException.ERROR_NOT_AUTHENTICATED -> NotificationAdminError.PermissionDenied
            StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> NotificationAdminError.Offline
            else -> null
        }

        /**
         * Replaces Firestore's `Timestamp` values with `Instant`s, the parser's date type. Kept here on purpose (as
         * on iOS): the inbox reader has its own copy, so the two never share, and conflict on, a helper.
         */
        fun readingDates(data: Map<String, Any?>): Map<String, Any?> =
            data.mapValues { (_, value) ->
                (value as? Timestamp)?.let { Instant.ofEpochSecond(it.seconds, it.nanoseconds.toLong()) } ?: value
            }

        /** A refusal without a readable message is not shown as an empty error. */
        private fun invalidDraft(message: String?): NotificationAdminError? =
            message?.trim()?.takeIf { it.isNotEmpty() }?.let(NotificationAdminError::InvalidDraft)
    }
}

/**
 * The canonical status codes Cloud Functions and Firestore share (by name), as far as the admin error mapping tells
 * them apart. The SDKs' own code enums cannot be loaded on the JVM (they build an Android `SparseArray`), so the
 * mapping works on this one and stays unit-tested.
 */
internal enum class FirebaseStatus {
    PERMISSION_DENIED,
    UNAUTHENTICATED,
    INVALID_ARGUMENT,
    FAILED_PRECONDITION,
    UNAVAILABLE,
    DEADLINE_EXCEEDED,
    OTHER;

    companion object {
        /** The status with an SDK code's [name] (`FirebaseFunctionsException.Code.name`, …); [OTHER] for the rest. */
        fun named(name: String): FirebaseStatus = entries.firstOrNull { it.name == name && it != OTHER } ?: OTHER
    }
}
