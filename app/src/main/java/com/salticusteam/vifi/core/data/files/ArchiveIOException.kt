package com.salticusteam.vifi.core.data.files

import com.salticusteam.vifi.core.model.ArchiveError
import java.io.IOException

/**
 * Carries an [ArchiveError] through OkHttp: an interceptor may only throw `IOException`s (anything else
 * crashes the dispatcher thread of an asynchronous call, as Coil makes). Unwrapped by [toArchiveError].
 */
class ArchiveIOException(val error: ArchiveError) : IOException(error.userMessage, error)

/** The archive error behind a download failure: an unwrapped [ArchiveIOException], or [ArchiveError.Offline]. */
fun IOException.toArchiveError(): ArchiveError = when (this) {
    is ArchiveIOException -> error
    else -> ArchiveError.Offline
}
