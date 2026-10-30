package com.salticusteam.vifi.feature.notificationsettings

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.data.notifications.settings.NotificationSettingsState.Availability
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.NotificationPermission
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Account › Bildirim Ayarları (iOS `NotificationPermissionUITests`, the settings screen's parts). */
@RunWith(AndroidJUnit4::class)
class NotificationSettingsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val taps = mutableListOf<String>()

    private fun show(state: NotificationSettingsUiState) {
        composeRule.setContent {
            ViFiTheme {
                NotificationSettingsContent(
                    state = state,
                    onEnable = { taps += "enable" },
                    onOpenSettings = { taps += "settings" },
                    onMarketingChange = { taps += if (it) "on" else "off" },
                    onBack = { taps += "back" },
                )
            }
        }
    }

    @Test
    fun settings_allowed_readsOpenAndOffersNothing() {
        show(NotificationSettingsUiState(NotificationPermission.AUTHORIZED, consentAvailability = Availability.READY))

        composeRule.onNodeWithTag("notificationSettings.list").assertIsDisplayed()
        composeRule.onNodeWithTag("notificationSettings.permission").assert(hasText("Açık")).assertIsDisplayed()
        composeRule.onNodeWithTag("notificationSettings.enable").assertDoesNotExist()
        composeRule.onNodeWithTag("notificationSettings.openSettings").assertDoesNotExist()
        composeRule.onNodeWithTag("notificationSettings.permissionNote").assertDoesNotExist()
    }

    @Test
    fun settings_notAsked_offersToEnable() {
        show(NotificationSettingsUiState(NotificationPermission.NOT_DETERMINED, systemCanAsk = true))

        composeRule.onNodeWithTag("notificationSettings.permission").assert(hasText("Sorulmadı"))
        composeRule.onNodeWithTag("notificationSettings.permissionNote")
            .assert(hasText("Bildirim izni henüz verilmedi."))
        composeRule.onNodeWithTag("notificationSettings.openSettings").assertDoesNotExist()
        composeRule.onNodeWithTag("notificationSettings.enable").assert(hasText("Bildirimleri Aç")).performClick()

        assertEquals(listOf("enable"), taps)
    }

    @Test
    fun settings_whileTheSystemDialogIsUp_enableIsDisabled() {
        show(
            NotificationSettingsUiState(
                NotificationPermission.NOT_DETERMINED,
                systemCanAsk = true,
                isRequestingPermission = true,
            ),
        )

        composeRule.onNodeWithTag("notificationSettings.enable").assertIsNotEnabled()
    }

    @Test
    fun settings_deniedForGood_sendsTheUserToSettings() {
        show(NotificationSettingsUiState(NotificationPermission.DENIED, systemCanAsk = false))

        composeRule.onNodeWithTag("notificationSettings.permission").assert(hasText("Kapalı"))
        composeRule.onNodeWithTag("notificationSettings.permissionNote")
            .assert(hasText("Bildirimler kapalı. Açmak için Ayarlar'a git."))
        composeRule.onNodeWithTag("notificationSettings.enable").assertDoesNotExist()
        composeRule.onNodeWithTag("notificationSettings.openSettings").assert(hasText("Ayarlar'ı Aç")).performClick()

        assertEquals(listOf("settings"), taps)
    }

    @Test
    fun settings_deniedButAskableAgain_offersToEnable() {
        show(NotificationSettingsUiState(NotificationPermission.DENIED, systemCanAsk = true))

        composeRule.onNodeWithTag("notificationSettings.permission").assert(hasText("Kapalı"))
        composeRule.onNodeWithTag("notificationSettings.enable").assertIsDisplayed()
        composeRule.onNodeWithTag("notificationSettings.openSettings").assertDoesNotExist()
    }

    @Test
    fun settings_beforeTheFirstRead_offersNothing() {
        show(NotificationSettingsUiState())

        composeRule.onNodeWithTag("notificationSettings.permission").assert(hasText("Yükleniyor…"))
        composeRule.onNodeWithTag("notificationSettings.enable").assertDoesNotExist()
        composeRule.onNodeWithTag("notificationSettings.openSettings").assertDoesNotExist()
        composeRule.onNodeWithTag("notificationSettings.marketing").assertIsOff().assertIsNotEnabled()
    }

    @Test
    fun settings_campaignSwitch_showsTheAnswerAndTakesAChange() {
        show(
            NotificationSettingsUiState(
                NotificationPermission.AUTHORIZED,
                consentAvailability = Availability.READY,
                marketingEnabled = true,
            ),
        )

        composeRule.onNodeWithTag("notificationSettings.marketing")
            .assert(hasText("Kampanya ve fırsat bildirimleri"))
            .assertIsOn()
            .assertIsEnabled()
            .performClick()

        assertEquals(listOf("off"), taps)
        composeRule.onNodeWithTag("notificationSettings.unavailable").assertDoesNotExist()
        composeRule.onNodeWithTag("notificationSettings.error").assertDoesNotExist()
    }

    @Test
    fun settings_whileSaving_theSwitchIsDisabled() {
        show(
            NotificationSettingsUiState(
                NotificationPermission.AUTHORIZED,
                consentAvailability = Availability.READY,
                marketingEnabled = true,
                isSavingConsent = true,
            ),
        )

        composeRule.onNodeWithTag("notificationSettings.marketing").assertIsOn().assertIsNotEnabled()
    }

    @Test
    fun settings_unreadableRecord_saysSoAndDisablesTheSwitch() {
        show(NotificationSettingsUiState(NotificationPermission.AUTHORIZED, consentAvailability = Availability.UNAVAILABLE))

        composeRule.onNodeWithTag("notificationSettings.marketing").assertIsOff().assertIsNotEnabled()
        composeRule.onNodeWithTag("notificationSettings.unavailable")
            .assert(hasText("Tercihin şu an okunamıyor. Bağlantını kontrol edip bu sayfayı tekrar aç."))
            .assertIsDisplayed()
    }

    @Test
    fun settings_failedSave_showsTheError() {
        show(
            NotificationSettingsUiState(
                NotificationPermission.AUTHORIZED,
                consentAvailability = Availability.READY,
                saveFailed = true,
            ),
        )

        composeRule.onNodeWithTag("notificationSettings.error")
            .assert(hasText("Tercihin kaydedilemedi. Bağlantını kontrol edip tekrar dene."))
            .assertIsDisplayed()
    }

    @Test
    fun settings_back_closesTheScreen() {
        show(NotificationSettingsUiState())

        composeRule.onNodeWithTag("topBar.backButton").performClick()

        assertEquals(listOf("back"), taps)
    }
}
