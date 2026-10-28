package com.salticusteam.vifi.feature.premium

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.data.billing.SubscriptionStore
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeSubscriptionService
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.feature.account.AccountContent
import com.salticusteam.vifi.feature.account.AccountUiState
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * iOS `PremiumUITests.testUpgradingFromAccountOpensPaywallAndSubscribes`: the account's upgrade row opens the
 * paywall, which loads the plans from the mock store, buys the yearly plan and closes itself once the subscription
 * is active, revealing the active status on the account underneath. Real store + ViewModel, Fake Google Play.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PremiumFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val scope = MainScope()
    private val service = FakeSubscriptionService()
    private val store = SubscriptionStore(service, scope)

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun upgradingFromAccount_opensThePaywallAndSubscribes() {
        composeRule.setContent {
            ViFiTheme {
                var isShowingPaywall by remember { mutableStateOf(false) }
                val isSubscribed by store.isSubscribed.collectAsState()
                if (isShowingPaywall) {
                    PaywallScreen(
                        onClose = { isShowingPaywall = false },
                        viewModel = remember { PaywallViewModel(store, FakeAnalyticsTracker()) },
                    )
                } else {
                    AccountContent(
                        state = AccountUiState("Ayşe Yılmaz", "+90 532 123 45 67", isSubscribed = isSubscribed),
                        resendSecondsRemaining = 0,
                        onEditProfile = {},
                        onSignOut = {},
                        onDeleteAccount = {},
                        onCodeChange = {},
                        onConfirmReverification = {},
                        onResendCode = {},
                        onCancelReverification = {},
                        onBack = {},
                        onUpgradePremium = { isShowingPaywall = true },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("account.premium.upgrade").performClick()
        composeRule.onNodeWithTag("paywall.sheet").assertIsDisplayed()
        // The mock store answers after a short latency; the subscribe button is enabled once the plans are in.
        composeRule.waitUntilExactlyOneExists(hasTestTag("paywall.option.yearly"), TIMEOUT_MS)

        composeRule.onNodeWithTag("paywall.subscribe").performClick()

        composeRule.waitUntilExactlyOneExists(hasTestTag("account.premium.active"), TIMEOUT_MS)
        composeRule.onNodeWithTag("account.coinBalance").assertDoesNotExist()
        composeRule.onNodeWithTag("account.premium.upgrade").assertDoesNotExist()
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
