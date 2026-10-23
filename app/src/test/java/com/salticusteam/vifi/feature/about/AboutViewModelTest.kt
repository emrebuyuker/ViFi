package com.salticusteam.vifi.feature.about

import com.salticusteam.vifi.core.data.repository.fake.FakeAnalyticsTracker
import com.salticusteam.vifi.core.model.AnalyticsEvent
import com.salticusteam.vifi.core.model.AnalyticsScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class AboutViewModelTest {

    @Test
    fun `onScreenShown reports the About screen view`() {
        val analytics = FakeAnalyticsTracker()

        AboutViewModel(analytics).onScreenShown()

        assertEquals(listOf<AnalyticsEvent>(AnalyticsEvent.ScreenView(AnalyticsScreen.ABOUT)), analytics.events)
    }
}
