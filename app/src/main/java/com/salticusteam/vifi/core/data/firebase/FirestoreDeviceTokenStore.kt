package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.salticusteam.vifi.core.data.repository.DeviceTokenStore
import javax.inject.Inject
import kotlinx.coroutines.tasks.await

/**
 * Writes `users/<uid>/devices/<token>` in the named 'vifi' Firestore database (owner read/write in the shared
 * rules). Merged, so a refresh only moves `updatedAt`.
 *
 * Added in 2.5 — `{platform: "android", updatedAt: server time}` device document (N11).
 */
class FirestoreDeviceTokenStore @Inject constructor(
    private val firestore: FirebaseFirestore,
) : DeviceTokenStore {

    override suspend fun register(uid: String, token: String) {
        firestore.collection(USERS).document(uid).collection(DEVICES).document(token)
            .set(mapOf(PLATFORM to PLATFORM_ANDROID, UPDATED_AT to FieldValue.serverTimestamp()), SetOptions.merge())
            .await()
    }

    private companion object {
        const val USERS = "users"
        const val DEVICES = "devices"
        const val PLATFORM = "platform"
        const val PLATFORM_ANDROID = "android"
        const val UPDATED_AT = "updatedAt"
    }
}
