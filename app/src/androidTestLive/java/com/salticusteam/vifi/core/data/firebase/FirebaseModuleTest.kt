package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.salticusteam.vifi.core.util.FirebaseConstants
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The Hilt graph resolves the live Firebase providers as process-wide singletons with the shared-backend settings. */
@HiltAndroidTest
class FirebaseModuleTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var firestore: FirebaseFirestore
    @Inject lateinit var sameFirestore: FirebaseFirestore
    @Inject lateinit var functions: FirebaseFunctions

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun firestore_isSingletonOnTheNamedVifiDatabase() {
        assertSame(firestore, sameFirestore)
        // The SDK caches one instance per database id: the injected one must be 'vifi', not DEFAULT.
        assertSame(
            firestore,
            FirebaseFirestore.getInstance(FirebaseApp.getInstance(), FirebaseConstants.FIRESTORE_DATABASE_ID),
        )
        assertNotSame(firestore, FirebaseFirestore.getInstance())
    }

    @Test
    fun functions_isPinnedToEuropeWest1() {
        assertSame(
            functions,
            FirebaseFunctions.getInstance(FirebaseApp.getInstance(), FirebaseConstants.FUNCTIONS_REGION),
        )
    }
}
