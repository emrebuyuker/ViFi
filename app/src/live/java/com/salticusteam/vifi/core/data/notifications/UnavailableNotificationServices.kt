package com.salticusteam.vifi.core.data.notifications

import com.salticusteam.vifi.core.data.repository.NotificationAdminService
import com.salticusteam.vifi.core.data.repository.NotificationSettingsService
import com.salticusteam.vifi.core.model.AudienceFacets
import com.salticusteam.vifi.core.model.NotificationAdminError
import com.salticusteam.vifi.core.model.NotificationAudience
import com.salticusteam.vifi.core.model.NotificationConsent
import com.salticusteam.vifi.core.model.NotificationConsentSource
import com.salticusteam.vifi.core.model.NotificationDraft
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationPreview
import com.salticusteam.vifi.core.model.NotificationSendResult
import com.salticusteam.vifi.core.model.NotificationSettingsError
import com.salticusteam.vifi.core.model.SentNotification
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// Live-flavor stand-ins for the 4.1.0 notification services until their Firebase implementations land (4.1.0
// PSH-04, INB-01, INB-05, PRM-01, ADM-01) and NIT-01 binds those instead. Each one fails safely the way its contract
// allows, so a live build never shows fake data and never claims a write it did not make.

/** The consent record reads as unreadable and refuses writes, so no answer is ever reported as saved. */
class UnavailableNotificationSettingsService @Inject constructor() : NotificationSettingsService {
    override fun consentChanges(uid: String): Flow<NotificationConsent?> = flow { throw NotificationSettingsError.Unavailable }

    override suspend fun setMarketing(uid: String, enabled: Boolean, source: NotificationConsentSource): Unit =
        throw NotificationSettingsError.Unavailable
}

/** Reports do not reach the server (callers only log). */
/** Every admin call fails with "Bildirim gönderme henüz kullanılamıyor." */
class UnavailableNotificationAdminService @Inject constructor() : NotificationAdminService {
    override suspend fun audience(): AudienceFacets = throw NotificationAdminError.Unavailable

    override suspend fun preview(kind: NotificationKind, audience: NotificationAudience): NotificationPreview =
        throw NotificationAdminError.Unavailable

    override suspend fun uploadImage(jpeg: ByteArray, notificationId: String): String =
        throw NotificationAdminError.Unavailable

    override suspend fun send(draft: NotificationDraft): NotificationSendResult = throw NotificationAdminError.Unavailable

    override suspend fun cancel(notificationId: String): Unit = throw NotificationAdminError.Unavailable

    override suspend fun history(): List<SentNotification> = throw NotificationAdminError.Unavailable
}
