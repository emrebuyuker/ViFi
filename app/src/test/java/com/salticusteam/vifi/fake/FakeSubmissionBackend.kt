package com.salticusteam.vifi.fake

import com.salticusteam.vifi.core.data.submission.ResumableExamSubmissionService
import com.salticusteam.vifi.core.data.submission.StoredFile
import com.salticusteam.vifi.core.data.submission.SubmissionBackend
import com.salticusteam.vifi.core.data.submission.UploadFile
import com.salticusteam.vifi.core.model.ExamSubmissionError

/**
 * An in-memory Storage + Firestore with the rules' relevant behaviour: no overwrite of a stored object, and a
 * second create of an existing document denied (the rules see it as an update). Failures can be injected per call.
 */
class FakeSubmissionBackend(override var currentUserId: String? = "uid") : SubmissionBackend {

    /** Stored objects by path. */
    val objects = linkedMapOf<String, ByteArray>()

    /** Created documents by id. */
    val documents = linkedMapOf<String, Map<String, Any>>()

    val uploadedPaths = mutableListOf<String>()
    val deletedPaths = mutableListOf<String>()
    var createCalls = 0
        private set

    /** Thrown by the upload whose 1-based index (over all uploads) is the key, once. */
    val uploadFailures = mutableMapOf<Int, ExamSubmissionError>()

    /** Thrown by the next document create, once. */
    var createFailure: ExamSubmissionError? = null

    /** When true, a create stores the document but then fails (its acknowledgement was lost). */
    var loseCreateAcknowledgement = false

    /** When set, [documentExists] cannot tell (offline). */
    var existenceUnknown = false

    override suspend fun storedFile(storagePath: String): StoredFile? = objects[storagePath]?.let {
        StoredFile(it.size.toLong(), ResumableExamSubmissionService.md5Base64(it))
    }

    override suspend fun upload(file: UploadFile) {
        val index = uploadedPaths.size + 1
        uploadFailures.remove(index)?.let { throw it }
        if (file.storagePath in objects) throw ExamSubmissionError.PermissionDenied
        objects[file.storagePath] = file.data
        uploadedPaths += file.storagePath
    }

    override suspend fun deleteFile(storagePath: String) {
        objects.remove(storagePath)
        deletedPaths += storagePath
    }

    override suspend fun createDocument(submissionId: String, fields: Map<String, Any>) {
        createCalls++
        createFailure?.let {
            createFailure = null
            throw it
        }
        if (submissionId in documents) throw ExamSubmissionError.PermissionDenied
        documents[submissionId] = fields
        if (loseCreateAcknowledgement) {
            loseCreateAcknowledgement = false
            throw ExamSubmissionError.Offline
        }
    }

    override suspend fun documentExists(submissionId: String, ownerId: String): Boolean? =
        if (existenceUnknown) null else documents[submissionId]?.get("ownerId") == ownerId
}
