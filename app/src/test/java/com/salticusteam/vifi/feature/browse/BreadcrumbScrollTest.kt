package com.salticusteam.vifi.feature.browse

import androidx.compose.foundation.ScrollState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class BreadcrumbScrollTest {

    @Test
    fun `scrollToEndOnceMeasured when not yet measured keeps the restored offset`() = runTest {
        // 700 is a restored offset that scrolling to Int.MAX_VALUE wraps to a negative value (QA-02).
        val restored = ScrollState(initial = 700)

        restored.scrollToEndOnceMeasured()

        assertEquals(700, restored.value)
    }
}
