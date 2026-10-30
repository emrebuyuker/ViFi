package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.InboxItem
import java.time.Instant
import kotlinx.coroutines.flow.Flow

/**
 * Reads what the in-app inbox shows (iOS `InboxReading`): the public broadcast announcements, the signed-in user's
 * own inbox, and the marker that tells which of them were already seen.
 *
 * Every flow emits right away and again on each change. A flow that fails throws
 * [com.salticusteam.vifi.core.model.InboxError] and ends, so a failed source never reads as an empty one.
 */
interface InboxReader {

    /** `announcements`: sent to everyone (newest 50, newest first). */
    fun broadcastChanges(): Flow<List<InboxItem>>

    /** `users/{uid}/inbox`: sent to this user — a segment, a campaign or an automatic message (newest 100). */
    fun personalChanges(uid: String): Flow<List<InboxItem>>

    /**
     * `users/{uid}/settings/inbox.readAt`: when the user last opened the inbox; `null` when the server has no marker
     * (a new account). A `null` is emitted only once the server confirmed it, never for a cache-only miss.
     */
    fun readMarkerChanges(uid: String): Flow<Instant?>

    /** Records "the inbox was opened now" (a server timestamp), so every device's unread count follows. */
    suspend fun markRead(uid: String)
}
