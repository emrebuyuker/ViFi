package com.salticusteam.vifi.core.data.firebase

import com.salticusteam.vifi.core.model.ArchiveItem
import com.salticusteam.vifi.core.model.ArchiveLevel
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.ExamDocument
import com.salticusteam.vifi.core.model.ExamKind
import com.salticusteam.vifi.core.model.ExamSummary
import com.salticusteam.vifi.core.util.StorageUrl

/**
 * Turns raw Cloud Firestore documents (`DocumentSnapshot.getData()`) into archive models (iOS
 * `FirestoreArchiveParser`, lockstep). Pure and Firestore-free, so it is unit-tested with plain maps.
 *
 * Firestore shape (the read side of the shared migration contract):
 * - `catalogNodes/{nodeId}`: `{ name, parentId, status, childCount, … }` — one document per university / faculty /
 *   department / course; a level lists with `parentId == <node id>` (universities use [ROOT_PARENT_ID]), filtered
 *   to `status == published`.
 * - `exams/{examId}`: `{ title, courseId, kind: "JPG"|"PDF", status, files: [{ storagePath, order }] }` — the exams
 *   of a course, listed with `courseId == <course id>`.
 */
object FirestoreArchiveParser {

    object Field {
        const val NAME = "name"
        const val TITLE = "title"
        const val PARENT_ID = "parentId"
        const val COURSE_ID = "courseId"
        const val STATUS = "status"
        const val CHILD_COUNT = "childCount"
        const val KIND = "kind"
        const val FILES = "files"
        const val STORAGE_PATH = "storagePath"
        const val ORDER = "order"
    }

    /** Documents with this status are the published archive shown to users. */
    const val PUBLISHED_STATUS = "published"

    /** `parentId` of a top-level (university) catalog node, so every level lists uniformly by `parentId`. */
    const val ROOT_PARENT_ID = ""

    /**
     * The entries listed under [path], built from the raw documents (id to data) and sorted for display.
     * `path.childLevel` decides whether the documents are catalog nodes or exams; an entry without a name, or an
     * exam without a known kind or any file, is left out (iOS parity).
     */
    fun items(docs: List<Pair<String, Map<String, Any?>>>, path: ArchivePath): List<ArchiveItem> {
        val childLevel = path.childLevel ?: return emptyList()
        val items = docs.mapNotNull { (id, data) ->
            val displayName = displayName(data, childLevel)
            if (displayName.isNullOrEmpty()) return@mapNotNull null
            val childPath = path.appending(id = id, displayName = displayName)
            if (childLevel == ArchiveLevel.EXAM) {
                val summary = examSummary(data) ?: return@mapNotNull null
                ArchiveItem(childPath, ArchiveLevel.EXAM, childCount = null, exam = summary)
            } else {
                ArchiveItem(childPath, childLevel, childCount = childCount(data), exam = null)
            }
        }
        return ArchiveSort.sorted(items, childLevel)
    }

    /** The exam stored in [data], or `null` when its kind is unknown or it has no usable files. */
    fun document(data: Map<String, Any?>, path: ArchivePath, bucket: String): ExamDocument? {
        val kind = examKind(data) ?: return null
        val urls = fileEntries(data).mapNotNull { entry ->
            (entry[Field.STORAGE_PATH] as? String)?.let { StorageUrl.canonical(it, bucket) }
        }
        if (urls.isEmpty()) return null
        return ExamDocument(path, kind, urls)
    }

    // Helpers

    /** Exams denormalise their display name as `title`; catalog nodes use `name`. */
    private fun displayName(data: Map<String, Any?>, level: ArchiveLevel): String? =
        data[if (level == ArchiveLevel.EXAM) Field.TITLE else Field.NAME] as? String

    /** Firestore integers arrive as `Long`. */
    private fun childCount(data: Map<String, Any?>): Int? =
        (data[Field.CHILD_COUNT] as? Number)?.toInt()?.coerceAtLeast(0)

    private fun examKind(data: Map<String, Any?>): ExamKind? = ExamKind.fromRaw(data[Field.KIND] as? String)

    private fun examSummary(data: Map<String, Any?>): ExamSummary? {
        val kind = examKind(data) ?: return null
        val count = fileEntries(data).size
        if (count == 0) return null
        return ExamSummary(kind, count)
    }

    /** The file entries with a non-empty `storagePath`, in `order` (stable for equal or absent orders). */
    private fun fileEntries(data: Map<String, Any?>): List<Map<*, *>> =
        (data[Field.FILES] as? List<*>).orEmpty()
            .filterIsInstance<Map<*, *>>()
            .filter { (it[Field.STORAGE_PATH] as? String)?.isNotEmpty() == true }
            .withIndex()
            .sortedWith(compareBy({ (it.value[Field.ORDER] as? Number)?.toInt() ?: it.index }, { it.index }))
            .map { it.value }
}
