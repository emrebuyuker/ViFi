package com.salticusteam.vifi.core.data.repository

/**
 * Reads the files the user picked for an exam submission (Photo Picker images, an opened PDF) and prepares them
 * for upload. Files are addressed by their content URI string, so callers stay free of Android types.
 *
 * Every function runs off the main thread and never throws (except `CancellationException`): a file that
 * cannot be used is reported through the return value.
 */
interface SubmissionFileSource {
    /**
     * The picked image at [uri] as a JPEG page no larger than [maxBytes] (longest side downsampled, EXIF
     * orientation applied), or `null` when it is not a decodable image or cannot be brought under the limit.
     */
    suspend fun jpegPage(uri: String, maxBytes: Int): ByteArray?

    /** The bytes of the picked PDF at [uri], checked against [maxBytes] before it is read into memory. */
    suspend fun pdf(uri: String, maxBytes: Int): PickedPdf
}

/** The outcome of reading a picked PDF. */
sealed interface PickedPdf {
    class Data(val bytes: ByteArray) : PickedPdf

    /** Larger than the upload limit; not read into memory. */
    data object TooLarge : PickedPdf

    data object Empty : PickedPdf

    /** The file could not be opened or read. */
    data object Unreadable : PickedPdf
}
