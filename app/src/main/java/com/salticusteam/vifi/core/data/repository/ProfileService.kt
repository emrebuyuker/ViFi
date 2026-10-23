package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.UserProfile

/**
 * Reads and stores the signed-in user's profile (`users/<uid>/profile`).
 *
 * The live implementation requires a signed-in +90 user; database rules reject every other request. Every
 * throwing operation throws `ProfileError` (or `CancellationException`).
 */
interface ProfileService {
    /** The stored profile for [uid], or `null` when the user has not completed it yet. */
    suspend fun loadProfile(uid: String): UserProfile?

    /** Creates or replaces the profile for [uid]. */
    suspend fun saveProfile(profile: UserProfile, uid: String)

    /**
     * Deletes the stored profile for [uid]. Called while the user is still signed in, as part of deleting the
     * account, so a deleted account leaves no profile behind.
     */
    suspend fun deleteProfile(uid: String)
}
