package com.salticusteam.vifi.core.model

// Added in 2.5 — archive tree levels in drill-down order (iOS ArchiveLevel parity).
/** The raw [depth] is the depth of a node of that level: `<university>/<faculty>/<department>/<lesson>/<exam>`. */
enum class ArchiveLevel(val depth: Int) {
    UNIVERSITY(1),
    FACULTY(2),
    DEPARTMENT(3),
    LESSON(4),
    EXAM(5);

    companion object {
        fun fromDepth(depth: Int): ArchiveLevel? = entries.firstOrNull { it.depth == depth }
    }
}

// Added in 2.5 — exam content type; the raw value is the backend contract ("JPG" | "PDF").
enum class ExamKind(val raw: String) {
    IMAGES("JPG"),
    PDF("PDF");

    companion object {
        fun fromRaw(raw: String?): ExamKind? = entries.firstOrNull { it.raw == raw }
    }
}

// Added in 2.5 — position of a node in the archive tree (iOS ArchivePath parity).
/**
 * Each level has a stable id ([components]) used for lookups, routing and identity, and a display name
 * ([displayNames]). On the Realtime Database (name-keyed) the two are equal; under Firestore the ids are
 * canonical slugs. Equality and hashing use the ids alone, so renaming a node never changes which
 * navigation route it is. The root path (no components) is the university list.
 */
class ArchivePath(
    val components: List<String>,
    displayNames: List<String>? = null,
) {
    /** Always the same length as [components]; falls back to the ids on a length mismatch. */
    val displayNames: List<String> =
        if (displayNames != null && displayNames.size == components.size) displayNames else components

    val depth: Int get() = components.size

    val isRoot: Boolean get() = components.isEmpty()

    /** Display name of the node this path points at (`null` for the root). */
    val displayName: String? get() = displayNames.lastOrNull()

    /** Level of the node this path points at (`null` for the root). */
    val level: ArchiveLevel? get() = ArchiveLevel.fromDepth(depth)

    /** Level of the entries listed under this path (`null` below exams). */
    val childLevel: ArchiveLevel? get() = ArchiveLevel.fromDepth(depth + 1)

    val parent: ArchivePath? get() = if (isRoot) null else prefix(depth - 1)

    /** Appends a level whose id and display name are the same (Realtime Database, name-keyed). */
    fun appending(name: String): ArchivePath = appending(id = name, displayName = name)

    /** Appends a level with a distinct stable id and display name (Firestore). */
    fun appending(id: String, displayName: String): ArchivePath =
        ArchivePath(components + id, displayNames + displayName)

    /** The ancestor made of the first [count] levels. */
    fun prefix(count: Int): ArchivePath =
        ArchivePath(components.take(count), displayNames.take(count))

    // Identity is the ids alone; display names never affect equality or hashing.
    override fun equals(other: Any?): Boolean = other is ArchivePath && other.components == components

    override fun hashCode(): Int = components.hashCode()

    override fun toString(): String = "ArchivePath(${components.joinToString("/")})"

    companion object {
        val ROOT = ArchivePath(emptyList())
    }
}
