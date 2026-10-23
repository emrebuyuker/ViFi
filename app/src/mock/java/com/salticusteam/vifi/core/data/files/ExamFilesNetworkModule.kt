package com.salticusteam.vifi.core.data.files

import android.content.Context
import com.salticusteam.vifi.core.di.ExamFilesClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Mock exam file downloads: every request is answered from the bundled sample files, never the network. */
@Module
@InstallIn(SingletonComponent::class)
object ExamFilesNetworkModule {

    @Provides
    @Singleton
    @ExamFilesClient
    fun provideExamFilesClient(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(SampleFilesInterceptor(context.assets))
        .build()
}
