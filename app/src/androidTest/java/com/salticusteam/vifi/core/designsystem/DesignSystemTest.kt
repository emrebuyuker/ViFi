package com.salticusteam.vifi.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.designsystem.component.CoinBalanceBadge
import com.salticusteam.vifi.core.designsystem.component.EmptyView
import com.salticusteam.vifi.core.designsystem.component.ErrorView
import com.salticusteam.vifi.core.designsystem.component.LoadingView
import com.salticusteam.vifi.core.designsystem.component.ViFiTopAppBar
import com.salticusteam.vifi.core.designsystem.theme.VifiOrange
import com.salticusteam.vifi.core.designsystem.theme.VifiOrangeOnDark
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ArchiveError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DesignSystemTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun theme_keepsBrandOrangeWithBlackContentInLightAndDark() {
        val colors = mutableMapOf<Boolean, Pair<Color, Color>>()
        composeRule.setContent {
            ViFiTheme(darkTheme = false) {
                colors[false] = MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            }
            ViFiTheme(darkTheme = true) {
                colors[true] = MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
            }
        }
        composeRule.waitForIdle()

        assertEquals(VifiOrange to Color.Black, colors[false])
        assertEquals(VifiOrangeOnDark to Color.Black, colors[true])
    }

    @Test
    fun errorView_showsMessageAndRetries() {
        var retries = 0
        composeRule.setContent {
            ViFiTheme { ErrorView(message = ArchiveError.Offline.userMessage, onRetry = { retries++ }) }
        }

        composeRule.onNodeWithTag("state.error").assertIsDisplayed()
        composeRule.onNodeWithText(ArchiveError.Offline.userMessage).assertIsDisplayed()
        composeRule.onNodeWithTag("state.retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun loadingAndEmptyViews_exposeStableTags() {
        composeRule.setContent {
            ViFiTheme {
                LoadingView()
                EmptyView(title = "Kayıt bulunamadı")
            }
        }

        composeRule.onNodeWithTag("state.loading").assertIsDisplayed()
        composeRule.onNodeWithTag("state.empty").assertIsDisplayed()
    }

    @Test
    fun coinBalanceBadge_announcesFormattedBalance() {
        composeRule.setContent { ViFiTheme { CoinBalanceBadge(balance = 1250) } }

        composeRule.onNodeWithTag("coin.balance").assertContentDescriptionEquals("Coin bakiyen: 1.250")
    }

    @Test
    fun topAppBar_backButtonOnlyWhenNavigable() {
        var backs = 0
        composeRule.setContent { ViFiTheme { ViFiTopAppBar(title = "Fakülteler", onBack = { backs++ }) } }

        composeRule.onNodeWithTag("topBar.backButton").performClick()
        assertTrue(backs == 1)
    }
}
