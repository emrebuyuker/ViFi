package com.salticusteam.vifi.feature.notificationcomposer

import com.salticusteam.vifi.core.model.NotificationDraft
import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.core.model.NotificationLocation
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** iOS `NotificationComposerLocationTests` (8): the Konum action, which is always a campaign. */
class NotificationComposerLocationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixture = NotificationComposerFixture()
    private val cafe = NotificationComposerFixture.CAFE

    @Test
    fun `choosing Konum without a place blocks sending and says why`() = runTest {
        val viewModel = fixture.readyViewModel()

        viewModel.onActionChoiceChange(ActionChoice.LOCATION)

        with(viewModel.uiState.value) {
            assertEquals(NotificationDraft.Issue.INVALID_LOCATION, issue)
            assertEquals(NotificationDraft.Issue.INVALID_LOCATION, visibleIssue)
            assertFalse(canSend)
            assertFalse(canSendTest)
            assertNull("It must not go out as if no action was chosen", draft.action)
        }
    }

    @Test
    fun `a picked place becomes the draft's location action`() = runTest {
        val viewModel = fixture.readyViewModel()
        viewModel.onActionChoiceChange(ActionChoice.LOCATION)

        viewModel.onLocationPicked(cafe)

        assertNull(viewModel.uiState.value.issue)
        assertTrue(viewModel.uiState.value.canSend)
        assertEquals(NotificationDraft.Action.Location(cafe), viewModel.uiState.value.draft.action)
    }

    @Test
    fun `a location notification always goes as a campaign, whatever the kind says`() = runTest {
        val viewModel = fixture.readyViewModel()
        viewModel.onKindSelected(NotificationKind.GENERAL)
        viewModel.onActionChoiceChange(ActionChoice.LOCATION)
        viewModel.onLocationPicked(cafe)

        assertEquals(NotificationKind.PROMO, viewModel.uiState.value.draft.effectiveKind)
        assertEquals("promo", viewModel.uiState.value.draft.payload()["kind"])
    }

    @Test
    fun `the place travels in the payload with its optional parts`() = runTest {
        val viewModel = fixture.readyViewModel()
        viewModel.onActionChoiceChange(ActionChoice.LOCATION)
        viewModel.onLocationPicked(cafe)

        val action = viewModel.uiState.value.draft.payload()["action"] as Map<*, *>
        assertEquals("location", action["type"])
        assertEquals(40.9903, action["latitude"])
        assertEquals(29.029, action["longitude"])
        assertEquals("ViFi Öğrenci Kafesi", action["name"])
        assertEquals("Caferağa, Kadıköy", action["address"])
        assertEquals("place-1", action["placeId"])

        viewModel.onLocationPicked(NotificationLocation(latitude = 1.0, longitude = 2.0, name = "Nokta"))
        val bare = viewModel.uiState.value.draft.payload()["action"] as Map<*, *>
        assertFalse(bare.containsKey("address"))
        assertFalse(bare.containsKey("placeId"))
    }

    @Test
    fun `a place the server would refuse blocks sending`() = runTest {
        val viewModel = fixture.readyViewModel()
        viewModel.onActionChoiceChange(ActionChoice.LOCATION)

        viewModel.onLocationPicked(NotificationLocation(latitude = 95.0, longitude = 0.0, name = "Kutup"))
        assertEquals(NotificationDraft.Issue.INVALID_LOCATION, viewModel.uiState.value.issue)

        viewModel.onLocationPicked(NotificationLocation(latitude = 1.0, longitude = 2.0, name = "  "))
        assertEquals(NotificationDraft.Issue.INVALID_LOCATION, viewModel.uiState.value.issue)

        viewModel.onLocationPicked(cafe)
        assertNull(viewModel.uiState.value.issue)
    }

    @Test
    fun `the picked place is kept while another action is chosen, but only a chosen Konum sends it`() = runTest {
        val viewModel = fixture.readyViewModel()
        viewModel.onActionChoiceChange(ActionChoice.LOCATION)
        viewModel.onLocationPicked(cafe)

        viewModel.onActionChoiceChange(ActionChoice.PAYWALL)
        assertEquals(NotificationDraft.Action.Paywall, viewModel.uiState.value.draft.action)
        assertEquals(NotificationKind.GENERAL, viewModel.uiState.value.draft.effectiveKind)

        viewModel.onActionChoiceChange(ActionChoice.NONE)
        assertNull(viewModel.uiState.value.draft.action)

        viewModel.onActionChoiceChange(ActionChoice.LOCATION)
        assertEquals(
            "Coming back finds the place still chosen",
            NotificationDraft.Action.Location(cafe),
            viewModel.uiState.value.draft.action,
        )
    }

    @Test
    fun `send previews and sends the campaign, then clears the place for the next notification`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = fixture.readyViewModel(service)
        viewModel.onKindSelected(NotificationKind.GENERAL)
        viewModel.onActionChoiceChange(ActionChoice.LOCATION)
        viewModel.onLocationPicked(cafe)

        viewModel.requestSend()
        advanceUntilIdle()
        assertEquals(
            "The reach shown is the campaign audience, who opted in",
            NotificationKind.PROMO,
            service.previewRequests.single().first,
        )
        viewModel.confirmSend()
        advanceUntilIdle()

        val sent = service.sentDrafts.single()
        assertEquals(NotificationDraft.Action.Location(cafe), sent.action)
        assertEquals(NotificationKind.PROMO, sent.effectiveKind)
        assertNotNull(viewModel.uiState.value.success)
        assertNull(viewModel.uiState.value.location)
        assertEquals(ActionChoice.NONE, viewModel.uiState.value.actionChoice)
    }

    @Test
    fun `a test send carries the place too`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = fixture.readyViewModel(service)
        viewModel.onActionChoiceChange(ActionChoice.LOCATION)
        viewModel.onLocationPicked(cafe)

        viewModel.sendTest()
        advanceUntilIdle()

        val sent = service.sentDrafts.single()
        assertTrue(sent.isTest)
        assertEquals(NotificationDraft.Action.Location(cafe), sent.action)
    }
}
