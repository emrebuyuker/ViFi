package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.salticusteam.vifi.core.data.repository.InboxReader
import com.salticusteam.vifi.core.model.InboxError
import com.salticusteam.vifi.core.model.InboxItem
import com.salticusteam.vifi.core.model.InboxSource
import com.salticusteam.vifi.core.model.NotificationPayloadParser
import com.salticusteam.vifi.core.util.LogCategory
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.tasks.await

/**
 * The inbox's Firestore sources in the named 'vifi' database (iOS `FirebaseInboxReader`): the public `announcements`
 * (newest 50), the user's `users/<uid>/inbox` (newest 100) and the read marker `users/<uid>/settings/inbox`. The limits
 * apply before expiry, as on iOS. A listener error ends its flow with [InboxError.Unavailable]; documents that do not
 * parse are dropped.
 *
 * Added in 4.1.0 — replaces 2.5's `announcements`-only reader; personal copies, images, actions, kinds, expiry and
 * the server-side read marker are new.
 */
class FirestoreInboxReader @Inject constructor(
    private val firestore: FirebaseFirestore,
) : InboxReader {

    override fun broadcastChanges(): Flow<List<InboxItem>> =
        listChanges(firestore.collection(ANNOUNCEMENTS), BROADCAST_LIMIT, InboxSource.BROADCAST)

    override fun personalChanges(uid: String): Flow<List<InboxItem>> =
        listChanges(user(uid).collection(INBOX), PERSONAL_LIMIT, InboxSource.PERSONAL)

    override fun readMarkerChanges(uid: String): Flow<Instant?> = callbackFlow {
        // Metadata changes too: the server's confirmation of a missing marker only flips `isFromCache`.
        val registration = marker(uid).addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null || snapshot == null) {
                LogCategory.APP.log.w(error, "Reading the inbox marker failed")
                close(InboxError.Unavailable)
                return@addSnapshotListener
            }
            // A marker missing only from the local cache (offline) is not "never opened": writing one could
            // overwrite the server's, so nothing is emitted until the server answers.
            if (snapshot.metadata.isFromCache && !snapshot.exists()) return@addSnapshotListener
            trySend(readAt(snapshot))
        }
        awaitClose { registration.remove() }
    }.conflate()

    override suspend fun markRead(uid: String) {
        // Exactly `{readAt: request.time}` (the shared rules), a full set.
        marker(uid).set(mapOf(READ_AT to FieldValue.serverTimestamp())).await()
    }

    // Helpers
    private fun listChanges(collection: Query, limit: Long, source: InboxSource): Flow<List<InboxItem>> =
        callbackFlow {
            val registration = collection.orderBy(CREATED_AT, Query.Direction.DESCENDING).limit(limit)
                .addSnapshotListener { snapshot, error ->
                    if (error != null || snapshot == null) {
                        LogCategory.APP.log.w(error, "Reading the %s inbox failed", source.name.lowercase())
                        close(InboxError.Unavailable)
                        return@addSnapshotListener
                    }
                    trySend(snapshot.documents.mapNotNull { item(it, source) })
                }
            awaitClose { registration.remove() }
        }.conflate()

    private fun user(uid: String) = firestore.collection(USERS).document(uid)

    private fun marker(uid: String) = user(uid).collection(SETTINGS).document(INBOX)

    internal companion object {
        const val ANNOUNCEMENTS = "announcements"
        const val USERS = "users"
        const val INBOX = "inbox"
        const val SETTINGS = "settings"
        const val CREATED_AT = "createdAt"
        const val READ_AT = "readAt"
        const val BROADCAST_LIMIT = 50L
        const val PERSONAL_LIMIT = 100L

        /** Firestore's `Timestamp` values become `Instant`s, the parser's date type. */
        fun parseable(data: Map<String, Any?>): Map<String, Any?> =
            data.mapValues { (_, value) -> (value as? Timestamp)?.let(::instant) ?: value }

        fun instant(timestamp: Timestamp): Instant = Instant.ofEpochSecond(timestamp.seconds, timestamp.nanoseconds.toLong())

        private fun item(document: DocumentSnapshot, source: InboxSource): InboxItem? =
            NotificationPayloadParser.inboxItem(document.id, source, parseable(document.data.orEmpty()))

        /** While the marker write is pending, the local estimate stands in, so the count does not flash. */
        private fun readAt(snapshot: DocumentSnapshot): Instant? =
            snapshot.getTimestamp(READ_AT, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)?.let(::instant)
    }
}
