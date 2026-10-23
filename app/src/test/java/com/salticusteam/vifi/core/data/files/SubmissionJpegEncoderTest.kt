package com.salticusteam.vifi.core.data.files

import com.salticusteam.vifi.core.data.files.SubmissionJpegEncoder.OrientationTransform
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubmissionJpegEncoderTest {

    @Test
    fun `sampleSize keeps the decoded longest side at or above the limit`() {
        assertEquals(1, SubmissionJpegEncoder.sampleSize(2000, 1500))
        assertEquals(1, SubmissionJpegEncoder.sampleSize(5999, 4000))
        assertEquals(2, SubmissionJpegEncoder.sampleSize(6000, 4000))
        assertEquals(4, SubmissionJpegEncoder.sampleSize(3000, 12000))
    }

    @Test
    fun `targetSize when within the limit keeps the size`() {
        assertEquals(3000 to 2000, SubmissionJpegEncoder.targetSize(3000, 2000))
    }

    @Test
    fun `targetSize scales the longest side to the limit keeping the aspect`() {
        assertEquals(3000 to 2250, SubmissionJpegEncoder.targetSize(4032, 3024))
        assertEquals(1500 to 3000, SubmissionJpegEncoder.targetSize(2000, 4000))
        assertEquals(3000 to 1, SubmissionJpegEncoder.targetSize(9000, 1))
    }

    @Test
    fun `firstFitting steps the quality down until it fits`() {
        val tried = mutableListOf<Int>()
        val result = SubmissionJpegEncoder.firstFitting(maxBytes = 50) { quality ->
            tried += quality
            ByteArray(quality)
        }

        assertEquals(listOf(80, 60, 45), tried)
        assertArrayEquals(ByteArray(45), result)
    }

    @Test
    fun `firstFitting when nothing fits returns null`() {
        assertNull(SubmissionJpegEncoder.firstFitting(maxBytes = 10) { ByteArray(it) })
        assertNull(SubmissionJpegEncoder.firstFitting(maxBytes = 100) { null })
    }

    @Test
    fun `orientationTransform maps every EXIF orientation`() {
        assertEquals(OrientationTransform.IDENTITY, SubmissionJpegEncoder.orientationTransform(1))
        assertEquals(OrientationTransform.IDENTITY, SubmissionJpegEncoder.orientationTransform(0))
        assertEquals(OrientationTransform(0, true), SubmissionJpegEncoder.orientationTransform(2))
        assertEquals(OrientationTransform(180, false), SubmissionJpegEncoder.orientationTransform(3))
        assertEquals(OrientationTransform(180, true), SubmissionJpegEncoder.orientationTransform(4))
        assertEquals(OrientationTransform(270, true), SubmissionJpegEncoder.orientationTransform(5))
        assertEquals(OrientationTransform(90, false), SubmissionJpegEncoder.orientationTransform(6))
        assertEquals(OrientationTransform(90, true), SubmissionJpegEncoder.orientationTransform(7))
        assertEquals(OrientationTransform(270, false), SubmissionJpegEncoder.orientationTransform(8))
    }
}
