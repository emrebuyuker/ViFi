package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseException
import com.salticusteam.vifi.core.model.GENERIC_ERROR_MESSAGE
import com.salticusteam.vifi.core.model.ProfileError
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class FirebaseProfileServiceTest {

    @Test
    fun `profileError when read cancelled maps database codes`() {
        assertEquals(ProfileError.PermissionDenied, FirebaseProfileService.profileError(DatabaseError.PERMISSION_DENIED))
        assertEquals(ProfileError.Offline, FirebaseProfileService.profileError(DatabaseError.NETWORK_ERROR))
        assertEquals(ProfileError.Offline, FirebaseProfileService.profileError(DatabaseError.DISCONNECTED))
        assertEquals(ProfileError.Unknown(GENERIC_ERROR_MESSAGE), FirebaseProfileService.profileError(DatabaseError.UNKNOWN_ERROR))
    }

    @Test
    fun `profileError when write fails maps the exception`() {
        assertEquals(
            ProfileError.PermissionDenied,
            FirebaseProfileService.profileError(DatabaseException("Firebase Database error: Permission denied")),
        )
        assertEquals(ProfileError.Offline, FirebaseProfileService.profileError(IOException("reset")))
        assertEquals(ProfileError.NotSignedIn, FirebaseProfileService.profileError(ProfileError.NotSignedIn))
        assertEquals(
            ProfileError.Unknown(GENERIC_ERROR_MESSAGE),
            FirebaseProfileService.profileError(IllegalStateException("boom")),
        )
    }
}
