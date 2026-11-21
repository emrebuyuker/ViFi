package com.salticusteam.vifi.feature.addexam

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.CatalogSelection
import com.salticusteam.vifi.core.model.ExamNameCatalog
import com.salticusteam.vifi.core.model.ExamSubmission
import com.salticusteam.vifi.feature.profile.CatalogPicker
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/** SMP-01 — the "Sınav Ekle" exam-name picker offers the 22-name list, in iOS order. */
class ExamNamePickerContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(onSelect: (CatalogSelection) -> Unit = {}) {
        composeRule.setContent {
            ViFiTheme {
                CatalogPicker(
                    title = "Sınav Adı",
                    options = AddExamUiState.initial(ownerId = "u", profile = null, currentYear = 2026)
                        .options(AddExamPicker.EXAM_NAME),
                    selection = CatalogSelection.EMPTY,
                    testTagPrefix = "addExam.examName",
                    onSelect = onSelect,
                    onBack = {},
                    allowsCustom = false,
                )
            }
        }
    }

    @Test
    fun examNamePicker_showsAll22Names() {
        setContent()

        val list = composeRule.onNodeWithTag("addExam.examName.list")
        assertEquals(22, ExamNameCatalog.names.size)
        for (item in ExamNameCatalog.names) {
            val tag = "addExam.examName.option.${item.id}"
            list.performScrollToNode(hasTestTag(tag))
            composeRule.onNodeWithTag(tag).assertIsDisplayed()
        }
    }

    @Test
    fun examNamePicker_lastEntrySelectable() {
        var selected: CatalogSelection? = null
        setContent { selected = it }

        composeRule.onNodeWithTag("addExam.examName.list")
            .performScrollToNode(hasTestTag("addExam.examName.option.cozumlu-sorular"))
        composeRule.onNodeWithTag("addExam.examName.option.cozumlu-sorular").performClick()

        assertEquals("ÇÖZÜMLÜ SORULAR", selected?.name)
        assertEquals("cozumlu-sorular", selected?.id)
    }

    @Test
    fun examText_staysYearThenName() {
        assertEquals("2024 ÇÖZÜMLÜ SORULAR", ExamSubmission.examText("ÇÖZÜMLÜ SORULAR", 2024))
        assertEquals("2019 YAZ OKULU FİNAL", ExamSubmission.examText("YAZ OKULU FİNAL", 2019))
    }
}
