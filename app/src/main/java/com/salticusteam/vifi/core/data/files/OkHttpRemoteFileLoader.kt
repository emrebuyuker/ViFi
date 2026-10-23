package com.salticusteam.vifi.core.data.files

import com.salticusteam.vifi.core.data.repository.RemoteFileLoader
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ExamFileError
import com.salticusteam.vifi.core.util.LogCategory
import java.io.File
import java.io.IOException
import java.time.Clock
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads exam files once and serves the local copy afterwards (iOS `RemoteFileLoader.localFile`), so an
 * exam that has been opened keeps working offline.
 *
 * - Files are written under a per-URL folder with a readable name ([LocalFileCache]); the local copy is the
 *   file's single persistent store.
 * - Concurrent requests for the same (canonical) URL share one download, which runs in [scope]: it is not
 *   cancelled when a caller goes away, so finishing it warms the cache for the next open.
 * - Credentials, the canonical URL and the 401/403 retry come from the client's [StorageAuthInterceptor].
 * - Every new file triggers a prune back within [FILES_BUDGET_BYTES], oldest first.
 */
class OkHttpRemoteFileLoader(
    private val client: OkHttpClient,
    private val filesDirectory: File,
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher,
    private val clock: Clock,
    /** Clears the image loader's memory and disk caches on [purge]. */
    private val clearImageCaches: suspend () -> Unit = {},
) : RemoteFileLoader {

    // Internal state
    private val mutex = Mutex()
    private val downloads = HashMap<String, Deferred<File>>()

    /** Bumped by [purge]: a download that finishes mid-purge must not resurrect a deleted file. */
    private val purgeGeneration = AtomicInteger()

    // Public API
    override suspend fun localFile(url: String, fileName: String): File {
        val canonical = ExamFileUrls.canonical(url)
        val destination = LocalFileCache.fileFor(filesDirectory, canonical, fileName)
        if (withContext(ioDispatcher) { destination.isFile }) {
            touch(destination)
            return destination
        }
        val download = mutex.withLock {
            downloads[canonical] ?: scope.async(ioDispatcher) {
                try {
                    download(canonical, destination)
                } finally {
                    val self = currentCoroutineContext()[Job]
                    mutex.withLock { if (downloads[canonical] === self) downloads.remove(canonical) }
                }
            }.also { downloads[canonical] = it }
        }
        return download.await()
    }

    override suspend fun pruneDiskCache() = withContext(ioDispatcher) {
        LocalFileCache.prune(filesDirectory, FILES_BUDGET_BYTES, PRUNE_PROTECTION_MILLIS, clock.millis())
    }

    override suspend fun purge() {
        purgeGeneration.incrementAndGet()
        mutex.withLock {
            downloads.values.forEach { it.cancel() }
            downloads.clear()
        }
        clearImageCaches()
        withContext(ioDispatcher) { filesDirectory.deleteRecursively() }
    }

    // Helpers
    private fun download(canonicalUrl: String, destination: File): File {
        val generation = purgeGeneration.get()
        val folder = checkNotNull(destination.parentFile) { "a cached file always has a folder" }
        if (!folder.isDirectory && !folder.mkdirs()) throw ExamFileError.WriteFailed
        val partial = File(folder, destination.name + PARTIAL_SUFFIX)

        try {
            client.newCall(Request.Builder().url(canonicalUrl).build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw if (response.code == 401 || response.code == 403) {
                        ArchiveError.PermissionDenied
                    } else {
                        ArchiveError.FileUnavailable(response.code)
                    }
                }
                val body = response.body ?: throw ArchiveError.FileUnavailable(response.code)
                partial.outputStream().use { body.byteStream().copyTo(it) }
            }
        } catch (e: IOException) {
            partial.delete()
            LogCategory.FILES.log.e(e, "Downloading a file failed")
            throw e.toArchiveError()
        }

        // A purge (account deletion) that ran during the download wins: don't keep the file.
        if (generation != purgeGeneration.get() || !partial.renameTo(destination)) {
            partial.delete()
            if (generation != purgeGeneration.get()) throw CancellationException("purged during download")
            throw ExamFileError.WriteFailed
        }
        scope.launch(ioDispatcher) { pruneDiskCache() }
        return destination
    }

    /** Marks the file's folder as recently used, so a prune removes the least recently opened files first. */
    private suspend fun touch(file: File) = withContext(ioDispatcher) {
        file.parentFile?.setLastModified(clock.millis())
    }

    companion object {
        /** On-disk budget for the local file copies (PDFs). */
        const val FILES_BUDGET_BYTES = 256L * 1024 * 1024

        /** Files touched this recently are never pruned, so an in-flight download or open file survives. */
        const val PRUNE_PROTECTION_MILLIS = 60_000L

        private const val PARTIAL_SUFFIX = ".part"
    }
}
