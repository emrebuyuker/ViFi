package com.salticusteam.vifi.feature.addexam

import app.cash.turbine.test
import com.salticusteam.vifi.core.data.catalog.CatalogSource
import com.salticusteam.vifi.core.data.catalog.UniversityCatalog
import com.salticusteam.vifi.core.data.repository.PickedPdf
import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.data.repository.fake.FakeArchiveRepository
import com.salticusteam.vifi.core.data.repository.fake.FakeExamSubmissionService
import com.salticusteam.vifi.core.data.repository.fake.FakeProfileService
import com.salticusteam.vifi.core.data.repository.fake.FakeRewardsConfigReader
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.salticusteam.vifi.core.data.submission.FileSubmissionDraftStore
import com.salticusteam.vifi.core.data.submission.SubmissionStatus
import com.salticusteam.vifi.core.data.submission.SubmissionTierResolver
import com.salticusteam.vifi.core.data.submission.SubmissionUploader
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.CatalogItem
import com.salticusteam.vifi.core.model.CatalogSelection
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamNameCatalog
import com.salticusteam.vifi.core.model.ExamSubmission
import com.salticusteam.vifi.core.model.ExamSubmissionError
import com.salticusteam.vifi.core.model.RewardsConfig
import com.salticusteam.vifi.core.model.SubmissionFileError
import com.salticusteam.vifi.core.model.SubmissionTier
import com.salticusteam.vifi.core.model.UserProfile
import com.salticusteam.vifi.fake.FakeSubmissionFileSource
import com.salticusteam.vifi.fake.FakeSubmissionQueue
import com.salticusteam.vifi.testing.MainDispatcherRule
import com.salticusteam.vifi.testing.MutableClock
import kotlin.time.Duration
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class AddExamViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    /**
     * Runs the queue's uploads and the app-scope work on the test scheduler as foreground work (unlike
     * `backgroundScope`, which `advanceUntilIdle()` skips); an upload left suspended is cancelled after the test.
     */
    private val outlivingJob = SupervisorJob()
    private val outlivingScope by lazy { CoroutineScope(mainDispatcherRule.testDispatcher + outlivingJob) }

    /** Matches FakeArchiveRepository's İTÜ > Mühendislik > Bilgisayar Mühendisliği > BLG 101 > 2023 Vize. */
    private val profile = FakeProfileService.SAMPLE_PROFILE.copy(
        university = "İstanbul Teknik Üniversitesi",
        universityId = "istanbul-teknik-universitesi",
        faculty = "Mühendislik",
        facultyId = "muhendislik",
    )
    private val vize = ExamNameCatalog.names.first()

    private lateinit var submissionService: FakeExamSubmissionService
    private lateinit var rewards: FakeRewardsConfigReader
    private lateinit var archive: FakeArchiveRepository
    private lateinit var files: FakeSubmissionFileSource
    private lateinit var analytics: FakeAnalyticsTracker
    private lateinit var draftRoot: File
    private lateinit var draftStore: FileSubmissionDraftStore

    /** Set by [viewModel]: the queue the last ViewModel was created with. */
    private lateinit var queue: FakeSubmissionQueue

    @Before
    fun setUp() {
        submissionService = FakeExamSubmissionService(latency = Duration.ZERO)
        rewards = FakeRewardsConfigReader(RewardsConfig(examUploadCoins = 10, tiers = FakeRewardsConfigReader.SAMPLE_TIERS))
        archive = FakeArchiveRepository()
        files = FakeSubmissionFileSource(images = (1..60).associate { "img$it" to byteArrayOf(it.toByte()) })
        analytics = FakeAnalyticsTracker()
        draftRoot = folder.newFolder("drafts")
        draftStore = FileSubmissionDraftStore(draftRoot, MutableClock(), mainDispatcherRule.testDispatcher)
    }

    @After
    fun tearDown() {
        outlivingJob.cancel()
    }

    /** A form; pass the [savedStateHandle] of an earlier one to recreate it as after process death. */
    private fun TestScope.viewModel(
        ownerId: String = "uid",
        profile: UserProfile? = this@AddExamViewModelTest.profile,
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
    ): AddExamViewModel {
        queue = FakeSubmissionQueue(
            draftStore,
            SubmissionUploader(draftStore, submissionService, analytics),
            outlivingScope,
        )
        return viewModel(ownerId, profile, savedStateHandle, queue)
    }

    private fun TestScope.viewModel(
        ownerId: String,
        profile: UserProfile?,
        savedStateHandle: SavedStateHandle,
        queue: FakeSubmissionQueue,
    ) = AddExamViewModel(
        ownerId = ownerId,
        profile = profile,
        savedStateHandle = savedStateHandle,
        draftStore = draftStore,
        submissionQueue = queue,
        rewardsConfigReader = rewards,
        tierResolver = SubmissionTierResolver(archive),
        fileSource = files,
        catalogSource = object : CatalogSource {
            override suspend fun catalog() = UniversityCatalog(
                listOf(CatalogItem("ege-universitesi", "Ege Üniversitesi")),
                emptyList(),
                emptyList(),
            )
        },
        analytics = analytics,
        appScope = outlivingScope,
        clock = MutableClock(),
    )

    /** The saved state as it comes back after process death: only its values survive, not the object. */
    private fun SavedStateHandle.afterProcessDeath() = SavedStateHandle(keys().associateWith { get<Any>(it) })

    private fun AddExamViewModel.choose(picker: AddExamPicker, selection: CatalogSelection) {
        onPickerOpen(picker)
        onSelection(selection)
    }

    private fun AddExamViewModel.fillIn(lesson: String = "BLG 101", year: Int = 2023, acceptTerms: Boolean = true) {
        choose(AddExamPicker.LESSON, CatalogSelection.custom(lesson))
        choose(AddExamPicker.EXAM_NAME, CatalogSelection.catalog(vize))
        onYearSelected(year)
        onImagesPicked(listOf("img1", "img2"))
        onTermsAcceptedChange(acceptTerms)
    }

    @Test
    fun `init prefills the school from the profile, the current year and rewards`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()

        val state = sut.uiState.value
        assertEquals("İstanbul Teknik Üniversitesi", state.university.name)
        assertEquals("Bilgisayar Mühendisliği", state.department.name)
        assertEquals(2026, state.year)
        assertEquals(2026, state.yearOptions.first())
        assertEquals(2000, state.yearOptions.last())
        assertEquals(10, state.rewards?.examUploadCoins)
        assertEquals("Ege Üniversitesi", state.catalog.universities.single().name)
        assertFalse(state.canSubmit)
    }

    @Test
    fun `init without a profile starts empty and blocks the lesson picker`() = runTest {
        val sut = viewModel(profile = null)
        advanceUntilIdle()

        sut.onPickerOpen(AddExamPicker.LESSON)

        assertTrue(sut.uiState.value.university.isEmpty)
        assertNull(sut.uiState.value.picker)
    }

    @Test
    fun `onScreenShown logs the AddExam screen view`() = runTest {
        viewModel().onScreenShown()

        assertEquals(listOf(AnalyticsEvent.ScreenView(AnalyticsScreen.ADD_EXAM)), analytics.events)
    }

    @Test
    fun `onSelection sets the open picker's field and closes it`() = runTest {
        val sut = viewModel()
        sut.choose(AddExamPicker.UNIVERSITY, CatalogSelection.catalog(CatalogItem("ege-universitesi", "Ege Üniversitesi")))

        assertEquals("Ege Üniversitesi", sut.uiState.value.university.name)
        assertNull(sut.uiState.value.picker)
    }

    @Test
    fun `onImagesPicked appends pages numbered in order`() = runTest {
        val sut = viewModel()
        sut.onImagesPicked(listOf("img1", "img2"))
        advanceUntilIdle()
        sut.uiState.test {
            skipItems(1)
            sut.onImagesPicked(listOf("img3"))
            assertTrue(awaitItem().isPreparingFiles)
            val done = awaitItem()
            assertFalse(done.isPreparingFiles)
            assertEquals(listOf(1, 2, 3), done.files.map { it.order })
            assertEquals(3.toByte(), done.files.last().data.single())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `onImagesPicked when an image is unusable skips it and reports`() = runTest {
        val sut = viewModel()
        sut.onImagesPicked(listOf("img1", "broken", "img2"))
        advanceUntilIdle()

        val state = sut.uiState.value
        assertEquals(listOf(1, 2), state.files.map { it.order })
        assertEquals(SubmissionFileError.ImageUnusable.userMessage, state.error)
    }

    @Test
    fun `onImagesPicked stops at the page limit`() = runTest {
        val sut = viewModel()
        sut.onImagesPicked((1..60).map { "img$it" })
        advanceUntilIdle()

        val state = sut.uiState.value
        assertEquals(ExamSubmission.MAX_FILE_COUNT, state.files.size)
        assertEquals(SubmissionFileError.TooManyPages.userMessage, state.error)
        assertEquals(ExamSubmission.MAX_FILE_COUNT, files.requestedImages.size)
    }

    @Test
    fun `onKindSelected clears the files and the error`() = runTest {
        val sut = viewModel()
        sut.onImagesPicked(listOf("img1", "broken"))
        advanceUntilIdle()

        sut.onKindSelected(ExamKind.PDF)

        assertEquals(ExamKind.PDF, sut.uiState.value.kind)
        assertTrue(sut.uiState.value.files.isEmpty())
        assertNull(sut.uiState.value.error)
    }

    @Test
    fun `onPdfPicked sets a single file or reports the problem`() = runTest {
        files.pdfs = mapOf(
            "ok" to PickedPdf.Data(byteArrayOf(9)),
            "big" to PickedPdf.TooLarge,
            "empty" to PickedPdf.Empty,
        )
        val sut = viewModel()
        sut.onKindSelected(ExamKind.PDF)

        sut.onPdfPicked("big")
        advanceUntilIdle()
        assertEquals(SubmissionFileError.PdfTooLarge.userMessage, sut.uiState.value.error)

        sut.onPdfPicked("empty")
        advanceUntilIdle()
        assertEquals(SubmissionFileError.EmptyFile.userMessage, sut.uiState.value.error)

        sut.onPdfPicked("missing")
        advanceUntilIdle()
        assertEquals(SubmissionFileError.Unreadable.userMessage, sut.uiState.value.error)

        sut.onPdfPicked("ok")
        advanceUntilIdle()
        assertNull(sut.uiState.value.error)
        assertEquals(1, sut.uiState.value.files.single().order)
    }

    @Test
    fun `onPdfPicked is ignored for an images submission`() = runTest {
        files.pdfs = mapOf("ok" to PickedPdf.Data(byteArrayOf(9)))
        val sut = viewModel()

        sut.onPdfPicked("ok")
        advanceUntilIdle()

        assertTrue(sut.uiState.value.files.isEmpty())
    }

    @Test
    fun `estimate resolves the tier once the placement is complete`() = runTest {
        val sut = viewModel()
        advanceUntilIdle()
        assertNull(sut.uiState.value.estimatedTier)

        sut.fillIn()
        advanceUntilIdle()
        assertEquals(SubmissionTier.DUPLICATE, sut.uiState.value.estimatedTier)
        assertEquals(CoinNote.Estimated(10, SubmissionTier.DUPLICATE), sut.uiState.value.coinNote)

        sut.onYearSelected(2019)
        advanceUntilIdle()
        assertEquals(SubmissionTier.NEW_EXAM, sut.uiState.value.estimatedTier)
        assertEquals(CoinNote.Estimated(60, SubmissionTier.NEW_EXAM), sut.uiState.value.coinNote)
    }

    @Test
    fun `estimate when the archive cannot be read leaves the tier unknown`() = runTest {
        archive.error = ArchiveError.Offline
        val sut = viewModel()
        sut.fillIn()
        advanceUntilIdle()

        assertNull(sut.uiState.value.estimatedTier)
        assertEquals(CoinNote.Flat(10), sut.uiState.value.coinNote)
    }

    @Test
    fun `estimate is cleared when the placement becomes incomplete`() = runTest {
        val sut = viewModel()
        sut.fillIn()
        advanceUntilIdle()

        sut.choose(AddExamPicker.LESSON, CatalogSelection.EMPTY)

        assertNull(sut.uiState.value.estimatedTier)
    }

    @Test
    fun `onSubmit sends the submission, tracks it and emits Submitted`() = runTest {
        val sut = viewModel()
        sut.fillIn()
        advanceUntilIdle()

        sut.events.test {
            sut.onSubmit()
            assertTrue(sut.uiState.value.isWorking)
            assertEquals(AddExamEvent.Submitted, awaitItem())
        }

        val (owner, submission) = submissionService.submissions.single()
        assertEquals("uid", owner)
        assertEquals("BLG 101", submission.lesson)
        assertEquals("VİZE", submission.examName)
        assertEquals(2, submission.files.size)
        assertTrue(sut.uiState.value.isWorking)
        assertTrue(AnalyticsEvent.ExamSubmitted(ExamKind.IMAGES, 2, SubmissionTier.DUPLICATE) in analytics.events)
    }

    @Test
    fun `onSubmit when the service fails shows the message and tracks the reason`() = runTest {
        submissionService.failure = ExamSubmissionError.PermissionDenied
        val sut = viewModel()
        sut.fillIn()
        advanceUntilIdle()

        sut.onSubmit()
        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isWorking)
        assertEquals(ExamSubmissionError.PermissionDenied.userMessage, state.error)
        assertTrue(AnalyticsEvent.ExamSubmitFailed("permission_denied") in analytics.events)
    }

    @Test
    fun `onSubmit when the form is incomplete does nothing`() = runTest {
        val sut = viewModel()
        sut.onSubmit()
        advanceUntilIdle()

        assertTrue(submissionService.submissions.isEmpty())
        assertFalse(sut.uiState.value.isWorking)
    }

    @Test
    fun `onSubmit before the terms are accepted does nothing`() = runTest {
        val sut = viewModel()
        sut.fillIn(acceptTerms = false)
        advanceUntilIdle()

        assertNotNull(sut.uiState.value.builtSubmission)
        assertFalse(sut.uiState.value.canSubmit)
        sut.onSubmit()
        advanceUntilIdle()

        assertTrue(submissionService.submissions.isEmpty())
        assertFalse(sut.uiState.value.isWorking)
    }

    @Test
    fun `onTermsAcceptedChange toggles the acceptance and gates submitting`() = runTest {
        val sut = viewModel()
        sut.fillIn()
        advanceUntilIdle()
        assertTrue(sut.uiState.value.canSubmit)

        sut.onTermsAcceptedChange(false)

        assertFalse(sut.uiState.value.termsAccepted)
        assertFalse(sut.uiState.value.canSubmit)
    }

    @Test
    fun `edits are ignored while submitting`() = runTest {
        submissionService = FakeExamSubmissionService(latency = Duration.INFINITE)
        val sut = viewModel()
        sut.fillIn()
        advanceUntilIdle()

        sut.onSubmit()
        sut.onKindSelected(ExamKind.PDF)
        sut.onPickerOpen(AddExamPicker.UNIVERSITY)
        sut.onTermsAcceptedChange(false)

        assertEquals(ExamKind.IMAGES, sut.uiState.value.kind)
        assertNull(sut.uiState.value.picker)
        assertTrue(sut.uiState.value.termsAccepted)
    }

    // --- Process death / background upload ---

    @Test
    fun `after process death the picks and the prepared pages come back`() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(savedStateHandle = handle)
        first.fillIn(lesson = "MAT 101", year = 2021)
        first.onPickerOpen(AddExamPicker.EXAM_NAME)
        advanceUntilIdle()

        val sut = viewModel(savedStateHandle = handle.afterProcessDeath())
        advanceUntilIdle()

        val state = sut.uiState.value
        assertEquals("MAT 101", state.lesson.name)
        assertEquals(vize.name, state.examName.name)
        assertEquals(2021, state.year)
        assertEquals(AddExamPicker.EXAM_NAME, state.picker)
        assertEquals(listOf(byteArrayOf(1).toList(), byteArrayOf(2).toList()), state.files.map { it.data.toList() })
        assertEquals(listOf(1, 2), state.files.map { it.order })
        assertFalse(state.isPreparingFiles)
        assertTrue(state.termsAccepted)
    }

    @Test
    fun `after process death a cleared or switched draft comes back empty`() = runTest {
        val handle = SavedStateHandle()
        val first = viewModel(savedStateHandle = handle)
        first.fillIn()
        advanceUntilIdle()
        first.onKindSelected(ExamKind.PDF)
        advanceUntilIdle()

        val sut = viewModel(savedStateHandle = handle.afterProcessDeath())
        advanceUntilIdle()

        assertEquals(ExamKind.PDF, sut.uiState.value.kind)
        assertTrue(sut.uiState.value.files.isEmpty())
    }

    @Test
    fun `onSubmit queues the draft and lets the user leave while it uploads`() = runTest {
        submissionService = FakeExamSubmissionService(latency = Duration.INFINITE)
        val sut = viewModel()
        sut.fillIn()
        advanceUntilIdle()

        sut.onSubmit()
        advanceUntilIdle()

        val state = sut.uiState.value
        assertTrue(state.isWorking)
        assertTrue(state.isSendingInBackground)
        assertTrue(state.canClose)
        assertEquals(1, queue.enqueued.size)
    }

    @Test
    fun `onSubmit when the draft cannot be queued shows the message and stays editable`() = runTest {
        val sut = viewModel()
        sut.fillIn()
        advanceUntilIdle()
        queue.enqueueFailure = ExamSubmissionError.Unknown(ExamSubmissionError.GENERIC_MESSAGE)

        sut.onSubmit()
        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isWorking)
        assertEquals(ExamSubmissionError.GENERIC_MESSAGE, state.error)
        assertTrue(AnalyticsEvent.ExamSubmitFailed("unknown") in analytics.events)
    }

    @Test
    fun `a failed submit can be sent again under a new submission id`() = runTest {
        submissionService.failure = ExamSubmissionError.PermissionDenied
        val sut = viewModel()
        sut.fillIn()
        advanceUntilIdle()
        sut.onSubmit()
        advanceUntilIdle()
        submissionService.failure = null

        sut.events.test {
            sut.onSubmit()
            assertEquals(AddExamEvent.Submitted, awaitItem())
        }

        val (failed, sent) = queue.enqueued
        assertTrue(failed.submissionId != sent.submissionId)
        assertEquals(listOf(sent.submissionId), submissionService.submissionIds)
    }

    @Test
    fun `after process death during an upload the form follows it until it is stored`() = runTest {
        submissionService = FakeExamSubmissionService(latency = Duration.INFINITE)
        val handle = SavedStateHandle()
        val first = viewModel(savedStateHandle = handle)
        first.fillIn()
        advanceUntilIdle()
        first.onSubmit()
        advanceUntilIdle()
        val submissionId = queue.enqueued.single().submissionId
        val survivingQueue = queue

        val sut = viewModel("uid", profile, handle.afterProcessDeath(), survivingQueue)
        advanceUntilIdle()
        assertTrue(sut.uiState.value.isWorking)
        assertTrue(sut.uiState.value.isSendingInBackground)

        sut.events.test {
            survivingQueue.set(submissionId, SubmissionStatus.Sent)
            assertEquals(AddExamEvent.Submitted, awaitItem())
        }
    }

    @Test
    fun `after process death a submit that was given up shows why and can be sent again`() = runTest {
        submissionService = FakeExamSubmissionService(latency = Duration.INFINITE)
        val handle = SavedStateHandle()
        val first = viewModel(savedStateHandle = handle)
        first.fillIn()
        advanceUntilIdle()
        first.onSubmit()
        advanceUntilIdle()
        val survivingQueue = queue
        survivingQueue.set(survivingQueue.enqueued.single().submissionId, SubmissionStatus.Failed(ExamSubmissionError.Offline))

        val sut = viewModel("uid", profile, handle.afterProcessDeath(), survivingQueue)
        advanceUntilIdle()

        val state = sut.uiState.value
        assertFalse(state.isWorking)
        assertEquals(ExamSubmissionError.Offline.userMessage, state.error)
        assertEquals(2, state.files.size)
        assertTrue(state.canSubmit)
    }

    @Test
    fun `closing an unsubmitted form discards its draft, a queued one is kept for the upload`() = runTest {
        val unsubmitted = viewModel()
        unsubmitted.fillIn()
        advanceUntilIdle()
        submissionService = FakeExamSubmissionService(latency = Duration.INFINITE)
        val queued = viewModel()
        queued.fillIn()
        advanceUntilIdle()
        queued.onSubmit()
        advanceUntilIdle()
        assertEquals(2, draftRoot.listFiles()?.size)

        ViewModelStore().apply {
            put("unsubmitted", unsubmitted)
            put("queued", queued)
        }.clear()
        advanceUntilIdle()

        val remaining = draftRoot.listFiles().orEmpty().single()
        assertTrue(File(remaining, "submission.json").exists())
    }
}
