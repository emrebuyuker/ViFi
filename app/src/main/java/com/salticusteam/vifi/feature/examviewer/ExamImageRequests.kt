package com.salticusteam.vifi.feature.examviewer

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil.ImageLoader
import coil.network.HttpException
import coil.request.ImageRequest
import com.salticusteam.vifi.core.data.files.ArchiveIOException
import com.salticusteam.vifi.core.data.files.ExamFileUrls
import com.salticusteam.vifi.core.di.ExamImageLoaderEntryPoint
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ExamFileError
import dagger.hilt.android.EntryPointAccessors
import java.io.IOException

/** The app's exam image loader (shared OkHttp client with credentials, 512 MB disk cache). */
@Composable
fun rememberExamImageLoader(): ImageLoader {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(context, ExamImageLoaderEntryPoint::class.java).examImageLoader()
    }
}

/**
 * Memory-cache keys of a page: the list card's image is reused as the full-screen placeholder (iOS
 * `pagePreviews` handoff), the full-screen one is dropped when its page leaves the screen.
 */
internal object PageCacheKeys {
    fun list(url: String): String = ExamFileUrls.canonical(url) + "#list"
    fun fullScreen(url: String): String = ExamFileUrls.canonical(url) + "#full"
}

/** Builds a page request; [attempt] is part of the request so a retry re-runs it. */
internal fun pageRequest(context: Context, url: String, attempt: Int, configure: ImageRequest.Builder.() -> Unit) =
    ImageRequest.Builder(context)
        .data(ExamFileUrls.canonical(url))
        .setParameter("attempt", attempt, memoryCacheKey = null)
        .apply(configure)
        .build()

/** The Turkish message for a page that failed to load (iOS `error.userMessage`). */
internal fun Throwable.pageErrorMessage(): String = when (this) {
    is ArchiveIOException -> error.userMessage
    is HttpException -> when {
        // Offline, Coil asks for a cache-only response; OkHttp answers a cache miss with a synthetic 504.
        response.code == 504 && response.request.cacheControl.onlyIfCached -> ArchiveError.Offline.userMessage
        response.code == 401 || response.code == 403 -> ArchiveError.PermissionDenied.userMessage
        else -> ArchiveError.FileUnavailable(response.code).userMessage
    }
    is IOException -> ArchiveError.Offline.userMessage
    else -> ExamFileError.UndecodableImage.userMessage
}

/**
 * Whether neighbour pages may be prefetched (VIEW-04): only on an unmetered network with Data Saver off, so a
 * prefetch never spends cellular data. The page on screen always loads.
 */
internal fun Context.allowsSpeculativeDownloads(): Boolean {
    val connectivity = getSystemService(ConnectivityManager::class.java) ?: return false
    if (connectivity.restrictBackgroundStatus == ConnectivityManager.RESTRICT_BACKGROUND_STATUS_ENABLED) return false
    val capabilities = connectivity.getNetworkCapabilities(connectivity.activeNetwork) ?: return false
    if (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)) return true
    return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_TEMPORARILY_NOT_METERED)
}
