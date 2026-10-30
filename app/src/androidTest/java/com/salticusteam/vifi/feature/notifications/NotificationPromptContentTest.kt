package com.salticusteam.vifi.feature.notifications

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.NotificationPermission
import com.salticusteam.vifi.core.model.NotificationPromptSource
import com.salticusteam.vifi.feature.notifications.NotificationPermissionFlow.Page
import com.salticusteam.vifi.feature.notifications.NotificationPermissionFlow.State
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The flow's two pages (iOS `NotificationPermissionUITests`, the parts that do not need the system dialog). */
@RunWith(AndroidJUnit4::class)
class NotificationPromptContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val taps = mutableListOf<String>()

    private fun show(
        state: State,
        source: NotificationPromptSource = NotificationPromptSource.PROMPT,
        height: Int = 720,
        fontScale: Float = 1f,
    ) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                ViFiTheme {
                    Box(Modifier.height(height.dp)) {
                        NotificationPromptContent(
                            state = state,
                            source = source,
                            onEnable = { taps += "enable" },
                            onOpenSettings = { taps += "settings" },
                            onNotNow = { taps += "notNow" },
                            onAnswer = { taps += if (it) "yes" else "no" },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun theFirstOfferExplainsTheBenefitsAndAsksTheSystemOnlyFromItsButton() {
        show(State(Page.PERMISSION, NotificationPermission.NOT_DETERMINED))

        composeRule.onNodeWithTag("notifications.priming").assertIsDisplayed()
        composeRule.onNodeWithText("Hiçbir şeyi kaçırma").assertIsDisplayed()
        composeRule.onNodeWithText("Bölümüne yeni sınav gelince ilk sen haberdar ol").assertIsDisplayed()
        composeRule.onNodeWithText("Gönderdiğin sınav onaylanınca coin'in yüklendiğini gör").assertIsDisplayed()
        composeRule.onNodeWithText("Önemli duyurular").assertIsDisplayed()
        composeRule.onNodeWithTag("notifications.priming.deniedNote").assertDoesNotExist()
        composeRule.onNodeWithTag("notifications.priming.openSettings").assertDoesNotExist()

        composeRule.onNodeWithTag("notifications.priming.enable").performClick()
        composeRule.onNodeWithTag("notifications.priming.dismiss").performClick()

        assertEquals(listOf("enable", "notNow"), taps)
    }

    @Test
    fun aReaskTheSystemNoLongerShowsOffersTheSettings() {
        show(
            State(Page.PERMISSION, NotificationPermission.DENIED, needsSettingsApp = true),
            source = NotificationPromptSource.REASK,
        )

        composeRule.onNodeWithText("Onaylanınca haber verelim mi?").assertIsDisplayed()
        composeRule.onNodeWithTag("notifications.priming.deniedNote").assertIsDisplayed()
        composeRule.onNodeWithText("Bildirimler şu an kapalı. Ayarlar'dan açabilirsin.").assertIsDisplayed()
        composeRule.onNodeWithTag("notifications.priming.enable").assertDoesNotExist()

        composeRule.onNodeWithTag("notifications.priming.openSettings").performClick()

        assertEquals(listOf("settings"), taps)
    }

    @Test
    fun whileTheSystemIsAskedNeitherButtonCanBeTapped() {
        show(State(Page.PERMISSION, NotificationPermission.NOT_DETERMINED, isWorking = true))

        composeRule.onNodeWithTag("notifications.priming.enable").assertIsNotEnabled()
        composeRule.onNodeWithTag("notifications.priming.dismiss").assertIsNotEnabled()
    }

    @Test
    fun theConsentStatesItsTermsAndOffersBothAnswersEqually() {
        show(State(Page.CONSENT, NotificationPermission.AUTHORIZED))

        composeRule.onNodeWithTag("notifications.consent").assertIsDisplayed()
        composeRule.onNodeWithText("Öğrenci fırsatları").assertIsDisplayed()
        composeRule.onNodeWithText("Kampüsüne yakın indirimler, öğrenciye özel kampanyalar ve Premium fırsatları.")
            .assertIsDisplayed()
        composeRule.onNodeWithText("Gece 22:00–09:00 arası asla.").assertIsDisplayed()
        composeRule.onNodeWithText("İstediğin an Hesap'tan kapat.").assertIsDisplayed()
        composeRule.onNodeWithTag("notifications.consent.error").assertDoesNotExist()

        composeRule.onNodeWithTag("notifications.consent.accept").performClick()
        composeRule.onNodeWithTag("notifications.consent.decline").performClick()

        assertEquals(listOf("yes", "no"), taps)
    }

    @Test
    fun aFailedSaveShowsTheErrorAndKeepsBothAnswers() {
        show(State(Page.CONSENT, NotificationPermission.AUTHORIZED, saveFailed = true))

        composeRule.onNodeWithTag("notifications.consent.error").assertIsDisplayed()
        composeRule.onNodeWithText("Tercihin kaydedilemedi. Bağlantını kontrol edip tekrar dene.").assertIsDisplayed()
        composeRule.onNodeWithTag("notifications.consent.accept").assertIsDisplayed()
        composeRule.onNodeWithTag("notifications.consent.decline").assertIsDisplayed()
    }

    @Test
    fun whileAnAnswerIsSavedNeitherCanBeTappedAgain() {
        show(State(Page.CONSENT, NotificationPermission.AUTHORIZED, isWorking = true, answering = false))

        composeRule.onNodeWithTag("notifications.consent.accept").assertIsNotEnabled()
        composeRule.onNodeWithTag("notifications.consent.decline").assertIsNotEnabled()
    }

    @Test
    fun atALargeFontScaleTheMessageScrollsAndTheAnswersStayInReach() {
        show(State(Page.CONSENT, NotificationPermission.AUTHORIZED), height = 360, fontScale = 2f)

        composeRule.onNodeWithTag("notifications.consent.accept").assertIsDisplayed()
        composeRule.onNodeWithTag("notifications.consent.decline").assertIsDisplayed()
    }
}
