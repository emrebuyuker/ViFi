package com.salticusteam.vifi.feature.notificationcomposer

import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import com.salticusteam.vifi.core.model.Gender
import com.salticusteam.vifi.core.model.NotificationAdminError
import com.salticusteam.vifi.core.model.NotificationAudience
import com.salticusteam.vifi.core.model.NotificationDraft
import com.salticusteam.vifi.core.model.NotificationDraft.Issue
import com.salticusteam.vifi.core.model.NotificationSegment
import com.salticusteam.vifi.testing.MainDispatcherRule
import java.time.Duration
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** iOS `NotificationComposerViewModelTests` (10): validation, timing, the facets and the audience cascade. */
class NotificationComposerViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixture = NotificationComposerFixture()
    private val clock get() = fixture.clock

    // --- Validation ---

    @Test
    fun `an untouched form is blocked but does not scold`() = runTest {
        val viewModel = fixture.makeViewModel()
        val state = viewModel.uiState.value

        assertEquals(Issue.EMPTY_TITLE, state.issue)
        assertNull(state.visibleIssue)
        assertFalse(state.canSend)
        assertFalse(state.canSendTest)
    }

    @Test
    fun `title and message are required, limited and counted`() = runTest {
        val viewModel = fixture.makeViewModel()

        viewModel.onTitleChange("   ")
        assertEquals("A blank title the admin typed is flagged", Issue.EMPTY_TITLE, viewModel.uiState.value.visibleIssue)

        viewModel.onTitleChange("a".repeat(NotificationDraft.TITLE_LIMIT + 1))
        assertEquals(Issue.TITLE_TOO_LONG, viewModel.uiState.value.issue)
        assertEquals(NotificationDraft.TITLE_LIMIT + 1, viewModel.uiState.value.titleLength)

        viewModel.onTitleChange("Başlık")
        assertEquals(Issue.EMPTY_BODY, viewModel.uiState.value.issue)

        viewModel.onBodyChange("a".repeat(NotificationDraft.BODY_LIMIT + 1))
        assertEquals(Issue.BODY_TOO_LONG, viewModel.uiState.value.issue)

        viewModel.onBodyChange("Mesaj")
        assertNull(viewModel.uiState.value.issue)
        assertTrue(viewModel.uiState.value.canSend)
    }

    @Test
    fun `lengths are counted in code points, like the server`() = runTest {
        val viewModel = fixture.makeViewModel()
        // A family emoji is one grapheme but five code points; iOS counted it as 1, the server as 5.
        val family = "👨‍👩‍👧"

        viewModel.onTitleChange("  $family  ")

        assertEquals(5, viewModel.uiState.value.titleLength)
        viewModel.onTitleChange("a".repeat(NotificationDraft.TITLE_LIMIT - 4) + family)
        assertEquals(Issue.TITLE_TOO_LONG, viewModel.uiState.value.issue)
    }

    @Test
    fun `a segment needs a university`() = runTest {
        val viewModel = fixture.readyViewModel()
        advanceUntilIdle()

        viewModel.onAudienceModeChange(AudienceMode.SEGMENT)
        assertEquals(Issue.INVALID_AUDIENCE, viewModel.uiState.value.issue)

        viewModel.onUniversitySelected("sakarya")
        assertNull(viewModel.uiState.value.issue)
        assertEquals(
            NotificationAudience.Segment(NotificationSegment("sakarya")),
            viewModel.uiState.value.draft.audience,
        )
    }

    @Test
    fun `a link must be https, the other actions need nothing`() = runTest {
        val viewModel = fixture.readyViewModel()

        viewModel.onActionChoiceChange(ActionChoice.URL)
        assertEquals("An empty link is not 'no action'", Issue.INVALID_URL, viewModel.uiState.value.issue)

        viewModel.onUrlTextChange("http://example.com")
        assertEquals(Issue.INVALID_URL, viewModel.uiState.value.issue)

        viewModel.onUrlTextChange("  https://example.com/kampanya  ")
        assertNull(viewModel.uiState.value.issue)
        assertEquals(NotificationDraft.Action.Url("https://example.com/kampanya"), viewModel.uiState.value.draft.action)

        viewModel.onActionChoiceChange(ActionChoice.PAYWALL)
        assertEquals(NotificationDraft.Action.Paywall, viewModel.uiState.value.draft.action)
        viewModel.onActionChoiceChange(ActionChoice.ADD_EXAM)
        assertEquals(NotificationDraft.Action.AddExam, viewModel.uiState.value.draft.action)
        viewModel.onActionChoiceChange(ActionChoice.NONE)
        assertNull(viewModel.uiState.value.draft.action)
    }

    @Test
    fun `scheduling defaults to an hour ahead and the validity follows the delivery`() = runTest {
        val viewModel = fixture.readyViewModel()
        val now = clock.now

        viewModel.onSchedulingToggle(true)
        assertEquals(now + Duration.ofHours(1), viewModel.uiState.value.scheduledAt)
        assertNull(viewModel.uiState.value.issue)

        viewModel.onExpiryToggle(true)
        assertEquals(now + Duration.ofHours(1) + Duration.ofDays(7), viewModel.uiState.value.expiresAt)
        assertNull(viewModel.uiState.value.issue)

        viewModel.onExpiresAtChange(now + Duration.ofMinutes(30))
        assertEquals(Issue.EXPIRY_BEFORE_DELIVERY, viewModel.uiState.value.issue)

        viewModel.onScheduledAtChange(now - Duration.ofHours(3))
        assertEquals(Issue.SCHEDULE_IN_PAST, viewModel.uiState.value.issue)

        viewModel.onSchedulingToggle(false)
        viewModel.onExpiryToggle(false)
        assertNull(viewModel.uiState.value.scheduledAt)
        assertNull(viewModel.uiState.value.expiresAt)
    }

    @Test
    fun `turning scheduling on moves a validity that would end before the delivery`() = runTest {
        val viewModel = fixture.readyViewModel()
        val now = clock.now
        viewModel.onExpiryToggle(true)
        viewModel.onExpiresAtChange(now + Duration.ofMinutes(30))

        viewModel.onSchedulingToggle(true)

        assertEquals(now + Duration.ofHours(1) + Duration.ofDays(7), viewModel.uiState.value.expiresAt)
        assertNull(viewModel.uiState.value.issue)
    }

    // --- Facets and the cascade ---

    @Test
    fun `facets load once, and a failure can be retried`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = fixture.makeViewModel(service)
        service.facetsResult = Result.failure(NotificationAdminError.Offline)
        advanceUntilIdle()

        assertEquals(NotificationAdminError.Offline.userMessage, viewModel.uiState.value.facetsError)
        assertNull(viewModel.uiState.value.estimatedRecipients)
        assertEquals(1, service.audienceCalls)

        service.facetsResult = Result.success(NotificationComposerFixture.FACETS)
        advanceUntilIdle()
        assertEquals("Nothing reloads by itself", 1, service.audienceCalls)

        viewModel.reloadFacets()
        assertTrue("The retry shows the loading state", viewModel.uiState.value.isFacetsLoading)
        advanceUntilIdle()
        assertEquals(NotificationComposerFixture.FACETS, viewModel.uiState.value.facets)
        assertNull(viewModel.uiState.value.facetsError)
    }

    @Test
    fun `each picker lists only what the facets hold, sorted for a Turkish reader`() = runTest {
        val viewModel = fixture.makeViewModel()
        advanceUntilIdle()

        assertEquals(listOf("ankara", "sakarya", "zonguldak"), viewModel.uiState.value.universities.map { it.id })
        assertTrue("No university chosen yet", viewModel.uiState.value.faculties.isEmpty())

        viewModel.onUniversitySelected("sakarya")
        assertEquals(listOf("iibf", "muhendislik"), viewModel.uiState.value.faculties.map { it.id })
        assertTrue("No faculty chosen yet", viewModel.uiState.value.departments.isEmpty())

        viewModel.onFacultySelected("muhendislik")
        assertEquals(listOf("bilgisayar", "makine"), viewModel.uiState.value.departments.map { it.id })
    }

    @Test
    fun `changing a level resets the levels below it`() = runTest {
        val viewModel = fixture.makeViewModel()
        advanceUntilIdle()
        viewModel.onUniversitySelected("sakarya")
        viewModel.onFacultySelected("muhendislik")
        viewModel.onDepartmentSelected("makine")

        viewModel.onFacultySelected("iibf")
        with(viewModel.uiState.value) {
            assertEquals("sakarya", universityId)
            assertEquals("iibf", facultyId)
            assertNull("A new faculty clears the department", departmentId)
        }

        viewModel.onFacultySelected("muhendislik")
        viewModel.onDepartmentSelected("makine")
        viewModel.onUniversitySelected("ankara")
        assertNull(viewModel.uiState.value.facultyId)
        assertNull("A new university clears both levels below", viewModel.uiState.value.departmentId)

        viewModel.onUniversitySelected("sakarya")
        viewModel.onFacultySelected("muhendislik")
        viewModel.onDepartmentSelected("makine")
        viewModel.onUniversitySelected("sakarya")
        assertEquals("Picking the same university keeps the levels below", "makine", viewModel.uiState.value.departmentId)
    }

    @Test
    fun `a department cannot be chosen without a faculty`() = runTest {
        val viewModel = fixture.makeViewModel()
        advanceUntilIdle()
        viewModel.onUniversitySelected("sakarya")

        viewModel.onDepartmentSelected("makine")

        assertNull(viewModel.uiState.value.departmentId)
    }

    @Test
    fun `the estimate follows the audience, level and gender`() = runTest {
        val viewModel = fixture.makeViewModel()
        assertNull("Unknown until the facets arrive", viewModel.uiState.value.estimatedRecipients)
        advanceUntilIdle()

        assertEquals("Everyone = all registered profiles", 20, viewModel.uiState.value.estimatedRecipients)

        viewModel.onAudienceModeChange(AudienceMode.SEGMENT)
        assertNull("No university yet", viewModel.uiState.value.estimatedRecipients)

        viewModel.onUniversitySelected("sakarya")
        assertEquals(12, viewModel.uiState.value.estimatedRecipients)

        viewModel.onGenderSelected(Gender.FEMALE)
        assertEquals(7, viewModel.uiState.value.estimatedRecipients)

        viewModel.onFacultySelected("muhendislik")
        assertEquals(5, viewModel.uiState.value.estimatedRecipients)

        viewModel.onGenderSelected(Gender.MALE)
        viewModel.onDepartmentSelected("makine")
        assertEquals(2, viewModel.uiState.value.estimatedRecipients)

        viewModel.onGenderSelected(null)
        assertEquals(6, viewModel.uiState.value.estimatedRecipients)
    }

    // --- Android ---

    @Test
    fun `the screen view is reported on each appearance`() = runTest {
        val viewModel = fixture.makeViewModel()

        viewModel.onScreenShown()
        viewModel.onScreenShown()

        assertEquals(
            List(2) { AnalyticsEvent.ScreenView(AnalyticsScreen.NOTIFICATION_COMPOSER) },
            fixture.analytics.events,
        )
    }
}
