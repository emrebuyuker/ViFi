package com.salticusteam.vifi.feature.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val finished = mutableListOf<String>()

    private fun setContent() {
        composeRule.setContent {
            ViFiTheme {
                OnboardingContent(pages = OnboardingPage.tour, onFinish = { finished += it })
            }
        }
    }

    @Test
    fun tour_startsOnTheFirstPageWithSkipAndNext() {
        setContent()

        composeRule.onNodeWithTag("onboarding.page.0").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding.skip").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding.next").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding.start").assertDoesNotExist()
    }

    @Test
    fun tour_nextAdvancesToTheLastPageWhereStartReplacesSkipAndNext() {
        setContent()

        repeat(3) {
            composeRule.onNodeWithTag("onboarding.next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("onboarding.page.3").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding.start").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding.skip").assertDoesNotExist()
        composeRule.onNodeWithTag("onboarding.next").assertDoesNotExist()
    }

    @Test
    fun tour_swipingMovesToTheNextPage() {
        setContent()

        composeRule.onNodeWithTag("onboarding.pager").performTouchInput { swipeLeft() }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("onboarding.page.1").assertIsDisplayed()
        composeRule.onNodeWithTag("onboarding.dots").assert(hasContentDescription("Sayfa 2 / 4"))
    }

    @Test
    fun tour_skipFinishesWithSkipped() {
        setContent()

        composeRule.onNodeWithTag("onboarding.skip").performClick()

        assertEquals(listOf("skipped"), finished)
    }

    @Test
    fun tour_startOnTheLastPageFinishesWithFinished() {
        setContent()
        repeat(3) {
            composeRule.onNodeWithTag("onboarding.next").performClick()
            composeRule.waitForIdle()
        }

        composeRule.onNodeWithTag("onboarding.start").performClick()

        assertEquals(listOf("finished"), finished)
    }

    @Test
    fun tour_pagesCarryTheIosCopy() {
        setContent()

        composeRule.onNodeWithTag("onboarding.page.0").assert(hasText("Üniversitelerin geçmiş sınavları", substring = true, ignoreCase = false))
    }
}
