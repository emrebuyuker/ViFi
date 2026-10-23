package com.salticusteam.vifi.fake

import com.salticusteam.vifi.core.data.repository.PickedPdf
import com.salticusteam.vifi.core.data.repository.SubmissionFileSource

/** Serves canned picked files by URI; any URI not configured is an unusable image / unreadable PDF. */
class FakeSubmissionFileSource(
    var images: Map<String, ByteArray> = emptyMap(),
    var pdfs: Map<String, PickedPdf> = emptyMap(),
) : SubmissionFileSource {

    val requestedImages = mutableListOf<String>()

    override suspend fun jpegPage(uri: String, maxBytes: Int): ByteArray? {
        requestedImages += uri
        return images[uri]
    }

    override suspend fun pdf(uri: String, maxBytes: Int): PickedPdf = pdfs[uri] ?: PickedPdf.Unreadable
}
