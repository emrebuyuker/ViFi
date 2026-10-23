package com.salticusteam.vifi.core.data.catalog

import android.content.Context
import com.salticusteam.vifi.core.util.LogCategory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * The catalog bundled under `assets/catalog/` (iOS `UniversityCatalog.bundled()`), parsed once on
 * [Dispatchers.IO] on first use and then cached for the process. Firebase-free, so both flavors use it as is.
 */
@Singleton
class BundledUniversityCatalog @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    // Internal state
    private val mutex = Mutex()
    private var cached: UniversityCatalog? = null

    suspend fun catalog(): UniversityCatalog = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            UniversityCatalog.load(
                open = context.assets::open,
                onError = { asset, error -> LogCategory.APP.log.e(error, "Profile catalog %s is missing or invalid", asset) },
            )
        }.also { cached = it }
    }
}
