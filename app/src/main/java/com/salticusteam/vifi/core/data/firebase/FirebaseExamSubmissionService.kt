package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import com.google.firebase.storage.StorageMetadata
import com.salticusteam.vifi.core.data.repository.AuthService
import com.salticusteam.vifi.core.data.repository.ExamSubmissionService
import com.salticusteam.vifi.core.data.submission.ExamSubmissionPayload
import com.salticusteam.vifi.core.data.submission.UploadFile
import com.salticusteam.vifi.core.model.ExamSubmission
import com.salticusteam.vifi.core.model.ExamSubmissionError
import com.salticusteam.vifi.core.util.FirebaseConstants
import com.salticusteam.vifi.core.util.LogCategory
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Creates a pending exam submission in the named `vifi` Firestore (`submissions/<id>`) after uploading its files
 * to Cloud Storage (`pending/<uid>/<submissionId>/…`) (iOS FirebaseExamSubmissionService).
 *
 * Files are uploaded first, one at a time, then the document is written with their stable `storagePath`s and a
 * server timestamp — so the moderation function (which bypasses the rules) can copy the owner's files on
 * approval. If any step fails, the already-uploaded files are removed best-effort so no orphans are left.
 */
class FirebaseExamSubmissionService @Inject constructor(
    private val authService: AuthService,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
) : ExamSubmissionService {

    override suspend fun submit(submission: ExamSubmission, ownerId: String) {
        // The rules enforce the owner too; failing early avoids pointless uploads.
        if (authService.currentUser?.id != ownerId) throw ExamSubmissionError.NotSignedIn
        if (!submission.isValid) throw ExamSubmissionError.InvalidSubmission

        val document = firestore.collection(FirebaseConstants.COLLECTION_SUBMISSIONS).document()
        val payload = ExamSubmissionPayload.make(submission, ownerId, submissionId = document.id)

        val uploaded = mutableListOf<String>()
        try {
            for (file in payload.files) {
                upload(file)
                uploaded += file.storagePath
            }
            val fields = payload.firestoreFields + (ExamSubmissionPayload.CREATED_AT to FieldValue.serverTimestamp())
            document.set(fields).await()
        } catch (e: Exception) {
            // Also on cancellation: the user left mid-upload, so nothing half-sent should stay behind.
            withContext(NonCancellable) { cleanUp(uploaded) }
            if (e is CancellationException) throw e
            throw submissionError(e)
        }
    }

    /** Uploads one file with only the content type set; the Storage rules reject custom metadata. */
    private suspend fun upload(file: UploadFile) {
        val metadata = StorageMetadata.Builder().setContentType(file.contentType).build()
        storage.reference.child(file.storagePath).putBytes(file.data, metadata).await()
    }

    /** Removes already-uploaded files after a later step failed. Best-effort: delete errors are ignored. */
    private suspend fun cleanUp(storagePaths: List<String>) {
        for (path in storagePaths) {
            try {
                storage.reference.child(path).delete().await()
            } catch (e: Exception) {
                LogCategory.FILES.log.w(e, "Could not remove an orphaned submission file")
            }
        }
    }

    internal companion object {
        /** Shown for a failure with no specific mapping (iOS copy, verbatim). */
        const val GENERIC_MESSAGE = "Gönderimin kaydedilemedi. Lütfen tekrar dene."

        /** Maps a Firestore or Storage failure to a domain error (iOS `submissionError(from:)`). */
        fun submissionError(error: Throwable): ExamSubmissionError {
            val mapped = when (error) {
                is ExamSubmissionError -> error
                is FirebaseFirestoreException -> firestoreError(error.code)
                is StorageException -> storageError(error.errorCode)
                is IOException -> ExamSubmissionError.Offline
                else -> null
            }
            if (mapped != null) return mapped
            LogCategory.APP.log.e(error, "Exam submission failed")
            return ExamSubmissionError.Unknown(GENERIC_MESSAGE)
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
