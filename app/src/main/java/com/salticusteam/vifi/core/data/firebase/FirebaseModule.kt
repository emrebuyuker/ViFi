package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import com.salticusteam.vifi.core.util.FirebaseConstants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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

    /**
     * Default RTDB instance. Disk persistence is enabled in FM-15: it must be switched on before *any*
     * RTDB use, and the legacy Java screens still call `FirebaseDatabase.getInstance()` directly.
     */
    @Provides
    @Singleton
    fun provideDatabase(): FirebaseDatabase = FirebaseDatabase.getInstance()

    @Provides
    @Singleton
    fun provideStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideFunctions(): FirebaseFunctions =
        FirebaseFunctions.getInstance(FirebaseApp.getInstance(), FirebaseConstants.FUNCTIONS_REGION)
}
