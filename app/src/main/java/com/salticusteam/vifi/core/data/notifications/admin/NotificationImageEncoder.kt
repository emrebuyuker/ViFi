package com.salticusteam.vifi.core.data.notifications.admin

/**
 * Turns a picked photo into the campaign image the Storage rule and the server accept (iOS
 * `SubmissionFileEncoder.jpeg(from:maxBytes:maxPixelSize:)` as the composer calls it): a JPEG of at most
 * [com.salticusteam.vifi.core.model.NotificationImage.MAX_BYTES], its longest side at most
 * [com.salticusteam.vifi.core.model.NotificationImage.MAX_PIXEL_SIZE] (never upscaled), upright. Runs off the main
 * thread; never throws.
 */
interface NotificationImageEncoder {

    /** Encodes the image at the content [uri] (a Photo Picker result). */
    suspend fun encode(uri: String): EncodedNotificationImage
}

/** What came of encoding a picked photo. */
sealed interface EncodedNotificationImage {

    /** The JPEG to upload. Identity equality: every pick is a new image, even with the same bytes. */
    class Jpeg(val bytes: ByteArray) : EncodedNotificationImage

    /** The photo could not be read (iOS: the picker's data could not be loaded). */
    data object Unreadable : EncodedNotificationImage

    /** Not an image, or no quality step brings it within the size cap. */
    data object Unusable : EncodedNotificationImage
}
