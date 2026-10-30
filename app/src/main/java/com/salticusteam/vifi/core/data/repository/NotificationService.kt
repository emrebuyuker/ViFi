package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.NotificationPermission

/**
 * Requests and reports the OS notification permission and manages push registration. Sending notifications is
 * out of the app's scope (done from the Firebase Console); the app only asks to receive them.
 *
 * Platform adaptation: iOS only has `permission()` and `requestAuthorization()`. On Android (13+ runtime
 * `POST_NOTIFICATIONS`, FCM) the token and topic members are added; no Android framework types appear in the
 * signatures (the live implementation obtains the Activity for the permission prompt itself).
 */
interface NotificationService {
    /** The current authorization status. */
    suspend fun permission(): NotificationPermission

    /**
     * Shows the system permission prompt (when still undetermined) and registers for push notifications on
     * grant. Returns whether permission was granted.
     */
    suspend fun requestAuthorization(): Boolean

    /**
     * Whether the system permission dialog can still be shown (Android 13+: never asked yet, or asked once and the
     * system still offers it again). `false` when already allowed, below Android 13, or after "don't ask again":
     * the way left is the app's notification settings (NDEC-01, iOS `needsSettingsApp`).
     */
    suspend fun canRequestPermission(): Boolean

    /** The current push registration token, or `null` when it is not available. */
    suspend fun token(): String?

    /** Subscribes this install to the push [topic] (e.g. a university segment). */
    suspend fun subscribe(topic: String)

    /** Unsubscribes this install from the push [topic]. */
    suspend fun unsubscribe(topic: String)
}
