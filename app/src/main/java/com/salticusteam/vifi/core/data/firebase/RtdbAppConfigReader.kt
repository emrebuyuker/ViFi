package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.salticusteam.vifi.core.data.repository.AppConfigReader
import com.salticusteam.vifi.core.model.AppUpdateConfig
import java.net.URI
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import timber.log.Timber

/**
 * Reads the app-update policy from the Realtime Database `config/appUpdate/android` node with a live listener
 * (iOS `FirebaseRemoteConfigReader`).
 *
 * The node is tiny and kept synced, so after the first launch the policy is available offline. Reads never
 * require a signed-in user — the gate must work on the login screen too — but App Check still guards them.
 * A missing, malformed or rejected node is reported as `null`, which the checker treats as "no update" (or
 * keeps an active gate latched).
 */
class RtdbAppConfigReader @Inject constructor(
    database: FirebaseDatabase,
) : AppConfigReader {

    private val reference = database.getReference(PATH).also { it.keepSynced(true) }

    override fun configChanges(): Flow<AppUpdateConfig?> = callbackFlow {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                trySend(parse(snapshot.value))
            }

            override fun onCancelled(error: DatabaseError) {
                // A rejected listener means the policy is unreadable; null lets the checker keep any active
                // gate latched rather than silently opening the app.
                Timber.e(error.toException(), "Update config listener cancelled")
                trySend(null)
            }
        }
        reference.addValueEventListener(listener)
        awaitClose { reference.removeEventListener(listener) }
    }.conflate()

    companion object {
        const val PATH = "config/appUpdate/android"

        /**
         * Parses the node's value, keeping only well-formed fields: a non-string, empty or (for `storeUrl`)
         * non-http(s) field is dropped. Returns `null` when the node is missing or has no usable field.
         */
        fun parse(value: Any?): AppUpdateConfig? {
            val map = value as? Map<*, *> ?: return null

            fun string(key: String): String? = (map[key] as? String)?.takeIf { it.isNotEmpty() }

            val config = AppUpdateConfig(
                minimumVersion = string("minimumVersion"),
                latestVersion = string("latestVersion"),
                storeUrl = string("storeUrl")?.takeIf(::isWebUrl),
                message = string("message"),
            )
            return config.takeUnless { it == AppUpdateConfig() }
        }

        private fun isWebUrl(text: String): Boolean = runCatching {
            val uri = URI(text)
            (uri.scheme == "https" || uri.scheme == "http") && !uri.host.isNullOrEmpty()
        }.getOrDefault(false)
    }
}
