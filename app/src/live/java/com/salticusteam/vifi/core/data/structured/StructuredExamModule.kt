package com.salticusteam.vifi.core.data.structured

import com.salticusteam.vifi.core.data.repository.ExamQuestionReader
import com.salticusteam.vifi.core.model.ArchiveError
import com.salticusteam.vifi.core.model.ExamQuestion
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Structured-exam bindings of the live flavor (4.2.0). SCT-01 wires a placeholder so the graph builds; Hat A (SQS-02)
 * replaces [UnavailableExamQuestionReader] with the Firestore reader (`questions where examId == X order by order`,
 * named `vifi` database, parsed by `ExamQuestionParser`).
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class StructuredExamModule {

    @Binds
    @Singleton
    abstract fun bindExamQuestionReader(impl: UnavailableExamQuestionReader): ExamQuestionReader
}

/** Placeholder until Hat A: every read fails as "not found", so a screen shows its error state instead of crashing. */
class UnavailableExamQuestionReader @Inject constructor() : ExamQuestionReader {
    override suspend fun questions(examId: String): List<ExamQuestion> = throw ArchiveError.NotFound
}
