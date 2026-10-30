package com.salticusteam.vifi.feature.notificationcomposer

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSummary
import com.salticusteam.vifi.feature.browse.BrowseUiState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The composer's exam picker (iOS `NotificationExamPickerView`) and the "Sınav" row it fills. */
@RunWith(AndroidJUnit4::class)
class NotificationExamPickerTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val picked = mutableListOf<String>()
    private var dismissed = 0
    private var wentBack = 0

    private val lesson = ArchivePath(
        listOf("bozok", "bozok__muh", "bozok__muh__bil", "bozok__muh__bil__mat"),
        listOf("BOZOK ÜNİVERSİTESİ", "MÜHENDİSLİK MİMARLIK FAKÜLTESİ", "BİLGİSAYAR MÜHENDİSLİĞİ", "MÜHENDİSLİK MATEMATİĞİ"),
    )

    private fun exam(id: String, name: String, hasFiles: Boolean) = ArchiveItem(
        path = lesson.appending(id, name),
        level = ArchiveLevel.EXAM,
        childCount = null,
        exam = if (hasFiles) ExamSummary(ExamKind.PDF, fileCount = 1) else null,
    )

    private val universities = listOf("İSTANBUL TEKNİK ÜNİVERSİTESİ", "BOĞAZİÇİ ÜNİVERSİTESİ", "BOZOK ÜNİVERSİTESİ").map { name ->
        ArchiveItem(ArchivePath.ROOT.appending(name.lowercase(), name), ArchiveLevel.UNIVERSITY, childCount = 3, exam = null)
    }

    private fun show(state: BrowseUiState, atRoot: Boolean = state.path.isRoot) {
        composeRule.setContent {
            ViFiTheme {
                var search by remember { mutableStateOf("") }
                ExamPickerContent(
                    state = state.copy(searchText = search),
                    onSearchTextChange = { search = it },
                    onItemClick = { picked += it.name },
                    onRetry = {},
                    onBack = if (atRoot) null else ({ wentBack++ }),
                    onDismiss = { dismissed++ },
                )
            }
        }
    }

    private fun tag(name: String) = composeRule.onNodeWithTag("$COMPOSER_TAG.$name")

    @Test
    fun root_offersOnlyClosing_andSearchFoldsTurkishLetters() {
        show(BrowseUiState(isLoading = false, items = universities))

        composeRule.onNode(hasText("Sınav Seç")).assertIsDisplayed()
        tag("exam.back").assertDoesNotExist()
        tag("exam.search").performTextInput("bogazici")
        tag("exam.row.BOĞAZİÇİ ÜNİVERSİTESİ").assertIsDisplayed()
        tag("exam.row.BOZOK ÜNİVERSİTESİ").assertDoesNotExist()
        tag("exam.row.BOĞAZİÇİ ÜNİVERSİTESİ").performClick()
        tag("exam.cancel").performClick()

        assertEquals(listOf("BOĞAZİÇİ ÜNİVERSİTESİ"), picked)
        assertEquals(1, dismissed)
    }

    @Test
    fun anExamWithoutFiles_cannotBePicked() {
        show(
            BrowseUiState(
                path = lesson,
                isLoading = false,
                items = listOf(exam("final-2019", "2019 FİNAL", hasFiles = true), exam("vize-2018", "2018 VİZE", hasFiles = false)),
            ),
        )

        composeRule.onNode(hasText("MÜHENDİSLİK MATEMATİĞİ")).assertIsDisplayed()
        tag("exam.row.2018 VİZE").assertIsNotEnabled()
        tag("exam.row.2019 FİNAL").assertIsEnabled().performClick()
        tag("exam.back").performClick()

        assertEquals(listOf("2019 FİNAL"), picked)
        assertEquals(1, wentBack)
    }

    @Test
    fun anEmptyLevel_andAnEmptySearch_sayWhy() {
        show(BrowseUiState(path = lesson, isLoading = false, items = emptyList()))

        composeRule.onNode(hasText("Bu derste henüz sınav yok")).assertIsDisplayed()
    }

    @Test
    fun aSearchWithNoMatch_saysSo() {
        show(BrowseUiState(isLoading = false, items = universities))

        tag("exam.search").performTextInput("odtü")

        composeRule.onNode(hasText("için sonuç yok", substring = true)).assertIsDisplayed()
    }

    @Test
    fun theExamRow_showsThePickAndWhereItIs() {
        val state = NotificationComposerUiState(
            title = "Başlık",
            body = "Mesaj",
            actionChoice = ActionChoice.EXAM,
            selectedExam = exam("final-2019", "2019 FİNAL", hasFiles = true),
        )
        var opened = 0
        composeRule.setContent {
            ViFiTheme {
                NotificationComposerContent(
                    state = state,
                    actions = NotificationComposerActions(onPickExam = { opened++ }),
                    onBack = {},
                    onOpenHistory = {},
                )
            }
        }

        tag("exam").performScrollTo().assertTextContains("2019 FİNAL", substring = true).performClick()
        tag("examPath").assertTextContains(
            "BOZOK ÜNİVERSİTESİ › MÜHENDİSLİK MİMARLIK FAKÜLTESİ › BİLGİSAYAR MÜHENDİSLİĞİ › MÜHENDİSLİK MATEMATİĞİ",
        )
        assertEquals(1, opened)
    }

    @Test
    fun withoutAPick_theExamRowAsksForOne() {
        composeRule.setContent {
            ViFiTheme {
                NotificationComposerContent(
                    state = NotificationComposerUiState(title = "Başlık", body = "Mesaj", actionChoice = ActionChoice.EXAM),
                    actions = NotificationComposerActions(),
                    onBack = {},
                    onOpenHistory = {},
                )
            }
        }

        tag("exam").performScrollTo().assertTextContains("Seç", substring = true)
        tag("examPath").assertDoesNotExist()
        tag("issue").performScrollTo().assertTextContains("Bağlanacak sınavı seç.")
    }
}
