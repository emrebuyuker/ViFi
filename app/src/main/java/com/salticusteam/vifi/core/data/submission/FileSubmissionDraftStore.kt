package com.salticusteam.vifi.core.data.submission

import com.salticusteam.vifi.core.model.SubmissionFile
import com.salticusteam.vifi.core.util.LogCategory
import java.io.File
import java.io.IOException
import java.time.Clock
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * [SubmissionDraftStore] on the file system: `<root>/<draftId>/page-<order>.bin` plus `submission.json` while a
 * submit is queued. The root lives in `noBackupFilesDir`, not the cache, so the system never clears pages an
 * upload still waits to send. Each file is written to a temporary name and renamed, so a killed process never
 * leaves a half-written page behind.
 */
class FileSubmissionDraftStore(
    private val root: File,
    private val clock: Clock,
    /** Tests pass their test dispatcher, so the file work runs under the virtual scheduler. */
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SubmissionDraftStore {

    private val json = Json { ignoreUnknownKeys = true }

    override fun newDraftId(): String = UUID.randomUUID().toString()

    override suspend fun writePages(draftId: String, files: List<SubmissionFile>) = io {
        val dir = draftDir(draftId)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create the draft directory")
        for (file in files) writeAtomically(File(dir, pageName(file.order)), file.data)
    }

    override suspend fun clearPages(draftId: String) = io {
        draftDir(draftId).listFiles()?.filter { it.name.startsWith(PAGE_PREFIX) }?.forEach { it.delete() }
        Unit
    }

    override suspend fun readPages(draftId: String): List<SubmissionFile> = io {
        try {
            draftDir(draftId).listFiles().orEmpty()
                .mapNotNull { file -> pageOrder(file.name)?.let { SubmissionFile(file.readBytes(), it) } }
                .sortedBy { it.order }
        } catch (e: IOException) {
            LogCategory.FILES.log.w(e, "Could not read the draft pages")
            emptyList()
        }
    }

    override suspend fun writeManifest(draftId: String, pending: PendingSubmission) = io {
        val dir = draftDir(draftId)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Cannot create the draft directory")
        writeAtomically(File(dir, MANIFEST), json.encodeToString(PendingSubmission.serializer(), pending).toByteArray())
    }

    override suspend fun readManifest(draftId: String): PendingSubmission? = io {
        val file = File(draftDir(draftId), MANIFEST)
        if (!file.isFile) return@io null
        try {
            json.decodeFromString(PendingSubmission.serializer(), file.readText())
        } catch (e: Exception) {
            LogCategory.FILES.log.w(e, "Could not read a queued submission")
            null
        }
    }

    override suspend fun deleteManifest(draftId: String) = io {
        File(draftDir(draftId), MANIFEST).delete()
        Unit
    }

    override suspend fun delete(draftId: String) = io {
        draftDir(draftId).deleteRecursively()
        Unit
    }

    override suspend fun discardIfNotQueued(draftId: String) = io {
        val dir = draftDir(draftId)
        if (!File(dir, MANIFEST).exists()) dir.deleteRecursively()
        Unit
    }

    override suspend fun pruneStale(keep: String) = io {
        val now = clock.millis()
        root.listFiles()?.filter { it.isDirectory && it.name != keep }?.forEach { dir ->
            val maxAge = if (File(dir, MANIFEST).exists()) QUEUED_MAX_AGE_MILLIS else DRAFT_MAX_AGE_MILLIS
            if (now - dir.lastModified() >= maxAge) dir.deleteRecursively()
        }
        Unit
    }

    // Helpers
    private suspend fun <T> io(block: () -> T): T = withContext(ioDispatcher) { block() }

    /** The draft's directory; ids come from [newDraftId], so anything else (a path) is refused. */
    private fun draftDir(draftId: String): File {
        require(draftId.matches(DRAFT_ID_PATTERN)) { "Invalid draft id" }
        return File(root, draftId)
    }

    private fun writeAtomically(target: File, bytes: ByteArray) {
        val temporary = File(target.parentFile, "${target.name}.tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(target)) {
            temporary.delete()
            throw IOException("Cannot store ${target.name}")
        }
    }

    internal companion object {
        const val MANIFEST = "submission.json"
        const val PAGE_PREFIX = "page-"
        private const val PAGE_SUFFIX = ".bin"
        private val DRAFT_ID_PATTERN = Regex("[A-Za-z0-9-]{1,64}")

        /** An unsubmitted draft no form came back for within a week is abandoned. */
        const val DRAFT_MAX_AGE_MILLIS: Long = 7 * 24 * 60 * 60 * 1_000L

        /** A queued upload outlives WorkManager's retries by far after a month; its leftovers are removed. */
        const val QUEUED_MAX_AGE_MILLIS: Long = 30 * 24 * 60 * 60 * 1_000L

        fun pageName(order: Int) = "$PAGE_PREFIX$order$PAGE_SUFFIX"

        fun pageOrder(name: String): Int? =
            name.takeIf { it.startsWith(PAGE_PREFIX) && it.endsWith(PAGE_SUFFIX) }
                ?.removePrefix(PAGE_PREFIX)?.removeSuffix(PAGE_SUFFIX)?.toIntOrNull()
    }
}
