package com.salticusteam.vifi.core.data.repository

/**
 * Keeps this install's push token under the signed-in user (iOS `DeviceRegistering`): the document
 * `users/{uid}/devices/{installationId}` = `{token, platform: 'android', appVersion, updatedAt}`, so targeted and
 * personal notifications reach the device. The shared rules accept exactly that shape.
 */
interface DeviceRegistrar {

    /** Saves [token] for [uid]. Idempotent; called after sign-in and whenever the token changes. Throws on failure. */
    suspend fun register(uid: String, token: String)

    /** Removes this install from [uid]. Called before signing out, while the user can still write. Throws on failure. */
    suspend fun unregister(uid: String)
}
