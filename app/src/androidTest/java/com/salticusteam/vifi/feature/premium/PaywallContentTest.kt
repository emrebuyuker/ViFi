package com.salticusteam.vifi.feature.premium

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.data.repository.fake.FakeSubscriptionService
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.StoreProductIds
import com.salticusteam.vifi.core.model.SubscriptionError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The paywall's states and actions on the stateless content (iOS PremiumUITests / PaywallView). */
@RunWith(AndroidJUnit4::class)
class PaywallContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val loaded = PaywallUiState(
        options = FakeSubscriptionService.SAMPLE_OPTIONS,
        selectedPlanId = StoreProductIds.YEARLY_PLAN,
    )

    private val selectedPlans = mutableListOf<String>()
    private var subscribes = 0
    private var restores = 0
    private var retries = 0
    private var closes = 0
    private var terms = 0
    private var privacy = 0

    private fun setContent(state: PaywallUiState) {
        composeRule.setContent {
            ViFiTheme {
                PaywallContent(
                    state = state,
                    onClose = { closes++ },
                    onPlanSelected = { selectedPlans += it },
                    onSubscribe = { subscribes++ },
                    onRestore = { restores++ },
                    onRetry = { retries++ },
                    onOpenTerms = { terms++ },
                    onOpenPrivacy = { privacy++ },
                )
            }
        }
    }

    @Test
    fun plans_yearlyIsSelectedAndSubscribeShowsItsPrice() {
        setContent(loaded)

        composeRule.onNodeWithTag("paywall.sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("paywall.option.yearly").assertIsSelected()
            .assert(hasText("En avantajlı", substring = true))
        composeRule.onNodeWithTag("paywall.option.monthly").assertIsNotSelected()
        composeRule.onNodeWithTag("paywall.subscribe").assertIsEnabled().assert(hasText("Abone Ol · ₺399,99"))

        composeRule.onNodeWithTag("paywall.option.monthly").performScrollTo().performClick()
        composeRule.onNodeWithTag("paywall.subscribe").performClick()

        assertEquals(listOf(StoreProductIds.MONTHLY_PLAN), selectedPlans)
        assertEquals(1, subscribes)
    }

    @Test
    fun loading_showsTheSpinnerAndNothingToBuy() {
        setContent(PaywallUiState())

        composeRule.onNodeWithTag("paywall.loading").assertIsDisplayed()
        composeRule.onNodeWithTag("paywall.subscribe").assertIsNotEnabled().assert(hasText("Abone Ol"))
    }

    @Test
    fun loadFailed_offersTheRetry() {
        setContent(PaywallUiState(loadFailed = true))

        composeRule.onNodeWithTag("paywall.loadFailed").assertIsDisplayed()
        composeRule.onNodeWithTag("paywall.retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun purchasing_locksClosePlansAndRestoreAndShowsProgress() {
        setContent(loaded.copy(isPurchasing = true))

        composeRule.onNodeWithTag("paywall.progress", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("paywall.subscribe").assertIsNotEnabled()
        composeRule.onNodeWithTag("paywall.restore").assertIsNotEnabled()
        composeRule.onNodeWithTag("paywall.close").assertIsNotEnabled()
        composeRule.onNodeWithTag("paywall.option.monthly").assertIsNotEnabled()
    }

    @Test
    fun restoring_locksSubscribeButNotClose() {
        setContent(loaded.copy(isRestoring = true))

        composeRule.onNodeWithTag("paywall.restore.progress", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("paywall.subscribe").assertIsNotEnabled()
        composeRule.onNodeWithTag("paywall.close").assertIsEnabled().performClick()

        assertEquals(1, closes)
    }

    @Test
    fun error_isShownAboveTheButton() {
        setContent(loaded.copy(error = SubscriptionError.StoreUnavailable.userMessage))

        composeRule.onNodeWithTag("paywall.error").assertIsDisplayed()
            .assert(hasText(SubscriptionError.StoreUnavailable.userMessage))
    }

    @Test
    fun notices_useTheirOwnText() {
        setContent(loaded.copy(notice = PaywallNotice.PURCHASE_PENDING))

        composeRule.onNodeWithTag("paywall.error")
            .assert(hasText("Satın alma onay bekliyor; onaylanınca aboneliğin başlar."))
    }

    @Test
    fun restoreAndLegalLinks_callBack() {
        setContent(loaded)

        composeRule.onNodeWithTag("paywall.restore").performClick()
        composeRule.onNodeWithTag("paywall.terms").performScrollTo().performClick()
        composeRule.onNodeWithTag("paywall.privacy").performScrollTo().performClick()

        assertEquals(1, restores)
        assertEquals(1, terms)
        assertEquals(1, privacy)
    }
}
