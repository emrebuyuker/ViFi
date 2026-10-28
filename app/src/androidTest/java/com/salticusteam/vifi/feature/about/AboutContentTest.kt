package com.salticusteam.vifi.feature.about

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasTestTag
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AboutContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private var replayClicks = 0
    private var contactClicks = 0
    private var termsClicks = 0
    private var privacyClicks = 0
    private var backClicks = 0

    private fun setContent() {
        composeRule.setContent {
            ViFiTheme {
                AboutContent(
                    versionName = "2.5",
                    versionCode = "16",
                    onReplayTour = { replayClicks++ },
                    onContact = { contactClicks++ },
                    onOpenTerms = { termsClicks++ },
                    onOpenPrivacy = { privacyClicks++ },
                    onBack = { backClicks++ },
                )
            }
        }
    }

    private fun scrollTo(tag: String) {
        composeRule.onNodeWithTag("about.list").performScrollToNode(hasTestTag(tag))
    }

    @Test
    fun about_showsTheVersionAndBackButton() {
        setContent()

        composeRule.onNodeWithTag("about.version", useUnmergedTree = true).assert(hasText("Sürüm 2.5 (16)")).assertIsDisplayed()
        composeRule.onNodeWithTag("topBar.backButton").performClick()
        assertEquals(1, backClicks)
    }

    @Test
    fun about_replayRowStartsTheTour() {
        setContent()
        scrollTo("about.replayOnboarding")

        composeRule.onNodeWithTag("about.replayOnboarding").assertIsDisplayed().performClick()

        assertEquals(1, replayClicks)
    }

    @Test
    fun about_contactRowOpensSupport() {
        setContent()
        scrollTo("about.contact")

        composeRule.onNodeWithTag("about.contact").assertIsDisplayed().performClick()

        assertEquals(1, contactClicks)
    }

    @Test
    fun about_legalRowsOpenTheTermsAndThePrivacyPolicy() {
        setContent()
        scrollTo("about.terms")
        composeRule.onNodeWithTag("about.terms").assertIsDisplayed().performClick()
        scrollTo("about.privacy")
        composeRule.onNodeWithTag("about.privacy").assertIsDisplayed().performClick()

        assertEquals(1, termsClicks)
        assertEquals(1, privacyClicks)
    }

    @Test
    fun about_showsTheDataSourceNote() {
        setContent()
        scrollTo("about.dataSource")

        composeRule.onNodeWithTag("about.dataSource").assertIsDisplayed()
    }
}
