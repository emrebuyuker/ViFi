package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.salticusteam.vifi.core.data.repository.NotificationSettingsService
import com.salticusteam.vifi.core.model.NotificationConsent
import com.salticusteam.vifi.core.model.NotificationConsentSource
import com.salticusteam.vifi.core.model.NotificationSettingsError
import com.salticusteam.vifi.core.util.LogCategory
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.tasks.await

/**
 * The campaign consent record `users/<uid>/settings/notifications` in the named 'vifi' database (iOS
 * `FirebaseNotificationSettingsService`). Every answer, a "no" included, is a full overwrite of exactly
 * `{marketing, source, version, updatedAt}` with a server timestamp; the shared rules reject anything else and never
 * allow a delete, so the record shows when and where consent was given or withdrawn.
 *
 * Added in 4.1.0 — the record is new; the server sends campaigns (`promo`) only where `marketing == true`.
 */
class FirestoreNotificationSettingsService @Inject constructor(
    private val firestore: FirebaseFirestore,
) : NotificationSettingsService {

    override fun consentChanges(uid: String): Flow<NotificationConsent?> = callbackFlow {
        val registration = record(uid).addSnapshotListener { snapshot, error ->
            if (error != null || snapshot == null) {
                LogCategory.APP.log.w(error, "Reading the campaign consent failed")
                close(NotificationSettingsError.Unavailable)
                return@addSnapshotListener
            }
            // A pending local write reads with its own values, so the switch follows the tap.
            trySend(consent(snapshot.getData(DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)))
        }
        awaitClose { registration.remove() }
    }.conflate()

    override suspend fun setMarketing(uid: String, enabled: Boolean, source: NotificationConsentSource) {
        record(uid).set(fields(enabled, source, FieldValue.serverTimestamp())).await()
    }

    private fun record(uid: String) =
        firestore.collection(USERS).document(uid).collection(SETTINGS).document(NOTIFICATIONS)

    internal companion object {
        const val USERS = "users"
        const val SETTINGS = "settings"
        const val NOTIFICATIONS = "notifications"

        /** An answer only when `marketing` is a boolean; a missing or odd record means "never answered". */
        fun consent(data: Map<String, Any?>?): NotificationConsent? =
            (data?.get("marketing") as? Boolean)?.let(::NotificationConsent)

        /** [updatedAt] is the server timestamp sentinel (`updatedAt == request.time`). */
        fun fields(enabled: Boolean, source: NotificationConsentSource, updatedAt: Any): Map<String, Any> = mapOf(
            "marketing" to enabled,
            "source" to source.raw,
            "version" to NotificationConsent.CURRENT_VERSION,
            "updatedAt" to updatedAt,
        )
    }
}
