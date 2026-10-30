package com.salticusteam.vifi.core.data.notifications

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.salticusteam.vifi.core.data.repository.NotificationEventReporter
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.model.NotificationEvent
import com.salticusteam.vifi.core.util.LogCategory
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Reports a notification's statistics once per user, event and notification (iOS `NotificationEventTracker`):
 * fire and forget, so the inbox never waits. Only a report the server took is remembered, on the device and for the
 * newest [MEMORY_LIMIT] of them; a failed one is sent again on the next tap. A report already in flight is not
 * sent twice. The server counts once per user anyway; this keeps the requests down.
 */
@Singleton
class NotificationEventTracker @Inject constructor(
    private val reporter: NotificationEventReporter,
    private val dataStore: DataStore<Preferences>,
    @ApplicationScope private val scope: CoroutineScope,
) {

    // Internal state
    private val inFlight = mutableSetOf<String>()

    // Intents
    /** Sends [event] for [notificationId] unless [uid] already did; the job is only for tests to wait on. */
    fun report(event: NotificationEvent, notificationId: String, uid: String): Job? {
        val key = "$uid|${event.raw}|$notificationId"
        if (!synchronized(inFlight) { inFlight.add(key) }) return null
        return scope.launch {
            try {
                if (key in remembered()) return@launch
                reporter.report(event, notificationId)
                remember(key)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                LogCategory.APP.log.w(e, "Reporting a notification %s failed", event.raw)
            } finally {
                synchronized(inFlight) { inFlight.remove(key) }
            }
        }
    }

    // Helpers
    private suspend fun remembered(): List<String> = decode(dataStore.data.first()[REPORTED])

    /** Oldest first; the oldest beyond [MEMORY_LIMIT] are forgotten (and would be counted by the server once more). */
    private suspend fun remember(key: String) {
        dataStore.edit { prefs ->
            prefs[REPORTED] = (decode(prefs[REPORTED]) - key + key).takeLast(MEMORY_LIMIT).joinToString(SEPARATOR)
        }
    }

    private fun decode(stored: String?): List<String> = stored?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()

    internal companion object {
        const val MEMORY_LIMIT = 500

        // Keys hold a uid, an event name and a notification id, none of which can contain a line break.
        private const val SEPARATOR = "\n"
        private val REPORTED = stringPreferencesKey("notifications.reportedEvents.v1")
    }
}
