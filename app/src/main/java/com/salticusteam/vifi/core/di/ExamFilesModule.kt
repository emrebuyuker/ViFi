package com.salticusteam.vifi.core.di

import android.content.Context
import coil.ImageLoader
import coil.annotation.ExperimentalCoilApi
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.salticusteam.vifi.core.data.files.OkHttpRemoteFileLoader
import com.salticusteam.vifi.core.data.files.PdfDocumentFactory
import com.salticusteam.vifi.core.data.files.PlatformPdfDocumentFactory
import com.salticusteam.vifi.core.data.repository.RemoteFileLoader
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient

/** The HTTP client exam files are downloaded with; each flavor provides it (live: credentialled, mock: sample files). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ExamFilesClient

/**
 * Exam file loading shared by both flavors (ARCH-08, VIEW-05/09): the PDF/file loader and the image loader
 * share the flavor's [ExamFilesClient], so both send the same credentials. Caches live under
 * `cacheDir/ExamFiles` (Files: local copies, Images: Coil's response cache).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExamFilesModule {

    @Binds
    abstract fun bindPdfDocumentFactory(impl: PlatformPdfDocumentFactory): PdfDocumentFactory

    companion object {
        private const val IMAGE_DISK_CACHE_BYTES = 512L * 1024 * 1024
        private const val IMAGE_MEMORY_CACHE_PERCENT = 0.25

        @Provides
        @Singleton
        fun provideImageLoader(
            @ApplicationContext context: Context,
            @ExamFilesClient client: OkHttpClient,
        ): ImageLoader = ImageLoader.Builder(context)
            .okHttpClient(client)
            .memoryCache { MemoryCache.Builder(context).maxSizePercent(IMAGE_MEMORY_CACHE_PERCENT).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(context.cacheDir, "ExamFiles/Images"))
                    .maxSizeBytes(IMAGE_DISK_CACHE_BYTES)
                    .build()
            }
            // Exam files never change behind a URL; Storage's cache headers would keep them off the disk cache.
            .respectCacheHeaders(false)
            .crossfade(true)
            .build()

        @OptIn(ExperimentalCoilApi::class) // DiskCache.clear
        @Provides
        @Singleton
        fun provideRemoteFileLoader(
            @ApplicationContext context: Context,
            @ExamFilesClient client: OkHttpClient,
            @ApplicationScope scope: CoroutineScope,
            clock: Clock,
            imageLoader: dagger.Lazy<ImageLoader>,
        ): RemoteFileLoader = OkHttpRemoteFileLoader(
            client = client,
            filesDirectory = File(context.cacheDir, "ExamFiles/Files"),
            scope = scope,
            ioDispatcher = Dispatchers.IO,
            clock = clock,
            clearImageCaches = {
                val loader = imageLoader.get()
                loader.memoryCache?.clear()
                withContext(Dispatchers.IO) { loader.diskCache?.clear() }
            },
        )
    }
}

/** Composables reach the shared image loader through this (they have no constructor to inject into). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ExamImageLoaderEntryPoint {
    fun examImageLoader(): ImageLoader
}
