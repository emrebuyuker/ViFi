package com.salticusteam.vifi.core.data.repository

import java.io.File

/**
 * Downloads exam files with an on-disk cache.
 *
 * Platform adaptation: the iOS `image(from:maxPixelSize:speculative:)` and `releaseImage(...)` members are
 * dropped. Android decodes, downsamples, memory-caches and prefetches images with Coil, so this loader only
 * deals with files on disk. Throws `ArchiveError` on failure.
 */
interface RemoteFileLoader {
    /** A local copy of the file at [url] (cached), named [fileName]; used for PDFs and sharing. */
    suspend fun localFile(url: String, fileName: String): File

    /** Trims the on-disk file cache back within its budget, oldest first. Safe at launch and in the background. */
    suspend fun pruneDiskCache()

    /** Removes every downloaded file and cached response. Used when the account is deleted. */
    suspend fun purge()
}
