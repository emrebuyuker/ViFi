package com.salticusteam.vifi.feature.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ArchivePath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BreadcrumbBarTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val university = ArchivePath.ROOT.appending("itu", "İTÜ")
    private val faculty = university.appending("muh", "MÜHENDİSLİK")
    private val department = faculty.appending("blg", "BİLGİSAYAR")
    private val deepLesson = ArchivePath.ROOT
        .appending("sau", "SAKARYA ÜNİVERSİTESİ")
        .appending("muh", "MÜHENDİSLİK FAKÜLTESİ")
        .appending("eem", "ELEKTRİK ELEKTRONİK MÜHENDİSLİĞİ")
        .appending("dif", "DİFERANSİYEL DENKLEMLER")

    @Test
    fun ancestorChip_returnsToThatLevel_andRootChipToTheUniversityList() {
        val selected = mutableListOf<ArchivePath>()
        composeRule.setContent { ViFiTheme { BreadcrumbBar(path = department, onSelect = { selected += it }) } }

        composeRule.onNodeWithTag("breadcrumb.2").performClick()
        composeRule.onNodeWithTag("breadcrumb.0").performClick()

        assertEquals(listOf(faculty, ArchivePath.ROOT), selected)
    }

    @Test
    fun currentChip_isSelectedAndNotTappable() {
        composeRule.setContent { ViFiTheme { BreadcrumbBar(path = department, onSelect = {}) } }

        composeRule.onNodeWithTag("breadcrumb.3")
            .assertHasNoClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Selected, true))
    }

    @Test
    fun browseContent_belowRoot_showsTrailAndHomeAction() {
        var homeClicks = 0
        composeRule.setContent {
            ViFiTheme {
                BrowseContent(
                    state = BrowseUiState(path = department, isLoading = false, items = emptyList()),
                    onSearchTextChange = {}, onItemClick = {}, onRetry = {}, onRefresh = {}, onBack = {},
                    onBreadcrumbSelect = {}, onHomeClick = { homeClicks++ },
                )
            }
        }

        composeRule.onNodeWithTag("browse.breadcrumb").assertExists()
        composeRule.onNodeWithTag("toolbar.home").performClick()

        assertEquals(1, homeClicks)
    }

    @Test
    fun narrowTrail_startsScrolledToTheCurrentChip() {
        composeRule.setContent { ViFiTheme { NarrowTrail(deepLesson) } }

        assertCurrentChipFullyVisible(deepLesson)
    }

    @Test
    fun narrowTrail_afterStateRestore_stillShowsTheCurrentChip() {
        // QA-02: the trail comes back with a restored, non-zero scroll offset (back navigation, rotation).
        val restorationTester = StateRestorationTester(composeRule)
        restorationTester.setContent { ViFiTheme { NarrowTrail(deepLesson) } }
        assertCurrentChipFullyVisible(deepLesson)

        restorationTester.emulateSavedInstanceStateRestore()

        assertCurrentChipFullyVisible(deepLesson)
    }

    @Composable
    private fun NarrowTrail(path: ArchivePath) {
        Box(Modifier.width(260.dp).testTag("trailHost")) {
            BreadcrumbBar(path = path, onSelect = {})
        }
    }

    private fun assertCurrentChipFullyVisible(path: ArchivePath) {
        composeRule.waitForIdle()
        val host = composeRule.onNodeWithTag("trailHost").getUnclippedBoundsInRoot()
        val chip = composeRule.onNodeWithTag("breadcrumb.${path.depth}").getUnclippedBoundsInRoot()
        assertTrue("current chip $chip is outside the trail $host", chip.left >= host.left && chip.right <= host.right)
    }
}
