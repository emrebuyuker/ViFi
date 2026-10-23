package com.salticusteam.vifi.core.model

import java.time.Instant
import java.util.Date
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnnouncementParserTest {

    @Test
    fun `parse reads title body and a Date as createdAt`() {
        val at = Instant.parse("2026-10-20T09:00:00Z")

        val result = AnnouncementParser.parse("a1", mapOf("title" to " Merhaba ", "body" to "Metin", "createdAt" to Date.from(at)))

        assertEquals(Announcement("a1", "Merhaba", "Metin", at), result)
    }

    @Test
    fun `parse accepts epoch millis and leaves an unknown date null`() {
        assertEquals(Instant.ofEpochMilli(5), AnnouncementParser.parse("a", mapOf("title" to "t", "createdAt" to 5L))?.createdAt)
        assertNull(AnnouncementParser.parse("a", mapOf("title" to "t", "createdAt" to "yesterday"))?.createdAt)
    }

    @Test
    fun `parse drops a document without any text`() {
        assertNull(AnnouncementParser.parse("a", mapOf("title" to "  ", "createdAt" to 1L)))
        assertNull(AnnouncementParser.parse("a", emptyMap()))
    }

    @Test
    fun `newestFirst orders by date and puts undated last`() {
        val old = Announcement("old", "t", "", Instant.parse("2026-01-01T00:00:00Z"))
        val new = Announcement("new", "t", "", Instant.parse("2026-02-01T00:00:00Z"))
        val undated = Announcement("undated", "t", "", null)

        assertEquals(listOf(new, old, undated), AnnouncementParser.newestFirst(listOf(undated, old, new)))
    }
}
