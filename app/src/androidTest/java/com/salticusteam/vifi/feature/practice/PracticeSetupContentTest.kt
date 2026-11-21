package com.salticusteam.vifi.feature.practice

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.ExamType
import com.salticusteam.vifi.core.model.PracticeScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The "Sınava gir" setup's two steps on the stateless content (iOS PracticeUITests). */
@RunWith(AndroidJUnit4::class)
class PracticeSetupContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val types = PracticeSetupUiState(
        isLoading = false,
        typeRows = listOf(
            PracticeTypeRow(ExamType.VIZE, 1),
            PracticeTypeRow(ExamType.FINAL, 40),
        ),
    )

    private val scopes = types.copy(
        selectedType = ExamType.VIZE,
        scopeRows = listOf(PracticeScopeRow(PracticeScope.TURKEY, 1)),
    )

    private val pickedTypes = mutableListOf<ExamType>()
    private val pickedScopes = mutableListOf<PracticeScope>()
    private var backToTypes = 0
    private var retries = 0
    private var backs = 0

    private fun setContent(state: PracticeSetupUiState) {
        composeRule.setContent {
            ViFiTheme {
                PracticeSetupContent(
                    state = state,
                    onTypeSelected = { pickedTypes += it },
                    onScopeSelected = { pickedScopes += it },
                    onBackToTypes = { backToTypes++ },
                    onRetry = { retries++ },
                    onBack = { backs++ },
                )
            }
        }
    }

    @Test
    fun types_listEachTypeWithItsCount_andPickingOneReportsIt() {
        setContent(types)

        composeRule.onNodeWithTag("practice.types").assertIsDisplayed()
        composeRule.onNodeWithTag("practice.type.vize").assert(hasText("Vize", substring = true))
            .assert(hasText("1 soru", substring = true))
        composeRule.onNodeWithTag("practice.type.final").assert(hasText("40 soru", substring = true))
        composeRule.onNodeWithTag("practice.type.butunleme").assertDoesNotExist()

        composeRule.onNodeWithTag("practice.type.final").performClick()

        assertEquals(listOf(ExamType.FINAL), pickedTypes)
    }

    @Test
    fun scopes_aSingleQuestionScopeIsOfferedAndPickable() {
        setContent(scopes)

        composeRule.onNodeWithTag("practice.scopes").assertIsDisplayed()
        composeRule.onNodeWithTag("practice.scope.tr").assert(hasText("Türkiye geneli", substring = true))
            .assert(hasText("1 soru", substring = true))
        composeRule.onNodeWithTag("practice.scope.course").assertDoesNotExist()

        composeRule.onNodeWithTag("practice.scope.tr").performClick()

        assertEquals(listOf(PracticeScope.TURKEY), pickedScopes)
    }

    @Test
    fun scopes_everyScopeShowsItsCount() {
        setContent(
            scopes.copy(
                scopeRows = listOf(
                    PracticeScopeRow(PracticeScope.COURSE, 3),
                    PracticeScopeRow(PracticeScope.UNIVERSITY, 12),
                    PracticeScopeRow(PracticeScope.TURKEY, 40),
                ),
            ),
        )

        composeRule.onNodeWithTag("practice.scope.course").assert(hasText("Bu ders", substring = true))
            .assert(hasText("3 soru", substring = true))
        composeRule.onNodeWithTag("practice.scope.university").assert(hasText("Üniversitem", substring = true))
            .assert(hasText("12 soru", substring = true))
        composeRule.onNodeWithTag("practice.scope.tr").assert(hasText("40 soru", substring = true))
    }

    @Test
    fun back_fromScopesReturnsToTypes_fromTypesLeavesTheSetup() {
        setContent(scopes)
        composeRule.onNodeWithTag("topBar.backButton").performClick()
        assertEquals(1, backToTypes)
        assertEquals(0, backs)
    }

    @Test
    fun back_fromTypesLeavesTheSetup() {
        setContent(types)
        composeRule.onNodeWithTag("topBar.backButton").performClick()
        assertEquals(1, backs)
        assertEquals(0, backToTypes)
    }

    @Test
    fun loadingAndErrorStates_areShown() {
        setContent(PracticeSetupUiState())
        composeRule.onNodeWithTag("state.loading").assertIsDisplayed()
    }

    @Test
    fun error_retryAsksAgain() {
        setContent(PracticeSetupUiState(isLoading = false, error = "İnternet bağlantısı yok."))

        composeRule.onNodeWithTag("state.error").assertIsDisplayed()
        composeRule.onNodeWithTag("state.retry").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun empty_showsTheEmptyState() {
        setContent(PracticeSetupUiState(isLoading = false))

        composeRule.onNodeWithTag("state.empty").assertIsDisplayed()
    }
}
