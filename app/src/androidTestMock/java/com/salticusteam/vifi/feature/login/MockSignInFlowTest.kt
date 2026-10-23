package com.salticusteam.vifi.feature.login

import android.content.Intent
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.salticusteam.vifi.app.MainActivity
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.util.LaunchArg
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain

/** The session gate end to end on sample data: signed out → phone → code → archive (iOS LoginUITests). */
@OptIn(ExperimentalTestApi::class)
@HiltAndroidTest
class MockSignInFlowTest {

    private val hiltRule = HiltAndroidRule(this)
    private val composeRule = createEmptyComposeRule()

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(hiltRule).around(composeRule)

    private fun launch(vararg args: LaunchArg): ActivityScenario<MainActivity> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java)
        args.forEach { intent.putExtra(it.extraKey, true) }
        return ActivityScenario.launch(intent)
    }

    @Test
    fun signedIn_opensTheArchiveDirectly() {
        launch().use {
            composeRule.waitUntilExactlyOneExists(hasTestTag("browse.list"), TIMEOUT_MS)
        }
    }

    @Test
    fun signedOut_signsInWithTheCodeAndOpensTheArchive() {
        launch(LaunchArg.SIGNED_OUT).use {
            composeRule.waitUntilExactlyOneExists(hasTestTag("login.phone"), TIMEOUT_MS)
            composeRule.onNodeWithTag("login.phone").performTextInput("5321234567")
            composeRule.onNodeWithTag("login.sendCode").performClick()

            composeRule.waitUntilExactlyOneExists(hasTestTag("login.code"), TIMEOUT_MS)
            // The sixth digit submits on its own.
            composeRule.onNodeWithTag("login.code").performTextInput(FakeAuthService.VALID_CODE)

            composeRule.waitUntilExactlyOneExists(hasTestTag("browse.list"), TIMEOUT_MS)
        }
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
