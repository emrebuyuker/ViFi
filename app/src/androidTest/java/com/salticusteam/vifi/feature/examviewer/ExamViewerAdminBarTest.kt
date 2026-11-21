package com.salticusteam.vifi.feature.examviewer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import coil.ImageLoader
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.feature.conversionadmin.ExamConversionAdminButtonContent
import com.salticusteam.vifi.feature.conversionadmin.ExamConversionButtonUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Yeni yapıya geçir" under the file viewer (ADX-04, iOS `testAdminSeesConvertActionOnAPlainExam`): shown for an admin,
 * absent for everyone else.
 */
@RunWith(AndroidJUnit4::class)
class ExamViewerAdminBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val path = ArchivePath(
        listOf("kocaeli-universitesi", "kocaeli-muhendislik", "kocaeli-muhendislik-matematik", "topoloji-1", "2002-vize"),
        listOf("KOCAELİ ÜNİVERSİTESİ", "MÜHENDİSLİK FAKÜLTESİ", "MATEMATİK BÖLÜMÜ", "TOPOLOJİ 1", "2002 VİZE"),
    )

    @Test
    fun adminSeesTheConvertButtonUnderTheImageViewerAndOthersDoNot() {
        var admin by mutableStateOf(false)
        composeRule.setContent {
            val imageLoader = ImageLoader(LocalContext.current)
            ViFiTheme {
                ExamImagesScreen(
                    state = ExamViewerUiState(path = path, routeKind = ExamKind.IMAGES),
                    imageLoader = imageLoader,
                    onBack = {},
                    onRetry = {},
                    onFullScreenShown = {},
                    bottomBar = {
                        ExamConversionAdminButtonContent(
                            state = ExamConversionButtonUiState(isAdmin = admin),
                            onStart = {},
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag("examAdmin.convert").assertDoesNotExist()

        admin = true
        composeRule.onNodeWithTag("examAdmin.convert").assertIsDisplayed()
    }
}
