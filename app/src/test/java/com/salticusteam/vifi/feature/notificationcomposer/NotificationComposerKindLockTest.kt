package com.salticusteam.vifi.feature.notificationcomposer

import com.salticusteam.vifi.core.model.NotificationKind
import com.salticusteam.vifi.testing.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * iOS `NotificationComposerKindLockTests` (7): while the action is Konum the "Tür" control shows Kampanya and cannot
 * be changed, and the admin's own choice comes back when another action is chosen.
 */
class NotificationComposerKindLockTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val fixture = NotificationComposerFixture()

    @Test
    fun `without a location action the kind is the admin's choice and can be changed`() = runTest {
        val viewModel = fixture.readyViewModel()
        assertFalse(viewModel.uiState.value.isKindLocked)
        assertEquals(NotificationKind.GENERAL, viewModel.uiState.value.effectiveKind)

        viewModel.onKindSelected(NotificationKind.PROMO)
        assertEquals(NotificationKind.PROMO, viewModel.uiState.value.kind)
        assertEquals(NotificationKind.PROMO, viewModel.uiState.value.effectiveKind)

        for (choice in ActionChoice.entries - ActionChoice.LOCATION) {
            viewModel.onActionChoiceChange(choice)
            assertFalse("$choice must not lock the kind", viewModel.uiState.value.isKindLocked)
            assertEquals(NotificationKind.PROMO, viewModel.uiState.value.effectiveKind)
        }
    }

    @Test
    fun `choosing Konum shows Kampanya and locks the control, even before a place is picked`() = runTest {
        val viewModel = fixture.readyViewModel()
        viewModel.onKindSelected(NotificationKind.GENERAL)

        viewModel.onActionChoiceChange(ActionChoice.LOCATION)

        with(viewModel.uiState.value) {
            assertTrue(isKindLocked)
            assertEquals(NotificationKind.PROMO, effectiveKind)
            assertEquals("The draft already says campaign; only the missing place blocks sending", NotificationKind.PROMO, draft.kind)
            assertEquals(NotificationKind.PROMO, draft.effectiveKind)
        }
    }

    @Test
    fun `a change to the locked control is ignored`() = runTest {
        val viewModel = fixture.readyViewModel()
        viewModel.onKindSelected(NotificationKind.GENERAL)
        viewModel.onActionChoiceChange(ActionChoice.LOCATION)

        viewModel.onKindSelected(NotificationKind.GENERAL)

        assertEquals(NotificationKind.PROMO, viewModel.uiState.value.effectiveKind)
        assertEquals("The admin's own choice is left as it was", NotificationKind.GENERAL, viewModel.uiState.value.kind)
    }

    @Test
    fun `switching away from Konum restores the admin's previous choice`() = runTest {
        val viewModel = fixture.readyViewModel()

        viewModel.onKindSelected(NotificationKind.GENERAL)
        viewModel.onActionChoiceChange(ActionChoice.LOCATION)
        viewModel.onActionChoiceChange(ActionChoice.PAYWALL)
        assertFalse(viewModel.uiState.value.isKindLocked)
        assertEquals(NotificationKind.GENERAL, viewModel.uiState.value.effectiveKind)
        assertEquals(NotificationKind.GENERAL, viewModel.uiState.value.draft.kind)

        viewModel.onKindSelected(NotificationKind.PROMO)
        viewModel.onActionChoiceChange(ActionChoice.LOCATION)
        assertEquals(NotificationKind.PROMO, viewModel.uiState.value.effectiveKind)
        viewModel.onActionChoiceChange(ActionChoice.NONE)
        assertEquals(NotificationKind.PROMO, viewModel.uiState.value.effectiveKind)
    }

    @Test
    fun `going back and forth keeps whatever was chosen last while unlocked`() = runTest {
        val viewModel = fixture.readyViewModel()
        viewModel.onKindSelected(NotificationKind.PROMO)

        viewModel.onActionChoiceChange(ActionChoice.LOCATION)
        viewModel.onActionChoiceChange(ActionChoice.URL)
        viewModel.onKindSelected(NotificationKind.GENERAL)
        viewModel.onActionChoiceChange(ActionChoice.LOCATION)

        assertEquals(NotificationKind.PROMO, viewModel.uiState.value.effectiveKind)
        viewModel.onActionChoiceChange(ActionChoice.ADD_EXAM)
        assertEquals(NotificationKind.GENERAL, viewModel.uiState.value.effectiveKind)
    }

    @Test
    fun `a location campaign is previewed and sent as a campaign, then the kind goes back to Duyuru`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = fixture.readyViewModel(service)
        viewModel.onKindSelected(NotificationKind.GENERAL)
        viewModel.onActionChoiceChange(ActionChoice.LOCATION)
        viewModel.onLocationPicked(NotificationComposerFixture.CAFE)

        viewModel.requestSend()
        advanceUntilIdle()
        assertEquals(NotificationKind.PROMO, service.previewRequests.first().first)
        viewModel.confirmSend()
        advanceUntilIdle()

        val sent = service.sentDrafts.single()
        assertEquals(NotificationKind.PROMO, sent.kind)
        assertEquals("promo", sent.payload()["kind"])
        assertFalse("The form is reset for the next notification", viewModel.uiState.value.isKindLocked)
        assertEquals(NotificationKind.GENERAL, viewModel.uiState.value.effectiveKind)
    }

    @Test
    fun `the locked control does not stop a test from being a campaign too`() = runTest {
        val service = StubNotificationAdminService()
        val viewModel = fixture.readyViewModel(service)
        viewModel.onActionChoiceChange(ActionChoice.LOCATION)
        viewModel.onLocationPicked(NotificationComposerFixture.CAFE)

        viewModel.sendTest()
        advanceUntilIdle()

        val sent = service.sentDrafts.single()
        assertTrue(sent.isTest)
        assertEquals(NotificationKind.PROMO, sent.kind)
    }
}
