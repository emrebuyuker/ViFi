package com.salticusteam.vifi.feature.browse

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.data.billing.SubscriptionStore
import com.salticusteam.vifi.core.data.coins.CoinWalletStore
import com.salticusteam.vifi.core.data.coins.ExamUnlocksStore
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeAuthService
import com.salticusteam.vifi.core.data.repository.fake.FakeExamPurchaseService
import com.salticusteam.vifi.core.data.repository.fake.FakeExamUnlockService
import com.salticusteam.vifi.core.data.repository.fake.FakeSubscriptionService
import com.salticusteam.vifi.core.data.repository.fake.FakeUnlocksReader
import com.salticusteam.vifi.core.data.repository.fake.FakeWalletReader
import com.salticusteam.vifi.core.data.session.SessionStore
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSummary
import com.salticusteam.vifi.core.model.ExamUnlockError
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ExamUnlockContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val scope = MainScope()

    @After
    fun tearDown() {
        scope.cancel()
    }

    private val lesson = ArchivePath(listOf("itu", "muh", "blg", "blg101"), listOf("İTÜ", "MÜH", "BLG", "BLG 101"))
    private fun exam(id: String, name: String) =
        ArchiveItem(lesson.appending(id, name), ArchiveLevel.EXAM, null, ExamSummary(ExamKind.PDF, 1))

    // Newest first: only "2024 Final" is locked.
    private val exams = listOf(exam("f24", "2024 Final"), exam("v23", "2023 Vize"), exam("f22", "2022 Final"))

    @Test
    fun lockedRow_tapOffersUnlock_freeRowOpens_badgeShowsBalance() {
        var opened: ArchiveItem? = null
        var lockedTap: ArchiveItem? = null
        composeRule.setContent {
            ViFiTheme {
                BrowseContent(
                    state = BrowseUiState(path = lesson, isLoading = false, items = exams),
                    onSearchTextChange = {}, onItemClick = { opened = it }, onRetry = {}, onRefresh = {}, onBack = {},
                    lockState = ExamUnlockUiState(balance = 120),
                    onLockedExamTap = { lockedTap = it },
                )
            }
        }

        composeRule.onNodeWithTag("coin.balance").assertIsDisplayed()
        composeRule.onNodeWithTag("row.lock", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("row.2024 Final").performClick()
        assertEquals(exams[0], lockedTap)
        assertNull(opened)

        composeRule.onNodeWithTag("row.2022 Final").performClick()
        assertEquals(exams[2], opened)
    }

    @Test
    fun sheet_whenAffordable_confirmsUnlock() {
        var unlocks = 0
        composeRule.setContent {
            ViFiTheme {
                ExamUnlockSheetContent("2024 Final", ExamUnlockUiState(balance = 120), { unlocks++ }, null, {})
            }
        }

        composeRule.onNodeWithTag("exam.unlock.confirm").performClick()
        assertEquals(1, unlocks)
    }

    @Test
    fun sheet_whenUnlocking_disablesConfirmAndClose() {
        composeRule.setContent {
            ViFiTheme {
                ExamUnlockSheetContent("2024 Final", ExamUnlockUiState(balance = 120, isUnlocking = true), {}, null, {})
            }
        }

        composeRule.onNodeWithTag("exam.unlock.confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("exam.unlock.close").assertIsNotEnabled()
    }

    @Test
    fun sheet_whenBalanceLow_offersEarnAndShowsError() {
        var earn = 0
        composeRule.setContent {
            ViFiTheme {
                ExamUnlockSheetContent(
                    "2024 Final",
                    ExamUnlockUiState(balance = 20, error = ExamUnlockError.InsufficientCoins.userMessage),
                    {}, { earn++ }, {},
                )
            }
        }

        composeRule.onNodeWithTag("exam.unlock.error").assertIsDisplayed()
        composeRule.onNodeWithTag("exam.unlock.earn").performClick()
        assertEquals(1, earn)
    }

    // --- 4.0.0: buying with money and Premium (UI-02 / UI-03) ---

    @Test
    fun sheet_offersMoneyUnlockAndPremium() {
        var purchases = 0
        var premium = 0
        composeRule.setContent {
            ViFiTheme {
                ExamUnlockSheetContent(
                    "2024 Final", ExamUnlockUiState(balance = 20), {}, {}, {},
                    onPurchaseWithMoney = { purchases++ },
                    onGoPremium = { premium++ },
                )
            }
        }

        composeRule.onNodeWithTag("exam.unlock.buy").performScrollTo().performClick()
        composeRule.onNodeWithTag("exam.unlock.premium").performScrollTo().performClick()
        assertEquals(1, purchases)
        assertEquals(1, premium)
    }

    @Test
    fun sheet_withoutPaywall_hidesThePremiumLink() {
        composeRule.setContent {
            ViFiTheme { ExamUnlockSheetContent("2024 Final", ExamUnlockUiState(balance = 120), {}, null, {}) }
        }

        composeRule.onNodeWithTag("exam.unlock.buy").assertExists()
        composeRule.onNodeWithTag("exam.unlock.premium").assertDoesNotExist()
    }

    @Test
    fun sheet_whilePurchasing_locksEveryActionAndClosing() {
        composeRule.setContent {
            ViFiTheme {
                ExamUnlockSheetContent(
                    "2024 Final", ExamUnlockUiState(balance = 120, isPurchasing = true), {}, {}, {},
                    onGoPremium = {},
                )
            }
        }

        composeRule.onNodeWithTag("exam.unlock.buy.progress", useUnmergedTree = true).assertExists()
        composeRule.onNodeWithTag("exam.unlock.buy").assertIsNotEnabled()
        composeRule.onNodeWithTag("exam.unlock.confirm").assertIsNotEnabled()
        composeRule.onNodeWithTag("exam.unlock.premium").assertIsNotEnabled()
        composeRule.onNodeWithTag("exam.unlock.close").assertIsNotEnabled()
    }

    @Test
    fun sheet_whenPaymentPending_saysTheExamOpensLater() {
        composeRule.setContent {
            ViFiTheme {
                ExamUnlockSheetContent("2024 Final", ExamUnlockUiState(balance = 20, isPurchasePending = true), {}, {}, {})
            }
        }

        composeRule.onNodeWithTag("exam.unlock.error").performScrollTo()
            .assert(hasText("Satın alma onay bekliyor; onaylanınca sınav açılır."))
    }

    /** iOS `PremiumUITests.testSubscriberHasNoLockedExamsAndNoCoinBadge`. */
    @Test
    fun subscriber_hasNoLockedExamsAndNoCoinBadge() {
        var opened: ArchiveItem? = null
        var lockedTap: ArchiveItem? = null
        composeRule.setContent {
            ViFiTheme {
                BrowseContent(
                    state = BrowseUiState(path = lesson, isLoading = false, items = exams),
                    onSearchTextChange = {}, onItemClick = { opened = it }, onRetry = {}, onRefresh = {}, onBack = {},
                    lockState = ExamUnlockUiState(balance = 120, isSubscribed = true),
                    onLockedExamTap = { lockedTap = it },
                )
            }
        }

        composeRule.onNodeWithTag("coin.balance").assertDoesNotExist()
        composeRule.onNodeWithTag("row.lock", useUnmergedTree = true).assertDoesNotExist()
        // The newest exam is locked for a free user; a subscriber opens it straight away.
        composeRule.onNodeWithTag("row.2024 Final").performClick()
        assertEquals(exams[0], opened)
        assertNull(lockedTap)
    }

    /**
     * iOS `ExamUnlockUITests.testBuyingALockedExamWithMoneyOpensIt`: a locked exam, no coins; buying it with money
     * (Fake Google Play + server) closes the sheet and opens the exam. Real ViewModel and stores, Fake services.
     */
    @Test
    fun buyingALockedExamWithMoney_opensIt() {
        val session = SessionStore(FakeAuthService(), scope)
        val unlocksStore = ExamUnlocksStore(FakeUnlocksReader(), session, scope)
        val purchases = FakeExamPurchaseService()
        val viewModel = ExamUnlockViewModel(
            walletStore = CoinWalletStore(FakeWalletReader(balance = 0), session, scope),
            unlocksStore = unlocksStore,
            subscriptionStore = SubscriptionStore(FakeSubscriptionService(), scope),
            unlockService = FakeExamUnlockService(),
            purchaseService = purchases,
            analytics = FakeAnalyticsTracker(),
        )
        var opened: ArchiveItem? = null
        composeRule.setContent {
            ViFiTheme {
                val lockState by viewModel.uiState.collectAsStateWithLifecycle()
                LaunchedEffect(Unit) {
                    viewModel.events.collect { event ->
                        if (event is ExamUnlockEvent.Unlocked) opened = event.exam
                    }
                }
                BrowseContent(
                    state = remember { BrowseUiState(path = lesson, isLoading = false, items = exams) },
                    onSearchTextChange = {}, onItemClick = { opened = it }, onRetry = {}, onRefresh = {}, onBack = {},
                    lockState = lockState,
                    onLockedExamTap = viewModel::onLockedExamTap,
                )
                lockState.examToUnlock?.let { exam ->
                    ExamUnlockSheet(
                        examName = exam.name,
                        state = lockState,
                        onUnlock = viewModel::onUnlockConfirm,
                        onPurchaseWithMoney = viewModel::onPurchaseWithMoney,
                        onEarnCoins = null,
                        onGoPremium = null,
                        onDismiss = viewModel::onSheetDismiss,
                    )
                }
            }
        }

        composeRule.onNodeWithTag("row.2024 Final").performClick()
        composeRule.waitUntilExactlyOneExists(hasTestTag("exam.unlock.buy"), TIMEOUT_MS)
        composeRule.onNodeWithTag("exam.unlock.buy").performScrollTo().performClick()

        composeRule.waitUntil(TIMEOUT_MS) { opened != null }
        assertEquals(exams[0], opened)
        assertEquals(listOf("f24"), purchases.purchasedExamIds)
        assertTrue(unlocksStore.isUnlocked("f24"))
        composeRule.waitUntilDoesNotExist(hasTestTag("exam.unlock.sheet"), TIMEOUT_MS)
    }

    private companion object {
        const val TIMEOUT_MS = 5_000L
    }
}
