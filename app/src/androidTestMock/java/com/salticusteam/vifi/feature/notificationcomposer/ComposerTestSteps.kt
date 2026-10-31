package com.salticusteam.vifi.feature.notificationcomposer

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.salticusteam.vifi.testing.TIMEOUT_MS
import com.salticusteam.vifi.testing.hasTestTagPrefix
import com.salticusteam.vifi.testing.tapWhenSingle
import com.salticusteam.vifi.testing.waitForTag
import com.salticusteam.vifi.testing.waitForTagGone

/** Steps shared by the admin flows on the mock app (iOS `NotificationComposerUITests` / `LocationCampaignUITests`). */

/** The history screen's tag prefix (the screen keeps its own constant private). */
const val HISTORY_PREFIX = "$COMPOSER_TAG.history"

fun ComposeTestRule.composer(name: String): SemanticsNodeInteraction = onNodeWithTag("$COMPOSER_TAG.$name")

fun ComposeTestRule.fillTitleAndBody(title: String, body: String = "Bölümüne 2024 finalleri eklendi.") {
    composer("title").performScrollTo().performTextInput(title)
    composer("body").performScrollTo().performTextInput(body)
}

/** Opens the dropdown [field] (`action`, `university`, …) and picks [option] (its full tag). */
fun ComposeTestRule.pickComposerOption(field: String, option: String) {
    composer(field).performScrollTo().performClick()
    onNodeWithTag(option).performClick()
    waitForIdle()
}

/** "Gönder", then "Gönder"/"Zamanla" in the confirmation; waits for the result line. */
fun ComposeTestRule.sendAndConfirm() {
    composer("send").performScrollTo().performClick()
    waitForTag("$COMPOSER_TAG.confirm")
    composer("confirm").performClick()
    waitForTagGone("$COMPOSER_TAG.confirm")
    waitForTag("$COMPOSER_TAG.success")
}

/** The composer's "Geçmiş" → the sent-notification history. */
fun ComposeTestRule.openHistory() {
    tapWhenSingle("$COMPOSER_TAG.history")
    waitForTag("$HISTORY_PREFIX.list")
}

/** The history row of the notification titled [title] (its id is the draft's, unknown to the test). */
fun ComposeTestRule.historyRow(title: String): SemanticsNodeInteraction {
    val row = hasTestTagPrefix("$HISTORY_PREFIX.row.") and hasText(title, substring = true)
    waitUntil(TIMEOUT_MS) { onAllNodes(row).fetchSemanticsNodes().size == 1 }
    return onNode(row)
}

/** The test tag of [title]'s history row, for its own menu (`<row>.menu`, `<row>.cancel`). */
fun ComposeTestRule.historyRowTag(title: String): String =
    historyRow(title).fetchSemanticsNode().config[SemanticsProperties.TestTag]
