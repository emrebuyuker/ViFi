package com.salticusteam.vifi.core.notifications

import com.salticusteam.vifi.core.data.repository.NotificationService
import com.salticusteam.vifi.core.di.ApplicationScope
import com.salticusteam.vifi.core.util.LogCategory
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Topics every install joins. */
object PushTopics {
    /** The broadcast "general" and "system" pushes go here (with a copy in `announcements`); `promo` never does. */
    const val ALL = "all"
}

/**
 * Keeps this install in [PushTopics.ALL]: joined on every app start and whenever FCM issues a new token,
 * regardless of sign-in, the notification permission or campaign consent (iOS joins on every token delivery).
 * Topic delivery needs no permission, so a user who allows notifications later already receives broadcasts.
 * Joining is idempotent; a failure is logged and retried on the next start or token.
 */
@Singleton
class PushTopicSubscription @Inject constructor(
    private val notifications: NotificationService,
    @ApplicationScope private val scope: CoroutineScope,
) {

    // Internal state
    private val started = AtomicBoolean(false)

    // Intents
    /** Joins once per process; later calls do nothing. */
    fun start() {
        if (started.compareAndSet(false, true)) refresh()
    }

    /** Joins again: a new token starts with no topics. */
    fun refresh() {
        scope.launch { subscribe() }
    }

    // Helpers
    private suspend fun subscribe() {
        try {
            notifications.subscribe(PushTopics.ALL)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.APP.log.w(e, "Subscribing to the all topic failed")
        }
    }
}
