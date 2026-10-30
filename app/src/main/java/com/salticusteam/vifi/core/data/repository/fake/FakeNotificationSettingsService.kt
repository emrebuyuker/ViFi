package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.repository.NotificationSettingsService
import com.salticusteam.vifi.core.model.NotificationConsent
import com.salticusteam.vifi.core.model.NotificationConsentSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The consent record held in memory (mock flavor, previews, tests; iOS `StubNotificationSettingsService`): a write
 * reaches every open flow. [readFailure] makes the next collected flow throw; [writeFailure] rejects writes.
 */
class FakeNotificationSettingsService(initial: NotificationConsent? = null) : NotificationSettingsService {

    /** One recorded answer. */
    data class Write(val uid: String, val marketing: Boolean, val source: NotificationConsentSource)

    var readFailure: Throwable? = null
    var writeFailure: Throwable? = null

    /** Every accepted write, in order. */
    val writes = mutableListOf<Write>()

    private val consent = MutableStateFlow(initial)

    /** Simulates the record changing elsewhere (another device answered). */
    fun set(value: NotificationConsent?) {
        consent.value = value
    }

    override fun consentChanges(uid: String): Flow<NotificationConsent?> = flow {
        readFailure?.let { throw it }
        emitAll(consent)
    }

    override suspend fun setMarketing(uid: String, enabled: Boolean, source: NotificationConsentSource) {
        writeFailure?.let { throw it }
        writes += Write(uid, enabled, source)
        consent.value = NotificationConsent(marketing = enabled)
    }
}
