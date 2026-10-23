package com.salticusteam.vifi.core.data.firebase

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import com.salticusteam.vifi.core.util.FirebaseConstants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Live Firebase SDK instances (iOS `AppEnvironment.live()` parity). Repositories get these through
 * constructor injection; calling `getInstance()` at a call site is not allowed in new code.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    /** Named 'vifi' database with an on-disk cache; settings are applied once, before the first use. */
    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore =
        FirebaseFirestore.getInstance(FirebaseApp.getInstance(), FirebaseConstants.FIRESTORE_DATABASE_ID).apply {
            firestoreSettings = firestoreSettings { setLocalCacheSettings(persistentCacheSettings { }) }
        }

    /** Default RTDB instance; persistence is configured once in VifiApplication ([FirebaseDatabaseSetup]). */
    @Provides
    @Singleton
    fun provideDatabase(): FirebaseDatabase = FirebaseDatabase.getInstance().also { FirebaseDatabaseSetup.configure(it) }

    @Provides
    @Singleton
    fun provideStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    /** The provider factory is installed by BackendStartup before any backend call. */
    @Provides
    @Singleton
    fun provideAppCheck(): FirebaseAppCheck = FirebaseAppCheck.getInstance()

    @Provides
    @Singleton
    fun provideFunctions(): FirebaseFunctions =
        FirebaseFunctions.getInstance(FirebaseApp.getInstance(), FirebaseConstants.FUNCTIONS_REGION)

    @Provides
    @Singleton
    fun provideFirebaseMessaging(): FirebaseMessaging = FirebaseMessaging.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseAnalytics(@ApplicationContext context: Context): FirebaseAnalytics =
        FirebaseAnalytics.getInstance(context)
}
