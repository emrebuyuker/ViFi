package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.salticusteam.vifi.core.data.moderation.ApprovalCommit
import com.salticusteam.vifi.core.data.moderation.ModerationBackend
import com.salticusteam.vifi.core.data.moderation.PublishedExamRef
import com.salticusteam.vifi.core.model.ExamSubmissionRecord
import com.salticusteam.vifi.core.model.GENERIC_ERROR_MESSAGE
import com.salticusteam.vifi.core.model.ModerationError
import com.salticusteam.vifi.core.model.SubmissionRecordParser
import com.salticusteam.vifi.core.util.FirebaseConstants
import com.salticusteam.vifi.core.util.LogCategory
import com.salticusteam.vifi.core.util.StorageUrl
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Moderation's backend operations on the named `vifi` Firestore and Cloud Storage, with no Cloud Function: the
 * writes are allowed by the shared rules' `isAdmin()` (iOS `FirebaseModerationService`'s Firebase half).
 *
 * Document shapes follow the shared rules exactly: a wallet carries only `balance` + `updatedAt`, a ledger entry
 * only whitelisted keys, so an extra field would be rejected as `PERMISSION_DENIED`.
 */
class FirebaseModerationBackend @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) : ModerationBackend {

    /** The Storage bucket the preview URLs are built for. */
    private val bucket: String = storage.reference.bucket

    override suspend fun pendingSubmissions(): List<ExamSubmissionRecord> = call {
        // No orderBy (it would need an index): the service sorts. From the server, so a pull-to-refresh is fresh.
        firestore.collection(FirebaseConstants.COLLECTION_SUBMISSIONS)
            .whereEqualTo(STATUS, PENDING)
            .get(Source.SERVER)
            .await()
            .documents
            .mapNotNull { document ->
                val createdAt = document.getTimestamp(CREATED_AT)?.toDate()?.toInstant()
                SubmissionRecordParser.record(document.id, document.data.orEmpty(), createdAt)
            }
    }

    override suspend fun submissionStatus(submissionId: String): String? = call {
        submission(submissionId).get(Source.SERVER).await().getString(STATUS)
    }

    override suspend fun ledgerEntryExists(ownerId: String, submissionId: String): Boolean = call {
        ledgerEntry(ownerId, submissionId).get(Source.SERVER).await().exists()
    }

    override suspend fun copyFile(sourcePath: String, destinationPath: String, contentType: String, maxBytes: Long) =
        call {
            val bytes = storage.reference.child(sourcePath).getBytes(maxBytes).await()
            val metadata = StorageMetadata.Builder().setContentType(contentType).build()
            storage.reference.child(destinationPath).putBytes(bytes, metadata).await()
            Unit
        }

    override suspend fun deleteFile(storagePath: String) = call {
        storage.reference.child(storagePath).delete().await()
        Unit
    }

    override suspend fun commitApproval(commit: ApprovalCommit) = call {
        val batch = firestore.batch()
        val catalog = firestore.collection(CATALOG_NODES)

        for (node in commit.nodesToCreate) {
            batch.set(catalog.document(node.id), node.fields)
        }
        for (id in commit.childCountIncrements) {
            batch.update(catalog.document(id), CHILD_COUNT, FieldValue.increment(1L))
        }
        if (commit.examId != null && commit.examDocument != null) {
            batch.set(firestore.collection(EXAMS).document(commit.examId), commit.examDocument)
        }
        if (commit.coins > 0) {
            batch.set(
                firestore.collection(WALLETS).document(commit.ownerId),
                mapOf("balance" to FieldValue.increment(commit.coins.toLong()), "updatedAt" to FieldValue.serverTimestamp()),
                SetOptions.merge(),
            )
            batch.set(
                ledgerEntry(commit.ownerId, commit.submissionId),
                mapOf(
                    "type" to "earn",
                    "amount" to commit.coins.toLong(),
                    "submissionId" to commit.submissionId,
                    "tier" to commit.tier.raw,
                    CREATED_AT to FieldValue.serverTimestamp(),
                ),
            )
        }
        val submissionFields = buildMap<String, Any> {
            put(STATUS, "approved")
            put("tier", commit.tier.raw)
            put("coins", commit.coins.toLong())
            put(REVIEWED_AT, FieldValue.serverTimestamp())
            commit.examId?.let { put("publishedExamId", it) }
        }
        batch.update(submission(commit.submissionId), submissionFields)
        batch.commit().await()
        Unit
    }

