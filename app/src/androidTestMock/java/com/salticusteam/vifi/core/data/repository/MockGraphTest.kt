package com.salticusteam.vifi.core.data.repository

import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeExamUnlockService
import com.salticusteam.vifi.core.model.ArchivePath
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** The mock flavor's graph serves sample data and never initialises Firebase (iOS -ViFiMockData). */
@HiltAndroidTest
class MockGraphTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject lateinit var archiveRepository: ArchiveRepository
    @Inject lateinit var examUnlockService: ExamUnlockService
    @Inject lateinit var authService: AuthService
    @Inject lateinit var loginDiagnosticsReporter: LoginDiagnosticsReporter

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @Test
    fun graph_bindsFakesOnly() = runTest {
        assertTrue(archiveRepository is FakeArchiveRepository)
        assertTrue(examUnlockService is FakeExamUnlockService)
        assertTrue(authService is FakeAuthService)
        assertTrue(loginDiagnosticsReporter === NoopLoginDiagnosticsReporter)
        assertTrue(archiveRepository.items(ArchivePath.ROOT).isNotEmpty())
    }

    @Test
    fun firebase_isNeverInitialised() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(FirebaseApp.getApps(context).isEmpty())
    }
}
