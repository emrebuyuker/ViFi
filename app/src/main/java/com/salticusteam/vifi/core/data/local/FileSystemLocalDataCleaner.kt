package com.salticusteam.vifi.core.data.local

import android.content.Context
import com.salticusteam.vifi.core.data.preferences.RecentExamsRepository
import com.salticusteam.vifi.core.util.LogCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Clears the recents list (DataStore) and deletes the exam file cache directories from disk.
 *
 * The directory name is fixed here until the exam viewers' file loader (VIEW-09, `RemoteFileLoader.purge()`)
 * lands; it must stay the same as the loader's, or a deletion leaves the files behind.
 */
class FileSystemLocalDataCleaner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recents: RecentExamsRepository,
) : LocalDataCleaner {

    override suspend fun purgeAccountData() {
        try {
            recents.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.FILES.log.e(e, "Clearing recent exams failed")
        }
        withContext(Dispatchers.IO) {
            cacheDirectories().forEach { directory ->
                if (directory.exists() && !directory.deleteRecursively()) {
                    LogCategory.FILES.log.w("Exam file cache could not be fully removed")
                }
            }
        }
    }

    /** Where downloaded exam files live: the evictable cache and, for kept PDFs, app files. */
    private fun cacheDirectories(): List<File> =
        listOf(File(context.cacheDir, EXAM_FILES_DIRECTORY), File(context.filesDir, EXAM_FILES_DIRECTORY))

    companion object {
        /** The exam file cache directory name, under `cacheDir` (and `filesDir`). */
        const val EXAM_FILES_DIRECTORY = "exam_files"
    }
}
