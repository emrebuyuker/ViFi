package com.salticusteam.vifi.core.data.submission

/**
 * The raw backend operations a submission is made of (Storage + Firestore on the live build). Every call throws
 * `ExamSubmissionError` (or `CancellationException`); the decisions — what an earlier attempt already did, what
 * may be cleaned up — live in [ResumableExamSubmissionService], so they are unit-tested against a fake backend.
 */
interface SubmissionBackend {
    /** The signed-in user's uid, or `null` when nobody is signed in. */
    val currentUserId: String?

    /** The object stored at [storagePath], or `null` when there is none. */
    suspend fun storedFile(storagePath: String): StoredFile?

    /** Uploads [file] with only its content type set; the Storage rules reject custom metadata and overwrites. */
    suspend fun upload(file: UploadFile)

    /** Deletes a Storage object; a missing object is not an error. */
    suspend fun deleteFile(storagePath: String)

    /**
     * Creates `submissions/<submissionId>` with [fields] plus the server-stamped `createdAt`. The rules allow only
     * a create, so writing a document that already exists fails with `ExamSubmissionError.PermissionDenied`.
     */
    suspend fun createDocument(submissionId: String, fields: Map<String, Any>)

    /**
     * Whether `submissions/<submissionId>` exists and belongs to [ownerId], read from the server: `true`, `false`
     * (also when the rules deny the read, which is how they answer for a missing document), or `null` when it
     * cannot be told (offline).
     */
    suspend fun documentExists(submissionId: String, ownerId: String): Boolean?
}

// Added in 3.1.0 — what Storage reports for an already uploaded submission file.
data class StoredFile(
    val sizeBytes: Long,
    /** Base64 MD5 of the content, as Storage reports it; `null` when not reported. */
    val md5Hash: String?,
)
