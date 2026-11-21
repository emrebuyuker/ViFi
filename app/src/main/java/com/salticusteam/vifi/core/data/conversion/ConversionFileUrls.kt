package com.salticusteam.vifi.core.data.conversion

import com.google.firebase.storage.FirebaseStorage
import com.salticusteam.vifi.core.model.SubmissionFileRef
import com.salticusteam.vifi.core.util.StorageUrl
import javax.inject.Inject

/**
 * The download URLs of a conversion job's original pages, for the admin's side-by-side review (iOS
 * `SubmissionFilePreview(files:kind:)`). The URLs carry no token: the credentialled file loader adds the admin's ID
 * token and App Check header, as for every exam file.
 *
 * Added in 4.2.0 — the original-pages view of a job.
 */
interface ConversionFileUrls {

    /** [files] in reading order (`order`), as plain download URLs; a file without a usable path is dropped. */
    fun urls(files: List<SubmissionFileRef>): List<String>
}

/** The live implementation: canonical Storage URLs in the app's bucket. */
class StorageConversionFileUrls @Inject constructor(
    storage: FirebaseStorage,
) : ConversionFileUrls {

    private val bucket: String = storage.reference.bucket

    override fun urls(files: List<SubmissionFileRef>): List<String> =
        files.sortedBy { it.order }.mapNotNull { StorageUrl.canonical(it.storagePath, bucket) }
}
