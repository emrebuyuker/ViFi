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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * Live [SubmitterProfileReader] over the Realtime Database `users/<uid>/profile` node (ADM-03, iOS
 * `FirebaseSubmitterProfileReader`). Admin-only: the rules let an admin read any profile.
 *
 * Unlike iOS (which reads the whole `profile` node and drops the rest in `SubmitterInfoParser`), only the five
 * display fields ([FIELDS]) are read, each on its own child reference, so the phone number and gender never reach this
 * device — not even the Realtime Database disk cache. Fails with [ProfileError.Offline] after [timeout].
 */
class FirebaseSubmitterProfileReader internal constructor(
    /** Reads one child of `users/<uid>/profile`; `null` when it does not exist. Throws `ProfileError`. */
    private val readField: suspend (uid: String, field: String) -> Any?,
    private val timeout: Duration,
) : SubmitterProfileReader {

    @Inject
    constructor(database: FirebaseDatabase) : this(
        readField = { uid, field -> readChild(database, uid, field) },
        timeout = DEFAULT_TIMEOUT,
    )

    override suspend fun submitter(uid: String): SubmitterInfo? =
        try {
            withContext(Dispatchers.IO) {
                withTimeout(timeout) {
                    val values = coroutineScope {
                        FIELDS.map { field -> async { field to readField(uid, field) } }.awaitAll()
                    }
                    SubmitterInfoParser.info(values.filter { it.second != null }.toMap())
                }
            }
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

    internal companion object {
        /** The only profile children read: what `SubmitterInfoParser` shows. Never `phoneNumber` or `gender`. */
        val FIELDS = listOf("name", "surname", "university", "faculty", "department")

        private const val ROOT_KEY = "users"
        private const val PROFILE_KEY = "profile"
        private val DEFAULT_TIMEOUT = 15.seconds

        private suspend fun readChild(database: FirebaseDatabase, uid: String, field: String): Any? =
            suspendCancellableCoroutine { continuation ->
                val reference = database.getReference(ROOT_KEY).child(uid).child(PROFILE_KEY).child(field)
                val listener = object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        continuation.resume(snapshot.value)
                    }

                    override fun onCancelled(error: DatabaseError) {
                        continuation.resumeWithException(FirebaseProfileService.profileError(error.code))
                    }
                }
                reference.addListenerForSingleValueEvent(listener)
                continuation.invokeOnCancellation { reference.removeEventListener(listener) }
            }
    }
}
