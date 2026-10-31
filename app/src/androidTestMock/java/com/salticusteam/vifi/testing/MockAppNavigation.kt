package com.salticusteam.vifi.testing

import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick

/** The signed-in home screen is up (the archive's root list; iOS `home.list`). */
fun ComposeTestRule.waitForHome() = waitForTag("browse.list")

/** Taps [tag] once it is the only one on screen (not still fading out with the previous screen). */
fun ComposeTestRule.tapWhenSingle(tag: String) {
    waitForTag(tag)
    onNodeWithTag(tag).performClick()
}

/** Home's bell → the notifications inbox. */
fun ComposeTestRule.openInboxFromHome() {
    waitForHome()
    tapWhenSingle("toolbar.inbox")
    waitForTag("inbox.list")
}

/** Home's account button → Hesap. */
fun ComposeTestRule.openAccountFromHome() {
    waitForHome()
    tapWhenSingle("toolbar.account")
    waitForTag("account.list")
}

/** Hesap › Bildirim Ayarları. */
fun ComposeTestRule.openNotificationSettingsFromHome() {
    openAccountFromHome()
    scrollToAndClick("account.list", "account.notificationSettings")
    waitForTag("notificationSettings.list")
}

/** Hesap › Bildirim Gönder (admins only). */
fun ComposeTestRule.openComposerFromHome() {
    openAccountFromHome()
    scrollToAndClick("account.list", "account.notificationComposer")
    waitForTag("notificationComposer.form")
}

/** The top bar's back arrow, once the screen it belongs to is the only one left. */
fun ComposeTestRule.tapBack() = tapWhenSingle("topBar.backButton")
