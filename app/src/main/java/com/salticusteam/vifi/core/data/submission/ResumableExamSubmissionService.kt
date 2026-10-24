package com.salticusteam.vifi.core.data.submission

import com.salticusteam.vifi.core.data.repository.ExamSubmissionService
import com.salticusteam.vifi.core.model.ExamSubmission
import com.salticusteam.vifi.core.model.ExamSubmissionError
import com.salticusteam.vifi.core.util.LogCategory
import java.security.MessageDigest
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlinx.coroutines.CancellationException

/**
 * Creates a pending exam submission (iOS FirebaseExamSubmissionService): uploads its files to Cloud Storage
 * (`pending/<uid>/<submissionId>/…`) one at a time, then writes `submissions/<submissionId>` with their stable
 * `storagePath`s and a server timestamp — so an admin's moderation can copy the owner's files on approval.
 *
 * Unlike iOS, an attempt may be cut off at any point (the upload worker is stopped, the process is killed), so the
 * submit is resumable instead of cleaning up after itself:
 * - a file already stored with the same size and MD5 is skipped; a different object at the path (never expected,
 *   ids are fresh per submit) is deleted first, since the Storage rules forbid overwrites;
 * - a document create that is refused because the document already exists (an earlier attempt's acknowledgement
 *   was lost; the rules evaluate the repeat as a denied update) counts as done once the document is confirmed.
 * Only [discardUploads], called when the submission is given up, removes files — and never those of a document
 * that exists or may exist.
 */
class ResumableExamSubmissionService @Inject constructor(
    private val backend: SubmissionBackend,
) : ExamSubmissionService {

    override suspend fun submit(submission: ExamSubmission, ownerId: String, submissionId: String) {
        // The rules enforce the owner too; failing early avoids pointless uploads.
        if (backend.currentUserId != ownerId) throw ExamSubmissionError.NotSignedIn
        if (!submission.isValid) throw ExamSubmissionError.InvalidSubmission

        val payload = ExamSubmissionPayload.make(submission, ownerId, submissionId)
        for (file in payload.files) {
            val stored = backend.storedFile(file.storagePath)
            if (stored != null) {
                if (stored.holds(file.data)) continue
                backend.deleteFile(file.storagePath)
            }
            backend.upload(file)
        }
        try {
            backend.createDocument(submissionId, payload.firestoreFields)
        } catch (e: ExamSubmissionError.PermissionDenied) {
            if (backend.documentExists(submissionId, ownerId) != true) throw e
        }
    }

    override suspend fun discardUploads(submission: ExamSubmission, ownerId: String, submissionId: String) {
        try {
            if (backend.documentExists(submissionId, ownerId) != false) return
            val payload = ExamSubmissionPayload.make(submission, ownerId, submissionId)
            for (file in payload.files) backend.deleteFile(file.storagePath)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.FILES.log.w(e, "Could not remove the files of a given-up submission")
        }
    }

    internal companion object {
        /** Whether the stored object has [data]'s size and (when reported) MD5. */
        fun StoredFile.holds(data: ByteArray): Boolean =
            sizeBytes == data.size.toLong() && (md5Hash == null || md5Hash == md5Base64(data))

        /** Kotlin's encoder: `java.util.Base64` needs API 26 and is not desugared for minSdk 24. */
        @OptIn(ExperimentalEncodingApi::class)
        fun md5Base64(data: ByteArray): String = Base64.encode(MessageDigest.getInstance("MD5").digest(data))
    }
}
