package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.ExamSubmission

/**
 * Submits a user-contributed exam for moderation: uploads its files to Cloud Storage and creates the pending
 * Firestore `submissions/<id>` document.
 *
 * The live implementation requires a signed-in +90 user; the database and storage rules reject every other
 * request. Every throwing operation throws `ExamSubmissionError` (or `CancellationException`).
 */
interface ExamSubmissionService {
    /**
     * Uploads the submission's files and creates the pending submission [submissionId] owned by [ownerId]. On
     * success the files live under `pending/<ownerId>/<submissionId>/...` and the document is written with
     * `status: "pending"`; an admin's device moderates it and credits any coins afterwards (ModerationService).
     *
     * Idempotent per [submissionId], so a retry after a failure, a cancellation or a killed process resumes:
     * files an earlier attempt already uploaded are kept, and an already created document is not created twice.
     * A failure leaves the uploaded files in place for that retry; [discardUploads] removes them once the
     * submission is given up.
     */
    suspend fun submit(submission: ExamSubmission, ownerId: String, submissionId: String)

    /**
     * Best-effort removal of the files a given-up submission uploaded, so no orphans stay in Storage. Does nothing
     * when the document may exist (it was created, or that cannot be checked): a live submission keeps its files.
     * Never throws except `CancellationException`.
     */
    suspend fun discardUploads(submission: ExamSubmission, ownerId: String, submissionId: String)
}
