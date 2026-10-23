package com.salticusteam.vifi.core.data.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamFileUrlsTest {

    private val bucket = "vifi-831a8.appspot.com"
    private val canonical = "https://firebasestorage.googleapis.com/v0/b/$bucket/o/imagess%2Fa%20b.jpg?alt=media"

    @Test
    fun `canonical drops the token and keeps alt=media encoded as it was`() {
        assertEquals(canonical, ExamFileUrls.canonical("$canonical&token=abc-123"))
        assertEquals(canonical, ExamFileUrls.canonical(canonical.replace("?alt=media", "?token=abc&alt=media")))
    }

    @Test
    fun `canonical removes the query when token was its only item`() {
        val base = "https://firebasestorage.googleapis.com/v0/b/$bucket/o/x.pdf"
        assertEquals(base, ExamFileUrls.canonical("$base?token=abc"))
    }

    @Test
    fun `canonical leaves other urls and token-free urls unchanged`() {
        assertEquals("https://example.com/a.jpg?token=1", ExamFileUrls.canonical("https://example.com/a.jpg?token=1"))
        assertEquals(canonical, ExamFileUrls.canonical(canonical))
    }

    @Test
    fun `isObject accepts only the app bucket over https`() {
        assertTrue(ExamFileUrls.isObject(canonical, bucket))
        assertFalse(ExamFileUrls.isObject(canonical, "other-bucket.appspot.com"))
        assertFalse(ExamFileUrls.isObject(canonical.replace("https://", "http://"), bucket))
        assertFalse(ExamFileUrls.isObject("https://example.com/v0/b/$bucket/o/x.jpg", bucket))
        assertFalse(ExamFileUrls.isObject(canonical, ""))
    }
}
