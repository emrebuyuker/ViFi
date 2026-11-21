package com.salticusteam.vifi.core.data.repository.fake

import com.salticusteam.vifi.core.data.conversion.ConversionFileUrls
import com.salticusteam.vifi.core.model.SubmissionFileRef

/** Sample-host URLs for a job's original pages (mock flavor, previews, tests); nothing is downloaded from them. */
class FakeConversionFileUrls : ConversionFileUrls {

    override fun urls(files: List<SubmissionFileRef>): List<String> =
        files.sortedBy { it.order }.map { "https://example.com/${it.storagePath}" }
}
