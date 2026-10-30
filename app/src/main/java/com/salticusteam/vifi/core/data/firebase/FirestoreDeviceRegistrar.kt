package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.salticusteam.vifi.BuildConfig
import com.salticusteam.vifi.core.data.preferences.DeviceInstallation
import com.salticusteam.vifi.core.data.repository.DeviceRegistrar
import com.salticusteam.vifi.core.util.LogCategory
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

/**
 * Writes `users/<uid>/devices/<installationId>` in the named 'vifi' Firestore database (iOS `FirebaseDeviceRegistrar`):
 * a full overwrite of exactly `{token, platform: "android", appVersion, updatedAt}`, the only shape the shared rules
 * accept. A server trigger removes the same token from any other account.
 *
 * Added in 4.1.0 — replaces 2.5's merged `devices/<token>` `{platform, updatedAt}` document, which the rules now
 * reject; that leftover of this install is deleted once, after the first successful registration.
 */
class FirestoreDeviceRegistrar @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val installation: DeviceInstallation,
) : DeviceRegistrar {

    override suspend fun register(uid: String, token: String) {
        devices(uid).document(installation.id())
            .set(document(token, BuildConfig.VERSION_NAME, FieldValue.serverTimestamp()))
            .await()
        removeLegacyDevice(uid, token)
    }

    override suspend fun unregister(uid: String) {
        devices(uid).document(installation.id()).delete().await()
    }

    // Helpers
    private fun devices(uid: String) = firestore.collection(USERS).document(uid).collection(DEVICES)

    /** Best effort: a failure is retried after the next registration; other old tokens' documents are harmless. */
    private suspend fun removeLegacyDevice(uid: String, token: String) {
        if (installation.isLegacyDeviceRemoved()) return
        try {
            devices(uid).document(token).delete().await()
            installation.markLegacyDeviceRemoved()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.APP.log.w(e, "Removing the pre-4.1.0 device document failed")
        }
    }

    internal companion object {
        const val USERS = "users"
        const val DEVICES = "devices"
        const val PLATFORM_ANDROID = "android"

        /** The rules' `appVersion` cap. */
        const val APP_VERSION_LIMIT = 32

        /** The device document; [updatedAt] is the server timestamp sentinel (`updatedAt == request.time`). */
        fun document(token: String, appVersion: String, updatedAt: Any): Map<String, Any> = mapOf(
            "token" to token,
            "platform" to PLATFORM_ANDROID,
            "appVersion" to appVersion.take(APP_VERSION_LIMIT),
            "updatedAt" to updatedAt,
        )
    }
}
