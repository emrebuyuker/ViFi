package com.salticusteam.vifi.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The practice events' wire format (iOS `FirebaseAnalyticsTracker` names and parameters). */
class PracticeAnalyticsMappingTest {

    @Test
    fun `practice events keep the iOS names`() {
        assertEquals("practice_started", AnalyticsEvent.PracticeStarted(ExamType.FINAL, PracticeScope.TURKEY).firebaseName)
        assertEquals("practice_answer_revealed", AnalyticsEvent.PracticeAnswerRevealed.firebaseName)
        assertEquals("practice_limit_reached", AnalyticsEvent.PracticeLimitReached.firebaseName)
        assertEquals("practice_paywall_opened", AnalyticsEvent.PracticePaywallOpened.firebaseName)
    }

    @Test
    fun `the start carries the exam type and the scope, the others have no parameters`() {
        assertEquals(
            mapOf("exam_type" to "ara-sinav", "scope" to "university"),
            AnalyticsEvent.PracticeStarted(ExamType.ARA_SINAV, PracticeScope.UNIVERSITY).firebaseParameters,
        )
        assertEquals(
            mapOf("exam_type" to "final", "scope" to "tr"),
            AnalyticsEvent.PracticeStarted(ExamType.FINAL, PracticeScope.TURKEY).firebaseParameters,
        )
        assertNull(AnalyticsEvent.PracticeAnswerRevealed.firebaseParameters)
        assertNull(AnalyticsEvent.PracticeLimitReached.firebaseParameters)
        assertNull(AnalyticsEvent.PracticePaywallOpened.firebaseParameters)
    }

    @Test
    fun `the practice screens keep the iOS screen names`() {
        assertEquals("PracticeSetup", AnalyticsScreen.PRACTICE_SETUP)
        assertEquals("PracticeSession", AnalyticsScreen.PRACTICE_SESSION)
    }
}
