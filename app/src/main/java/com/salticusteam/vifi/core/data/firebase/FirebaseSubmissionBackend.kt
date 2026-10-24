package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.salticusteam.vifi.core.data.repository.AuthService
import com.salticusteam.vifi.core.data.submission.ExamSubmissionPayload
import com.salticusteam.vifi.core.data.submission.StoredFile
import com.salticusteam.vifi.core.data.submission.SubmissionBackend
import com.salticusteam.vifi.core.data.submission.UploadFile
import com.salticusteam.vifi.core.model.ExamSubmissionError
import com.salticusteam.vifi.core.util.FirebaseConstants
import com.salticusteam.vifi.core.util.LogCategory
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * [SubmissionBackend] on the named `vifi` Firestore (`submissions/<id>`) and Cloud Storage (`pending/…`). Every
 * Firebase failure is mapped to an [ExamSubmissionError] (iOS `submissionError(from:)`).
 */
class FirebaseSubmissionBackend @Inject constructor(
    private val authService: AuthService,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) : SubmissionBackend {

    override val currentUserId: String? get() = authService.currentUser?.id

    override suspend fun storedFile(storagePath: String): StoredFile? = mapped {
        try {
            val metadata = storage.reference.child(storagePath).metadata.await()
            StoredFile(sizeBytes = metadata.sizeBytes, md5Hash = metadata.md5Hash)
        } catch (e: StorageException) {
            if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) null else throw e
        }
    }

    override suspend fun upload(file: UploadFile) = mapped {
        val metadata = StorageMetadata.Builder().setContentType(file.contentType).build()
        storage.reference.child(file.storagePath).putBytes(file.data, metadata).await()
        Unit
    }

    override suspend fun deleteFile(storagePath: String) = mapped {
        try {
            storage.reference.child(storagePath).delete().await()
        } catch (e: StorageException) {
            if (e.errorCode != StorageException.ERROR_OBJECT_NOT_FOUND) throw e
        }
        Unit
    }

    override suspend fun createDocument(submissionId: String, fields: Map<String, Any>) = mapped {
        val document = firestore.collection(FirebaseConstants.COLLECTION_SUBMISSIONS).document(submissionId)
        document.set(fields + (ExamSubmissionPayload.CREATED_AT to FieldValue.serverTimestamp())).await()
        Unit
    }

    override suspend fun documentExists(submissionId: String, ownerId: String): Boolean? = try {
        val snapshot = firestore.collection(FirebaseConstants.COLLECTION_SUBMISSIONS).document(submissionId)
            .get(Source.SERVER).await()
        snapshot.exists() && snapshot.getString(OWNER_ID) == ownerId
    } catch (e: CancellationException) {
        throw e
    } catch (e: FirebaseFirestoreException) {
        // The read rule dereferences `resource.data`, so a missing document is denied rather than empty.
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) false else null
    } catch (e: Exception) {
        null
    }

    // Helpers
    /** Runs a Firebase call, rethrowing its failure as an [ExamSubmissionError]. */
    private suspend fun <T> mapped(block: suspend () -> T): T = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        throw submissionError(e)
    }

    internal companion object {
        private const val OWNER_ID = "ownerId"

        /** Maps a Firestore or Storage failure to a domain error (iOS `submissionError(from:)`). */
        fun submissionError(error: Throwable): ExamSubmissionError {
            val mapped = when (error) {
                is ExamSubmissionError -> error
                is FirebaseFirestoreException -> firestoreError(error.code)
                is StorageException -> storageError(error.errorCode) ?: (error.cause as? IOException)?.let {
                    ExamSubmissionError.Offline
                }
                is IOException -> ExamSubmissionError.Offline
                else -> null
            }
            if (mapped != null) return mapped
            LogCategory.APP.log.e(error, "Exam submission failed")
            return ExamSubmissionError.Unknown(ExamSubmissionError.GENERIC_MESSAGE)
        }

        fun firestoreError(code: FirebaseFirestoreException.Code): ExamSubmissionError? = when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> ExamSubmissionError.PermissionDenied
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> ExamSubmissionError.Offline
            else -> null
        }

        /** Storage reports transport failures as `ERROR_UNKNOWN` wrapping an [IOException]. */
        fun storageError(errorCode: Int): ExamSubmissionError? = when (errorCode) {
            StorageException.ERROR_NOT_AUTHORIZED,
            StorageException.ERROR_NOT_AUTHENTICATED -> ExamSubmissionError.PermissionDenied
            StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> ExamSubmissionError.Offline
            else -> null
        }
    }
}
