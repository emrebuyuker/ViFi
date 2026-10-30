package com.salticusteam.vifi.core.data.notifications

import com.salticusteam.vifi.core.data.repository.NotificationAdminService
import com.salticusteam.vifi.core.model.AudienceFacets
import com.salticusteam.vifi.core.model.NotificationAdminError
import com.salticusteam.vifi.core.model.NotificationAudience
import com.salticusteam.vifi.core.model.NotificationDraft
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationPreview
import com.salticusteam.vifi.core.model.NotificationSendResult
import com.salticusteam.vifi.core.model.SentNotification
import javax.inject.Inject

// Live-flavor stand-in for the 4.1.0 admin notification service until its Firebase implementation lands (ADM-01).
// It fails safely the way its contract allows, so a live build never shows fake data and never claims a send it did
// not make. (The device registration, inbox, statistics and consent stand-ins were replaced in PSH-04, INB-01,
// INB-05 and PRM-01.)

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
