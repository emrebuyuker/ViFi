package com.salticusteam.vifi.feature.notificationcomposer

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The composer's "Görsel" section (iOS `NotificationComposerImageSection`), driven through the stateless form. */
@RunWith(AndroidJUnit4::class)
class NotificationComposerImageSectionTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val taps = mutableListOf<String>()

    private fun show(state: NotificationComposerUiState) {
        composeRule.setContent {
            ViFiTheme {
                NotificationComposerContent(
                    state = state,
                    actions = NotificationComposerActions(
                        onPickImage = { taps += "pick" },
                        onRemoveImage = { taps += "remove" },
                    ),
                    onBack = {},
                    onOpenHistory = {},
                )
            }
        }
    }

    private fun tag(name: String) = composeRule.onNodeWithTag("$COMPOSER_TAG.$name")

    private val ready = NotificationComposerUiState(title = "Başlık", body = "Mesaj")

    @Test
    fun withoutAnImage_onlyPickingIsOffered() {
        show(ready)

        tag("imagePicker").performScrollTo().assertTextContains("Görsel Seç").performClick()
        tag("imagePreview").assertDoesNotExist()
        tag("imageRemove").assertDoesNotExist()
        assertEquals(listOf("pick"), taps)
    }

    @Test
    fun aPickedImage_isPreviewedAndCanBeChangedOrRemoved() {
        show(ready.copy(image = ComposerImage(byteArrayOf(1, 2, 3))))

        tag("imagePreview").performScrollTo().assertIsDisplayed()
        tag("imagePicker").performScrollTo().assertTextContains("Görseli Değiştir")
        tag("imageRemove").performScrollTo().performClick()
        assertEquals(listOf("remove"), taps)
    }

    @Test
    fun anImageBeingPrepared_showsProgressAndBlocksSending() {
        show(ready.copy(isPreparingImage = true))

        tag("imagePreparing").performScrollTo().assertIsDisplayed()
        tag("send").performScrollTo().assertIsNotEnabled()
        tag("testSend").assertIsNotEnabled()
    }

    @Test
    fun anUnusablePhoto_saysWhy() {
        show(ready.copy(imageError = ComposerImageError.UNUSABLE))

        tag("imageError").performScrollTo().assertTextContains("Bu görsel kullanılamıyor. Başka bir görsel seç.")
    }

    @Test
    fun anUnreadablePhoto_saysWhy() {
        show(ready.copy(imageError = ComposerImageError.UNREADABLE))

        tag("imageError").performScrollTo().assertTextContains("Görsel yüklenemedi. Başka bir görsel seç.")
    }
}
