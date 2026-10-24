package com.salticusteam.vifi.core.data.submission

import com.salticusteam.vifi.core.model.SubmissionFile

/**
 * Keeps the prepared pages of an "Sınav Ekle" draft on disk, one directory per draft, so they survive process
 * death (the form restores them by the draft id it keeps in its saved state) and can be uploaded by a background
 * worker after the form is gone. A queued submit adds a [PendingSubmission] manifest to the same directory; while
 * it is there the upload owns the directory and the form must not discard it.
 *
 * Writes throw `IOException`; reads of a missing or unreadable draft return an empty result. Everything runs off
 * the main thread.
 */
interface SubmissionDraftStore {
    /** The id for a new, empty draft. */
    fun newDraftId(): String

    /** Stores [files] under their `order`, replacing pages with the same order. */
    suspend fun writePages(draftId: String, files: List<SubmissionFile>)

    /** Removes every page of the draft. */
    suspend fun clearPages(draftId: String)

    /** The draft's pages in ascending order; empty when there are none or they cannot be read. */
    suspend fun readPages(draftId: String): List<SubmissionFile>

    suspend fun writeManifest(draftId: String, pending: PendingSubmission)

    /** The queued submission, or `null` when nothing is queued (or the manifest cannot be read). */
    suspend fun readManifest(draftId: String): PendingSubmission?

    suspend fun deleteManifest(draftId: String)

    /** Deletes the whole draft (pages and manifest). */
    suspend fun delete(draftId: String)

    /** Deletes the draft unless an upload is queued for it (its manifest exists). */
    suspend fun discardIfNotQueued(draftId: String)

    /** Deletes abandoned drafts (e.g. a form whose task was swiped away), never the one with id [keep]. */
    suspend fun pruneStale(keep: String)
}
