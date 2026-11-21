package com.salticusteam.vifi.core.data.moderation

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.salticusteam.vifi.core.data.firebase.FirebaseProfileService
import com.salticusteam.vifi.core.data.repository.SubmitterProfileReader
import com.salticusteam.vifi.core.model.ProfileError
import com.salticusteam.vifi.core.model.SubmitterInfo
import com.salticusteam.vifi.core.util.LogCategory
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Live [SubmitterProfileReader] over the Realtime Database `users/<uid>/profile` node (ADM-03, iOS
 * `FirebaseSubmitterProfileReader`). Admin-only: the rules let an admin read any profile. The snapshot is parsed
 * inside the callback by [SubmitterInfoParser], so the phone number and gender never leave the data layer. Fails with
 * [ProfileError.Offline] after [timeout].
 */
class FirebaseSubmitterProfileReader internal constructor(
    private val database: FirebaseDatabase,
    private val timeout: Duration,
) : SubmitterProfileReader {

    @Inject
    constructor(database: FirebaseDatabase) : this(database, DEFAULT_TIMEOUT)

    override suspend fun submitter(uid: String): SubmitterInfo? =
        try {
            withContext(Dispatchers.IO) { withTimeout(timeout) { read(uid) } }
        } catch (e: TimeoutCancellationException) {
            throw ProfileError.Offline
        } catch (e: CancellationException) {
            throw e
        } catch (e: ProfileError) {
            throw e
        } catch (e: Exception) {
            LogCategory.APP.log.w(e, "Reading a submitter profile failed")
            throw FirebaseProfileService.profileError(e)
        }

    private suspend fun read(uid: String): SubmitterInfo? = suspendCancellableCoroutine { continuation ->
        val reference = database.getReference(ROOT_KEY).child(uid).child(PROFILE_KEY)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                continuation.resume(SubmitterInfoParser.info(snapshot.value))
            }

            override fun onCancelled(error: DatabaseError) {
                continuation.resumeWithException(FirebaseProfileService.profileError(error.code))
            }
        }
        reference.addListenerForSingleValueEvent(listener)
        continuation.invokeOnCancellation { reference.removeEventListener(listener) }
    }

    private companion object {
        const val ROOT_KEY = "users"
        const val PROFILE_KEY = "profile"
        val DEFAULT_TIMEOUT = 15.seconds
    }
}
