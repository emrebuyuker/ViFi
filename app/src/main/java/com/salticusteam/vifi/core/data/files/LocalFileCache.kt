package com.salticusteam.vifi.core.data.files

import java.io.File
import java.security.MessageDigest

/**
 * On-disk layout and pruning of downloaded exam files (iOS `RemoteFileLoader` local files).
 *
 * Each file lives at `<directory>/<sha256 of its canonical URL>/<sanitized file name>`: one folder per source
 * file, so equal names from different exams never collide while the file keeps a readable name.
 */
object LocalFileCache {

    /** Stay well below the 255-byte file name limit, keeping the extension intact. */
    private const val MAX_BASE_LENGTH = 100
    private const val FORBIDDEN_CHARACTERS = "/\\:?%*|\"<>"
    private const val FALLBACK_NAME = "Dosya"

    fun fileFor(directory: File, canonicalUrl: String, fileName: String): File =
        File(File(directory, sha256(canonicalUrl)), sanitizedFileName(fileName))

    /** A single path component that is safe on disk and still recognisable when shared. */
    fun sanitizedFileName(fileName: String): String {
        val cleaned = buildString {
            for (c in fileName) append(if (c in FORBIDDEN_CHARACTERS || c.isISOControl()) '-' else c)
        }.trim { it.isWhitespace() || it == '.' }
        if (cleaned.isEmpty()) return FALLBACK_NAME

        val dot = cleaned.lastIndexOf('.')
        val baseName = if (dot > 0) cleaned.substring(0, dot) else cleaned
        val extension = if (dot > 0) cleaned.substring(dot + 1) else ""
        if (baseName.length <= MAX_BASE_LENGTH) return cleaned
        val truncated = baseName.take(MAX_BASE_LENGTH).trimEnd()
        return if (extension.isEmpty()) truncated else "$truncated.$extension"
    }

    /**
     * Deletes per-file folders under [directory], least recently used first, until the total is within
     * [budgetBytes]. Folders touched within [keepNewerThanMillis] of [nowMillis] are left alone, so a file
     * being written or just opened is never removed.
     */
    fun prune(directory: File, budgetBytes: Long, keepNewerThanMillis: Long, nowMillis: Long) {
        val folders = directory.listFiles()?.filter { !it.name.startsWith(".") } ?: return
        val entries = folders.map { Entry(it, sizeOf(it), it.lastModified()) }
        var total = entries.sumOf { it.size }
        if (total <= budgetBytes) return

        val protectedAfter = nowMillis - keepNewerThanMillis
        for (entry in entries.sortedBy { it.modified }) {
            if (total <= budgetBytes) break
            if (entry.modified >= protectedAfter) continue
            if (entry.file.deleteRecursively()) total -= entry.size
        }
    }

    private fun sizeOf(file: File): Long = file.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private class Entry(val file: File, val size: Long, val modified: Long)
}
