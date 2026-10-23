package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.salticusteam.vifi.core.data.repository.AnnouncementsReader
import com.salticusteam.vifi.core.model.Announcement
import com.salticusteam.vifi.core.model.AnnouncementParser
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * Follows the `announcements` collection of the named 'vifi' database, newest first, at most [LIMIT] (iOS
 * `FirestoreAnnouncementsReader`). Ordered by one field, so the automatic index serves it. Read-only: the
 * announcements are authored in the Firebase Console.
 */
class FirestoreAnnouncementsReader @Inject constructor(
    private val firestore: FirebaseFirestore,
) : AnnouncementsReader {

    override fun announcementsChanges(): Flow<List<Announcement>> = callbackFlow {
        val registration = firestore.collection(COLLECTION)
            .orderBy(AnnouncementParser.Field.CREATED_AT, Query.Direction.DESCENDING)
            .limit(LIMIT)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error.toDomainError())
                    return@addSnapshotListener
                }
                val items = snapshot?.documents.orEmpty().mapNotNull { doc ->
                    val data = doc.data.orEmpty().mapValues { (_, value) -> (value as? Timestamp)?.toDate() ?: value }
                    AnnouncementParser.parse(doc.id, data)
                }
                trySend(AnnouncementParser.newestFirst(items))
            }
        awaitClose { registration.remove() }
    }.conflate()

    private fun Throwable.toDomainError(): Throwable = when (this) {
        is FirebaseFirestoreException -> FirestoreArchiveRepository.archiveError(code) ?: this
        else -> this
    }

    private companion object {
        const val COLLECTION = "announcements"
        const val LIMIT = 50L
    }
}