    override suspend fun reject(submissionId: String, reason: String?) = call {
        val fields = buildMap<String, Any> {
            put(STATUS, "rejected")
            put(REVIEWED_AT, FieldValue.serverTimestamp())
            reason?.let { put("rejectionReason", it) }
        }
        submission(submissionId).update(fields).await()
        Unit
    }

    override suspend fun publishedExam(examId: String): PublishedExamRef? = call {
        val snapshot = firestore.collection(EXAMS).document(examId).get(Source.SERVER).await()
        snapshot.data?.let(::publishedExamRef)
    }

    override suspend fun deleteExam(examId: String, courseId: String) = call {
        val batch = firestore.batch()
        batch.delete(firestore.collection(EXAMS).document(examId))
        batch.update(firestore.collection(CATALOG_NODES).document(courseId), CHILD_COUNT, FieldValue.increment(-1L))
        batch.commit().await()
        Unit
    }

    override fun fileUrl(storagePath: String): String? = StorageUrl.canonical(storagePath, bucket)

    // Helpers

    private fun submission(id: String) = firestore.collection(FirebaseConstants.COLLECTION_SUBMISSIONS).document(id)

    private fun ledgerEntry(ownerId: String, submissionId: String) =
        firestore.collection(WALLETS).document(ownerId).collection(LEDGER).document(submissionId)

    /** Runs a backend call off the main thread, turning any failure into a [ModerationError]. */
    private suspend fun <T> call(block: suspend () -> T): T =
        try {
            withContext(Dispatchers.IO) { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw moderationError(e)
        }

    internal companion object {
        const val CATALOG_NODES = "catalogNodes"
        const val EXAMS = "exams"
        const val WALLETS = "wallets"
        const val LEDGER = "ledger"
        const val STATUS = "status"
        const val PENDING = "pending"
        const val CHILD_COUNT = "childCount"
        const val CREATED_AT = "createdAt"
        const val REVIEWED_AT = "reviewedAt"

        /** The course and file paths of an `exams/<id>` document's [data]; Firestore-free for unit tests. */
        fun publishedExamRef(data: Map<String, Any?>): PublishedExamRef = PublishedExamRef(
            courseId = (data[FirestoreArchiveParser.Field.COURSE_ID] as? String)?.takeIf { it.isNotEmpty() },
            storagePaths = (data[FirestoreArchiveParser.Field.FILES] as? List<*>).orEmpty().mapNotNull { entry ->
                ((entry as? Map<*, *>)?.get(FirestoreArchiveParser.Field.STORAGE_PATH) as? String)
                    ?.takeIf { it.isNotEmpty() }
            },
        )

        /** Maps a Firestore or Storage failure to a domain error (iOS `moderationError(from:)`). */
        fun moderationError(error: Throwable): ModerationError {
            val mapped = when (error) {
                is ModerationError -> error
                is FirebaseFirestoreException -> firestoreError(error.code)
                is StorageException -> storageError(error.errorCode)
                is IOException -> ModerationError.Offline
                else -> null
            }
            if (mapped != null) return mapped
            LogCategory.APP.log.e(error, "Moderation operation failed")
            return ModerationError.Unknown(GENERIC_ERROR_MESSAGE)
        }

        fun firestoreError(code: FirebaseFirestoreException.Code): ModerationError? = when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> ModerationError.PermissionDenied
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> ModerationError.Offline
            else -> null
        }

        /** Storage reports transport failures as `ERROR_UNKNOWN` wrapping an [IOException]. */
        fun storageError(errorCode: Int): ModerationError? = when (errorCode) {
            StorageException.ERROR_NOT_AUTHORIZED,
            StorageException.ERROR_NOT_AUTHENTICATED -> ModerationError.PermissionDenied
            StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> ModerationError.Offline
            StorageException.ERROR_OBJECT_NOT_FOUND -> ModerationError.Unknown(FILE_MISSING_MESSAGE)
            else -> null
        }

        /** A pending file vanished (the owner deleted it, or another admin already moved on). */
        const val FILE_MISSING_MESSAGE = "Gönderimin bir dosyası bulunamadı; listeyi yenile."
    }
}
