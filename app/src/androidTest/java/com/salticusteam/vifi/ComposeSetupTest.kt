package com.salticusteam.vifi

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Guards the Compose + Material3 toolchain (FM-09): a Material3 tree must render on device. */
@RunWith(AndroidJUnit4::class)
class ComposeSetupTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun material3Content_rendersOnDevice() {
        composeRule.setContent {
            MaterialTheme {
                Scaffold { padding ->
                    Text(
                        text = "ViFi",
                        modifier = Modifier
                            .padding(padding)
                            .testTag("composeSetup.text"),
                    )
                }
            }
        }

        composeRule.onNodeWithTag("composeSetup.text").assertIsDisplayed()
    }
}
