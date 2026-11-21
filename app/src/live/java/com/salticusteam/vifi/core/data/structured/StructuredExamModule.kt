package com.salticusteam.vifi.core.data.structured

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import com.google.firebase.storage.FirebaseStorage
import com.salticusteam.vifi.core.data.firebase.ExamQuestionParser
import com.salticusteam.vifi.core.data.firebase.FirestoreArchiveRepository
import com.salticusteam.vifi.core.data.repository.ExamQuestionReader
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ExamQuestion
import com.salticusteam.vifi.core.model.GENERIC_ERROR_MESSAGE
import com.salticusteam.vifi.core.util.LogCategory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** Structured-exam bindings of the live flavor (4.2.0): the Firestore question reader and the figure URLs. */
@Module
@InstallIn(SingletonComponent::class)
abstract class StructuredExamModule {

    @Binds
    @Singleton
    abstract fun bindExamQuestionReader(impl: FirestoreExamQuestionReader): ExamQuestionReader

    companion object {
        /** Figures live in the app's own bucket (`questionFigures/<jobId>/<nnn>-<k>.jpg`). */
        @Provides
        @Singleton
        fun provideQuestionFigureUrlResolver(storage: FirebaseStorage): QuestionFigureUrlResolver =
            StorageQuestionFigureUrlResolver(storage.reference.bucket)
    }
}

/**
 * Reads an exam's published questions from the named `vifi` Firestore database (iOS `FirebaseExamQuestionReader`):
 * `questions where examId == X order by order`, about 10 to 30 reads per exam, served by the `questions (examId,
 * order)` composite index. Nothing prefetches questions. Documents are parsed by the Firestore-free
 * [ExamQuestionParser]; top-level `Timestamp` values become [java.time.Instant]s here first. Reading is gated by the
 * rules to signed-in +90 users and admins.
 */
class FirestoreExamQuestionReader @Inject constructor(
    private val firestore: FirebaseFirestore,
) : ExamQuestionReader {

    override suspend fun questions(examId: String): List<ExamQuestion> {
        val rows = try {
            withContext(Dispatchers.IO) {
                firestore.collection(COLLECTION)
                    .whereEqualTo(ExamQuestionParser.Field.EXAM_ID, examId)
                    .orderBy(ExamQuestionParser.Field.ORDER)
                    .get(Source.DEFAULT)
                    .await()
                    .documents
                    .map { it.id to convertingTimestamps(it.data.orEmpty()) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw e.toQuestionReadError(examId)
        }
        return withContext(Dispatchers.Default) { ExamQuestionParser.questions(rows, examId) }
    }

    internal companion object {
        const val COLLECTION = "questions"

        /** Replaces top-level `Timestamp` values with `Instant`s, so the parser stays free of Firestore types. */
        fun convertingTimestamps(data: Map<String, Any?>): Map<String, Any?> =
            data.mapValues { (_, value) -> (value as? Timestamp)?.toDate()?.toInstant() ?: value }

        /** The archive's mapping (iOS `archiveError(from:)`); anything unexpected is logged, never shown raw. */
        fun Throwable.toQuestionReadError(examId: String): ArchiveError {
            val mapped = when (this) {
                is ArchiveError -> this
                is FirebaseFirestoreException -> FirestoreArchiveRepository.archiveError(code)
                is IOException -> ArchiveError.Offline
                else -> null
            }
            if (mapped != null) return mapped
            LogCategory.ARCHIVE.log.e(this, "Firestore read of the questions of %s failed", examId)
            return ArchiveError.Unknown(GENERIC_ERROR_MESSAGE)
        }
    }
}
