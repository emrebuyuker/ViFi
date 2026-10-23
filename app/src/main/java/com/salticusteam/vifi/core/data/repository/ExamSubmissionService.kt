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
     * Uploads the submission's files and creates the pending submission owned by [ownerId]. On success the
     * files live under `pending/<ownerId>/<submissionId>/...` and the document is written with
     * `status: "pending"`; the server (Admin SDK) handles moderation and any coin reward afterwards.
     */
    suspend fun submit(submission: ExamSubmission, ownerId: String)
}
