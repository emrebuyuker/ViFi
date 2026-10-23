package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.salticusteam.vifi.core.data.repository.ArchiveRepository
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.GENERIC_ERROR_MESSAGE
import com.salticusteam.vifi.core.util.LogCategory
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * Reads the exam archive from the named 'vifi' Cloud Firestore database (iOS `FirestoreArchiveRepository`).
 *
 * A level is one query: catalog nodes by `parentId` (universities by [FirestoreArchiveParser.ROOT_PARENT_ID]), or a
 * course's exams by `courseId`, both filtered to `status == published` — equality filters only, so the automatic
 * single-field indexes serve them and no composite index is needed (BE-10). Documents are parsed by the
 * Firestore-free [FirestoreArchiveParser].
 *
 * Reads go through [Source.DEFAULT] (server, falling back to the persistent on-disk cache FirebaseModule
 * configures), so visited levels keep working offline. [archiveChanges] keeps the passive default like iOS:
 * Firestore has no cheap whole-archive change signal; lists refresh on pull-to-refresh.
 */
class FirestoreArchiveRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    storage: FirebaseStorage,
) : ArchiveRepository {

    /** The Storage bucket used to build file URLs from the stored `storagePath`s. */
    private val bucket: String = storage.reference.bucket

    override suspend fun items(path: ArchivePath): List<ArchiveItem> {
        val childLevel = path.childLevel ?: return emptyList()
        val query = if (childLevel == ArchiveLevel.EXAM) examsQuery(path) else catalogQuery(path)
        val docs = read(path) {
            query.get(Source.DEFAULT).await().documents.map { it.id to it.data.orEmpty() }
        }
        return withContext(Dispatchers.Default) { FirestoreArchiveParser.items(docs, path) }
    }

    override suspend fun document(path: ArchivePath): ExamDocument {
        val examId = path.components.lastOrNull() ?: throw ArchiveError.NotFound
        val snapshot = read(path) { firestore.collection(EXAMS).document(examId).get(Source.DEFAULT).await() }
        // Offline with persistence, a cache miss comes back as a non-existent snapshot instead of an exception.
        // That is "not downloaded yet", not a deletion — only a server-confirmed miss is NotFound (iOS parity).
        val data = snapshot.data
            ?: throw if (snapshot.metadata.isFromCache) ArchiveError.Offline else ArchiveError.NotFound
        return FirestoreArchiveParser.document(data, path, bucket) ?: throw ArchiveError.NotFound
    }

    /**
     * Warms the on-disk cache with the whole published archive, so an offline list of a branch the user never
     * visited still comes from the cache (Firestore has no `keepSynced`). Read from [Source.SERVER]: offline it
     * throws instead of returning cached data, so the caller does not record a cache-only read as a warm.
     */
    override suspend fun prefetchArchive() {
        prefetchPublished(CATALOG_NODES)
        prefetchPublished(EXAMS)
    }

    // Helpers

    private fun catalogQuery(path: ArchivePath): Query {
        val parentId = path.components.lastOrNull() ?: FirestoreArchiveParser.ROOT_PARENT_ID
        return firestore.collection(CATALOG_NODES)
            .whereEqualTo(FirestoreArchiveParser.Field.PARENT_ID, parentId)
            .whereEqualTo(FirestoreArchiveParser.Field.STATUS, FirestoreArchiveParser.PUBLISHED_STATUS)
    }

    private fun examsQuery(path: ArchivePath): Query =
        firestore.collection(EXAMS)
            .whereEqualTo(FirestoreArchiveParser.Field.COURSE_ID, path.components.last())
            .whereEqualTo(FirestoreArchiveParser.Field.STATUS, FirestoreArchiveParser.PUBLISHED_STATUS)

    /** A single-field equality query (automatic index); only the caching side effect matters. */
    private suspend fun prefetchPublished(collection: String) {
        withContext(Dispatchers.IO) {
            firestore.collection(collection)
                .whereEqualTo(FirestoreArchiveParser.Field.STATUS, FirestoreArchiveParser.PUBLISHED_STATUS)
                .get(Source.SERVER)
                .await()
        }
    }

    /** Runs a read, turning any failure into the user-facing [ArchiveError]. */
    private suspend fun <T> read(path: ArchivePath, block: suspend () -> T): T =
        try {
            withContext(Dispatchers.IO) { block() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.toArchiveError(path)
        }

    internal companion object {
        const val CATALOG_NODES = "catalogNodes"
        const val EXAMS = "exams"

        /** Maps a read failure to a domain error (iOS `archiveError(from:at:)`); anything unexpected is logged. */
        fun Throwable.toArchiveError(path: ArchivePath): ArchiveError {
            val mapped = when (this) {
                is ArchiveError -> this
                is FirebaseFirestoreException -> archiveError(code)
                is IOException -> ArchiveError.Offline
                else -> null
            }
            if (mapped != null) return mapped
            LogCategory.ARCHIVE.log.e(this, "Firestore read of %s failed", path.components.joinToString("/"))
            // iOS shows localizedDescription here; Android never surfaces a raw exception message.
            return ArchiveError.Unknown(GENERIC_ERROR_MESSAGE)
        }

        /**
         * PERMISSION_DENIED covers both a security-rule rejection (not signed in with a +90 phone) and an App Check
         * rejection (the 403 of an unregistered debug token or a non Play-signed build): Firestore reports both with
         * the same code, so they share [ArchiveError.PermissionDenied] like on iOS.
         */
        fun archiveError(code: FirebaseFirestoreException.Code): ArchiveError? = when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED,
            FirebaseFirestoreException.Code.UNAUTHENTICATED -> ArchiveError.PermissionDenied
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> ArchiveError.Offline
            FirebaseFirestoreException.Code.NOT_FOUND -> ArchiveError.NotFound
            else -> null
        }
    }
}
