package com.salticusteam.vifi.feature.conversionadmin

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.salticusteam.vifi.core.data.repository.fake.FakeExamConversionAdminService
import com.salticusteam.vifi.core.data.repository.fake.InMemorySubjectMappingService
import com.salticusteam.vifi.core.data.repository.fake.SampleQuestions
import com.salticusteam.vifi.core.designsystem.theme.ViFiTheme
import com.salticusteam.vifi.core.model.AIMonthlyUsage
import com.salticusteam.vifi.core.model.AIProvider
import com.salticusteam.vifi.core.model.AdminSubject
import com.salticusteam.vifi.core.model.BulkConversionEstimate
import com.salticusteam.vifi.core.model.BulkConversionScope
import com.salticusteam.vifi.core.model.ConversionStatus
import com.salticusteam.vifi.core.model.ExamConversion
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.model.SubjectCourse
import com.salticusteam.vifi.core.model.SubjectTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The admin conversion screens on their stateless content (iOS `SubjectMappingUITests`, the conversion review and bulk
 * flows of the iOS UI tests): lists, review with the title editor and the confirmations, bulk, subject mapping and the
 * self-contained admin components.
 */
class ConversionAdminContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val ready: ExamConversion = FakeExamConversionAdminService.SAMPLES.first { it.status == ConversionStatus.READY }
    private val failed: ExamConversion = FakeExamConversionAdminService.SAMPLES.first { it.status == ConversionStatus.FAILED }
    private val drafts: List<ExamQuestion> = SampleQuestions.questions(ready.id)

    // --- Account entries ---

    @Test
    fun accountEntriesOpenTheirScreens() {
        val opened = mutableListOf<String>()
        composeRule.setContent {
            ViFiTheme {
                androidx.compose.foundation.layout.Column {
                    ConversionAdminEntryRows(
                        enabled = true,
                        onReady = { opened += "ready" },
                        onFailed = { opened += "failed" },
                        onBulk = { opened += "bulk" },
                        onSubjects = { opened += "subjects" },
                    )
                }
            }
        }

        composeRule.onNodeWithTag("account.conversionReady").performClick()
        composeRule.onNodeWithTag("account.conversionFailed").performClick()
        composeRule.onNodeWithTag("account.conversionBulk").performClick()
        composeRule.onNodeWithTag("account.subjectMapping").performClick()

        assertEquals(listOf("ready", "failed", "bulk", "subjects"), opened)
    }

    // --- Lists (ADX-01 / ADX-03) ---

    @Test
    fun readyListOpensTheReviewOfTheTappedJob() {
        var opened: String? = null
        composeRule.setContent {
            ViFiTheme {
                ConversionJobsContent(
                    state = ConversionJobsUiState(ConversionListKind.READY, isLoading = false, jobs = listOf(ready)),
                    onOpenJob = { opened = it },
                    onRestart = { _, _ -> },
                    onRetry = {},
                    onRefresh = {},
                    onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("conversion.ready.list").assertIsDisplayed()
        composeRule.onNodeWithTag("conversion.row.${ready.id}").performClick()

        assertEquals(ready.id, opened)
    }

    @Test
    fun emptyReadyListSaysSo() {
        composeRule.setContent {
            ViFiTheme {
                ConversionJobsContent(
                    state = ConversionJobsUiState(ConversionListKind.READY, isLoading = false, jobs = emptyList()),
                    onOpenJob = {}, onRestart = { _, _ -> }, onRetry = {}, onRefresh = {}, onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("conversion.ready.empty").assertIsDisplayed()
        composeRule.onNodeWithText("Hazır sınav yok").assertIsDisplayed()
    }

    @Test
    fun failedListRestartsWithThePickedProvider() {
        val restarts = mutableListOf<Pair<String, AIProvider>>()
        composeRule.setContent {
            ViFiTheme {
                ConversionJobsContent(
                    state = ConversionJobsUiState(ConversionListKind.FAILED, isLoading = false, jobs = listOf(failed)),
                    onOpenJob = {}, onRestart = { job, provider -> restarts += job.id to provider },
                    onRetry = {}, onRefresh = {}, onBack = {},
                )
            }
        }

        composeRule.onNodeWithText("Sayfalar okunamadı").assertIsDisplayed()
        composeRule.onNodeWithTag("conversion.restart.${failed.id}").performClick()
        composeRule.onNodeWithTag("ai.provider.claude").assertIsDisplayed().assert(hasText("Claude ile ilerle"))
        composeRule.onNodeWithTag("ai.provider.gemini").performClick()

        assertEquals(listOf(failed.id to AIProvider.GEMINI), restarts)
    }

    // --- Review (ADX-01 / ADX-02) ---

    private fun setReview(
        state: ConversionReviewUiState,
        actions: ConversionReviewActions = ConversionReviewActions(),
        onFinished: () -> Unit = {},
    ) {
        composeRule.setContent { ViFiTheme { ConversionReviewContent(state, actions, onFinished) } }
    }

    private fun reviewState(
        job: ExamConversion = ready,
        title: String? = "2023 VİZE",
        questions: List<ExamQuestion> = drafts,
    ): ConversionReviewUiState {
        val suggestion = title?.let { com.salticusteam.vifi.core.util.ExamListTitle.parse(it) }
        return ConversionReviewUiState(
            job = job,
            isLoading = false,
            drafts = questions,
            titleYear = suggestion?.year?.toString().orEmpty(),
            titleEntryId = suggestion?.entryId,
            titleNumber = suggestion?.number?.toString().orEmpty(),
        )
    }

    @Test
    fun reviewShowsTheTitleTheQuestionsAndTheOriginalPagesEntry() {
        setReview(reviewState())

        composeRule.onNodeWithTag("conversion.review").assertIsDisplayed()
        composeRule.onNodeWithTag("conversion.review.title").performScrollTo().assert(hasText("2023 VİZE"))
        composeRule.onNodeWithTag("conversion.review.title.original").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("conversion.review.original").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("conversion.review.question.${drafts.first().order}").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun publishNeedsAListTitle() {
        setReview(reviewState(title = null))

        composeRule.onNodeWithTag("conversion.review.title").performScrollTo().assert(hasText("Listeden bir ad seç"))
        composeRule.onNodeWithTag("conversion.review.publish").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun publishAsksForConfirmationFirst() {
        var published = 0
        setReview(reviewState(), ConversionReviewActions(onPublish = { published++ }))

        composeRule.onNodeWithTag("conversion.review.publish").performScrollTo().assertIsEnabled().performClick()
        assertEquals("Nothing runs before the confirmation", 0, published)
        composeRule.onNodeWithText("Yayınlansın mı?").assertIsDisplayed()
        composeRule.onNodeWithTag("conversion.review.publish.confirm").performClick()

        assertEquals(1, published)
    }

    @Test
    fun regeneratingAQuestionAsksForTheProvider() {
        val regenerated = mutableListOf<Pair<Int, AIProvider>>()
        val first = drafts.first()
        setReview(reviewState(), ConversionReviewActions(onRegenerateQuestion = { order, provider -> regenerated += order to provider }))

        composeRule.onNodeWithTag("conversion.question.regenerate.${first.order}").performScrollTo().performClick()
        composeRule.onNodeWithTag("ai.provider.claude").performClick()

        assertEquals(listOf(first.order to AIProvider.CLAUDE), regenerated)
    }

    @Test
    fun aRegeneratingQuestionShowsItsProgressAndLocksItsButtons() {
        val first = drafts.first()
        setReview(reviewState().copy(regeneratingOrders = setOf(first.order)))

        composeRule.onNodeWithTag("conversion.review.regenerating.${first.order}").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("conversion.question.edit.${first.order}").performScrollTo().assertIsNotEnabled()
        composeRule.onNodeWithTag("conversion.question.regenerate.${first.order}").assertIsNotEnabled()
    }

    @Test
    fun discardingAsksForConfirmationAndRegenerateAllForTheProvider() {
        var discarded = 0
        val all = mutableListOf<AIProvider>()
        setReview(reviewState(), ConversionReviewActions(onDiscard = { discarded++ }, onRegenerateAll = { all += it }))

        composeRule.onNodeWithTag("conversion.review.discard").performScrollTo().performClick()
        composeRule.onNodeWithTag("conversion.review.discard.confirm").performClick()
        assertEquals(1, discarded)

        composeRule.onNodeWithTag("conversion.review.regenerateAll").performScrollTo().performClick()
        composeRule.onNodeWithTag("ai.provider.gemini").performClick()
        assertEquals(listOf(AIProvider.GEMINI), all)
    }

    @Test
    fun theOutcomeDialogLeavesTheReview() {
        var finished = 0
        setReview(reviewState().copy(outcome = ReviewOutcome.Published(questionCount = 3)), onFinished = { finished++ })

        composeRule.onNodeWithTag("conversion.review.outcome.message").assert(hasText("3 soru yayınlandı."))
        composeRule.onNodeWithTag("conversion.review.outcome.ok").performClick()

        assertEquals(1, finished)
    }

    // --- Question editor (ADX-01 / ADX-05) ---

    @Test
    fun editorSaveIsLockedUntilTheDraftIsValid() {
        var draft by mutableStateOf(QuestionEditDraft(drafts.first()).copy(text = ""))
        composeRule.setContent {
            ViFiTheme {
                QuestionEditorContent(
                    title = "Soru 1",
                    draft = draft,
                    isSaving = false,
                    error = null,
                    onDraftChange = { draft = it },
                    onSave = {},
                    onCancel = {},
                )
            }
        }

        composeRule.onNodeWithTag("question.edit.save").assertIsNotEnabled()
        composeRule.onNodeWithTag("question.edit.error").assert(hasText("Soru metni boş olamaz."))
        composeRule.onNodeWithTag("question.edit.text").performTextInput("Yeni soru")
        composeRule.onNodeWithTag("question.edit.save").assertIsEnabled()
    }

    // --- Bulk conversion (ADX-03) ---

    private fun setBulk(
        state: BulkConversionUiState,
        onProvider: (AIProvider) -> Unit = {},
        onEstimate: () -> Unit = {},
        onConfirm: () -> Unit = {},
    ) {
        composeRule.setContent {
            ViFiTheme {
                BulkConversionContent(
                    state = state,
                    onScopeSelected = { _, _ -> },
                    onProviderSelected = onProvider,
                    onEstimate = onEstimate,
                    onConfirm = onConfirm,
                    onBack = {},
                )
            }
        }
    }

    @Test
    fun bulkEstimateNeedsAScopeAndAProvider() {
        var estimates = 0
        setBulk(BulkConversionUiState(), onEstimate = { estimates++ })

        composeRule.onNodeWithTag("bulk.estimate").assertIsNotEnabled()
        assertEquals(0, estimates)
    }

    @Test
    fun bulkProviderChoiceReportsThePick() {
        val picked = mutableListOf<AIProvider>()
        setBulk(BulkConversionUiState(), onProvider = { picked += it })

        composeRule.onNodeWithTag("bulk.provider.gemini").performClick()

        assertEquals(listOf(AIProvider.GEMINI), picked)
    }

    @Test
    fun bulkEstimateShowsTheNumbersTheBudgetWarningAndConfirmsBeforeQueueing() {
        var confirmed = 0
        val estimate = BulkConversionEstimate(examCount = 30, estimatedCostUsd = 8.0, jobIds = emptyList())
        setBulk(
            BulkConversionUiState(
                phase = BulkPhase.Estimated(estimate),
                usage = AIMonthlyUsage("2026-11", 45.0),
                scope = BulkConversionScope.Department("d1"),
                scopeName = "A › B › C",
                provider = AIProvider.CLAUDE,
            ),
            onConfirm = { confirmed++ },
        )

        composeRule.onNodeWithTag("ai.usage").assertIsDisplayed()
        composeRule.onNodeWithTag("bulk.overBudget").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("bulk.confirm").performScrollTo().performClick()
        assertEquals(0, confirmed)
        composeRule.onNodeWithText("Kuyruğa alınsın mı?").assertIsDisplayed()
        composeRule.onNodeWithTag("bulk.confirm.queue").performClick()

        assertEquals(1, confirmed)
    }

    @Test
    fun bulkQueuedResultIsShown() {
        setBulk(BulkConversionUiState(phase = BulkPhase.Queued(BulkConversionEstimate(12, 5.0, emptyList()))))

        composeRule.onNodeWithTag("bulk.queued").performScrollTo().assert(hasText("12 sınav kuyruğa alındı."))
    }

    // --- Subject mapping (ADX-06) ---

    private val samples: List<AdminSubject> = InMemorySubjectMappingService.SAMPLES

    private fun mappingState(subjects: List<AdminSubject> = samples) = SubjectMappingUiState(
        isLoading = false,
        subjects = subjects,
        possibleMatches = com.salticusteam.vifi.core.model.SubjectNameSimilarity.possibleMatches(subjects),
    )

    @Test
    fun subjectListShowsThePossibleMatchHintAndOpensASubject() {
        var opened: String? = null
        composeRule.setContent {
            ViFiTheme {
                SubjectMappingContent(
                    state = mappingState(),
                    onOpenSubject = { opened = it },
                    onSearchChange = {}, onRetry = {}, onRefresh = {}, onBack = {},
                )
            }
        }

        composeRule.onNodeWithTag("subjects.list").assertIsDisplayed()
        composeRule.onNodeWithTag("subjects.row.algoritma").assertIsDisplayed()
        composeRule.onNodeWithTag("subjects.possibleMatch", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithTag("subjects.row.algoritma").performClick()

        assertEquals("algoritma", opened)
    }

    @Test
    fun movingACourseAsksForPickThenConfirmation() {
        val moves = mutableListOf<Pair<SubjectCourse, SubjectTarget>>()
        val fizik2 = samples.first { it.id == "fizik-2" }
        composeRule.setContent {
            ViFiTheme {
                SubjectDetailContent(
                    subjectId = fizik2.id,
                    state = mappingState(),
                    actions = SubjectDetailActions(onSetCourseSubject = { course, target -> moves += course to target }),
                )
            }
        }

        composeRule.onNodeWithTag("subjects.course.change.c-fizik2-ege").performClick()
        composeRule.onNodeWithTag("subjects.picker.row.fizik-1").performClick()
        composeRule.onNodeWithText("Ders taşınsın mı?").assertIsDisplayed()
        assertTrue("Nothing moves before the confirmation", moves.isEmpty())
        composeRule.onNodeWithTag("subjects.move.confirm").performClick()

        assertEquals(listOf(fizik2.courses.first() to SubjectTarget.Existing("fizik-1")), moves)
    }

    @Test
    fun mergingAsksForPickThenConfirmation() {
        val merges = mutableListOf<Pair<String, String>>()
        val algoritma = samples.first { it.id == "algoritma" }
        composeRule.setContent {
            ViFiTheme {
                SubjectDetailContent(
                    subjectId = algoritma.id,
                    state = mappingState(),
                    actions = SubjectDetailActions(onMerge = { subject, target -> merges += subject.id to target.id }),
                )
            }
        }

        composeRule.onNodeWithTag("subjects.menu").performClick()
        composeRule.onNodeWithTag("subjects.merge").performClick()
        composeRule.onNodeWithTag("subjects.picker.row.algoritmalar").performClick()
        composeRule.onNodeWithText("Dersler birleştirilsin mi?").assertIsDisplayed()
        composeRule.onNodeWithTag("subjects.merge.confirm").performClick()

        assertEquals(listOf("algoritma" to "algoritmalar"), merges)
    }

    @Test
    fun renamingSendsTheTypedName() {
        val renames = mutableListOf<String>()
        val fizik = samples.first { it.id == "fizik-1" }
        composeRule.setContent {
            ViFiTheme {
                SubjectDetailContent(
                    subjectId = fizik.id,
                    state = mappingState(),
                    actions = SubjectDetailActions(onRename = { _, name -> renames += name }),
                )
            }
        }

        composeRule.onNodeWithTag("subjects.menu").performClick()
        composeRule.onNodeWithTag("subjects.rename").performClick()
        composeRule.onNodeWithTag("subjects.rename.field").performTextInput(" Genel")
        composeRule.onNodeWithTag("subjects.rename.confirm").performClick()

        assertEquals(listOf("Fizik 1 Genel"), renames)
    }

    @Test
    fun aGoneSubjectLeavesTheDetail() {
        var left = 0
        composeRule.setContent {
            ViFiTheme {
                SubjectDetailContent(
                    subjectId = "merged-away",
                    state = mappingState(),
                    actions = SubjectDetailActions(onBack = { left++ }),
                )
            }
        }

        composeRule.waitForIdle()

        assertEquals(1, left)
    }

    // --- Self-contained admin components (ADX-04 / ADX-05) ---

    @Test
    fun convertButtonIsHiddenForANonAdminAndAsksForTheProviderForAnAdmin() {
        val started = mutableListOf<AIProvider>()
        var state by mutableStateOf(ExamConversionButtonUiState(isAdmin = false))
        composeRule.setContent {
            ViFiTheme { ExamConversionAdminButtonContent(state = state, onStart = { started += it }) }
        }

        composeRule.onNodeWithTag("examAdmin.convert").assertDoesNotExist()

        state = ExamConversionButtonUiState(isAdmin = true)
        composeRule.onNodeWithTag("examAdmin.convert").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("ai.provider.claude").performClick()

        assertEquals(listOf(AIProvider.CLAUDE), started)
    }

    @Test
    fun convertButtonShowsTheQueuedJobStatus() {
        composeRule.setContent {
            ViFiTheme {
                ExamConversionAdminButtonContent(
                    state = ExamConversionButtonUiState(isAdmin = true, job = ready.copy(status = ConversionStatus.QUEUED)),
                    onStart = {},
                )
            }
        }

        composeRule.onNodeWithTag("examAdmin.convert.status").assert(hasText("Sıraya alındı (Claude)"))
    }

    @Test
    fun publishedQuestionMenuOffersEditAndRegenerateToAnAdminOnly() {
        val regenerated = mutableListOf<AIProvider>()
        val question = drafts.first()
        var admin by mutableStateOf(false)
        composeRule.setContent {
            ViFiTheme {
                PublishedQuestionAdminMenuContent(
                    question = question,
                    state = PublishedQuestionAdminUiState(isAdmin = admin),
                    onSaveEdit = { null },
                    onRegenerate = { regenerated += it },
                    onMessageDismissed = {},
                )
            }
        }

        composeRule.onNodeWithTag("questionAdmin.menu").assertDoesNotExist()

        admin = true
        composeRule.onNodeWithTag("questionAdmin.menu").assertIsDisplayed().performClick()
        composeRule.onNodeWithTag("questionAdmin.edit").assertIsDisplayed()
        composeRule.onNodeWithTag("questionAdmin.regenerate").performClick()
        composeRule.onNodeWithTag("ai.provider.gemini").performClick()

        assertEquals(listOf(AIProvider.GEMINI), regenerated)
    }

    @Test
    fun publishedQuestionMessageIsShownAndDismissed() {
        var dismissed = 0
        composeRule.setContent {
            ViFiTheme {
                PublishedQuestionAdminMenuContent(
                    question = drafts.first(),
                    state = PublishedQuestionAdminUiState(isAdmin = true, message = PublishedQuestionMessage.Regenerating),
                    onSaveEdit = { null },
                    onRegenerate = {},
                    onMessageDismissed = { dismissed++ },
                )
            }
        }

        composeRule.onNodeWithTag("questionAdmin.message").assert(hasText("Soru yeniden üretiliyor; birkaç saniye sonra yenile."))
        composeRule.onNodeWithTag("questionAdmin.message.ok").performClick()

        assertEquals(1, dismissed)
    }
}
