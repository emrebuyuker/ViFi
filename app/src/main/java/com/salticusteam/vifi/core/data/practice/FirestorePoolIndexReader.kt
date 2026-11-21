package com.salticusteam.vifi.core.data.practice

import com.google.firebase.firestore.FirebaseFirestore
import com.salticusteam.vifi.core.data.firebase.FirestoreArchiveRepository.Companion.toArchiveError
import com.salticusteam.vifi.core.data.repository.PoolIndexReader
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.PoolIndex
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Reads `questionPoolIndex/<subjectId>` from the named 'vifi' Firestore database: one document read (iOS
 * `FirebasePracticeIndexReader`). Only the server writes it; +90 users and admins read it. A missing document means
 * the subject has no practice questions. Wrap it in a [CachingPoolIndexReader] so a subject is read once per session.
 *
 * Added in 4.2.0 — the live flavor's practice-pool index reader.
 */
class FirestorePoolIndexReader @Inject constructor(
    private val firestore: FirebaseFirestore,
) : PoolIndexReader {

    override suspend fun index(subjectId: String): PoolIndex? {
        val data = try {
            firestore.collection(COLLECTION).document(subjectId).get().await().data
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.toArchiveError(ArchivePath.ROOT)
        } ?: return null
        return PoolIndexParser.index(data, subjectId)
    }

    internal companion object {
        const val COLLECTION = "questionPoolIndex"
    }
}
