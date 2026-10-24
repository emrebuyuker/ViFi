package com.salticusteam.vifi.feature.update

import androidx.compose.material3.Text
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.Modifier
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.AppUpdate
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppUpdateGateTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val update = AppUpdate(version = "4.0", storeUrl = "https://store", message = null)

    private fun setGate(
        state: AppUpdateUiState,
        onOpenStore: (AppUpdate) -> Unit = {},
        onDismissed: () -> Unit = {},
        onSkip: () -> Unit = {},
    ) = composeRule.setContent {
        ViFiTheme {
            AppUpdateGateContent(state, onOpenStore, onDismissed, onOptionalSkipChosen = onSkip) {
                Text("içerik", Modifier.testTag("app.content"))
            }
        }
    }

    @Test
    fun required_replacesContentAndShowsDefaultMessage() {
        setGate(AppUpdateUiState(required = update))

        composeRule.onNodeWithTag("forceUpdate.screen").assertIsDisplayed()
        composeRule.onNodeWithTag("forceUpdate.title").assertIsDisplayed()
        composeRule.onNodeWithTag("forceUpdate.message").assertIsDisplayed()
        composeRule.onNodeWithTag("app.content").assertDoesNotExist()
    }

    @Test
    fun required_updateButtonOpensStore() {
        var opened: AppUpdate? = null
        setGate(AppUpdateUiState(required = update), onOpenStore = { opened = it })

        composeRule.onNodeWithTag("forceUpdate.update").performClick()

        assertEquals(update, opened)
    }

    @Test
    fun required_backPressDoesNotDismissTheGate() {
        setGate(AppUpdateUiState(required = update))

        // With no handler the back press would finish the activity (Espresso then throws).
        Espresso.pressBack()
        composeRule.onNodeWithTag("forceUpdate.screen").assertIsDisplayed()
    }

    @Test
    fun optional_showsDialogOverContentAndLaterDismisses() {
        var dismissed = 0
        setGate(AppUpdateUiState(optional = update), onDismissed = { dismissed++ })

        composeRule.onNodeWithTag("app.content").assertIsDisplayed()
        composeRule.onNodeWithTag("optionalUpdate.dialog").assertIsDisplayed()
        composeRule.onNodeWithTag("optionalUpdate.later").performClick()

        assertEquals(1, dismissed)
    }

    @Test
    fun optional_updateOpensStoreAndDismisses() {
        var opened: AppUpdate? = null
        var dismissed = 0
        setGate(AppUpdateUiState(optional = update), onOpenStore = { opened = it }, onDismissed = { dismissed++ })

        composeRule.onNodeWithTag("optionalUpdate.update").performClick()

        assertEquals(update, opened)
        assertEquals(1, dismissed)
    }

    @Test
    fun optional_skipSkipsAndDismissesWithoutOpeningStore() {
        var opened: AppUpdate? = null
        var skipped = 0
        var dismissed = 0
        setGate(
            AppUpdateUiState(optional = update),
            onOpenStore = { opened = it },
            onDismissed = { dismissed++ },
            onSkip = { skipped++ },
        )

        composeRule.onNodeWithTag("optionalUpdate.skip").assertIsDisplayed().performClick()

        assertEquals(1, skipped)
        assertEquals(1, dismissed)
        assertEquals(null, opened)
    }

    @Test
    fun upToDate_showsContentOnly() {
        setGate(AppUpdateUiState())

        composeRule.onNodeWithTag("app.content").assertIsDisplayed()
        composeRule.onNodeWithTag("forceUpdate.screen").assertDoesNotExist()
        composeRule.onNodeWithTag("optionalUpdate.dialog").assertDoesNotExist()
    }
}
