package com.salticusteam.vifi.core.data.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.salticusteam.vifi.core.data.profile.ProfileWriteContract
import com.salticusteam.vifi.core.data.profile.UserProfileParser
import com.salticusteam.vifi.core.data.repository.ProfileService
import com.salticusteam.vifi.core.model.GENERIC_ERROR_MESSAGE
import com.salticusteam.vifi.core.model.ProfileError
import com.salticusteam.vifi.core.model.UserProfile
import com.salticusteam.vifi.core.util.LogCategory
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Reads and writes the signed-in user's profile in the Realtime Database (`users/<uid>/profile`)
 * (iOS FirebaseProfileService).
 *
 * Reading and writing require a signed-in +90 user; the rules reject everything else. A read is answered from
 * the on-disk cache first when offline (persistence is set up by [FirebaseDatabaseSetup]). Every operation
 * fails with [ProfileError.Offline] after [timeout].
 */
class FirebaseProfileService internal constructor(
    private val database: FirebaseDatabase,
    private val auth: FirebaseAuth,
    private val timeout: Duration,
) : ProfileService {

    @Inject
    constructor(database: FirebaseDatabase, auth: FirebaseAuth) : this(database, auth, DEFAULT_TIMEOUT)

    override suspend fun loadProfile(uid: String): UserProfile? = run("Loading the profile") {
        val snapshot = suspendCancellableCoroutine { continuation ->
            val reference = profileReference(uid)
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    continuation.resume(snapshot)
                }

                override fun onCancelled(error: DatabaseError) {
                    continuation.resumeWithException(profileError(error.code))
                }
            }
            reference.addListenerForSingleValueEvent(listener)
            continuation.invokeOnCancellation { reference.removeEventListener(listener) }
        }
        // The node is tiny (13 leaves); parsing here keeps DataSnapshot inside the data layer.
        UserProfileParser.profile(snapshot.value)
    }

    override suspend fun saveProfile(profile: UserProfile, uid: String) = run("Saving the profile") {
        // The uid must be the signed-in user; the rules enforce this too, but failing early avoids a round trip.
        val user = auth.currentUser?.takeIf { it.uid == uid } ?: throw ProfileError.NotSignedIn
        val violations = ProfileWriteContract.violations(profile, user.phoneNumber)
        if (violations.isNotEmpty()) {
            // Field names only — never the values (personal data).
            LogCategory.APP.log.w("Profile write would break the rules on: %s", violations.joinToString())
            throw ProfileError.PermissionDenied
        }
        val values = UserProfileParser.fields(profile) + (UserProfileParser.UPDATED_AT_KEY to ServerValue.TIMESTAMP)
        profileReference(uid).updateChildren(values).await()
        Unit
    }

    override suspend fun deleteProfile(uid: String) = run("Deleting the profile") {
        // Removed while the user is still signed in, so the owner-only rules still permit the write; after the
        // account is deleted the node could never be reached again.
        if (auth.currentUser?.uid != uid) throw ProfileError.NotSignedIn
        profileReference(uid).removeValue().await()
        Unit
    }

    // Helpers
    private fun profileReference(uid: String): DatabaseReference =
        database.getReference(ROOT_KEY).child(uid).child(PROFILE_KEY)

    /** Runs [block] off the main thread with the timeout, mapping every failure to a [ProfileError]. */
    private suspend fun <T> run(operation: String, block: suspend () -> T): T =
        try {
            withContext(Dispatchers.IO) { withTimeout(timeout) { block() } }
        } catch (e: TimeoutCancellationException) {
            LogCategory.APP.log.e("%s timed out", operation)
            throw ProfileError.Offline
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = profileError(e)
            LogCategory.APP.log.e(e, "%s failed", operation)
            throw error
        }

    internal companion object {
        private const val ROOT_KEY = "users"
        private const val PROFILE_KEY = "profile"
        val DEFAULT_TIMEOUT = 15.seconds

        /** Maps the code a read listener is cancelled with (iOS `profileError(fromCancellation:)`). */
        fun profileError(code: Int): ProfileError = when (code) {
            DatabaseError.PERMISSION_DENIED -> ProfileError.PermissionDenied
            DatabaseError.NETWORK_ERROR, DatabaseError.DISCONNECTED -> ProfileError.Offline
            else -> ProfileError.Unknown(GENERIC_ERROR_MESSAGE)
        }

        /**
         * Maps an exception from a write Task (iOS `profileError(from:)`). RTDB reports a rejected write as a
         * `DatabaseException` whose message carries the reason ("Permission denied"), not a typed code.
         */
        fun profileError(error: Throwable): ProfileError = when {
            error is ProfileError -> error
            error is IOException -> ProfileError.Offline
            error.message?.contains("permission", ignoreCase = true) == true -> ProfileError.PermissionDenied
            error.message?.contains("network", ignoreCase = true) == true ||
                error.message?.contains("disconnected", ignoreCase = true) == true -> ProfileError.Offline
            // iOS shows localizedDescription here; Android never surfaces a raw exception message.
            else -> ProfileError.Unknown(GENERIC_ERROR_MESSAGE)
        }
    }
}
