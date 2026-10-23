package com.salticusteam.vifi.core.data.files

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.salticusteam.vifi.core.data.firebase.FirebaseRequestAuthorizer
import com.salticusteam.vifi.core.data.repository.RequestAuthorizer
import com.salticusteam.vifi.core.di.ExamFilesClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

/** Live exam file downloads: Firebase Storage with the user's ID token and App Check token (ARCH-07). */
@Module
@InstallIn(SingletonComponent::class)
object ExamFilesNetworkModule {

    @Provides
    @Singleton
    fun provideRequestAuthorizer(
        auth: FirebaseAuth,
        appCheck: FirebaseAppCheck,
        storage: FirebaseStorage,
    ): RequestAuthorizer = FirebaseRequestAuthorizer(auth, appCheck, bucket = storage.reference.bucket)

    @Provides
    @Singleton
    @ExamFilesClient
    fun provideExamFilesClient(authorizer: RequestAuthorizer): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(StorageAuthInterceptor(authorizer))
        // A stalled connection is bounded per read; the whole call is generous so a large PDF on a slow link finishes.
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(300, TimeUnit.SECONDS)
        .build()
}
