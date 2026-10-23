package com.salticusteam.vifi.core.data.files

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalFileCacheTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `sanitizedFileName replaces forbidden characters and trims dots and spaces`() {
        assertEquals("2018-1 VİZE.pdf", LocalFileCache.sanitizedFileName(" 2018/1 VİZE.pdf. "))
        assertEquals("a-b-c-d.pdf", LocalFileCache.sanitizedFileName("a:b?c*d.pdf"))
        assertEquals("Dosya", LocalFileCache.sanitizedFileName(" ... "))
    }

    @Test
    fun `sanitizedFileName truncates a long base name but keeps the extension`() {
        val name = LocalFileCache.sanitizedFileName("x".repeat(150) + ".pdf")
        assertEquals("x".repeat(100) + ".pdf", name)
    }

    @Test
    fun `fileFor uses one folder per url and keeps the readable name`() {
        val dir = tmp.root
        val a = LocalFileCache.fileFor(dir, "https://h/a", "Sınav.pdf")
        val b = LocalFileCache.fileFor(dir, "https://h/b", "Sınav.pdf")
        assertEquals("Sınav.pdf", a.name)
        assertEquals(64, a.parentFile!!.name.length)
        assertNotEquals(a.parentFile, b.parentFile)
        assertEquals(a, LocalFileCache.fileFor(dir, "https://h/a", "Sınav.pdf"))
    }

    @Test
    fun `prune removes oldest folders first until within budget`() {
        val now = 1_000_000L
        val oldest = folder("old", bytes = 100, modified = now - 300_000)
        val middle = folder("mid", bytes = 100, modified = now - 200_000)
        val newest = folder("new", bytes = 100, modified = now - 100_000)

        LocalFileCache.prune(tmp.root, budgetBytes = 150, keepNewerThanMillis = 60_000, nowMillis = now)

        assertFalse(oldest.exists())
        assertFalse(middle.exists())
        assertTrue(newest.exists())
    }

    @Test
    fun `prune keeps recently touched folders even over budget`() {
        val now = 1_000_000L
        val old = folder("old", bytes = 100, modified = now - 300_000)
        val recent = folder("recent", bytes = 100, modified = now - 10_000)

        LocalFileCache.prune(tmp.root, budgetBytes = 50, keepNewerThanMillis = 60_000, nowMillis = now)

        assertFalse(old.exists())
        assertTrue(recent.exists())
    }

    @Test
    fun `prune does nothing within budget or without a directory`() {
        val now = 1_000_000L
        val a = folder("a", bytes = 100, modified = now - 300_000)
        LocalFileCache.prune(tmp.root, budgetBytes = 1_000, keepNewerThanMillis = 0, nowMillis = now)
        LocalFileCache.prune(File(tmp.root, "missing"), budgetBytes = 0, keepNewerThanMillis = 0, nowMillis = now)
        assertTrue(a.exists())
    }

    private fun folder(name: String, bytes: Int, modified: Long): File {
        val folder = tmp.newFolder(name)
        File(folder, "f.pdf").writeBytes(ByteArray(bytes))
        folder.setLastModified(modified)
        return folder
    }
}
