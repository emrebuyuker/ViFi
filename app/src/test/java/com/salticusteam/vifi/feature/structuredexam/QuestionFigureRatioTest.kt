package com.salticusteam.vifi.feature.structuredexam

import com.salticusteam.vifi.core.model.QuestionFigure
import org.junit.Assert.assertEquals
import org.junit.Test

/** The figure placeholder's height / width (iOS `QuestionFigureView`): the stored size clamped to 0.1…2, else 2/3. */
class QuestionFigureRatioTest {

    private fun ratio(width: Int?, height: Int?) = QuestionFigure("p.jpg", width, height).heightRatio()

    @Test
    fun `a known size keeps its own ratio`() {
        assertEquals(800f / 1200f, ratio(1200, 800), 0.0001f)
        assertEquals(1.5f, ratio(400, 600), 0.0001f)
    }

    @Test
    fun `an unknown or partial size falls back to 3 by 2`() {
        assertEquals(2f / 3f, ratio(null, null), 0.0001f)
        assertEquals(2f / 3f, ratio(1200, null), 0.0001f)
        assertEquals(2f / 3f, ratio(null, 800), 0.0001f)
    }

    @Test
    fun `extreme sizes are clamped to 0_1 to 2`() {
        assertEquals(0.1f, ratio(5000, 10), 0.0001f)
        assertEquals(2f, ratio(100, 1000), 0.0001f)
    }
}
