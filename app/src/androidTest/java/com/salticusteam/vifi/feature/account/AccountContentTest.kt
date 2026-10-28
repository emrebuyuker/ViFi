package com.salticusteam.vifi.feature.account

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AccountContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var editProfileClicks = 0
    private var signOutCalls = 0
    private var deleteCalls = 0
    private var cancelReverificationCalls = 0
    private val typedCodes = mutableListOf<String>()
    private var aboutClicks = 0
    private var upgradeClicks = 0
    private var manageClicks = 0

    private fun setContent(state: AccountUiState = AccountUiState("Ayşe Yılmaz", "+90 532 123 45 67")) {
        composeRule.setContent {
            ViFiTheme {
                AccountContent(
                    state = state,
                    resendSecondsRemaining = 0,
                    onEditProfile = { editProfileClicks++ },
                    onSignOut = { signOutCalls++ },
                    onDeleteAccount = { deleteCalls++ },
                    onCodeChange = { typedCodes += it },
                    onConfirmReverification = {},
                    onResendCode = {},
                    onCancelReverification = { cancelReverificationCalls++ },
                    onBack = {},
                    onAbout = { aboutClicks++ },
                    onUpgradePremium = { upgradeClicks++ },
                    onManageSubscription = { manageClicks++ },
                )
            }
        }
    }

    @Test
    fun account_showsProfileNameAndPhone() {
        setContent()

        composeRule.onNodeWithTag("account.editProfile").assert(hasText("Ayşe Yılmaz")).assertIsDisplayed()
        composeRule.onNodeWithTag("account.phone").assert(hasText("+90 532 123 45 67")).assertIsDisplayed()
        composeRule.onNodeWithTag("topBar.backButton").assertIsDisplayed()
    }

    @Test
    fun account_showsCoinBalance() {
        setContent(AccountUiState("Ayşe Yılmaz", "+90 532 123 45 67", coinBalance = 1250))

        // The row is one TalkBack element whose description carries the Turkish-grouped amount.
        composeRule.onNodeWithTag("account.coinBalance").assertIsDisplayed()
            .assert(hasContentDescription("1.250", substring = true))
    }

    @Test
    fun account_whenFree_offersPremiumAndShowsCoins() {
        setContent()

        composeRule.onNodeWithTag("account.coinBalance").assertIsDisplayed()
        composeRule.onNodeWithTag("account.premium.active").assertDoesNotExist()
        composeRule.onNodeWithTag("account.premium.upgrade")
            .assert(hasText("ViFi Premium'a Geç"))
            .performClick()

        assertEquals(1, upgradeClicks)
    }

    /** iOS `PremiumUITests.testAccountShowsActivePremiumAndHidesCoins`. */
    @Test
    fun account_whenSubscribed_showsActivePremiumAndHidesCoins() {
        setContent(AccountUiState("Ayşe Yılmaz", "+90 532 123 45 67", coinBalance = 1250, isSubscribed = true))

        composeRule.onNodeWithTag("account.premium.active").assertIsDisplayed()
            .assert(hasText("Tüm sınavlar açık", substring = true))
        composeRule.onNodeWithTag("account.coinBalance").assertDoesNotExist()
        composeRule.onNodeWithTag("account.premium.upgrade").assertDoesNotExist()

        composeRule.onNodeWithTag("account.premium.manage").performClick()
        assertEquals(1, manageClicks)
    }

    @Test
    fun account_whenAboutRowTapped_opensAbout() {
        setContent()

        composeRule.onNodeWithTag("account.about").performClick()

        assertEquals(1, aboutClicks)
    }

    @Test
    fun account_whenProfileRowTapped_opensTheEditor() {
        setContent()

        composeRule.onNodeWithTag("account.editProfile").performClick()

        assertEquals(1, editProfileClicks)
    }

    @Test
    fun account_signOutAsksForConfirmationFirst() {
        setContent()

        composeRule.onNodeWithTag("account.signOut").performClick()
        composeRule.onNodeWithTag("account.signOut.confirm").assertIsDisplayed()
        assertEquals(0, signOutCalls)

        composeRule.onNodeWithTag("account.signOut.confirm").performClick()

        assertEquals(1, signOutCalls)
        composeRule.onNodeWithTag("account.signOut.confirm").assertDoesNotExist()
    }

    @Test
    fun account_cancellingSignOutKeepsTheUserSignedIn() {
        setContent()

        composeRule.onNodeWithTag("account.signOut").performClick()
        composeRule.onNodeWithTag("account.signOut.cancel").performClick()

        assertEquals(0, signOutCalls)
        composeRule.onNodeWithTag("account.signOut.confirm").assertDoesNotExist()
    }

    @Test
    fun account_withoutProfileOrPhone_showsPlaceholders() {
        setContent(AccountUiState())

        composeRule.onNodeWithTag("account.editProfile").assert(hasText("Profili Düzenle"))
        composeRule.onNodeWithTag("account.phone").assert(hasText("Telefon numarası yok"))
    }

    @Test
    fun account_deleteAsksForConfirmationFirst() {
        setContent()

        composeRule.onNodeWithTag("account.delete").performClick()
        composeRule.onNodeWithTag("account.delete.confirm").assertIsDisplayed()
        assertEquals(0, deleteCalls)

        composeRule.onNodeWithTag("account.delete.confirm").performClick()

        assertEquals(1, deleteCalls)
        composeRule.onNodeWithTag("account.delete.confirm").assertDoesNotExist()
    }

    @Test
    fun account_cancellingDeletionDeletesNothing() {
        setContent()

        composeRule.onNodeWithTag("account.delete").performClick()
        composeRule.onNodeWithTag("account.delete.cancel").performClick()

        assertEquals(0, deleteCalls)
    }

    @Test
    fun account_whileReverifying_showsTheCodeEntryAndError() {
        setContent(
            AccountUiState(
                formattedPhoneNumber = "+90 532 123 45 67",
                isReverifying = true,
                codeText = "12",
                error = "Kod hatalı.",
            ),
        )

        composeRule.onNodeWithTag("account.reauth.code").assertIsDisplayed()
        composeRule.onNodeWithTag("account.reauth.verify").assertIsNotEnabled()
        composeRule.onNodeWithTag("account.delete").assertIsNotEnabled()
        composeRule.onNodeWithTag("account.error").assert(hasText("Kod hatalı."))

        composeRule.onNodeWithTag("account.reauth.code").performTextInput("3")
        composeRule.onNodeWithTag("account.reauth.cancel").performClick()

        assertEquals("123", typedCodes.last())
        assertEquals(1, cancelReverificationCalls)
    }

    @Test
    fun account_whileWorking_disablesEveryAction() {
        setContent(AccountUiState(formattedPhoneNumber = "+90 532 123 45 67", isWorking = true))

        composeRule.onNodeWithTag("account.delete").assertIsNotEnabled()
        composeRule.onNodeWithTag("account.signOut").assertIsNotEnabled()
        composeRule.onNodeWithTag("account.editProfile").assertIsNotEnabled()
        composeRule.onNodeWithTag("account.delete.progress").assertIsDisplayed()
    }
}
