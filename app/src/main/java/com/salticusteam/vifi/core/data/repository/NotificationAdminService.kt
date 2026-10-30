package com.salticusteam.vifi.core.data.repository

import com.salticusteam.vifi.core.model.AudienceFacets
import com.salticusteam.vifi.core.model.NotificationAudience
import com.salticusteam.vifi.core.model.NotificationDraft
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationPreview
import com.salticusteam.vifi.core.model.NotificationSendResult
import com.salticusteam.vifi.core.model.SentNotification

/**
 * Admin-only notification sending ("Bildirim Gönder", iOS `NotificationAdminServicing`). Every call goes through an
 * admin-gated Cloud Function (europe-west1), except the history (Firestore `notifications`, readable by admins) and
 * the image upload (Storage `notificationImages/`); the server re-validates everything and is the authority on who
 * receives what. Failures are thrown as [com.salticusteam.vifi.core.model.NotificationAdminError].
 */
interface NotificationAdminService {

    /** The audience pickers' data: only universities / faculties / departments registered users have. */
    suspend fun audience(): AudienceFacets

    /** The exact reach of a notification (campaigns narrowed to users who opted in). */
    suspend fun preview(kind: NotificationKind, audience: NotificationAudience): NotificationPreview

    /**
     * Uploads a campaign image — a JPEG of at most [com.salticusteam.vifi.core.model.NotificationImage.MAX_BYTES] —
     * for notification [notificationId] and returns its Storage path for [NotificationDraft.imagePath]. Call it
     * before [send]. Storage forbids overwriting, so an image already uploaded for this id (a retried send) is kept
     * and its path returned.
     */
    suspend fun uploadImage(jpeg: ByteArray, notificationId: String): String

    /** Sends (or schedules) the draft. Idempotent per [NotificationDraft.id]: a retry returns the first result. */
    suspend fun send(draft: NotificationDraft): NotificationSendResult

    /** Cancels a scheduled notification; fails with `NotCancelable` once it has gone out. */
    suspend fun cancel(notificationId: String)

    /** The most recent notifications, newest first (admin and automatic). */
    suspend fun history(): List<SentNotification>
}
