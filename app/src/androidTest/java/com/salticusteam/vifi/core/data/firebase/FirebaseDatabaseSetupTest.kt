package com.salticusteam.vifi.core.data.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.database.FirebaseDatabase
import org.junit.Test
import org.junit.runner.RunWith

/** Configuring twice must not crash (setPersistenceEnabled throws after first use — iOS double-init trap). */
@RunWith(AndroidJUnit4::class)
class FirebaseDatabaseSetupTest {

    @Test
    fun configure_isIdempotentAfterTheDatabaseIsInUse() {
        FirebaseDatabaseSetup.configure()
        FirebaseDatabase.getInstance().getReference("config") // first use
        FirebaseDatabaseSetup.configure()
    }
}
