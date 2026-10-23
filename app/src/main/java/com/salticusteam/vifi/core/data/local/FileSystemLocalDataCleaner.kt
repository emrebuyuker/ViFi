package com.salticusteam.vifi.core.data.local

import com.salticusteam.vifi.core.data.preferences.RecentExamsRepository
import com.salticusteam.vifi.core.data.repository.RemoteFileLoader
import com.salticusteam.vifi.core.util.LogCategory
import javax.inject.Inject
import kotlinx.coroutines.CancellationException

/**
 * Clears the recents list (DataStore) and the exam file caches on disk.
 *
 * The file caches are purged through [RemoteFileLoader.purge] (VIEW-09), which owns their directories: it
 * cancels in-flight downloads first and clears the image loader's caches too, so nothing is left behind.
 */
class FileSystemLocalDataCleaner @Inject constructor(
    private val recents: RecentExamsRepository,
    private val fileLoader: RemoteFileLoader,
) : LocalDataCleaner {

    override suspend fun purgeAccountData() {
        try {
            recents.clear()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.FILES.log.e(e, "Clearing recent exams failed")
        }
        try {
            fileLoader.purge()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            LogCategory.FILES.log.e(e, "Purging the exam file caches failed")
        }
    }
}
