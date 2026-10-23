package com.salticusteam.vifi.app

import androidx.lifecycle.ViewModel
import com.salticusteam.vifi.core.data.archive.ArchivePrefetcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Hands the singleton [ArchivePrefetcher] to the signed-in archive host. */
@HiltViewModel
class ArchivePrefetchViewModel @Inject constructor(private val prefetcher: ArchivePrefetcher) : ViewModel() {

    /**
     * Warms the offline cache if due. Suspends for the warm, so the caller's scope owns it: the archive host runs
     * it in a `LaunchedEffect`, which a sign-out (the host leaving the composition) cancels (iOS RootView `.task`).
     */
    suspend fun onArchiveShown() = prefetcher.prefetchIfNeeded()
}
