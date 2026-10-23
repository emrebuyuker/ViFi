package com.salticusteam.vifi.core.data.firebase

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreException.Code
import com.salticusteam.vifi.core.data.firebase.FirestoreArchiveRepository.Companion.toArchiveError
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ArchivePath
import com.salticusteam.vifi.core.model.GENERIC_ERROR_MESSAGE
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** On device because FirebaseFirestoreException.Code initialises an Android SparseArray. */
@RunWith(AndroidJUnit4::class)
class FirestoreArchiveErrorMappingTest {

    @Test
    fun archiveError_mapsFirestoreCodesLikeIos() {
        // Rule rejection and App Check rejection (403) share PERMISSION_DENIED.
        assertEquals(ArchiveError.PermissionDenied, FirestoreArchiveRepository.archiveError(Code.PERMISSION_DENIED))
        assertEquals(ArchiveError.PermissionDenied, FirestoreArchiveRepository.archiveError(Code.UNAUTHENTICATED))
        assertEquals(ArchiveError.Offline, FirestoreArchiveRepository.archiveError(Code.UNAVAILABLE))
        assertEquals(ArchiveError.Offline, FirestoreArchiveRepository.archiveError(Code.DEADLINE_EXCEEDED))
        assertEquals(ArchiveError.NotFound, FirestoreArchiveRepository.archiveError(Code.NOT_FOUND))
        assertNull(FirestoreArchiveRepository.archiveError(Code.FAILED_PRECONDITION))
    }

    @Test
    fun toArchiveError_neverSurfacesARawMessage() {
        val path = ArchivePath.ROOT
        val denied = FirebaseFirestoreException("Missing or insufficient permissions.", Code.PERMISSION_DENIED)

        assertEquals(ArchiveError.PermissionDenied, denied.toArchiveError(path))
        assertEquals(ArchiveError.Offline, IOException("reset").toArchiveError(path))
        assertEquals(ArchiveError.NotFound, ArchiveError.NotFound.toArchiveError(path))
        assertEquals(
            ArchiveError.Unknown(GENERIC_ERROR_MESSAGE),
            FirebaseFirestoreException("index", Code.FAILED_PRECONDITION).toArchiveError(path),
        )
    }
}
