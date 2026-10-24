package com.salticusteam.vifi.core.data.repository

/**
 * Records this install's push token under the signed-in user, for future per-user notifications (N11). The
 * shared backend allows the owner to manage `users/<uid>/devices/<token>`; nothing sends to it yet (iOS does not
 * write it either), announcements still go to the `all` topic.
 */
interface DeviceTokenStore {
    /** Creates or refreshes the [token] entry of [uid]. Throws on failure. */
    suspend fun register(uid: String, token: String)
}
