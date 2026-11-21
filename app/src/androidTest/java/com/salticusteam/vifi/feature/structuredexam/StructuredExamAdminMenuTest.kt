package com.salticusteam.vifi.feature.structuredexam

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.data.repository.fake.SampleQuestions
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.feature.conversionadmin.PublishedQuestionAdminMenuContent
import com.salticusteam.vifi.feature.conversionadmin.PublishedQuestionAdminUiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "Yönetici" menu in the question cards' header slot (ADX-05, iOS `StructuredExamUITests`
 * `testAdminSeesQuestionActionsAndOthersDoNot`): present for an admin, absent for everyone else.
 */
@RunWith(AndroidJUnit4::class)
class StructuredExamAdminMenuTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val path = ArchivePath(
        listOf("kocaeli-universitesi", "kocaeli-muhendislik", "kocaeli-muhendislik-matematik", "topoloji-1", SampleQuestions.EXAM_ID),
        listOf("KOCAELİ ÜNİVERSİTESİ", "MÜHENDİSLİK FAKÜLTESİ", "MATEMATİK BÖLÜMÜ", "TOPOLOJİ 1", "2003 FİNAL"),
    )

    @Test
    fun adminSeesTheQuestionMenuAndOthersDoNot() {
        var admin by mutableStateOf(false)
        composeRule.setContent {
            ViFiTheme {
                StructuredExamContent(
                    state = StructuredExamUiState(
                        path = path,
                        kind = ExamKind.IMAGES,
                        isLoading = false,
                        questions = SampleQuestions.questions(),
                    ),
                    figureImages = QuestionFigureImages.Unavailable,
                    onRetry = {},
                    onToggleAnswer = {},
                    questionAdminAction = { question ->
                        PublishedQuestionAdminMenuContent(
                            question = question,
                            state = PublishedQuestionAdminUiState(isAdmin = admin),
                            onSaveEdit = { null },
                            onRegenerate = {},
                            onMessageDismissed = {},
                        )
                    },
                )
            }
        }

        composeRule.onNodeWithTag("questions.card.1").assertIsDisplayed()
        composeRule.onAllNodesWithTag("questionAdmin.menu").assertCountEquals(0)

        admin = true
        composeRule.onAllNodesWithTag("questionAdmin.menu")[0].assertIsDisplayed()
    }
}
