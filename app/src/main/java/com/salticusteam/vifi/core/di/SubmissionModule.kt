package com.salticusteam.vifi.core.di

import android.content.Context
import androidx.work.WorkManager
import com.salticusteam.vifi.core.data.files.ContentResolverSubmissionFileSource
import com.salticusteam.vifi.core.data.repository.SubmissionFileSource
import com.salticusteam.vifi.core.data.submission.FileSubmissionDraftStore
import com.salticusteam.vifi.core.data.submission.SubmissionDraftStore
import com.salticusteam.vifi.core.data.submission.SubmissionQueue
import com.salticusteam.vifi.core.data.submission.WorkManagerSubmissionQueue
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.time.Clock
import javax.inject.Singleton

/**
 * Picked-file reading, the on-disk drafts and the background upload queue are Firebase-free, so both flavors share
 * these bindings (only the `ExamSubmissionService` the upload worker calls differs per flavor).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SubmissionModule {

    @Binds
    abstract fun bindSubmissionFileSource(impl: ContentResolverSubmissionFileSource): SubmissionFileSource

    @Binds
    @Singleton
    abstract fun bindSubmissionQueue(impl: WorkManagerSubmissionQueue): SubmissionQueue

    companion object {
        @Provides
        @Singleton
        fun provideSubmissionDraftStore(@ApplicationContext context: Context, clock: Clock): SubmissionDraftStore =
            FileSubmissionDraftStore(File(context.noBackupFilesDir, "exam_submissions"), clock)

        /** Configured with Hilt's worker factory by `VifiApplication` (on-demand initialization). */
        @Provides
        @Singleton
        fun provideWorkManager(@ApplicationContext context: Context): WorkManager = WorkManager.getInstance(context)
    }
}
